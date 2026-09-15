# Investa

**One View. Every Investment.**

Investa adalah aplikasi Android *offline-first* untuk mencatat dan memantau portofolio investasi pribadi. Seluruh data utama disimpan secara lokal pada perangkat menggunakan Room dan SQLite, sehingga aset, transaksi, cash, serta pengaturan tetap dapat digunakan tanpa koneksi internet.

> Investa adalah alat pencatatan portofolio pribadi, bukan aplikasi untuk memberi nasihat keuangan atau melakukan perdagangan aset.

## Fitur

- Dashboard portofolio: total nilai, total modal, profit/loss, alokasi aset, dan top assets.
- Manajemen aset untuk kategori Crypto, ID Stocks, US Stocks, Bonds, Mutual Fund, dan Gold.
- Tambah, ubah, dan hapus aset.
- Detail aset dengan nilai saat ini, modal, average price, unrealized P/L, dan riwayat transaksi.
- Transaksi BUY dan SELL yang memperbarui posisi aset serta saldo cash secara atomik.
- Cash account multi-mata uang, saat ini mendukung IDR dan USD.
- Konversi USD/IDR menggunakan nilai tukar yang tersimpan lokal.
- Laporan portofolio: performance chart, ringkasan per kategori/per aset, serta realized gain/loss.
- Riwayat transaksi dengan pemuatan bertahap agar daftar panjang tetap responsif.
- Pembaruan harga pasar dan kurs USD/IDR opsional melalui Yahoo Finance. Saat koneksi tidak tersedia, aplikasi tetap memakai data terakhir yang tersimpan di perangkat.
- Pengaturan tema, bahasa, mata uang utama, format angka, dan exchange rate.

## Teknologi

| Area | Teknologi |
| --- | --- |
| Bahasa | Kotlin |
| UI | Android Views dan XML Layout |
| Penyimpanan lokal | Room Database / SQLite |
| State | ViewModel, StateFlow, dan Kotlin Coroutines |
| List & paging UI | RecyclerView dan ViewPager2 |
| Chart | MPAndroidChart |
| Komponen | Material Components |
| Ikon | Lucide - Android Vector Drawables |

## Arsitektur singkat

```text
app/src/main/java/com/example/investa/
├── data/
│   ├── dao/           # Query Room
│   ├── entity/        # Entity database
│   ├── repository/    # Akses dan transaksi data
│   └── InvestaDatabase.kt
├── model/             # Model aplikasi
├── navigation/        # Screen dan navigation coordinator
├── ui/                # Renderer/handler setiap halaman
├── utils/             # Formatter, kalkulator, currency, API helper
├── viewmodel/         # State dan aksi UI
├── MainActivity.kt
└── SplashActivity.kt
```

Database lokal mencakup tabel berikut:

- `assets`
- `transactions`
- `currencies`
- `cash_accounts`
- `app_preferences`

## Perhitungan utama

- **Current value**: quantity × current price.
- **Unrealized P/L**: current value − invested amount.
- **Realized P/L**: untuk transaksi SELL, net proceeds (`total`) − `costBasis`.
- **Average cost**: average price disimpan pada aset yang masih dimiliki; cost basis SELL disimpan permanen pada transaksi agar nilai historis tidak berubah.
- **Cash BUY/SELL**: BUY mengurangi cash sebesar total termasuk fee; SELL menambah cash sebesar total/net proceeds setelah fee.
- **Konversi USD**: `USD amount × USD.exchangeRate`, dengan exchange rate didefinisikan sebagai jumlah IDR untuk 1 USD.

## Menjalankan proyek

### Prasyarat

- Android Studio terbaru.
- Android SDK dengan `compileSdk 36`.
- Perangkat atau emulator Android API 24 atau lebih baru.
- JDK yang sesuai dengan konfigurasi Android Studio/Gradle proyek.

### Langkah

1. Clone atau unduh repository ini.
2. Buka folder proyek melalui Android Studio.
3. Pastikan Android SDK yang dibutuhkan tersedia, lalu lakukan **Sync Project with Gradle Files**.
4. Pilih perangkat atau emulator.
5. Jalankan konfigurasi `app`.

Alternatif melalui terminal:

```bash
./gradlew assembleDebug
```

APK debug akan tersedia di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Catatan koneksi internet

Aplikasi tidak membutuhkan backend, Firebase, maupun akun pengguna. Koneksi internet hanya digunakan saat pengguna secara manual meminta pembaruan harga market atau kurs USD/IDR dari Yahoo Finance. Data lokal di Room tetap menjadi *source of truth* dan aplikasi dapat dipakai tanpa internet.

## Lisensi

Belum ada lisensi yang ditetapkan untuk proyek ini.
