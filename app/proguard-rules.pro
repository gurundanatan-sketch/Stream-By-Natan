# Minify dimatikan; aturan ini berjaga-jaga bila nanti diaktifkan.
-keep class org.webrtc.** { *; }
-keep class com.pedro.** { *; }
-keep class com.natan.streambynatan.** { *; }
-dontwarn org.webrtc.**

# Filter GPU RootEncoder dipanggil lewat refleksi
-keep class com.pedro.encoder.input.gl.render.filters.** { *; }
