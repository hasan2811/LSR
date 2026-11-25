# HSE Life-Saving Rules (LSR) Inspector

[![Android 14+](https://img.shields.io/badge/Platform-Android%2024%2B-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-blue.svg)](https://developer.android.com/jetpack/compose)
[![Compliance](https://img.shields.io/badge/Standard-IOGP%20459%20%7C%20OSHA-orange.svg)](https://www.iogp.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)

**HSE LSR Inspector** adalah sistem audit kepatuhan keselamatan kerja lapangan (*Health, Safety & Environment*) skala enterprise yang dibangun untuk industri berisiko tinggi (*Oil & Gas, Petrochemical, Mining, Heavy Construction*). 

Aplikasi ini mendigitalisasi audit lapangan berbasis standar internasional **IOGP Report 459 (9 Life-Saving Rules)** dan **OSHA 1910/1926**, menjamin keandalan *offline-first* di area kerja terpencil, serta menyediakan sinkronisasi cloud real-time ke **Firebase Firestore** dan integrasi lembar kerja korporat via **Google Apps Script**.

---

## 📑 Dokumentasi Resmi Proyek

Untuk mempermudah pemahaman arsitektur, kepatuhan, dan panduan kontribusi bagi pengembang maupun AI agents:

| Dokumen | Deskripsi |
| :--- | :--- |
| 🧠 [**Project Context & Handover Snapshot**](docs/PROJECT_CONTEXT.md) | **Baca pertama kali** saat lanjut *vibecoding*: ringkasan baseline, fitur aktif, gotchas, & prompt sesi baru. |
| 📐 [**Architecture & Business Rules**](docs/ARCHITECTURE_AND_RULES.md) | *Single Source of Truth*: state sistem terkini, skema Firestore/GAS v3.2, matriks RBAC + SoD, & roadmap. |
| 📝 [**Changelog (v2.1.0)**](CHANGELOG.md) | Riwayat rilis terstruktur, perbaikan keamanan, RBAC, dan stabilitas sync. |
| 📘 [**Product Requirements Document (PRD)**](docs/PRD.md) | Visi produk, persona pengguna, cakupan 42 audit rules IOGP/OSHA, alur kerja inspeksi, dan KPI. |
| 🏛️ [**System Architecture & Technical Design**](docs/ARCHITECTURE.md) | Arsitektur data MVI/MVVM, Room DB, Dual-Pipe Sync Engine, skema Firestore, dan visualisasi alur data. |
| 📊 [**Google Apps Script Blueprint (v3.2)**](docs/APPS_SCRIPT_BLUEPRINT.md) | Blueprint integrasi Sheets dua arah: `LockService`, 25s mutation lock, auth tab `user`, `APP_API_TOKEN`, & deployment. |
| 🛡️ [**Permissions, Authority & Security Matrix**](docs/PERMISSIONS_AND_SECURITY.md) | Matriks hak akses (RBAC & Segregation of Duties), aturan Firestore, CSV & backup hardening. |
| 🤖 [**AI Development & Vibecoding Guide**](docs/AI_DEVELOPMENT_GUIDE.md) | Panduan komprehensif bagi AI Agent & Developer, aturan Ponytail YAGNI, indeks file kode, dan resep implementasi. |
| ⚡ [**AI Agents Ruleset**](AGENTS.md) | Aturan operasional global untuk coding assistant (Claude, Antigravity, Cursor). |

---

## 🚀 Fitur Utama

- **Offline-First Resilience & Multi-Layer Smart Cache**: Data audit tersimpan aman di database lokal Room (`AppDatabase` v6, migrasi non-destruktif) dengan *L1 bounded window (`250`) + `LruCache(200)`*, **cursor-based infinite scroll** (batch `25` + 75% prefetching), outbox `WorkManager` (`SyncWorker`, `CacheEvictionWorker` TTL 7 hari/50 MB), dan *100 MB Persistent Cache* Firestore.
- **Dual-Pipeline Cloud Synchronization**:
  1. *Firebase Auth, Cloud Firestore & Storage (`lowerzakum-482af`)*: Autentikasi terverifikasi, multi-tenant workspace proyek (`Lower Zakum Offshore Operations`), sinkronisasi foto bukti lapangan terkompresi, dan live stream tanpa data dummy.
  2. *Google Apps Script Enterprise Connector*: Ekspor data inspeksi otomatis ke Google Sheets & spreadsheet korporat.
- **Cross-User Push Notifications & Direct Communication**: Sinkronisasi token FCM (`LsrMessagingService`), notifikasi tugas PIC real-time, pesan langsung (`COMMENT`), dan tombol **Kirim Pengingat** (`REMINDER_PING`) langsung dari detail temuan.
- **Multi-Dimensional Filtering & Sleek Search**: Pencarian cepat (*clipping-free*) dan filter terpadu berdasarkan **Tanggal**, **Pengguna (Inspektur/PIC)**, **Tipe LSR**, **Perusahaan**, dan **Lokasi** pada tab Aktivitas & Temuan.
- **42 Rules Audit Engine & SLA Tracking**: Penilaian kepatuhan berbasis 7 modul LSR (42 aturan IOGP Report 459 & OSHA) dengan tombol jawaban ergonomis (*56dp*), pelacakan SLA otomatis (24 jam Kritis / 72 jam Tinggi), penugasan PIC, rating auditor (1–5 bintang), serta ekspor laporan **PDF** dan **CSV**.
- **Centralized Navigation, Settings Hub & Dark Theme**: Navigasi 4 tab (`Beranda`, `Aktivitas`, `Temuan`, `Pengaturan`) dengan Pusat Pengaturan lengkap dan pemilih tema (`Sistem`, `Terang`, `Gelap` kontras tinggi).

---

## 🛠️ Persyaratan Sistem & Tech Stack

- **Android Studio**: Ladybug / Meerkat atau versi lebih baru
- **JDK**: Java 17 atau Java 21 (bundled JBR)
- **Android SDK**: Min SDK 24 (Android 7.0), Target SDK 36 (Android 16), Compile SDK 36
- **Gradle**: Gradle 9.6+ dengan AGP 9.5.2
- **UI Framework**: Jetpack Compose dengan Material 3
- **Database Lokal**: Android Room 2.7.0 dengan KSP
- **Cloud Backend**: Firebase Authentication & Cloud Firestore (BoM 2024.09.00)

---

## ⚡ Panduan Instalasi & Menjalankan Proyek

### 1. Clone & Konfigurasi Firebase Lokal
1. Clone repository resmi proyek dari GitHub (`origin`, branch `main`):
   ```bash
   git clone https://github.com/hasan2811/LSR.git
   ```
   *(Pastikan `git remote -v` selalu mengarah ke `https://github.com/hasan2811/LSR.git` saat melakukan `git push origin main`.)*
2. Buka Android Studio, pilih **Open** dan arahkan ke direktori root repository ini.
3. Letakkan file **`google-services.json`** proyek Firebase Anda ke folder `app/google-services.json` (file ini sengaja dikecualikan di `.gitignore` demi keamanan credential; project tetap dapat di-build tanpa file tersebut berkat `googleServices.missing.passthrough=true`).

### 2. Konfigurasi Keystore Debug
Aplikasi menggunakan keystore debug lokal. Jika file `debug.keystore` belum tersedia di direktori root, buat menggunakan JDK keytool:
```bash
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

### 3. Build & Install ke Emulator / Device
Jalankan Gradle task melalui Android Studio Terminal:
```bash
./gradlew :app:installDebug
```

Untuk meluncurkan aplikasi langsung ke perangkat (`package_name: com.lowerzakum.lsr`):
```bash
adb shell am start -n com.lowerzakum.lsr/com.example.MainActivity
```

---

## 🏛️ Struktur Direktori Proyek

```
LSR/
├── .gitignore                      # Proteksi secret (google-services.json, .env, *.jks, local.properties)
├── firebase.json                   # Konfigurasi Firebase CLI (rules & indexes)
├── firestore.rules                 # Prototipe Security Rules Firestore (Validator Pattern)
├── firestore.indexes.json          # Definisi indeks komposit Firestore (notifications & findings)
├── app/
│   ├── build.gradle.kts            # Konfigurasi modul aplikasi (applicationId: com.lowerzakum.lsr)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Izin & deklarasi activity/service/FileProvider
│       │   └── java/com/example/
│       │       ├── MainActivity.kt # Entry point activity & tema (AppThemeMode)
│       │       ├── data/           # Layer Data (Room v6, Firestore, Auth, FCM, Sync, Export)
│       │       │   ├── AppDatabase.kt
│       │       │   ├── AppLanguage.kt
│       │       │   ├── CustomChecklistStore.kt
│       │       │   ├── EvidencePhotoHelper.kt
│       │       │   ├── FirebaseAuthManager.kt
│       │       │   ├── FirestoreManager.kt
│       │       │   ├── FirestoreModels.kt
│       │       │   ├── GoogleAppsScriptSnippet.kt
│       │       │   ├── LsrChecklist.kt
│       │       │   ├── LsrDao.kt
│       │       │   ├── LsrMessagingService.kt
│       │       │   ├── LsrModel.kt
│       │       │   ├── LsrRepository.kt
│       │       │   ├── NotificationHelper.kt
│       │       │   ├── ReportExporter.kt
│       │       │   └── SyncWorker.kt
│       │       └── ui/             # Layer Presentasi (Compose Screens & Components)
│       │           ├── LsrInspectionApp.kt # Shell navigasi 4 tab & Survey View
│       │           ├── LsrViewModel.kt     # State holder MVI/MVVM, Filter & SettingsSection
│       │           ├── components/         # Komponen UI bersama (Logo, Badge, ActivityFilterSheet)
│       │           ├── screens/            # Layar fitur terisolasi
│       │           │   ├── LoginGateScreen.kt
│       │           │   ├── DashboardScreen.kt
│       │           │   ├── FindingsScreen.kt
│       │           │   ├── FindingDetailScreen.kt
│       │           │   ├── FeedScreen.kt
│       │           │   ├── SettingsScreen.kt # Pusat Pengaturan terpadu & pemilih tema
│       │           │   └── ProfileScreen.kt  # Sub-halaman Profil & Akun Inspektur
│       │           └── theme/              # Design System M3 (Color, Theme, Type)
│       └── test/java/com/example/
│           └── ScaleFeaturesUnitTest.kt    # 21 unit tests (SLA, CSV, RBAC/SoD, Filter, L1 cap, TTL safety, GAS v3.2)
├── CHANGELOG.md                    # Riwayat versi & audit keamanan rilis
├── Code.gs                         # Script Google Apps Script v3.2 siap deploy ke Google Sheets
├── docs/                           # Dokumentasi Arsitektur, Rules, PRD, dan Security
├── AGENTS.md                       # Ruleset AI vibecoding & Ponytail YAGNI
└── gradle/                         # Wrapper & Version Catalog (libs.versions.toml)
```

---

## 🔒 Lisensi & Hak Cipta
Hak Cipta © 2026 HSSE Tech Operations. Sesuai standar IOGP 459 & OSHA compliance standards.
