// Halaman tamu Stream By Natan: ruang tunggu (antre), WebRTC ke host, berbagi layar,
// mikrofon bisa dibisukan host tapi bisa dinyalakan lagi sendiri, dan bisa dikeluarkan host.
(function () {
  const params = new URLSearchParams(location.search);
  const room = params.get('room');
  const mode = params.get('mode') === 'camera' ? 'camera' : 'guest';
  const $ = (id) => document.getElementById(id);
  const say = (t) => { $('status').textContent = t; };

  if (!room) { $('intro').textContent = 'Link undangan tidak lengkap. Minta link baru ke host.'; $('joinCard').hidden = true; return; }
  if (mode === 'camera') {
    $('intro').textContent = 'HP ini akan menjadi kamera tambahan untuk siaran.';
    document.querySelector('label[for=name]').textContent = 'Nama kamera (misal: kamera mimbar)';
  }

  try { const saved = localStorage.getItem(mode === 'camera' ? 'sbn_nama_kamera' : 'sbn_nama'); if (saved) $('name').value = saved; } catch (e) {}

  firebase.initializeApp(window.FIREBASE_CONFIG);
  const db = firebase.firestore();
  const peersCol = db.collection('rooms').doc(room).collection('peers');

  let peerRef = null, pc = null, camStream = null, screenStream = null, videoSender = null;
  let micOn = true, camOn = true, facing = mode === 'camera' ? 'environment' : 'user';
  let lastHostMute = 0, unsub = [];
  let filter = 'normal';
  try { filter = localStorage.getItem('sbn_filter') || 'normal'; } catch (e) {}
  const FILTERS = [
    ['normal', 'Normal', 'none'],
    ['cerah', 'Cerah', 'brightness(1.12) contrast(1.05)'],
    ['hangat', 'Hangat', 'sepia(0.2) saturate(1.2)'],
    ['sejuk', 'Sejuk', 'hue-rotate(-10deg) saturate(1.1)'],
    ['sinematik', 'Sinematik', 'contrast(1.18) saturate(0.85)'],
    ['vintage', 'Vintage', 'sepia(0.55) contrast(0.92)'],
    ['hitamputih', 'Hitam putih', 'grayscale(1)'],
    ['sepia', 'Sepia', 'sepia(1)'],
    ['cerah_warna', 'Warna cerah', 'saturate(1.45)'],
    ['pastel', 'Pastel', 'saturate(0.7) contrast(0.88) brightness(1.08)'],
    ['dramatis', 'Dramatis', 'contrast(1.35) saturate(1.1)'],
    ['pudar', 'Pudar', 'contrast(0.8) saturate(0.75) brightness(1.06)'],
    ['senja', 'Senja', 'sepia(0.35) saturate(1.3) hue-rotate(-12deg)'],
    ['malam', 'Malam', 'brightness(0.85) hue-rotate(15deg)'],
    ['retro', 'Retro', 'sepia(0.3) contrast(0.95) saturate(1.2)'],
    ['klasik', 'Klasik', 'sepia(0.4) saturate(1.3)'],
    ['emas', 'Emas', 'sepia(0.45) saturate(1.5)'],
    ['negatif', 'Negatif', 'invert(1)'],
    ['kartun', 'Kartun', 'saturate(1.6) contrast(1.25)'],
    ['tajam', 'Tajam', 'contrast(1.12)'],
    ['duotone', 'Duotone', 'grayscale(1) sepia(0.4) hue-rotate(200deg)'],
  ];
  FILTERS.forEach(([id, label]) => { const o = document.createElement('option'); o.value = id; o.textContent = label; $('filterSel').appendChild(o); });
  $('filterSel').value = filter;
  const applyFilterPreview = () => { const f = FILTERS.find((x) => x[0] === filter); $('preview').style.filter = f ? f[2] : 'none'; };
  applyFilterPreview();
  $('filterSel').onchange = () => {
    filter = $('filterSel').value;
    try { localStorage.setItem('sbn_filter', filter); } catch (e) {}
    applyFilterPreview();
    if (peerRef) peerRef.update({ filter }).catch(() => {});
    say('Filter ' + $('filterSel').selectedOptions[0].textContent + ' dipakai.');
  };

  async function getCamera() {
    return navigator.mediaDevices.getUserMedia({
      audio: mode === 'guest' ? { echoCancellation: true, noiseSuppression: true } : false,
      video: { facingMode: facing, width: { ideal: 720 }, height: { ideal: 1280 } },
    });
  }

  $('joinBtn').onclick = async () => {
    const name = $('name').value.replace(/[<>\u0000-\u001f]/g, '').replace(/\s+/g, ' ').trim().slice(0, 30);
    if (name.length < 2) {
      alertStatus(mode === 'camera' ? 'Isi nama kamera dulu, minimal 2 huruf.' : 'Isi nama kamu dulu, minimal 2 huruf. Nama ini tampil di ruang tunggu host dan di layar siaran.');
      $('name').focus();
      return;
    }
    try { localStorage.setItem(mode === 'camera' ? 'sbn_nama_kamera' : 'sbn_nama', name); } catch (e) {}
    $('joinBtn').disabled = true;
    try { camStream = await getCamera(); }
    catch (e) { alertStatus('Izin kamera/mikrofon ditolak. Izinkan lalu coba lagi.'); $('joinBtn').disabled = false; return; }
    $('preview').srcObject = camStream;
    $('joinCard').hidden = true; $('statusCard').hidden = false; $('mediaCard').hidden = false;
    if (mode === 'camera') { $('micBtn').hidden = true; $('screenBtn').hidden = true; }
    peerRef = peersCol.doc();
    await peerRef.set({ name, mode, status: 'waiting', joinedAt: Date.now(), micOn: mode === 'guest', screen: false, app: 'web', filter });
    if (mode === 'guest') { $('chatCard').hidden = false; listenMessages(); }
    say(mode === 'camera' ? 'Menyambung ke host…' : 'Kamu di ruang tunggu. Tunggu host memasukkanmu.');
    watchQueue();
    unsub.push(peerRef.onSnapshot(onMyDoc));
  };

  // Chat ruang tunggu dengan host
  function addMsg(name, text, me) {
    const div = document.createElement('div'); div.className = 'msg' + (me ? ' me' : '');
    const b = document.createElement('b'); b.textContent = me ? 'Kamu' : name; div.appendChild(b);
    div.appendChild(document.createTextNode(text));
    $('msgs').appendChild(div); $('msgs').scrollTop = $('msgs').scrollHeight;
  }
  function listenMessages() {
    unsub.push(peerRef.collection('messages').orderBy('at').onSnapshot((snap) => {
      snap.docChanges().forEach((c) => {
        if (c.type !== 'added') return;
        const d = c.doc.data();
        addMsg(d.name || 'Host', String(d.text || ''), d.from === 'guest');
        if (d.from !== 'guest') say('Pesan dari ' + (d.name || 'host') + ': ' + d.text);
      });
    }));
  }
  async function sendMsg() {
    const t = $('msgInput').value.replace(/[<>\u0000-\u001f]/g, '').trim().slice(0, 300);
    if (!t || !peerRef) return;
    $('msgInput').value = '';
    await peerRef.collection('messages').add({ from: 'guest', name: $('name').value.trim().slice(0, 30), text: t, at: Date.now() }).catch(() => say('Pesan gagal terkirim.'));
  }
  $('sendBtn').onclick = sendMsg;
  $('msgInput').addEventListener('keydown', (e) => { if (e.key === 'Enter') sendMsg(); });

  function alertStatus(t) { $('statusCard').hidden = false; say(t); }

  // Posisi antrean: siapa yang datang lebih dulu
  function watchQueue() {
    if (mode === 'camera') return;
    unsub.push(peersCol.where('status', '==', 'waiting').onSnapshot((snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, t: d.data().joinedAt || 0 })).sort((a, b) => a.t - b.t);
      const pos = list.findIndex((x) => x.id === peerRef.id);
      if (pos >= 0) say(`Kamu di ruang tunggu, antrean ke-${pos + 1} dari ${list.length}. Tunggu host memasukkanmu.`);
    }));
  }

  async function onMyDoc(doc) {
    const d = doc.data(); if (!d) return;
    if (d.status === 'admitted' && !pc) { say('Host memasukkanmu. Menyambung…'); await connect(); }
    if (d.status === 'kicked') { say('Kamu dikeluarkan dari siaran oleh host.'); cleanup(); }
    if (d.hostMuteAt && d.hostMuteAt !== lastHostMute) {
      const first = lastHostMute === 0 && Date.now() - d.hostMuteAt > 10000;
      lastHostMute = d.hostMuteAt;
      if (!first) { setMic(false, false); say('Mikrofonmu dibisukan host. Kamu bisa menyalakannya lagi dengan tombol Nyalakan mikrofon.'); }
    }
    if (d.answer && pc && !pc.currentRemoteDescription) {
      await pc.setRemoteDescription(new RTCSessionDescription(d.answer));
    }
  }

  async function connect() {
    pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' }] });
    camStream.getTracks().forEach((t) => { const s = pc.addTrack(t, camStream); if (t.kind === 'video') videoSender = s; });
    pc.ontrack = (e) => { if (e.track.kind === 'audio') $('hostAudio').srcObject = e.streams[0] || new MediaStream([e.track]); };
    pc.onicecandidate = (e) => { if (e.candidate) peerRef.collection('guestCandidates').add(e.candidate.toJSON()); };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'connected') say(mode === 'camera' ? 'Tersambung sebagai kamera tambahan.' : 'Kamu sudah tampil di siaran!');
      if (pc.connectionState === 'failed') say('Sambungan gagal. Coba muat ulang halaman.');
    };
    unsub.push(peerRef.collection('hostCandidates').onSnapshot((snap) => {
      snap.docChanges().forEach((c) => { if (c.type === 'added') pc.addIceCandidate(new RTCIceCandidate(c.doc.data())).catch(() => {}); });
    }));
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    await peerRef.update({ offer: { type: offer.type, sdp: offer.sdp } });
  }

  function setMic(on, notify = true) {
    micOn = on;
    camStream && camStream.getAudioTracks().forEach((t) => (t.enabled = on));
    $('micBtn').textContent = on ? 'Matikan mikrofon' : 'Nyalakan mikrofon';
    $('micBtn').setAttribute('aria-pressed', String(on));
    if (peerRef) peerRef.update({ micOn: on });
    if (notify) say(on ? 'Mikrofon menyala.' : 'Mikrofon mati.');
  }

  $('micBtn').onclick = () => setMic(!micOn);
  $('camBtn').onclick = () => {
    camOn = !camOn;
    camStream.getVideoTracks().forEach((t) => (t.enabled = camOn));
    $('camBtn').textContent = camOn ? 'Matikan kamera' : 'Nyalakan kamera';
    $('camBtn').setAttribute('aria-pressed', String(camOn));
  };

  // Berbagi layar: video kamera diganti layar tanpa memutus sambungan
  $('screenBtn').onclick = async () => {
    if (screenStream) { stopScreen(); return; }
    if (!navigator.mediaDevices.getDisplayMedia) { say('Browser ini belum mendukung berbagi layar. Coba pakai Chrome di laptop/komputer.'); return; }
    try {
      screenStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
      const track = screenStream.getVideoTracks()[0];
      if (videoSender) await videoSender.replaceTrack(track);
      $('preview').srcObject = screenStream;
      track.onended = stopScreen;
      $('screenBtn').textContent = 'Hentikan berbagi layar';
      peerRef.update({ screen: true });
      say('Kamu sedang berbagi layar.');
    } catch (e) { say('Berbagi layar dibatalkan.'); screenStream = null; }
  };

  async function stopScreen() {
    if (!screenStream) return;
    screenStream.getTracks().forEach((t) => t.stop());
    screenStream = null;
    if (videoSender) await videoSender.replaceTrack(camStream.getVideoTracks()[0]);
    $('preview').srcObject = camStream;
    $('screenBtn').textContent = 'Bagikan layar';
    peerRef.update({ screen: false });
    say('Berbagi layar dihentikan.');
  }

  $('flipBtn').onclick = async () => {
    facing = facing === 'user' ? 'environment' : 'user';
    const fresh = await navigator.mediaDevices.getUserMedia({ video: { facingMode: facing } });
    const nt = fresh.getVideoTracks()[0];
    camStream.getVideoTracks().forEach((t) => { camStream.removeTrack(t); t.stop(); });
    camStream.addTrack(nt);
    if (videoSender && !screenStream) await videoSender.replaceTrack(nt);
    $('preview').srcObject = camStream;
  };

  $('leaveBtn').onclick = async () => {
    if (peerRef) await peerRef.update({ status: 'left' }).catch(() => {});
    say('Kamu sudah keluar. Terima kasih!');
    cleanup();
  };

  function cleanup() {
    unsub.forEach((u) => u()); unsub = [];
    if (pc) { pc.close(); pc = null; }
    [camStream, screenStream].forEach((s) => s && s.getTracks().forEach((t) => t.stop()));
    $('mediaCard').hidden = true;
  }

  window.addEventListener('beforeunload', () => { if (peerRef) peerRef.update({ status: 'left' }); });
})();
