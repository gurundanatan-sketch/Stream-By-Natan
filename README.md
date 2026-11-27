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

1. Buat proyek di Firebase Console.
2. Tambahkan aplikasi Android dengan nama paket `com.natan.streambynatan`, lalu isi **SHA-1 dan SHA-256** dari `signing/BACA-SAYA.txt`.
3. Unduh `google-services.json`, lalu salin isinya ke secret `GOOGLE_SERVICES_JSON`.
4. **Authentication**: aktifkan **Email/Password** dan **Google**.
5. **Firestore**: buat database, lalu tempel isi `firestore.rules` ke tab Rules.
6. **Hosting**: untuk halaman tamu (folder `web/`).
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

**Komentar & penonton** (TikTok lewat Euler Stream, YouTube lewat YouTube Data API)
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
- Naskah khotbah dibisikkan pelan mengikuti suaramu, lirik pujian mengikuti nyanyian
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
- Tamu bisa **berbagi layar** (dari browser yang mendukung, misalnya Chrome di laptop)
- Host bisa **membisukan mikrofon tamu**; tamu bisa menyalakannya lagi sendiri
- Host bisa **mengeluarkan tamu**
- Tata letak otomatis: tidak ada yang bicara atau beberapa orang bicara bersamaan, ukuran layar **sama**; hanya satu orang bicara, layarnya otomatis **penuh layar**
- Tamu masuk lewat link atau kode QR, tanpa memasang aplikasi

**Setelah live**
- Ringkasan live, perkembangan antar-live (hari & jam terbaik)
- Rekaman otomatis dipotong per pertanyaan dan per poin khotbah, dengan judul sesuai pertanyaan/poin, tanpa batas durasi
- Pustaka rekaman di dalam aplikasi: putar, unduh ke folder Download, bagikan, hapus
- Transkrip live (opsional)
- Pembaruan otomatis dari GitHub Releases

## Yang sengaja tidak dimasukkan (dilewati saat diskusi)
Giveaway (ditunda), peringatan baterai/suhu, terjemahan suara, balas komentar pakai suara, tombol jeda layar,
pengingat minum & istirahat, daftar lagu permintaan, peringatan live sepi, kuis tebak-tebakan,
riwayat & pencarian komentar, platform tambahan (Facebook/RTMP lain), kunci sentuhan, getaran penanda,
cuplikan Shorts, kendali tombol earphone, zoom & senter lewat suara, susunan acara, mode latihan khotbah,
papan nama di layar, simpan ke Google Drive.

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
- Kode ini belum pernah dikompilasi di lingkungan pembuatnya (tanpa Android SDK), jadi build pertama di GitHub Actions mungkin masih menunjukkan beberapa error kecil. Kirim pesan error-nya, nanti diperbaiki.
- FFmpeg dan WebRTC menambah ukuran APK. APK dibuat untuk HP ARM 32 & 64 bit supaya tidak terlalu besar.
- Komentar TikTok butuh API key Euler Stream. Paket gratisnya terbatas per hari.
- Berbagi layar dari browser HP Android belum didukung Chrome Android. Tamu sebaiknya berbagi layar dari laptop atau komputer.
- Suara tamu dan notifikasi Saweria ikut tersiar kalau **audio internal** dinyalakan (butuh izin rekam layar).
