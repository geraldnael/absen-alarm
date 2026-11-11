# Alarm Absen (Android)

Alarm lokal untuk absen harian. Nada dipilih dari file di HP atau nada sistem, berbunyi sebagai alarm sungguhan,
dan notifikasinya punya tombol **Sudah absen**, **Tunda 10 mnt**, serta ketuk untuk membuka Monev MagangHub.

## Build APK lewat GitHub Actions
1. Buat repo baru di GitHub (misalnya `absen-alarm`), lalu push isi folder ini (workflow ada di `.github/workflows/build-apk.yml`).
2. Buka tab **Actions**, pilih run "Build APK" yang terbaru, tunggu hijau (sekitar 3-6 menit).
3. Di bagian **Artifacts**, unduh `absen-alarm-apk`, ekstrak, lalu pasang `app-debug.apk` di HP (izinkan "Pasang dari sumber ini").

## Pengaturan di Infinix (XOS)
- Izinkan notifikasi saat diminta pertama kali.
- Pengaturan > Aplikasi > Alarm Absen > Baterai > **Tanpa batasan**, dan aktifkan **Mulai otomatis** (autostart).
- Pengaturan > Dynamic Bar: cek apakah Alarm Absen bisa diaktifkan di sana.
