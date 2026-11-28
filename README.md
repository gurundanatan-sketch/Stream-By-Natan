# Stream By Natan

Aplikasi Android untuk **live streaming ke YouTube dan TikTok sekaligus**, dibuat ramah pembaca layar (Jieshuo), dengan tampilan futuristik gelap bernuansa neon.

- Bahasa: **Kotlin** + Jetpack Compose
- Mesin siaran: **RootEncoder 2.7.0** (satu encoder, dikirim ke dua server RTMP)
- Backend: **Firebase** saja (tanpa VPS)
- APK dibangun otomatis oleh **GitHub Actions**

---

## 1. Cara pakai repo ini (dari Termux)

```bash
pkg install git openjdk-17 python
cd ~
unzip Stream-By-Natan-master-backup.zip
cd Stream-By-Natan
git init
git branch -M main
git add .
git commit -m "Stream By Natan versi pertama"
git remote add origin https://github.com/gurundanatan-sketch/Stream-By-Natan.git
git push -u origin main
```

Folder `signing/` **tidak ikut terunggah** (sudah diabaikan `.gitignore`) karena berisi kunci rahasia.

## 2. GitHub Secrets (sekali saja)

Buka **GitHub > repo > Settings > Secrets and variables > Actions > New repository secret**.

| Nama secret | Isi | Wajib? |
|---|---|---|
| `SIGNING_KEYSTORE_BASE64` | isi file `signing/SIGNING_KEYSTORE_BASE64.txt` | **Wajib** (supaya update bisa menimpa versi lama) |
| `SIGNING_STORE_PASSWORD` | lihat `signing/BACA-SAYA.txt` | Wajib |
| `SIGNING_KEY_ALIAS` | `streambynatan` | Wajib |
| `SIGNING_KEY_PASSWORD` | lihat `signing/BACA-SAYA.txt` | Wajib |
| `GOOGLE_SERVICES_JSON` | seluruh isi file `google-services.json` dari Firebase | Untuk login, tamu, cadangan |
| `FIREBASE_SERVICE_ACCOUNT` | JSON akun layanan Firebase | Untuk halaman tamu |
| `FIREBASE_PROJECT_ID` | ID proyek Firebase | Untuk halaman tamu |
| `FIREBASE_WEB_CONFIG` | objek konfigurasi aplikasi web Firebase | Untuk halaman tamu |
| `PEXELS_API_KEY` atau `PIXABAY_API_KEY` | API key gratis dari pexels.com/api atau pixabay.com/api | Untuk video latar sinematik pembuka/penutup |

### Tanda tangan pribadi
Semua APK ditandatangani dengan **kunci pribadi yang sama** (`signing/streambynatan-release.jks`), jadi versi baru langsung **menimpa versi lama tanpa uninstall**. Kalau secret kunci belum diisi, APK tetap dibuat sebagai artifact, tapi **tidak diterbitkan** ke Releases, supaya pembaruan otomatis tidak rusak.

> Penting: kalau sebelumnya kamu sudah memasang APK lama yang ditandatangani kunci lain, hapus sekali saja versi lama itu. Setelah itu, semua pembaruan berikutnya bisa langsung ditimpa.

## 3. Workflows GitHub Actions

| File | Kapan jalan | Fungsi |
|---|---|---|
| `.github/workflows/android.yml` | setiap push ke `main` | Unduh 200 efek suara + 20 musik, bangun APK rilis bertanda tangan, simpan artifact, terbitkan ke **Releases** (dipakai pembaruan otomatis) |
| `.github/workflows/check.yml` | pull request / branch lain | Cek skrip Python, JavaScript halaman tamu, kompilasi debug, lint |
| `.github/workflows/firebase-hosting.yml` | perubahan di folder `web/` | Terbitkan halaman tamu ke Firebase Hosting |
| `.github/workflows/sound-assets.yml` | manual | Uji unduhan aset suara dan lihat hasilnya |

APK ada di tab **Actions** (artifact `StreamByNatan-apk`) dan di halaman **Releases**.

## 4. Pengaturan Firebase (sekali saja)

Setiap kali `firestore.rules` atau folder `web/` berubah, jalankan dari Termux:

```
firebase deploy --only firestore:rules,hosting --project stream-by-natan-29448
```


1. Buat proyek di Firebase Console.
2. Tambahkan aplikasi Android dengan nama paket `com.natan.streambynatan`, lalu isi **SHA-1 dan SHA-256** dari `signing/BACA-SAYA.txt`.
3. Unduh `google-services.json`, lalu salin isinya ke secret `GOOGLE_SERVICES_JSON`.
4. **Authentication**: aktifkan **Email/Password** dan **Google**.
5. **Firestore**: buat database, lalu tempel isi `firestore.rules` ke tab Rules.
6. **Hosting**: halaman undangan, halaman unduh, dan halaman tamu browser (folder `web/`). Terbitkan dari Termux:
   ```
   python scripts/write_web_config.py stream-by-natan-29448
   firebase deploy --only hosting --project stream-by-natan-29448
   ```
   File `web/.well-known/assetlinks.json` membuat link undangan langsung membuka aplikasi (berisi sidik jari SHA-256 kunci pribadi).
7. Cloud Messaging dan Crashlytics otomatis aktif.

## 5. Pengaturan YouTube (Google Cloud Console)

Proyek Firebase otomatis menjadi proyek Google Cloud.
1. Aktifkan **YouTube Data API v3** di menu "API dan Layanan".
2. Isi **Google Auth Platform**, lalu tambahkan emailmu sebagai pengguna uji. Supaya izin tidak perlu diulang tiap 7 hari, tekan **Publikasikan aplikasi** di bagian **Audiens**.
3. Buat **OAuth Client ID** tipe Android: nama paket `com.natan.streambynatan` dan SHA-1 dari `signing/BACA-SAYA.txt`.

## 6. Build di Termux (opsional)

`signing/keystore.properties` otomatis dipakai, jadi APK dari Termux juga bisa menimpa versi dari GitHub.
```bash
python scripts/download_assets.py
./gradlew assembleRelease
```

---

## Daftar fitur

**Siaran**
- Live ke YouTube & TikTok sekaligus, bitrate adaptif, sambung ulang otomatis, rekam ke HP saat live
- Sumber: kamera HP, berbagi layar, suara saja (dengan gambar pilihanmu), webcam USB, HP lain sebagai kamera
- Audio mikrofon + audio internal (Android 10+) dengan volume terpisah; suara Jieshuo tidak ikut tersiar
- Layanan latar depan, layar tetap menyala, Jangan Ganggu, pengecualian baterai, pemantau koneksi dengan peringatan suara
- Halaman stream key terenkripsi, tombol "Tempel key TikTok" dan deteksi clipboard (Ya/Tidak)
- Pengaturan siaran YouTube dari aplikasi: judul, deskripsi, thumbnail, audiens, visibilitas, jadwal, kategori, tag, latensi; stream key diambil otomatis
- Pemeriksaan sebelum live (internet, stream key, mikrofon, audio internal)
- Mode latihan, profil live, cadangan pengaturan ke Firebase

**Komentar & penonton** (TikTok dibaca langsung dari halaman live TikTok tanpa API key, YouTube lewat YouTube Data API)
- TTS bahasa Indonesia (Google TTS) dengan suara/kecepatan/volume sendiri
- Terima kasih otomatis untuk gift (jumlah koin), follow, subscribe, share, Super Chat, member, Saweria; template {nama} {hadiah} {jumlah} {koin}
- Filter kata kasar, mute penonton, terjemahan komentar offline (ML Kit)
- Antrean pertanyaan (tanda tanya, kata tanya baku/tidak baku, singkatan) dengan tombol Pertanyaan berikutnya/Ulangi/Tahan/Lewati; singkatan dipanjangkan sebelum dibacakan
- Ringkasan komentar yang terlewat, sapaan penonton baru, penonton setia, papan pendukung teratas
- Polling lewat komentar, target like/koin/penonton (per platform & gabungan), daftar permohonan doa
- Jawaban otomatis untuk pertanyaan yang sering ditanyakan (TTS/gambar), dengan kalimat perintah sendiri

**Layar siaran**
- Teks overlay, teks berjalan (di atas subtitle), subtitle otomatis paling bawah
- Pengumuman otomatis berkala, kartu ayat penuh layar dari naskah atau dari suaramu
- Catatan live dibisikkan pelan mengikuti suaramu (atau tampil sebagai teks di layar host kalau bantuan suara mati), lirik Live music mengikuti nyanyian
- Info persembahan/QRIS, notifikasi Saweria lewat link overlay, kode QR Saweria (ukuran, posisi, lama, warna bisa diatur)
- Layar pembuka & penutup sinematik (9 template) dengan 15 gaya transisi
- Pengganti latar belakang (gambar/video/buram), asisten posisi kamera, penyesuai cahaya

**Suara**
- Efek mikrofon: peredam bising bawaan, peredam bising kuat, penyeimbang volume, gema ringan
- 200 efek suara (Wikimedia Commons) + 20 musik latar (Incompetech, kredit Kevin MacLeod), lagu sendiri, efek suara dari file audio/video sendiri (bisa dipotong)
- Efek suara otomatis untuk gift, pintasan efek suara di jendela mengambang
- Perintah suara yang bisa diatur (termasuk ganti kamera: "kamera depan", "pindah ke kamera mimbar")

**Tamu live**
- Banyak orang bisa masuk **ruang tunggu**, antre berdasarkan siapa yang datang lebih dulu
- Host memasukkan satu orang, beberapa yang dipilih, atau semua
- Tamu masuk **lewat aplikasi ini sendiri (gratis, tanpa akun)** dengan kamera, mikrofon, dan **berbagi layar HP**
- Tamu juga bisa masuk lewat browser (berbagi layar dari browser butuh laptop/komputer)
- Host bisa **membisukan mikrofon tamu**; tamu bisa menyalakannya lagi sendiri
- Host bisa **mengeluarkan tamu**
- Tata letak otomatis: tidak ada yang bicara atau beberapa orang bicara bersamaan, ukuran layar **sama**; hanya satu orang bicara, layarnya otomatis **penuh layar**
- **Satu link undangan pintar** (`https://<proyek>.web.app/join/?room=…`): membuka aplikasi kalau sudah terpasang; kalau belum, muncul halaman dengan tombol **Unduh aplikasi** (APK terbaru dari GitHub Releases) dan **Masuk lewat browser**
- Kode QR undangan, kode ruang yang bisa ditempel di layar masuk, dan tombol **Bagikan link download aplikasi**

**Setelah live**
- Ringkasan live, perkembangan antar-live (hari & jam terbaik)
- Rekaman otomatis dipotong per pertanyaan, per poin Catatan live, dan **per tamu** (dari masuk sampai keluar), dengan judul sesuai pertanyaan/poin, tanpa batas durasi
- Pustaka rekaman di dalam aplikasi: putar, unduh ke folder Download, bagikan, hapus
- Transkrip live (opsional)
- Pembaruan otomatis dari GitHub Releases

**Tata letak baru**
- Sebelum live: ikon atas (Akun, Pengaturan), saklar platform, satu baris sumber & profil, tombol Mulai besar, opsi tambahan bisa dilipat
- Saat live: kamera layar penuh seperti TikTok, status di atas, komentar mengambang, bilah bawah **Tamu / Alat / Bagikan / Sempurnakan / Lainnya**
- **Alat**: ganti kamera manual atau tambah kamera (webcam USB, HP lain), perintah suara ganti kamera tetap ada, giveaway, kuis, request lagu, rundown
- **Bagikan**: bagikan link live ke WhatsApp, Telegram, Facebook, Instagram, X, LINE, Messenger, SMS, email, dll, plus tombol salin link
- **Sempurnakan**: 21 filter + kecerahan/kontras/saturasi, latar, impor gambar dari galeri ke layar, overlay gambar/video, **TEMA**, teks layar, efek suara, efek mikrofon, pengubah suara (robot, tinggi, chipmunk, berat, raksasa, gema, telepon), musik latar dari galeri dengan volume
- **Lainnya**: tampilan komentar, kualitas 480/720/1080/otomatis, musik mengecil otomatis saat host bicara (tidak mati), dll

**Fitur tambahan (semua opsional, Pengaturan > Semua fitur / Alat live)**
- Giveaway lewat kata komentar, kuis, request lagu, rundown acara, laporan latihan Catatan live
- Peringatan baterai & suhu, pengingat minum, peringatan live sepi (dengan ide topik)
- Balas komentar YouTube dengan suara ("balas …"), tombol jeda layar (tulisan/gambar sendiri), kunci sentuh
- Papan nama di layar, getar/suara/TTS per kejadian (Pengaturan > Getar, suara & TTS per kejadian)
- Platform tambahan: Facebook, Instagram, dan 2 RTMP bebas
- **TEMA**: ucapkan "tema" + judul, gambar tema tampil layar penuh (judul & gambar diatur sendiri)

**Tamu & moderator**
- Label "lewat aplikasi" / "lewat browser", chat ruang tunggu (disensor kata kasar, host bisa membalas, dibacakan TTS di mode tunanetra)
- Tamu bisa ganti kamera depan/belakang dan memilih filter (21 pilihan, di aplikasi maupun browser)
- Ruang tamu bisa dibuka sebelum live
- Moderator (lewat username atau link undangan) punya semua hak kelola tamu dari HP-nya sendiri

**Akun & sosial**
- Menu Akun (ikon kanan atas): profil (foto, nama, username, bio, tautan), edit & **bagikan profil**, kritik & saran, Tentang aplikasi (panduan & profil pembuat), kebijakan privasi, bahasa (Indonesia bawaan + bahasa dunia & daerah), Tampilan (tema gelap/terang, teks besar, kontras tinggi), izin setelan terbatas, Keluar, Beralih akun, Hapus akun
- Ikuti kreator + notifikasi saat mereka live, jadwal live dengan pengingat, halaman profil publik `/@username`, daftar "Sedang live sekarang" di beranda web
- Pertama kali dibuka: tutorial 6 bagian (bisa dilewati), pilihan mode pembaca layar, izin (termasuk foto/video & musik/audio), login
- Khusus pembuat (7 email): Kotak saran dengan badge, kunci Euler & YouTube API disimpan di Firestore (`private/creatorKeys`), hanya bisa dibaca akun pembuat; tidak ada di dalam APK

## Belum dimasukkan
Terjemahan suara, riwayat & pencarian komentar, cuplikan Shorts, kendali tombol earphone, zoom & senter lewat suara,
simpan ke Google Drive. Versi desktop & iOS menyusul setelah rilis Android.

## Penyimpanan
- Efek suara dan musik hasil unduhan GitHub Actions ada **di dalam APK** (`res/raw`), jadi **tidak pernah muncul di galeri**.
- Rekaman, potongan video, dan transkrip disimpan di memori pribadi aplikasi. File baru masuk ke folder Download hanya saat kamu menekan **Unduh**.

## Sistem yang dipakai
- **FFmpeg** (FFmpegKit lanjutan yang masih dirawat, `com.moizhassan.ffmpeg:ffmpeg-kit-16kb`): memotong rekaman per pertanyaan/poin dan mengambil suara dari video untuk efek suara sendiri. MediaMuxer bawaan Android dipakai sebagai cadangan kalau FFmpeg gagal.
- **Lottie**: animasi efek cahaya & partikel di layar pembuka/penutup (file dibuat oleh `scripts/make_lottie.py`).
- **Video latar sinematik** dari Pexels/Pixabay, diunduh GitHub Actions ke dalam aplikasi.
- **RNNoise**: peredam bising AI (offline), selain peredam bising bawaan Android dan peredam bising kuat.
- **Tink + Android Keystore + DataStore**: penyimpanan stream key & API key terenkripsi.
- **Kenney (CC0)** + **Wikimedia Commons**: 200 efek suara; **Incompetech**: 20 musik latar.

## Catatan
- Kode ini belum pernah dikompilasi di lingkungan pembuatnya (tanpa Android SDK), jadi build di GitHub Actions mungkin masih menunjukkan beberapa error kecil. Kirim pesan error-nya, nanti diperbaiki.
- RNNoise 1.0.2 menandai dirinya butuh compileSdk 37; pemeriksaan itu dilewati di `app/build.gradle` karena library-nya hanya kode C (JNI).
- FFmpeg dan WebRTC menambah ukuran APK. APK dibuat untuk HP ARM 32 & 64 bit supaya tidak terlalu besar.
- Komentar TikTok dibaca dari halaman live TikTok di browser tersembunyi (skrip `app/src/main/assets/tiktok_reader.js`). Aplikasi mengambil versi terbaru skrip ini dari GitHub setiap live, jadi kalau TikTok mengubah tampilan halamannya, cukup perbarui file itu. Kalau TikTok minta login atau verifikasi, buka **Pengaturan > Stream key & server > Buka halaman TikTok**. Nilai koin hadiah adalah perkiraan untuk hadiah populer.
- Supaya pembaca TikTok tetap jalan saat live, izinkan **Tampil di atas aplikasi lain**.
- Berbagi layar dari browser HP Android belum didukung Chrome Android. Tamu di HP sebaiknya masuk lewat aplikasi.
- **Paket**: sekarang semua fitur gratis (masa promosi). Struktur premium sudah disiapkan di `core/Plans.kt` untuk setelah rilis Play Store. Peran tamu selalu gratis.
- Suara tamu dan notifikasi Saweria ikut tersiar kalau **audio internal** dinyalakan (butuh izin rekam layar).
