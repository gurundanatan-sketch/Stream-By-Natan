/*
 * Stream By Natan - pembaca halaman TikTok LIVE (tanpa API key).
 * Dijalankan di dalam WebView tersembunyi yang membuka https://www.tiktok.com/@username/live
 * Membaca komentar, hadiah, like, ikuti, bagikan, bergabung, dan jumlah penonton dari tampilan halaman,
 * lalu mengirimnya ke aplikasi lewat jembatan window.SBN.post(json).
 *
 * File ini juga diambil dari GitHub saat live dimulai (cabang main), jadi kalau TikTok mengubah
 * tampilan halamannya, cukup perbarui file ini di repo tanpa memasang ulang aplikasi.
 */
(function () {
  if (window.__sbnReady) return;
  window.__sbnReady = true;

  var VERSION = 1;
  var seen = typeof WeakSet !== 'undefined' ? new WeakSet() : null;
  var lastViewers = -1, lastLikes = -1, lastStatus = '';
  var recent = [];            // anti-dobel: teks yang sudah dikirim beberapa detik terakhir

  function post(o) { try { window.SBN.post(JSON.stringify(o)); } catch (e) {} }
  function status(s) { if (s !== lastStatus) { lastStatus = s; post({ t: 'status', v: s }); } }
  function txt(el) { return el ? (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
  function q(el, sel) { try { return el.querySelector(sel); } catch (e) { return null; } }
  function attr(el, a) { return el && el.getAttribute ? (el.getAttribute(a) || '') : ''; }
  function num(s) {
    if (!s) return -1;
    s = String(s).toLowerCase().replace(/,/g, '.').replace(/\s/g, '');
    var m = s.match(/([\d.]+)\s*(k|rb|ribu|m|jt|juta)?/);
    if (!m) return -1;
    var n = parseFloat(m[1]);
    if (isNaN(n)) return -1;
    var u = m[2] || '';
    if (u === 'k' || u === 'rb' || u === 'ribu') n *= 1000;
    if (u === 'm' || u === 'jt' || u === 'juta') n *= 1000000;
    if (!u && /^\d{1,3}(\.\d{3})+$/.test(m[1])) n = parseInt(m[1].replace(/\./g, ''), 10);
    return Math.round(n);
  }
  function dedupe(key) {
    var now = Date.now();
    recent = recent.filter(function (r) { return now - r.at < 8000; });
    for (var i = 0; i < recent.length; i++) if (recent[i].k === key) return true;
    recent.push({ k: key, at: now });
    return false;
  }

  // --- Lebar tampilan desktop supaya kolom komentar TikTok muncul ---
  function forceDesktopViewport() {
    try {
      var m = document.querySelector('meta[name=viewport]');
      if (!m) { m = document.createElement('meta'); m.name = 'viewport'; document.head.appendChild(m); }
      if (m.content !== 'width=1280') m.content = 'width=1280';
    } catch (e) {}
  }

  // --- Video live dibisukan & disembunyikan supaya suara TikTok tidak ikut ke siaran dan HP tidak berat ---
  function silenceMedia() {
    try {
      var list = document.querySelectorAll('video, audio');
      for (var i = 0; i < list.length; i++) {
        var v = list[i];
        v.muted = true; v.volume = 0;
        if (!v.paused) { try { v.pause(); } catch (e) {} }
      }
      if (!document.getElementById('__sbn_css')) {
        var st = document.createElement('style');
        st.id = '__sbn_css';
        st.textContent = 'video,canvas{visibility:hidden!important}*{animation-duration:0s!important;transition:none!important}';
        document.head.appendChild(st);
      }
    } catch (e) {}
  }

  // --- Mengenali jenis kejadian dari teks (bahasa Inggris & Indonesia) ---
  var RX = {
    join: /(joined|bergabung|telah bergabung|masuk ke live)/i,
    follow: /(followed|followed the host|mengikuti host|mulai mengikuti|mengikuti kreator|mengikuti)/i,
    share: /(shared|membagikan|berbagi live|bagikan live)/i,
    like: /(liked the live|tapped|menyukai live|mengetuk)/i,
    gift: /(sent|mengirim|mengirimkan)\b/i
  };

  function userFrom(el) {
    var nameEl = q(el, '[data-e2e="message-owner-name"]') || q(el, '[data-e2e*="owner-name"]') || q(el, '[data-e2e*="nickname"]') || q(el, 'a[href*="/@"]') || q(el, 'span');
    var name = txt(nameEl);
    var link = q(el, 'a[href*="/@"]');
    var id = '';
    if (link) { var m = (attr(link, 'href') || '').match(/\/@([^/?#]+)/); if (m) id = decodeURIComponent(m[1]); }
    if (!id) id = name;
    return { id: id, name: name, nameEl: nameEl };
  }

  function giftFrom(el, full) {
    // contoh: "Budi mengirim Mawar x5" / "Budi sent Rose x5"
    var count = 1;
    var m = full.match(/[x×]\s*(\d+)/i);
    if (m) count = parseInt(m[1], 10) || 1;
    var img = q(el, 'img[alt]');
    var gname = '';
    var gm = full.match(/(?:sent|mengirim(?:kan)?)\s+(.+?)(?:\s*[x×]\s*\d+|$)/i);
    if (gm) gname = gm[1].trim();
    if (!gname && img) gname = attr(img, 'alt');
    return { gift: gname || 'hadiah', count: count };
  }

  function classify(el) {
    if (!el || el.nodeType !== 1) return;
    if (seen) { if (seen.has(el)) return; seen.add(el); }
    var e2e = attr(el, 'data-e2e');
    var full = txt(el);
    if (!full || full.length > 400) return;

    if (e2e === 'chat-message' || /chat-message/.test(e2e)) {
      var u = userFrom(el);
      var comment = '';
      var cEl = q(el, '[data-e2e="chat-message-content"]') || q(el, '[data-e2e*="comment"]');
      if (cEl) comment = txt(cEl);
      if (!comment) {
        comment = full;
        if (u.name && comment.indexOf(u.name) === 0) comment = comment.substring(u.name.length);
        comment = comment.replace(/^[\s:：·-]+/, '').trim();
      }
      if (!comment || !u.name) return;
      if (dedupe('c|' + u.name + '|' + comment)) return;
      post({ t: 'comment', id: u.id, name: u.name, text: comment });
      return;
    }

    var isSocial = /social|enter|member|join|gift|like|share|follow/i.test(e2e) || /social|enter|gift/i.test(el.className || '');
    if (!isSocial && !(RX.join.test(full) || RX.follow.test(full) || RX.share.test(full) || RX.gift.test(full))) return;
    // Hanya potongan kecil (1 baris kejadian), bukan wadah besar
    if (el.children && el.children.length > 12) return;
    var us = userFrom(el);
    var name = us.name || full.split(' ')[0];
    var id = us.id || name;

    if (/gift/i.test(e2e) || (RX.gift.test(full) && /[x×]\s*\d+|hadiah|gift|mawar|rose/i.test(full))) {
      var g = giftFrom(el, full);
      if (dedupe('g|' + name + '|' + g.gift + '|' + g.count)) return;
      post({ t: 'gift', id: id, name: name, gift: g.gift, count: g.count });
    } else if (RX.share.test(full)) {
      if (dedupe('s|' + name)) return;
      post({ t: 'share', id: id, name: name });
    } else if (RX.follow.test(full)) {
      if (dedupe('f|' + name)) return;
      post({ t: 'follow', id: id, name: name });
    } else if (RX.join.test(full)) {
      if (dedupe('j|' + name)) return;
      post({ t: 'join', id: id, name: name });
    } else if (RX.like.test(full)) {
      if (dedupe('l|' + name)) return;
      post({ t: 'like', id: id, name: name, count: 1 });
    }
  }

  function scan(root) {
    try {
      var list = root.querySelectorAll ? root.querySelectorAll('[data-e2e="chat-message"], [data-e2e*="social-message"], [data-e2e*="enter-message"], [data-e2e*="gift"], [data-e2e*="member"]') : [];
      for (var i = 0; i < list.length; i++) classify(list[i]);
      if (root.nodeType === 1 && root.matches && root.matches('[data-e2e], div, span')) classify(root);
    } catch (e) {}
  }

  function readCounters() {
    var vEl = document.querySelector('[data-e2e="live-people-count"]') || document.querySelector('[data-e2e*="people-count"]') || document.querySelector('[data-e2e*="viewer"]');
    var v = num(txt(vEl));
    if (v >= 0 && v !== lastViewers) { lastViewers = v; post({ t: 'viewers', v: v }); }
    var lEl = document.querySelector('[data-e2e*="like-count"]') || document.querySelector('[data-e2e*="likes"]');
    var l = num(txt(lEl));
    if (l >= 0 && l !== lastLikes) { var diff = lastLikes < 0 ? 0 : l - lastLikes; lastLikes = l; post({ t: 'likes', total: l, diff: diff }); }
  }

  function readPageState() {
    var body = txt(document.body).toLowerCase();
    if (/verify to continue|drag the slider|captcha|verifikasi untuk melanjutkan|geser/.test(body) && body.length < 3000) { status('captcha'); return; }
    if (/live has ended|live ini telah berakhir|siaran langsung telah berakhir|is not live|tidak sedang live|live tidak tersedia/.test(body)) { status('offline'); return; }
    if (document.querySelector('[data-e2e="chat-message"]') || document.querySelector('[data-e2e*="chat"]')) { status('chat'); return; }
    status('loading');
  }

  var obs = new MutationObserver(function (muts) {
    for (var i = 0; i < muts.length; i++) {
      var added = muts[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        var n = added[j];
        if (n.nodeType === 1) scan(n);
        else if (n.nodeType === 3 && n.parentElement) classify(n.parentElement.closest ? (n.parentElement.closest('[data-e2e]') || n.parentElement) : n.parentElement);
      }
    }
  });

  function start() {
    forceDesktopViewport();
    silenceMedia();
    // pesan yang sudah ada saat halaman dibuka tidak dibacakan
    try {
      var old = document.querySelectorAll('[data-e2e="chat-message"], [data-e2e*="social-message"], [data-e2e*="enter-message"], [data-e2e*="gift"]');
      for (var i = 0; i < old.length; i++) if (seen) seen.add(old[i]);
    } catch (e) {}
    obs.observe(document.documentElement || document.body, { childList: true, subtree: true });
    setInterval(function () { silenceMedia(); readCounters(); readPageState(); forceDesktopViewport(); }, 2000);
    post({ t: 'status', v: 'ready', version: VERSION });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
