package com.natan.streambynatan.ui

/**
 * Isi panduan di "Tentang aplikasi". Setiap panduan dipecah per langkah dengan judul,
 * supaya pembaca layar bisa melompat antar-langkah, dan dibuka satu per satu dari daftar menu.
 * Tulisan dibuat selengkap mungkin: tombol mana yang ditekan, di mana letaknya, dan apa yang terjadi sesudahnya.
 */
data class Guide(val id: String, val title: String, val group: String, val steps: List<Pair<String, String>>)

object Guides {
    const val APP_NAME = "Stream By Natan"

    /** Enam bagian tutorial yang muncul saat aplikasi pertama kali dibuka (juga ada di Tentang aplikasi). */
    val tutorial: List<Guide> = listOf(
        Guide("welcome", "Selamat datang", "Tutorial awal", listOf(
            "Apa itu Stream By Natan" to "Stream By Natan adalah aplikasi untuk siaran langsung (live) dari HP ke YouTube, TikTok, Facebook, Instagram, dan server RTMP lain, bisa satu platform atau beberapa sekaligus. Fiturnya dibuat selengkap live dari laptop, tapi cukup memakai HP.",
            "Bagian-bagian aplikasi" to "Di bagian bawah layar ada empat menu utama:\n1. Live: tempat menyiapkan dan memulai siaran.\n2. Rekaman: hasil rekaman setiap live dan potongan videonya.\n3. Progres: perkembangan dari live ke live, misalnya hari dan jam terbaik.\n4. Pengaturan: semua pengaturan lengkap.\nDi kanan atas halaman Live ada ikon Akun (lingkaran orang) untuk profil, panduan, bahasa, tampilan, dan akun.",
            "Yang bisa kamu lakukan" to "Live memakai kamera HP, berbagi layar, suara saja, atau webcam USB. Komentar bisa dibacakan, tamu bisa ikut tampil, ada filter, overlay, efek suara, musik latar, kuis, giveaway, polling, dan rekaman yang dipotong otomatis.",
            "Gratis" to "Semua fitur gratis selama masa promosi. Tamu yang ikut tampil selalu gratis dan tidak perlu membuat akun.",
            "Membuka tutorial lagi" to "Tutorial ini bisa dibuka kapan saja: tekan ikon Akun di kanan atas halaman Live, lalu Tentang aplikasi. Semua panduan fitur juga ada di sana.",
        )),
        Guide("stream_keys", "Cara mendapatkan stream key", "Tutorial awal", listOf(
            "Apa itu stream key" to "Stream key adalah kode rahasia dari platform (YouTube, TikTok, dan lainnya) supaya siaran dari aplikasi ini masuk ke akunmu. Jangan bagikan stream key ke siapa pun, karena orang yang memegangnya bisa siaran atas namamu.",
            "YouTube: cara paling mudah" to "Kamu tidak perlu menyalin stream key YouTube kalau akun YouTube dihubungkan:\n1. Buka tab Pengaturan, lalu Stream key & server.\n2. Di bagian YouTube, tekan Hubungkan YouTube (login Google).\n3. Pilih akun Google yang punya kanal YouTube-mu, lalu izinkan.\nSetelah itu aplikasi bisa membuat siaran dan mengambil stream key sendiri.",
            "YouTube: cara manual" to "1. Buka YouTube Studio (studio.youtube.com atau aplikasi YT Studio).\n2. Tekan Buat, lalu Mulai live.\n3. Pilih Streaming, lalu salin Kunci streaming.\nKanal yang baru pertama kali live harus verifikasi nomor HP dan menunggu sekitar 24 jam sampai fitur live aktif.",
            "TikTok" to "1. Buka browser (Chrome) dan masuk ke tiktok.com/live/producer, lalu login dengan akun TikTok-mu.\n2. Salin URL server dan Kunci streaming.\n3. Kunci streaming TikTok berganti setiap kali live, jadi ulangi langkah ini setiap mau live.\nTidak semua akun TikTok mendapat akses kunci streaming. Biasanya butuh jumlah pengikut tertentu dan usia minimal 18 tahun.",
            "Facebook" to "1. Buka facebook.com/live/producer di browser.\n2. Pilih Gunakan kunci streaming.\n3. Salin URL server dan Kunci streaming.",
            "Instagram dan server lain" to "Instagram hanya memberi kunci streaming kepada sebagian akun, lewat menu Live di instagram.com. Platform lain yang memberi alamat RTMP/RTMPS dan stream key bisa dipakai lewat Server lain 1 atau Server lain 2.",
        )),
        Guide("fill_keys", "Cara mengisi stream key", "Tutorial awal", listOf(
            "Buka halaman stream key" to "1. Tekan tab Pengaturan di bawah.\n2. Tekan Stream key & server.",
            "YouTube" to "Nyalakan Live ke YouTube. Kalau sudah menghubungkan YouTube, kolom stream key bisa dibiarkan, karena diisi otomatis. Kalau tidak, tempel Kunci streaming dari YouTube Studio ke kolom Stream key YouTube. Server YouTube sudah terisi, jangan diubah kecuali kamu tahu alamatnya.",
            "TikTok" to "1. Nyalakan Live ke TikTok.\n2. Tempel URL server ke kolom Server TikTok.\n3. Tempel Kunci streaming ke kolom Stream key TikTok, atau salin dulu kuncinya lalu tekan Tempel key TikTok.\n4. Di bagian Komentar & kejadian, isi Username TikTok tanpa tanda @ supaya komentar, hadiah, dan penonton TikTok bisa dibaca.\nTips: kalau kamu menyalin kunci TikTok lalu membuka aplikasi ini, aplikasi akan bertanya apakah kunci itu mau dipakai. Tekan Ya.",
            "Platform tambahan" to "Gulir ke bagian Platform tambahan. Untuk Facebook, Instagram, Server lain 1, atau Server lain 2:\n1. Nyalakan saklar Live ke nama platform.\n2. Periksa alamat server (Facebook dan Instagram sudah terisi).\n3. Tempel stream key, atau tekan Tempel key.",
            "Simpan" to "Tekan Simpan di bawah. Muncul pesan Tersimpan. Semua stream key disimpan terenkripsi hanya di HP-mu dan tidak dikirim ke mana pun selain ke platform tujuan saat live.",
        )),
        Guide("start_live", "Cara mulai live dan mode latihan", "Tutorial awal", listOf(
            "Buka halaman Live" to "Tekan tab Live di bawah. Dari atas ke bawah ada: Platform tujuan, Tampilan siaran, Sumber video, Profil live, tombol besar MULAI LIVE, dan Opsi lainnya.",
            "Pilih platform tujuan" to "Di Platform tujuan, ketuk YouTube, TikTok, atau platform lain sampai menyala. Bisa satu atau beberapa sekaligus. Kalau di belakang namanya tertulis belum ada key, isi stream key dulu (lihat panduan Cara mengisi stream key). Live ke 3 platform atau lebih butuh internet unggah yang lebih kencang.",
            "Pilih tampilan siaran" to "Di Tampilan siaran pilih Tegak 9:16 (paling cocok untuk TikTok dan YouTube Shorts) atau Mendatar 16:9 (cocok untuk YouTube biasa). Kalau memilih Mendatar, putar HP ke posisi mendatar. Layar aplikasi ikut mendatar selama live.",
            "Pilih sumber video" to "Kamera HP: memakai kamera depan atau belakang.\nLayar: menyiarkan apa pun yang ada di layar HP, misalnya main game atau presentasi.\nSuara saja: hanya suara, dengan gambar diam yang bisa kamu pilih.\nWebcam USB: kamera yang dicolok ke HP lewat kabel OTG.",
            "Pilih profil live" to "Profil adalah kumpulan pengaturan siap pakai: Ngobrol santai, Presentasi, Catatan live, dan Live music. Pilih yang sesuai acaramu. Profil bisa diubah atau ditambah di Pengaturan, Profil live.",
            "Opsi lainnya" to "Tekan Opsi lainnya untuk membuka:\n- Mode latihan: mencoba live tanpa disiarkan, hanya direkam untuk didengar ulang.\n- Sertakan audio internal: suara aplikasi lain, tamu, dan Saweria ikut tersiar.\n- Kualitas video.\n- Ruang tamu.\n- Periksa sebelum live: mengecek internet, stream key, dan mikrofon.\n- Siaran YouTube: judul, thumbnail, visibilitas, dan lainnya.\n- Cek monetisasi di YouTube Studio.\n- Tempel key TikTok dari clipboard.",
            "Mulai" to "Tekan tombol besar MULAI LIVE. Izinkan kamera dan mikrofon kalau diminta. Kalau memakai Layar atau audio internal, Android meminta izin merekam layar. Tekan Mulai sekarang. Dalam beberapa detik status di bagian atas menunjukkan YouTube dan TikTok tersambung.",
            "Selama live" to "Kamera tampil penuh layar. Di atas ada status (durasi, penonton, kondisi internet). Komentar mengambang di layar. Di bawah ada lima tombol: Tamu, Alat, Bagikan, Sempurnakan, dan Lainnya. Untuk berhenti, tekan tombol berhenti lalu konfirmasi.",
        )),
        Guide("invite_guests", "Cara mengundang tamu", "Tutorial awal", listOf(
            "Buka ruang tamu" to "Ruang tamu bisa dibuka sebelum atau saat live.\nSebelum live: halaman Live, Opsi lainnya, bagian Tamu live, lalu Buka ruang tamu.\nSaat live: tekan Tamu di bawah, lalu Buka ruang tamu.",
            "Bagikan undangan" to "Tekan Bagikan link undangan, lalu pilih WhatsApp atau aplikasi lain. Ada juga kode QR yang bisa dipindai, dan Bagikan link download aplikasi untuk tamu yang belum punya aplikasinya.",
            "Yang dilakukan tamu" to "1. Tamu membuka link undangan.\n2. Kalau aplikasi Stream By Natan sudah terpasang, link langsung membuka aplikasi. Kalau belum, tamu bisa mengunduh aplikasinya atau masuk lewat browser.\n3. Tamu mengisi nama (wajib), lalu tekan Masuk ruang tunggu.\n4. Tamu bisa mengirim pesan ke host, memilih filter, dan mengganti kamera depan atau belakang sambil menunggu.",
            "Memasukkan tamu" to "Di panel Tamu, tamu tampil berurutan sesuai siapa yang datang duluan, dengan keterangan lewat aplikasi atau lewat browser. Pilihannya:\n- Masukkan berikutnya: memasukkan tamu paling awal.\n- Centang beberapa tamu, lalu Masukkan terpilih.\n- Masukkan semua.\n- Tolak terpilih.",
            "Saat tamu tampil" to "Kalau tidak ada yang bicara atau beberapa orang bicara bersamaan, semua layar sama besar. Kalau hanya satu orang yang bicara, layarnya otomatis penuh. Kamu bisa menekan Bisukan mic (tamu bisa menyalakannya lagi sendiri), Keluarkan, atau Balas pesan tamu.",
            "Rekaman per tamu" to "Setelah live, rekaman otomatis dipotong untuk setiap tamu, dari saat tamu masuk sampai keluar. Hasilnya ada di tab Rekaman.",
        )),
        Guide("live_notes", "Cara memakai Catatan live", "Tutorial awal", listOf(
            "Apa itu Catatan live" to "Catatan live adalah naskah atau poin-poin yang kamu siapkan sebelum live, misalnya untuk renungan, presentasi, atau materi kelas. Saat live, catatan muncul sebagai teleprompter di layarmu atau dibisikkan pelan, dan poin-poin pentingnya bisa tampil di siaran.",
            "Tulis catatan" to "1. Buka tab Pengaturan, lalu Catatan live & lirik.\n2. Tambah catatan baru dan beri judul.\n3. Baris yang diawali tanda pagar lalu spasi (# ) menjadi judul poin.\n4. Tulis poin atau kutipan yang ingin tampil di siaran dengan format [Judul] isi.",
            "Saat live" to "Pilih profil Catatan live sebelum mulai. Saat kamu mengucapkan judul sebuah poin, isinya tampil penuh layar di siaran. Ucapkan poin berikutnya untuk lanjut, atau tutup tampilan untuk menutupnya.",
            "Teleprompter atau bisikan" to "Kalau mode pembaca layar mati, catatan tampil di layarmu dengan huruf besar seperti teleprompter dan tidak terlihat penonton. Kalau mode pembaca layar menyala, catatan dibisikkan pelan dan hanya kamu yang mendengar.",
            "Latihan" to "Buka Pengaturan, Alat live, lalu nyalakan Laporan latihan Catatan live. Mulai live dengan Mode latihan. Setelah selesai, kamu mendapat laporan berapa lama setiap poin dibawakan.",
        )),
    )

    val features: List<Guide> = listOf(
        // ---------------------------------------------------------------- Live
        Guide("platforms", "Live ke banyak platform", "Live", listOf(
            "Pilih tujuan" to "Di halaman Live, ketuk nama platform di Platform tujuan sampai menyala: YouTube, TikTok, Facebook, Instagram, Server lain 1, Server lain 2. Ketuk lagi untuk mematikan.",
            "Internet" to "Setiap platform dikirim terpisah. Kira-kira butuh internet unggah 3 Mbps per platform. Tekan Periksa sebelum live di Opsi lainnya untuk melihat apakah internetmu cukup.",
            "Kalau satu platform gagal" to "Platform lain tetap jalan. Status masing-masing platform terlihat di bagian atas layar live. Aplikasi mencoba menyambung ulang secara otomatis.",
        )),
        Guide("orientation", "Tampilan tegak atau mendatar", "Live", listOf(
            "Memilih" to "Di halaman Live, bagian Tampilan siaran, pilih Tegak 9:16 atau Mendatar 16:9 sebelum menekan MULAI LIVE. Pilihan ini tidak bisa diubah saat live sedang berjalan.",
            "Tegak 9:16" to "Cocok untuk TikTok, YouTube Shorts, dan penonton HP. Pegang HP tegak seperti biasa.",
            "Mendatar 16:9" to "Cocok untuk YouTube biasa dan penonton laptop atau TV. Setelah menekan MULAI LIVE, putar HP ke posisi mendatar. Layar aplikasi ikut mendatar selama live dan kembali tegak setelah live selesai.",
            "Gambar otomatis menyesuaikan" to "Semua yang muncul otomatis di siaran mengikuti tampilan yang kamu pilih: gambar TEMA, gambar dari galeri, gambar jeda, jawaban FAQ, kartu ayat, kode QR, layar pembuka dan penutup. Di tampilan tegak semuanya penuh layar tegak, di tampilan mendatar penuh layar mendatar. Bagian gambar yang kelebihan dipotong di tengah supaya tidak ada garis hitam.",
            "TikTok saat mendatar" to "Kalau memilih Mendatar dan TikTok menyala, ada dua pilihan Tampilan di TikTok:\n- Mendatar asli (seperti TikTok Live Studio): penonton TikTok melihat video mendatar di tengah dan bisa memutar HP untuk menonton layar penuh.\n- Bingkai tegak: video mendatar di tengah, judul di atas, latar video diburamkan, dan ruang kosong di bawah untuk komentar TikTok.\nKarena satu siaran dikirim ke semua platform, YouTube juga ikut menerima bingkai tegak. Bingkai tegak paling cocok kalau kamu live ke TikTok saja, dan hanya berlaku untuk sumber Kamera HP.",
            "Judul bingkai" to "Judul di atas video diisi di kolom Judul di atas video. Kalau dikosongkan, aplikasi memakai judul siaran YouTube. Saat live, judul bisa diganti di Lainnya, bagian Bingkai TikTok.",
        )),
        Guide("youtube_connect", "Menghubungkan YouTube", "YouTube", listOf(
            "Kenapa perlu" to "Kalau YouTube dihubungkan, aplikasi bisa membuat siaran lengkap dengan judul dan thumbnail, mengambil stream key otomatis, membaca komentar YouTube, dan membalas komentar pakai suara.",
            "Caranya" to "1. Buka tab Pengaturan, lalu Stream key & server.\n2. Tekan Hubungkan YouTube (login Google).\n3. Pilih akun Google yang punya kanal YouTube-mu.\n4. Kalau muncul tulisan Google belum memverifikasi aplikasi ini, tekan Lanjutan, lalu Buka Stream By Natan.\n5. Centang semua izin, lalu tekan Lanjutkan.\nTombolnya berubah menjadi YouTube terhubung.",
            "Kalau gagal" to "Pastikan akun Google itu memang punya kanal YouTube dan fitur live sudah aktif (sudah verifikasi nomor HP). Coba lagi setelah beberapa menit.",
        )),
        Guide("youtube_broadcast", "Pengaturan siaran YouTube", "YouTube", listOf(
            "Membuka" to "Di halaman Live, tekan Opsi lainnya, lalu Siaran YouTube. Halaman ini butuh YouTube yang sudah dihubungkan.",
            "Detail" to "Judul: wajib, maksimal 100 huruf.\nDeskripsi: penjelasan siaran, boleh berisi link.\nThumbnail: tekan Pilih thumbnail dari galeri. Ukuran disarankan 1280 x 720, maksimal 2 MB. Kanal harus sudah verifikasi nomor HP supaya thumbnail bisa dipasang.",
            "Visibilitas dan audiens" to "Visibilitas: Publik (semua orang bisa menemukan), Tidak publik (hanya yang punya link), atau Pribadi (hanya kamu).\nAudiens: pilih Bukan untuk anak-anak, kecuali siaranmu memang dibuat khusus untuk anak. Siaran untuk anak-anak tidak punya komentar live dan iklannya dibatasi.",
            "Monetisasi" to "Nyalakan Jeda iklan otomatis selama live kalau kanalmu sudah dimonetisasi, lalu atur setiap berapa menit iklan muncul (minimal 5 menit). Tombol Cek monetisasi di YouTube Studio membuka YouTube Studio untuk memastikan monetisasi siaran menyala. Lihat panduan Cek monetisasi YouTube.",
            "Kategori, tag, bahasa, lokasi" to "Kategori: jenis konten, misalnya Orang & blog atau Pendidikan.\nTag: kata kunci supaya siaran mudah dicari, satu per baris.\nBahasa: bahasa yang dipakai di siaran.\nLokasi: ketik nama kota atau tempat, misalnya Manado. Kalau YouTube menolak lokasinya, siaran tetap dibuat.",
            "Siaran" to "Latensi: Normal (kualitas terbaik), Rendah, atau Sangat rendah (paling cepat untuk ngobrol dengan penonton).\nPenonton bisa memutar mundur (DVR).\nJadwalkan: isi berapa menit lagi siaran dimulai, atau 0 untuk langsung.\nBuat siaran baru otomatis setiap Mulai live: kalau dinyalakan, setiap kali menekan MULAI LIVE aplikasi membuat siaran baru memakai isian ini.",
            "Membuat siaran" to "Tekan Buat siaran YouTube sekarang. Aplikasi membuat siaran, memasang thumbnail, lalu mengambil stream key sendiri. Setelah itu kembali ke halaman Live dan tekan MULAI LIVE. Semua isian disimpan untuk live berikutnya.",
        )),
        Guide("monetization", "Cek monetisasi YouTube", "YouTube", listOf(
            "Kenapa perlu dicek" to "Monetisasi (iklan) live YouTube tidak bisa dinyalakan atau dimatikan dari aplikasi lain, termasuk aplikasi ini, karena YouTube hanya mengizinkannya lewat YouTube Studio. Kadang siaran yang dibuat dari aplikasi luar mulai dengan monetisasi mati, padahal setelan kanal sudah menyala. Karena itu sebaiknya dicek sebelum atau di awal live.",
            "Syarat" to "1. Kanal sudah masuk Program Partner YouTube (sudah dimonetisasi).\n2. Siaran tidak ditandai Dibuat untuk anak-anak.\n3. Akun YouTube sudah dihubungkan di aplikasi, supaya tombol cek langsung membuka siaran yang benar.",
            "Letak tombol" to "Tombol Cek monetisasi di YouTube Studio ada di tiga tempat:\n1. Halaman Live, Opsi lainnya (sebelum live). Tombol ini muncul kalau YouTube menyala di Platform tujuan.\n2. Halaman Siaran YouTube, bagian Monetisasi.\n3. Saat live: tombol Lainnya di bawah, bagian YouTube.",
            "Langkah mengecek" to "1. Tekan Cek monetisasi di YouTube Studio.\n2. YouTube Studio terbuka di aplikasi YT Studio atau browser. Kalau siaran sudah dibuat dari aplikasi ini, yang terbuka langsung halaman siaran itu. Kalau belum, yang terbuka Live Control Room.\n3. Tekan Edit (ikon pensil).\n4. Pilih tab Monetisasi.\n5. Ubah menjadi Aktif.\n6. Pilih waktu iklan Otomatis (disarankan), Terjadwal, atau Manual.\n7. Tekan Simpan, lalu kembali ke aplikasi Stream By Natan.",
            "Supaya tidak perlu cek setiap kali" to "Di YouTube Studio, buka Live Control Room, lalu Setelan (ikon roda gigi). Nyalakan Monetisasi sebagai bawaan dan pilih iklan Otomatis. Siaran berikutnya ikut memakai setelan ini. Tetap cek sesekali, karena siaran dari aplikasi luar kadang tetap mulai dengan monetisasi mati.",
            "Jeda iklan dari aplikasi" to "Setelah monetisasi aktif, kamu bisa:\n- Menyalakan Jeda iklan otomatis di halaman Siaran YouTube.\n- Saat live, menekan Lainnya, lalu Jeda iklan sekarang untuk menyisipkan iklan 30 detik.\nKedua fitur ini tidak berfungsi kalau monetisasi siaran masih mati.",
        )),
        Guide("share_live", "Membagikan link live", "Live", listOf(
            "Saat live" to "Tekan Bagikan di bawah layar live. Pilih WhatsApp, Telegram, Facebook, X, Instagram, LINE, Messenger, email, SMS, atau Aplikasi lain. Tekan Salin link untuk menyalin link YouTube dan TikTok sekaligus.",
            "Otomatis" to "Di Pengaturan, Semua fitur, nyalakan Bagikan link live otomatis. Beberapa detik setelah live dimulai, menu bagikan muncul sendiri.",
            "Link YouTube dan TikTok" to "Supaya link benar, isi Link kanal YouTube dan Username TikTok di Pengaturan, Stream key & server.",
        )),
        Guide("quality", "Kualitas video", "Live", listOf(
            "Pilihan" to "480p hemat kuota dan cocok untuk internet lemah. 720p seimbang dan disarankan. 1080p paling tajam tapi butuh internet kencang dan membuat HP lebih panas. Otomatis menurunkan kualitas sendiri saat internet melemah.",
            "Letak" to "Sebelum live: halaman Live, Opsi lainnya, Kualitas video. Saat live: Lainnya, Kualitas video (berlaku di live berikutnya). Pengaturan lebih rinci (frame per detik, bitrate) ada di Pengaturan, Video & koneksi.",
        )),
        Guide("floating", "Berbagi layar dan jendela mengambang", "Live", listOf(
            "Berbagi layar" to "Pilih sumber Layar di halaman Live, lalu MULAI LIVE. Izinkan perekaman layar. Semua yang ada di layar HP ikut tersiar, jadi tutup notifikasi pribadi dulu.",
            "Jendela mengambang" to "Saat berbagi layar, muncul tombol kecil mengambang. Dari situ kamu bisa menyalakan atau mematikan mikrofon dan audio HP, memutar efek suara, dan berhenti berbagi tanpa kembali ke aplikasi. Izinkan Tampil di atas aplikasi lain kalau diminta.",
        )),

        // ---------------------------------------------------------------- Tamu & moderator
        Guide("moderators", "Cara menambah moderator", "Moderator", listOf(
            "Apa itu moderator" to "Moderator adalah orang yang kamu percaya untuk membantu mengurus live dari HP-nya sendiri: memasukkan, membisukan, mengeluarkan, dan membalas tamu, membisukan penonton, dan mengatur antrean pertanyaan.",
            "Lewat username" to "1. Tekan ikon Akun di kanan atas halaman Live.\n2. Tekan Moderator.\n3. Di Tambah lewat username, ketik username orangnya.\n4. Tekan Tambah moderator.\nOrang itu harus sudah memasang aplikasi dan membuat profil dengan username.",
            "Lewat link" to "1. Menu Akun, Moderator.\n2. Tekan Bagikan link undangan moderator, lalu kirim lewat WhatsApp atau aplikasi lain.\n3. Orang yang menerima membuka link itu, login di aplikasi, lalu menekan Terima.",
            "Cara moderator bekerja" to "Saat kamu live dan ruang tamu terbuka, moderator membuka menu Akun, Panel moderator, lalu memilih namamu. Panel moderator berisi Antrean pertanyaan, daftar Tamu, dan Penonton.",
            "Mencabut" to "Menu Akun, Moderator, lalu tekan Cabut status moderator di samping namanya.",
        )),

        // ---------------------------------------------------------------- Komentar
        Guide("comments", "Komentar TikTok dan YouTube", "Komentar", listOf(
            "TikTok" to "Isi Username TikTok (tanpa @) di Pengaturan, Stream key & server, bagian Komentar & kejadian. Komentar, hadiah, follow, share, dan jumlah penonton dibaca langsung dari halaman live TikTok tanpa API key.",
            "Kalau komentar TikTok tidak muncul" to "1. Buka Pengaturan, Stream key & server.\n2. Tekan Buka halaman TikTok (login / verifikasi), lalu login atau selesaikan verifikasi.\n3. Kembali, lalu tekan Sambung ulang komentar TikTok.\n4. Izinkan Tampil di atas aplikasi lain supaya pembaca komentar tetap jalan.",
            "YouTube" to "Kalau YouTube sudah dihubungkan, komentar dibaca otomatis. Kalau belum, isi ID video YouTube live di kolom yang sama.",
            "Di layar" to "Komentar mengambang di layar live-mu. Ukurannya bisa diatur di Lainnya, Pengaturan komentar.",
        )),
        Guide("comment_settings", "Pengaturan komentar", "Komentar", listOf(
            "Letak" to "Saat live, tekan Lainnya, lalu gulir ke Pengaturan komentar. Pengaturan lebih lengkap ada di tab Pengaturan, Komentar & pertanyaan.",
            "Pilihan" to "Bacakan komentar (TTS), Filter kata kasar, Terjemahkan komentar asing, Tampilkan komentar di layar siaran (penonton ikut melihat), Ukuran komentar di layarmu, Kecepatan TTS, dan Mute penonton tertentu.",
            "Daftar kata" to "Di Pengaturan, Komentar & pertanyaan kamu bisa mengubah daftar kata tanya, singkatan yang dipanjangkan sebelum dibacakan (misalnya gmn jadi gimana), kata kasar yang disensor, dan penonton yang di-mute.",
        )),
        Guide("questions", "Antrean pertanyaan", "Komentar", listOf(
            "Otomatis" to "Komentar yang berupa pertanyaan (ada tanda tanya atau kata tanya seperti apa, gimana, kapan) masuk antrean. Komentar biasa tetap dibacakan seperti biasa.",
            "Kendali" to "Saat live tekan Alat. Di bagian Pertanyaan ada Pertanyaan berikutnya, Ulangi, Tahan, dan Lewati. Bisa juga dengan suara: pertanyaan berikutnya, ulangi pertanyaan, tahan pertanyaan, lewati pertanyaan.",
            "Rekaman" to "Setiap pertanyaan yang dijawab otomatis menjadi potongan video sendiri, dengan judul sesuai pertanyaannya.",
        )),
        Guide("voice_reply", "Balas komentar YouTube pakai suara", "Komentar", listOf(
            "Syarat" to "YouTube sudah dihubungkan, dan fitur Balas komentar YouTube pakai suara dinyalakan (Lainnya, Penanda & bantuan, atau Pengaturan, Alat live).",
            "Cara" to "Ucapkan kata pemicu (bawaannya balas), nama penonton, lalu pesannya. Contoh: balas Budi terima kasih sudah bergabung. Balasan terkirim ke live chat YouTube dari akunmu.",
            "Mengubah kata pemicu" to "Pengaturan, Alat live, bagian Balas komentar YouTube dengan suara, kolom Kata pemicu.",
        )),

        // ---------------------------------------------------------------- Sempurnakan
        Guide("filters", "Filter", "Sempurnakan", listOf(
            "Memilih filter" to "Saat live tekan Sempurnakan di bawah, lalu bagian Filter. Geser daftar dan ketuk filter: Normal, Cerah, Hangat, Sejuk, Sinematik, Vintage, Hitam putih, Sepia, Warna cerah, Pastel, Dramatis, Pudar, Senja, Malam, Retro, Klasik, Emas, Negatif, Kartun, Tajam, Duotone. Filter langsung terlihat di siaran.",
            "Penyesuaian" to "Di bawahnya ada pengatur kecerahan, kontras, dan kepekatan warna, serta Kulit halus.",
            "Tamu" to "Tamu memilih filternya sendiri dari layar tamu (di aplikasi maupun browser). Filter tamu terlihat di siaran.",
        )),
        Guide("background", "Latar belakang dan gambar dari galeri", "Sempurnakan", listOf(
            "Ganti latar" to "Sempurnakan, Latar belakang. Nyalakan Ganti latar belakang, lalu pilih buram, Gambar dari galeri, atau Video dari galeri. Tubuhmu tetap terlihat, latarnya diganti.",
            "Impor gambar ke layar" to "Sempurnakan, Impor gambar ke layar siaran. Pilih gambar dari galeri, lalu gambar tampil penuh layar di siaran selama 15 detik, mengikuti tampilan tegak atau mendatar.",
        )),
        Guide("overlay", "Overlay gambar dan video", "Sempurnakan", listOf(
            "Menambah" to "Sempurnakan, Overlay gambar & video. Pilih ukuran (kecil, sedang, penuh) dan posisi, lalu tekan Tambah gambar atau Tambah video dan pilih dari galeri.",
            "Menampilkan" to "Ketuk nama overlay untuk menampilkan, ketuk lagi untuk menyembunyikan. Tekan Hapus untuk membuangnya dari daftar.",
            "Catatan" to "Video diputar berulang, hanya 20 detik pertama, supaya HP tetap ringan. Overlay ukuran penuh mengikuti tampilan tegak atau mendatar.",
        )),
        Guide("themes", "TEMA otomatis", "Sempurnakan", listOf(
            "Menyiapkan tema" to "Sempurnakan, Tema. Ketik judul tema (misalnya Persahabatan), lalu tekan Pilih gambar tema dari galeri. Ulangi untuk tema lain. Buat sebanyak yang kamu mau.",
            "Menyalakan" to "Nyalakan Tema otomatis lewat suara di bagian yang sama.",
            "Memanggil dengan suara" to "Saat live, ucapkan kalimat yang ada kata tema, misalnya tema kita hari ini adalah persahabatan. Gambar tema yang judulnya paling cocok langsung tampil penuh layar, mengikuti tampilan tegak atau mendatar.",
            "Manual dan lama tampil" to "Tekan Tampilkan: nama tema untuk memunculkannya tanpa suara. Lama tampil diatur di Pengaturan, Alat live, bagian TEMA. Ucapkan tutup tampilan untuk menutupnya lebih cepat.",
        )),
        Guide("screen_text", "Teks layar", "Sempurnakan", listOf(
            "Menulis" to "Sempurnakan, Teks layar. Ketik teksnya, lalu nyalakan Tampilkan teks layar.",
            "Mengatur" to "Pilih posisi atas, tengah, atau bawah, ukuran, warna, dan Latar gelap di belakang teks supaya mudah dibaca. Teks tampil di semua platform.",
        )),
        Guide("sfx", "Efek suara", "Sempurnakan", listOf(
            "Memutar" to "Sempurnakan, Efek suara. Ketuk nama efek untuk memutarnya. Penonton ikut mendengar.",
            "Efek untuk hadiah" to "Pengaturan, Mikrofon, musik & efek suara, bagian Efek suara otomatis untuk gift.",
            "Buat sendiri" to "Di bagian Efek suara sendiri (dari file audio atau video), pilih file, potong bagian yang diinginkan, lalu simpan dengan nama.",
        )),
        Guide("mic", "Efek mikrofon dan pengubah suara", "Sempurnakan", listOf(
            "Efek mikrofon" to "Sempurnakan, Efek mikrofon: Peredam bising AI (RNNoise), Peredam bising kuat, Penyeimbang volume, dan Gema ruangan.",
            "Pengubah suara" to "Sempurnakan, Pengubah suara: Robot, Tinggi, Cempreng, Berat, Raksasa, Gema, atau Telepon. Pilih Normal untuk kembali. Bekerja tanpa internet.",
        )),
        Guide("music", "Musik latar", "Sempurnakan", listOf(
            "Memilih musik" to "Sempurnakan, Musik latar. Pilih dari musik bawaan, atau Impor dari galeri untuk memakai lagu sendiri. Tekan Pakai musik bawaan lagi untuk kembali.",
            "Volume" to "Geser pengatur volume musik sampai suaramu tetap jelas.",
            "Mengecil saat bicara" to "Lainnya, Musik latar: nyalakan Musik mengecil saat host bicara dan atur Tingkat musik saat bicara. Musik mengecil otomatis saat kamu bicara, tidak hilang, lalu kembali normal.",
            "Hak cipta" to "Lagu milik orang lain bisa membuat siaran dibisukan atau diturunkan oleh platform. Musik bawaan aplikasi aman dipakai.",
        )),

        // ---------------------------------------------------------------- Alat
        Guide("cameras", "Pindah dan tambah kamera", "Alat", listOf(
            "Manual" to "Saat live tekan Alat, bagian Kamera. Ketuk kamera yang mau dipakai: depan, belakang, webcam USB, atau kamera tambahan.",
            "Kamera tambahan dari HP lain" to "Alat, Tambah kamera dari HP lain. Bagikan linknya ke HP lain. HP itu membuka link, memberi nama kamera (misalnya kamera mimbar), lalu menjadi kamera tambahan.",
            "Dengan suara" to "Ucapkan kamera depan, kamera belakang, atau pindah ke diikuti nama kamera, misalnya pindah ke kamera mimbar.",
            "Webcam USB" to "Colok webcam lewat kabel OTG dan izinkan akses USB. Pilih sumber Webcam USB sebelum live, atau pilih dari Alat, Kamera saat live.",
        )),
        Guide("giveaway", "Giveaway", "Alat", listOf(
            "Menyiapkan" to "Pengaturan, Alat live, bagian Giveaway: nyalakan, isi kata ikut (bawaannya !ikut), lama pendaftaran, dan hadiah.",
            "Memulai" to "Saat live tekan Alat, lalu Mulai giveaway. Penonton ikut dengan mengetik kata ikut di komentar.",
            "Mengundi" to "Pemenang dipilih acak saat waktu habis. Bisa juga tekan Undi sekarang atau ucapkan undi pemenang. Nama pemenang diumumkan dan tampil di layar.",
        )),
        Guide("quiz", "Kuis", "Alat", listOf(
            "Menyiapkan soal" to "Pengaturan, Alat live, bagian Kuis. Tulis satu soal per baris dengan format pertanyaan | jawaban. Contoh: Ibu kota Sulawesi Utara? | Manado.",
            "Bermain" to "Saat live tekan Alat, lalu Kuis berikutnya. Soal tampil di layar, penonton menjawab di komentar, dan yang pertama benar mendapat poin.",
            "Skor" to "Tekan Papan skor untuk menampilkan juara.",
        )),
        Guide("poll", "Polling", "Alat", listOf(
            "Memulai" to "Alat, Polling. Tulis pertanyaan dan pilihan jawaban. Penonton mengetik huruf pilihannya di komentar.",
            "Hasil" to "Hasil diumumkan otomatis saat waktu habis, atau ucapkan hasil polling. Lama polling diatur di Pengaturan, Komentar & pertanyaan.",
        )),
        Guide("songs", "Daftar lagu permintaan", "Alat", listOf(
            "Menyalakan" to "Pengaturan, Alat live, bagian Request lagu: nyalakan dan atur kata pemicunya (bawaannya !lagu).",
            "Penonton meminta" to "Penonton mengetik !lagu lalu judul lagu di komentar.",
            "Antrean" to "Alat, Lagu permintaan menampilkan antreannya. Tekan Lagu berikutnya atau ucapkan lagu berikutnya untuk menampilkan permintaan di layar.",
        )),
        Guide("rundown", "Susunan acara", "Alat", listOf(
            "Menyusun" to "Pengaturan, Alat live, bagian Rundown acara. Tulis satu bagian per baris dengan format judul | menit. Contoh: Pembukaan | 5.",
            "Menjalankan" to "Alat, Acara berikutnya, atau ucapkan acara berikutnya. Aplikasi memberi tahu saat waktu satu bagian hampir habis.",
        )),
        Guide("supporters", "Pendukung teratas, target, dan pengumuman", "Alat", listOf(
            "Pendukung teratas" to "Menampilkan tiga pengirim hadiah terbesar. Ucapkan pendukung teratas.",
            "Target live" to "Pengaturan, Target & timer: atur target like, koin, dan penonton. Saat tercapai ada ucapan dan efek suara. Nyalakan Target live di Alat.",
            "Pengumuman otomatis" to "Pengaturan, Teks di layar, bagian Pengumuman otomatis: tulis pesan dan atur setiap berapa menit diulang. Nyalakan Pengumuman otomatis di Alat.",
        )),
        Guide("saweria", "Menghubungkan Saweria", "Alat", listOf(
            "Akun Saweria" to "Buat akun di saweria.co lalu atur halaman donasimu.",
            "Link overlay" to "1. Di dasbor Saweria, buka Overlay, lalu salin link Alert.\n2. Di aplikasi, buka Pengaturan, Tampilan & transisi, bagian Saweria.\n3. Tempel di kolom Link overlay Saweria.\nDonasi akan dibacakan dan diberi ucapan terima kasih otomatis.",
            "Kode QR" to "Isi Link halaman Saweria di bagian yang sama. Saat live ucapkan tampilkan kode qr, dan sembunyikan kode qr untuk menutupnya.",
        )),

        // ---------------------------------------------------------------- Kendali
        Guide("voice_commands", "Perintah suara", "Kendali", listOf(
            "Menyalakan" to "Lainnya, Perintah suara, atau Pengaturan, Semua fitur, Perintah suara.",
            "Contoh perintah" to "Pertanyaan berikutnya, ulangi pertanyaan, tahan, lewati, poin berikutnya, tampilkan kode qr, tutup tampilan, kamera depan, kamera belakang, pindah ke (nama kamera), matikan mikrofon, nyalakan mikrofon, jeda layar, buka layar, putar musik, hentikan musik, undi pemenang, kuis berikutnya, lagu berikutnya, acara berikutnya, hasil polling, sudah berapa lama.",
            "Mengubah kalimat" to "Pengaturan, Perintah suara. Setiap perintah bisa diberi kalimat sendiri, satu per baris.",
        )),
        Guide("pause", "Jeda layar", "Kendali", listOf(
            "Fungsi" to "Menutup layar siaran sementara dengan gambar atau tulisan Sebentar ya. Mikrofon bisa ikut dibisukan. Gambar jeda tampil penuh mengikuti tampilan tegak atau mendatar.",
            "Cara" to "Tekan tombol jeda di layar live, atau ucapkan jeda layar. Ucapkan buka layar atau tekan tombolnya lagi untuk melanjutkan.",
            "Mengatur" to "Pengaturan, Alat live, bagian Jeda layar: tulisan, gambar dari galeri, dan Matikan mikrofon saat jeda.",
        )),
        Guide("touch_lock", "Kunci sentuhan", "Kendali", listOf(
            "Fungsi" to "Mengunci layar saat live supaya tidak tersentuh tanpa sengaja, misalnya saat HP dipegang atau ditaruh di tripod.",
            "Mengunci dan membuka" to "Tekan tombol gembok di layar live. Untuk membuka, tekan lama tombol Buka kunci sekitar 2 detik.",
        )),
        Guide("cues", "Getaran dan suara penanda", "Kendali", listOf(
            "Fungsi" to "HP bergetar, berbunyi, atau mengucapkan peringatan saat ada kejadian penting: hadiah besar, tamu masuk ruang tunggu, pesan tamu, koneksi putus, pertanyaan baru, follow baru, baterai atau suhu, dan tindakan moderator. Hanya kamu yang mendengar, penonton tidak.",
            "Mengatur" to "Pengaturan, Getar, suara & TTS per kejadian. Untuk setiap kejadian atur Getar, Pola getar, Suara, dan Ucapkan dengan TTS.",
        )),
        Guide("battery", "Peringatan baterai dan suhu", "Kendali", listOf(
            "Fungsi" to "Memberi tahu saat baterai hampir habis atau HP terlalu panas, supaya live tidak putus tiba-tiba.",
            "Mengatur" to "Pengaturan, Alat live, bagian Pengingat & peringatan: batas baterai (persen) dan batas suhu (derajat Celsius).",
        )),
        Guide("reminders", "Pengingat minum dan live sepi", "Kendali", listOf(
            "Minum" to "Mengingatkan minum dan istirahat setiap beberapa menit. Atur di Pengaturan, Alat live.",
            "Live sepi" to "Kalau beberapa menit tidak ada komentar, aplikasi memberi ide topik untuk mengajak penonton ngobrol.",
        )),
        Guide("nameplate", "Papan nama", "Tampilan siaran", listOf(
            "Fungsi" to "Nama dan keterangan, misalnya Natan, Host, tampil di siaran seperti di televisi.",
            "Mengatur" to "Pengaturan, Alat live, bagian Papan nama: nama, peran, letak (kiri bawah, kiri atas, kanan atas), lama tampil di awal live (0 = terus), dan papan nama untuk tamu.",
        )),
        Guide("screen_reader", "Mode pembaca layar (tunanetra)", "Aksesibilitas", listOf(
            "Fungsi" to "Kalau dinyalakan, semua bantuan suara aktif: catatan dibisikkan, peringatan dan status diucapkan pelan dan hanya kamu yang mendengar. Kalau dimatikan, semuanya tampil sebagai tulisan di layar.",
            "Mengubah" to "Menu Akun, Tampilan, Mode pembaca layar (tunanetra). Bisa juga saat live di Lainnya, Penanda & bantuan.",
            "Komentar" to "Membacakan komentar dengan TTS adalah pilihan terpisah, karena berguna juga untuk pengguna yang bisa melihat.",
            "TalkBack dan Jieshuo" to "Aplikasi ini bisa dipakai bersama TalkBack atau Jieshuo. Suara pembaca layar tidak ikut tersiar ke penonton.",
        )),

        // ---------------------------------------------------------------- Rekaman
        Guide("recordings", "Rekaman dan potongan otomatis", "Rekaman", listOf(
            "Rekaman" to "Setiap live direkam otomatis di memori aplikasi dan tidak masuk galeri. Bisa dimatikan di Lainnya, Rekam siaran ke HP.",
            "Potongan otomatis" to "Setelah live, rekaman dipotong otomatis per pertanyaan, per poin Catatan live, dan per tamu.",
            "Menyimpan dan membagikan" to "Buka tab Rekaman. Ketuk rekaman atau potongan untuk memutarnya. Tekan Unduh untuk menyimpannya ke folder Download, Bagikan untuk mengirimnya, atau Hapus.",
        )),

        // ---------------------------------------------------------------- Sosial
        Guide("profile", "Profil dan halaman publik", "Sosial", listOf(
            "Mengisi profil" to "Menu Akun, Edit profil: tekan Ganti foto profil, isi nama, username (huruf kecil, angka, titik, garis bawah), bio, dan tautan YouTube, TikTok, Instagram, Facebook. Tekan Simpan profil.",
            "Halaman publik" to "Profilmu punya halaman web stream-by-natan-29448.web.app/@username. Saat kamu live, halaman itu menampilkan tulisan Sedang live dan tombol menonton.",
            "Membagikan" to "Menu Akun, Bagikan profil.",
        )),
        Guide("follow", "Mengikuti kreator dan jadwal live", "Sosial", listOf(
            "Mengikuti" to "Menu Akun, Mengikuti. Ketik username kreator, tekan Cari, lalu Ikuti.",
            "Notifikasi" to "Kamu mendapat notifikasi saat kreator yang diikuti mulai live. Izinkan notifikasi kalau diminta.",
            "Jadwal live" to "Menu Akun, Jadwal live. Isi judul dan waktu, lalu Tambah jadwal. Jadwal tampil di halaman profilmu.",
        )),

        // ---------------------------------------------------------------- Akun
        Guide("account", "Akun: keluar, beralih, hapus", "Akun", listOf(
            "Keluar" to "Menu Akun, Keluar, lalu konfirmasi. Pengaturan di HP tetap ada.",
            "Beralih akun" to "Menu Akun, Beralih akun. Pilih akun yang pernah dipakai lalu Pakai akun ini, atau Tambah akun untuk login dengan akun lain.",
            "Hapus akun" to "Menu Akun, Hapus akun, lalu konfirmasi. Profil, username, foto, daftar ikuti, moderator, dan cadangan pengaturan dihapus permanen dan tidak bisa dikembalikan.",
        )),
        Guide("language_display", "Bahasa dan tampilan", "Akun", listOf(
            "Bahasa" to "Menu Akun, Pilihan bahasa. Bahasa Indonesia adalah bawaan. Ada bahasa dunia dan bahasa daerah. Terjemahan bahasa dunia diunduh sekali lalu bisa dipakai tanpa internet.",
            "Tampilan" to "Menu Akun, Tampilan: tema gelap atau terang, Huruf besar, dan Kontras tinggi. Perubahan langsung terlihat.",
        )),
        Guide("restricted", "Izin setelan terbatas", "Akun", listOf(
            "Kapan perlu" to "Kalau aplikasi dipasang dari file APK (bukan Play Store), Android 13 ke atas bisa memblokir beberapa izin, misalnya akses Jangan Ganggu dan aksesibilitas.",
            "Caranya" to "1. Menu Akun, Izin setelan terbatas, lalu Buka Info aplikasi.\n2. Tekan titik tiga di kanan atas.\n3. Pilih Izinkan setelan terbatas, lalu konfirmasi dengan sidik jari atau PIN.\n4. Kembali ke aplikasi dan beri izin yang diperlukan.",
        )),
        Guide("feedback", "Kirim saran dan masukan", "Akun", listOf(
            "Caranya" to "Menu Akun, Kirim saran & masukan. Pilih jenisnya (Saran, Masalah / error, Pertanyaan, atau Lainnya), tulis pesannya sejelas mungkin, lalu Kirim. Pesanmu langsung sampai ke pembuat aplikasi.",
            "Supaya cepat ditangani" to "Kalau melaporkan masalah, tulis apa yang kamu lakukan, apa yang terjadi, dan apa yang seharusnya terjadi. Versi aplikasi dan model HP ikut terkirim otomatis.",
        )),
        Guide("updates", "Pembaruan aplikasi", "Akun", listOf(
            "Otomatis" to "Saat ada versi baru, aplikasi menawarkan pembaruan. Ikuti petunjuknya untuk mengunduh, lalu pasang. Kalau diminta, izinkan aplikasi memasang pembaruan. Versi baru menimpa yang lama tanpa menghapus data.",
            "Manual" to "Pengaturan, Sistem & pembaruan, bagian Pembaruan, lalu tekan Cek pembaruan sekarang.",
        )),
    )

    val all: List<Guide> get() = tutorial + features

    // ------------------------------------------------------------------ Tentang pembuat
    const val CREATOR_NAME = "Natanel Liaha"
    const val CREATOR_ORIGIN = "Lahir di Manado, 26 Desember 2002"

    val creatorProfile: List<Pair<String, String>> = listOf(
        "Tentang pembuat" to "Stream By Natan dibuat oleh Natanel Liaha, lahir di Manado pada 26 Desember 2002. " +
            "Natanel adalah seorang tunanetra yang ingin mengembangkan karya yang bermanfaat bagi banyak orang di media sosial, " +
            "khususnya bagi para live streamer yang hobi live dengan kebutuhannya masing-masing.",
        "Mengapa aplikasi ini dibuat" to "Banyak streamer hanya punya HP, sementara fitur live yang lengkap biasanya membutuhkan laptop atau komputer " +
            "dan beberapa aplikasi sekaligus. Stream By Natan menyatukan semuanya di satu aplikasi: live ke banyak platform sekaligus, " +
            "komentar yang dibacakan, tamu live, filter, overlay, musik latar, kuis, giveaway, hingga rekaman yang dipotong otomatis. " +
            "Tujuannya supaya siapa pun bisa merasakan pengalaman live seperti memakai laptop, cukup dengan HP.",
        "Dibuat dengan aksesibilitas" to "Karena dibuat oleh seorang tunanetra, aplikasi ini dirancang agar nyaman dipakai dengan pembaca layar: " +
            "setiap tombol punya keterangan, ada mode bantuan suara, catatan bisa dibisikkan, dan peringatan bisa diucapkan. " +
            "Pengguna yang bisa melihat tetap mendapat tampilan yang modern dan rapi.",
        "Pesan untuk publik" to "Melalui aplikasi ini, Natanel ingin membuktikan bahwa penyandang disabilitas juga mampu menciptakan karya " +
            "yang bermanfaat bagi banyak orang. Keterbatasan bukan penghalang untuk berkarya, berbagi, dan memberi dampak.",
        "Harapan" to "Semoga Stream By Natan membantu para streamer tampil lebih percaya diri, lebih dekat dengan penontonnya, " +
            "dan membuka kesempatan yang sama bagi siapa saja, termasuk teman-teman penyandang disabilitas, untuk berkarya di dunia live streaming.",
    )
}
