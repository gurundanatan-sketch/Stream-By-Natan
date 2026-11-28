package com.natan.streambynatan.ui

/**
 * Isi panduan di "Tentang aplikasi". Setiap panduan dipecah per langkah dengan judul,
 * supaya pembaca layar bisa melompat antar-langkah, dan dibuka satu per satu dari daftar menu.
 */
data class Guide(val id: String, val title: String, val group: String, val steps: List<Pair<String, String>>)

object Guides {
    const val APP_NAME = "Stream By Natan"

    /** Enam bagian tutorial yang muncul saat aplikasi pertama kali dibuka. */
    val tutorial: List<Guide> = listOf(
        Guide("welcome", "Selamat datang", "Tutorial awal", listOf(
            "Apa itu Stream By Natan" to "Aplikasi live streaming dari HP ke YouTube, TikTok, Facebook, dan server lain sekaligus, dengan fitur selengkap live dari laptop.",
            "Yang bisa kamu lakukan" to "Live dengan kamera HP, berbagi layar, suara saja, atau webcam USB. Komentar dibacakan, tamu bisa ikut tampil, ada efek suara, musik latar, filter, overlay, kuis, giveaway, dan rekaman otomatis.",
            "Gratis" to "Semua fitur gratis selama masa promosi. Tamu selalu gratis.",
        )),
        Guide("stream_keys", "Cara mendapatkan stream key", "Tutorial awal", listOf(
            "YouTube" to "Buka YouTube Studio, tekan Buat, lalu Siaran langsung. Salin Kunci streaming. Kanal baru perlu verifikasi nomor HP dan menunggu sekitar 24 jam sebelum bisa live pertama kali. Atau hubungkan YouTube di aplikasi supaya stream key diambil otomatis.",
            "TikTok" to "Buka halaman LIVE Producer di situs TikTok (tiktok.com/live/producer) dari browser, login, lalu salin URL server dan Kunci streaming. Tidak semua akun TikTok mendapat akses kunci streaming.",
            "Facebook" to "Buka Facebook Live Producer (facebook.com/live/producer), pilih Gunakan kunci streaming, lalu salin Kunci streaming.",
            "Server lain" to "Platform apa pun yang memberi alamat RTMP dan stream key bisa dipakai lewat Server lain 1 atau 2.",
        )),
        Guide("fill_keys", "Cara mengisi stream key", "Tutorial awal", listOf(
            "Buka pengaturan" to "Buka Pengaturan, lalu Stream key dan server.",
            "Isi" to "Tempel stream key YouTube, server dan stream key TikTok, serta username TikTok untuk membaca komentar. Platform tambahan diisi di bagian Platform tambahan.",
            "Aman" to "Semua stream key disimpan terenkripsi di HP dan tidak pernah dikirim ke mana pun selain ke platform tujuan.",
        )),
        Guide("start_live", "Cara mulai live dan mode latihan", "Tutorial awal", listOf(
            "Pilih platform" to "Di halaman Live, nyalakan platform tujuan: YouTube, TikTok, Facebook, atau server lain. Bisa satu atau beberapa sekaligus.",
            "Pilih sumber dan profil" to "Pilih sumber video (Kamera, Layar, Suara saja, Webcam) dan profil live (Ngobrol santai, Presentasi, Catatan live, Live music).",
            "Mode latihan" to "Buka Opsi lainnya lalu nyalakan Mode latihan untuk mencoba tanpa disiarkan. Hasilnya direkam supaya bisa didengar ulang.",
            "Mulai" to "Tekan tombol besar Mulai live. Saat live, kamera tampil penuh layar dan tombol kendali ada di bawah: Tamu, Alat, Bagikan, Sempurnakan, Lainnya.",
        )),
        Guide("invite_guests", "Cara mengundang tamu", "Tutorial awal", listOf(
            "Buka ruang tamu" to "Tekan Tamu, lalu Buka ruang tamu. Ruang tamu bisa dibuka sebelum atau saat live.",
            "Bagikan link" to "Bagikan link undangan atau kode QR, misalnya lewat WhatsApp.",
            "Tamu masuk" to "Kalau aplikasi sudah terpasang, link langsung membuka aplikasi. Kalau belum, tamu bisa mengunduh aplikasi atau masuk lewat browser. Tamu mengisi nama lalu masuk ruang tunggu.",
            "Pilih tamu" to "Tamu antre berdasarkan siapa yang datang duluan, dengan keterangan lewat aplikasi atau lewat browser. Pilih satu atau beberapa tamu untuk dimasukkan. Kamu juga bisa membalas pesan tamu, membisukan mikrofon tamu, dan mengeluarkan tamu.",
        )),
        Guide("live_notes", "Cara memakai Catatan live", "Tutorial awal", listOf(
            "Tulis catatan" to "Buka Pengaturan, lalu Catatan live dan lirik. Baris yang diawali tanda pagar dan spasi menjadi judul poin.",
            "Poin di layar" to "Tulis poin atau kutipan dengan format [Judul] isi. Saat kamu mengucapkan judulnya, isinya tampil penuh layar di siaran.",
            "Teleprompter atau bisikan" to "Kalau mode pembaca layar mati, catatan tampil di layarmu dengan huruf besar seperti teleprompter. Kalau mode pembaca layar menyala, catatan dibisikkan pelan dan hanya kamu yang mendengar.",
            "Latihan" to "Nyalakan Laporan latihan Catatan live, lalu mulai Mode latihan. Setelah selesai, kamu mendapat durasi setiap poin.",
        )),
    )

    val features: List<Guide> = listOf(
        Guide("platforms", "Live ke banyak platform", "Live", listOf(
            "Pilih tujuan" to "Di halaman Live, nyalakan tombol YouTube, TikTok, Facebook, Instagram, atau Server lain.",
            "Internet" to "Setiap platform dikirim terpisah, jadi butuh internet unggah lebih besar. Pemeriksaan sebelum live memberi saran berapa platform yang aman.",
            "Instagram" to "Instagram hanya bisa kalau akunmu sudah mendapat stream key dari Instagram.",
        )),
        Guide("youtube_connect", "Menghubungkan YouTube", "Live", listOf(
            "Hubungkan" to "Buka Pengaturan, Stream key dan server, lalu tekan Hubungkan YouTube. Pilih akun Google yang punya kanal YouTube-mu.",
            "Peringatan Google" to "Kalau muncul aplikasi belum diverifikasi, tekan Lanjutan lalu buka Stream By Natan.",
            "Manfaat" to "Aplikasi bisa membuat siaran, judul, deskripsi, thumbnail, membaca komentar, dan membalas komentar pakai suara.",
        )),
        Guide("comments", "Komentar TikTok dan YouTube", "Komentar", listOf(
            "TikTok" to "Isi username TikTok. Komentar, hadiah, ikuti, bagikan, dan penonton dibaca dari halaman live TikTok tanpa API key.",
            "YouTube" to "Isi ID video atau hubungkan YouTube. Komentar dibaca otomatis.",
            "Verifikasi TikTok" to "Kalau TikTok minta login atau verifikasi, buka Pengaturan, Stream key, lalu Buka halaman TikTok.",
        )),
        Guide("comment_settings", "Pengaturan komentar", "Komentar", listOf(
            "Letak" to "Saat live, tekan Lainnya lalu Pengaturan komentar.",
            "Pilihan" to "Komentar dibacakan TTS atau tidak, filter kata kasar, mute penonton, terjemahan komentar, tampilkan komentar di layar siaran, ukuran dan posisi komentar di layarmu, serta kecepatan dan suara TTS.",
        )),
        Guide("screen_reader", "Mode pembaca layar (tunanetra)", "Aksesibilitas", listOf(
            "Fungsi" to "Kalau dinyalakan, semua bantuan suara aktif: catatan dibisikkan, peringatan diucapkan. Kalau dimatikan, semuanya tampil sebagai tulisan di layar.",
            "Mengubah" to "Buka menu Akun, Tampilan, atau Pengaturan, TTS dan suara.",
            "Komentar" to "Pembacaan komentar lewat TTS adalah pilihan terpisah, karena berguna juga bagi pengguna yang bisa melihat.",
        )),
        Guide("questions", "Antrean pertanyaan", "Komentar", listOf(
            "Otomatis" to "Komentar berbentuk pertanyaan masuk antrean. Komentar biasa tetap dibacakan.",
            "Kendali" to "Gunakan Pertanyaan berikutnya, Ulangi, Tahan, dan Lewati, atau perintah suara.",
            "Rekaman" to "Setiap pertanyaan yang dijawab otomatis menjadi potongan video sendiri setelah live.",
        )),
        Guide("filters", "Filter", "Sempurnakan", listOf(
            "Pilih filter" to "Saat live tekan Sempurnakan, lalu Filter. Ada banyak pilihan seperti Cerah, Hangat, Sejuk, Sinematik, Vintage, Hitam putih, Sepia, Pastel, Dramatis, Senja, Retro, Emas, Kartun, dan lainnya.",
            "Penyesuaian dasar" to "Atur kecerahan, kontras, dan kepekatan warna, serta kulit halus.",
            "Tamu" to "Tamu juga bisa memilih filternya sendiri dari layar tamu.",
        )),
        Guide("background", "Latar belakang dan gambar dari galeri", "Sempurnakan", listOf(
            "Ganti latar" to "Sempurnakan, Latar belakang: pilih buram, gambar, atau video dari galeri. Tubuhmu tetap terlihat, latarnya diganti.",
            "Impor gambar" to "Sempurnakan, Impor gambar: pilih gambar dari galeri untuk langsung ditampilkan penuh layar di siaran.",
        )),
        Guide("overlay", "Overlay gambar dan video", "Sempurnakan", listOf(
            "Tambah overlay" to "Sempurnakan, Overlay, lalu Tambah dari galeri. Pilih gambar atau video, ukuran (kecil, sedang, penuh) dan posisinya.",
            "Tampilkan" to "Ketuk nama overlay untuk menampilkan atau menyembunyikannya. Cocok untuk review produk atau menampilkan contoh.",
            "Video" to "Video diputar berulang, maksimal 20 detik pertama, supaya HP tetap ringan.",
        )),
        Guide("sfx", "Efek suara", "Sempurnakan", listOf(
            "Putar" to "Sempurnakan, Efek suara. Ada sekitar 200 efek suara bawaan.",
            "Buat sendiri" to "Di Pengaturan, Efek suara, kamu bisa membuat efek dari file audio atau video, lalu memotong bagian yang diinginkan.",
        )),
        Guide("mic", "Efek mikrofon dan pengubah suara", "Sempurnakan", listOf(
            "Efek mikrofon" to "Peredam bising (bawaan, kuat, atau AI RNNoise), penyeimbang volume, gema, dan suara studio.",
            "Pengubah suara" to "Pilih Robot, Tinggi, Cempreng, Berat, Raksasa, Gema, atau Telepon. Suaramu langsung berubah tanpa internet.",
        )),
        Guide("music", "Musik latar", "Sempurnakan", listOf(
            "Pilih musik" to "Sempurnakan, Musik latar: pilih dari 20 musik bawaan, atau Impor dari galeri untuk memakai lagu sendiri.",
            "Volume" to "Atur volume musik sesukamu supaya penonton nyaman.",
            "Mengecil saat bicara" to "Nyalakan Musik mengecil saat host bicara di Lainnya. Musik mengecil otomatis saat kamu bicara, tidak hilang, lalu kembali normal.",
        )),
        Guide("screen_text", "Teks layar", "Sempurnakan", listOf(
            "Tulis" to "Sempurnakan, Teks layar: ketik judul atau teks apa saja.",
            "Atur" to "Pilih posisi atas, tengah, atau bawah, ukuran, warna, dan latar. Teks tampil di YouTube, TikTok, dan platform lain.",
        )),
        Guide("themes", "TEMA otomatis", "Sempurnakan", listOf(
            "Siapkan tema" to "Sempurnakan, Tema: tambah judul tema dan pilih gambarnya dari galeri. Buat sebanyak yang kamu mau.",
            "Panggil dengan suara" to "Saat live, ucapkan kalimat apa saja yang ada kata TEMA, misalnya tema kita hari ini adalah persahabatan. Gambar tema yang judulnya paling cocok langsung tampil penuh layar.",
            "Lama tampil" to "Lama tampil bisa diatur. Ucapkan tutup tampilan untuk menutupnya lebih cepat.",
        )),
        Guide("giveaway", "Giveaway", "Alat", listOf(
            "Mulai" to "Alat, Giveaway: isi hadiah lalu mulai. Penonton ikut dengan mengetik kata kunci di komentar, bawaannya !ikut.",
            "Undi" to "Pemenang dipilih acak saat waktu habis, atau tekan Undi sekarang, atau ucapkan undi pemenang.",
        )),
        Guide("quiz", "Kuis", "Alat", listOf(
            "Siapkan soal" to "Pengaturan, Alat live: tulis soal dan jawabannya.",
            "Main" to "Alat, Kuis berikutnya. Soal tampil di layar, penonton menjawab di komentar, yang pertama benar dapat poin.",
            "Skor" to "Tekan Papan skor untuk menampilkan juara.",
        )),
        Guide("poll", "Polling", "Alat", listOf(
            "Mulai" to "Alat, Polling: tulis pertanyaan dan pilihan. Penonton mengetik huruf pilihannya di komentar.",
            "Hasil" to "Hasil diumumkan otomatis saat waktu habis.",
        )),
        Guide("songs", "Daftar lagu permintaan", "Alat", listOf(
            "Penonton meminta" to "Penonton mengetik !lagu judul lagu di komentar.",
            "Antrean" to "Alat, Lagu permintaan menampilkan antreannya. Tekan Lagu berikutnya untuk menampilkan permintaan di layar.",
        )),
        Guide("rundown", "Susunan acara", "Alat", listOf(
            "Susun" to "Pengaturan, Alat live: tulis urutan acara dan durasinya, misalnya Pembukaan 5 menit.",
            "Jalankan" to "Alat, Acara berikutnya. Aplikasi memberi tahu saat waktu satu bagian habis.",
        )),
        Guide("supporters", "Pendukung teratas, target, dan pengumuman", "Alat", listOf(
            "Pendukung teratas" to "Menampilkan tiga pengirim hadiah terbesar.",
            "Target live" to "Atur target like, koin, dan penonton. Saat tercapai, ada ucapan dan efek suara.",
            "Pengumuman otomatis" to "Pesan yang diulang otomatis, misalnya ajakan follow, setiap beberapa menit.",
        )),
        Guide("saweria", "Menghubungkan Saweria", "Alat", listOf(
            "Akun Saweria" to "Buat akun di saweria.co.",
            "Link overlay" to "Di dasbor Saweria buka Overlay, salin link Alert, lalu tempel di Pengaturan, Saweria. Donasi dibacakan dan diberi ucapan terima kasih otomatis.",
            "Kode QR" to "Isi link halaman Saweria-mu, lalu tampilkan kode QR dari menu Alat atau dengan suara: tampilkan kode qr.",
        )),
        Guide("cameras", "Pindah dan tambah kamera", "Alat", listOf(
            "Manual" to "Alat, Kamera: pilih kamera depan, belakang, webcam USB, atau kamera tambahan.",
            "Kamera tambahan" to "Tekan Tambah kamera dari HP lain, bagikan linknya. HP lain itu menjadi kamera tambahan.",
            "Dengan suara" to "Ucapkan pindah ke diikuti nama kamera, misalnya pindah ke kamera belakang.",
        )),
        Guide("voice_commands", "Perintah suara", "Kendali", listOf(
            "Contoh" to "Pertanyaan berikutnya, ulangi pertanyaan, tahan, lewati, tampilkan kode qr, poin berikutnya, kamera depan, matikan mikrofon, jeda layar, undi pemenang, kuis berikutnya, lagu berikutnya.",
            "Ubah kalimat" to "Semua kalimat perintah bisa diubah di Pengaturan, Perintah suara.",
        )),
        Guide("pause", "Jeda layar", "Kendali", listOf(
            "Fungsi" to "Menutup layar siaran sementara dengan gambar atau tulisan Sebentar ya, mikrofon ikut dibisukan.",
            "Cara" to "Tekan tombol Jeda di layar live, atau ucapkan jeda layar. Ucapkan buka layar untuk melanjutkan.",
        )),
        Guide("touch_lock", "Kunci sentuhan", "Kendali", listOf(
            "Fungsi" to "Mengunci layar saat live supaya tidak tersentuh tanpa sengaja.",
            "Membuka" to "Tekan lama tombol Buka kunci selama 2 detik.",
        )),
        Guide("nameplate", "Papan nama", "Tampilan siaran", listOf(
            "Fungsi" to "Nama dan keterangan, misalnya Natan, Host, tampil di siaran.",
            "Posisi" to "Kiri bawah di atas teks berjalan, kiri atas, atau kanan atas. Bisa tampil terus atau hanya di awal live.",
        )),
        Guide("cues", "Getaran dan suara penanda", "Kendali", listOf(
            "Fungsi" to "HP bergetar atau berbunyi saat ada kejadian penting: hadiah besar, tamu masuk, pesan tamu, koneksi putus, baterai lemah.",
            "Atur" to "Setiap kejadian bisa diatur pola getarnya, efek suaranya, dan peringatan suara TTS. Suara penanda hanya terdengar olehmu.",
        )),
        Guide("battery", "Peringatan baterai dan suhu", "Kendali", listOf(
            "Fungsi" to "Memberi tahu saat baterai hampir habis atau HP terlalu panas, supaya live tidak putus tiba-tiba.",
        )),
        Guide("reminders", "Pengingat minum dan live sepi", "Kendali", listOf(
            "Minum" to "Mengingatkan minum dan istirahat setiap beberapa menit.",
            "Live sepi" to "Kalau beberapa menit tidak ada komentar, aplikasi memberi ide untuk mengajak penonton.",
        )),
        Guide("voice_reply", "Balas komentar YouTube pakai suara", "Komentar", listOf(
            "Syarat" to "YouTube sudah dihubungkan.",
            "Cara" to "Ucapkan balas, nama penonton, lalu pesannya. Contoh: balas Budi terima kasih sudah bergabung. Balasan terkirim dari akunmu.",
        )),
        Guide("moderators", "Cara menambah moderator", "Moderator", listOf(
            "Lewat username" to "Menu Akun, Moderator, ketik username orangnya, lalu Tambah. Orang itu harus sudah punya profil di aplikasi.",
            "Lewat link" to "Menu Akun, Moderator, Bagikan link undangan moderator. Orang yang membukanya cukup login lalu tekan Terima.",
            "Hak moderator" to "Moderator bisa memasukkan, membisukan, mengeluarkan, dan membalas tamu, membisukan penonton, serta mengatur antrean pertanyaan.",
            "Panel moderator" to "Saat kamu live, moderator membuka menu Akun, Panel moderator, lalu memilih namamu.",
        )),
        Guide("follow", "Mengikuti kreator dan jadwal live", "Sosial", listOf(
            "Ikuti" to "Menu Akun, Mengikuti, ketik username kreator, lalu Ikuti.",
            "Notifikasi" to "Kamu dapat notifikasi saat kreator yang diikuti mulai live, dan pengingat sebelum jadwal live-nya.",
            "Jadwal live" to "Menu Akun, Jadwal live: umumkan jadwal live-mu. Jadwal tampil di profil dan pengikut diingatkan.",
        )),
        Guide("profile", "Profil dan halaman publik", "Sosial", listOf(
            "Edit profil" to "Menu Akun, ketuk profilmu: ganti foto, nama, username, bio, dan tautan YouTube, Instagram, TikTok, Facebook.",
            "Halaman publik" to "Profilmu punya halaman web stream-by-natan-29448.web.app/@username, dengan status Sedang live saat kamu live.",
            "Bagikan" to "Tekan Bagikan profil untuk mengirim link profilmu ke mana saja.",
        )),
        Guide("share_live", "Membagikan link live", "Live", listOf(
            "Cara" to "Saat live tekan Bagikan. Pilih WhatsApp, Telegram, Facebook, X, Instagram, Line, Messenger, email, atau Salin link.",
        )),
        Guide("recordings", "Rekaman dan potongan otomatis", "Rekaman", listOf(
            "Rekaman" to "Setiap live direkam otomatis di dalam aplikasi, tidak masuk galeri.",
            "Potongan" to "Setelah live, rekaman dipotong otomatis per pertanyaan, per poin Catatan live, dan per tamu (dari tamu masuk sampai keluar).",
            "Simpan" to "Di Rekaman, tekan Simpan ke Download untuk membagikan video.",
        )),
        Guide("quality", "Kualitas video", "Live", listOf(
            "Pilihan" to "Lainnya, Kualitas video: 480p, 720p, 1080p, atau Otomatis yang menurunkan kualitas sendiri saat internet lemah.",
        )),
        Guide("floating", "Jendela mengambang dan berbagi layar", "Live", listOf(
            "Berbagi layar" to "Pilih sumber Layar sebelum live. Tombol mengambang muncul untuk berbagi audio, mikrofon, dan berhenti berbagi.",
        )),
        Guide("account", "Akun: keluar, beralih, hapus", "Akun", listOf(
            "Keluar" to "Menu Akun, Keluar, lalu pilih Ya.",
            "Beralih akun" to "Menu Akun, Beralih akun: pilih akun yang pernah dipakai, atau Tambah akun.",
            "Hapus akun" to "Menu Akun, Hapus akun: profil, data cadangan, dan akunmu dihapus permanen setelah konfirmasi.",
        )),
        Guide("language_display", "Bahasa dan tampilan", "Akun", listOf(
            "Bahasa" to "Menu Akun, Pilihan bahasa. Bahasa Indonesia tetap bawaan.",
            "Tampilan" to "Menu Akun, Tampilan: tema gelap atau terang, huruf besar, dan kontras tinggi.",
        )),
        Guide("restricted", "Izin setelan terbatas", "Akun", listOf(
            "Kapan perlu" to "Kalau aplikasi dipasang dari luar Play Store, Android 13 ke atas bisa membatasi beberapa izin, misalnya akses Jangan Ganggu.",
            "Cara" to "Menu Akun, Izin setelan terbatas. Di Info aplikasi, tekan titik tiga di kanan atas, lalu Izinkan setelan terbatas.",
        )),
        Guide("feedback", "Kirim saran dan masukan", "Akun", listOf(
            "Cara" to "Menu Akun, Kirim saran dan masukan. Pilih jenisnya, tulis, lalu kirim. Pesanmu langsung sampai ke pembuat aplikasi.",
        )),
        Guide("updates", "Pembaruan aplikasi", "Akun", listOf(
            "Otomatis" to "Saat ada versi baru, aplikasi menawarkan pembaruan. Versi baru dipasang menimpa yang lama tanpa menghapus aplikasi.",
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
