# Launcher Kebasen (Android TV)
1. Buat repo GitHub, upload isi folder ini ke branch `main`. (Opsional: Settings > Secrets > Actions > `TMDB_KEY` dari themoviedb.org untuk rekomendasi.)
2. Tab **Actions** > build selesai > unduh artifact `TvLauncher` (APK).
3. Pasang ke TV: ADB wireless dari HP, atau Downloader. Launcher lama tetap aktif. Pilih/ganti lewat **Pintasan > Pilih Launcher Utama**.
4. Update: buat tag `v1.0.1` > Actions mengunggah APK ke **Releases** > di TV buka **Pintasan > Cek Update**.

Ubah lokasi: `LAT/LON` di `Data.kt`. Ganti wallpaper: `app/src/main/res/drawable-nodpi/wp_*.jpg`.
`app/launcher.p12` ikut di repo supaya update bisa menimpa versi lama; ganti dengan keystore milikmu jika repo publik.
