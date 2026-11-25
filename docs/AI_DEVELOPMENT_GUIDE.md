# AI Development & Vibecoding Guide

Panduan ini ditujukan bagi AI coding agent (Antigravity, Cursor, Claude Code, GitHub Copilot) dan insinyur perangkat lunak untuk mengembangkan, memelihara, dan menambahkan fitur baru pada basis kode **HSE LSR Inspector** dengan kecepatan tinggi (*vibecoding*) tanpa menimbulkan hutang teknis (*technical debt*).

---

## 1. Aturan Emas Ponytail untuk AI Agent

Saat diminta melakukan tugas pemrograman pada repository ini:

1. **Pragmatic Senior Engineer Persona**: Jangan pernah menulis kode yang tidak diminta. Pilihan paling sederhana yang menyelesaikan masalah secara tuntas adalah pilihan terbaik.
2. **Reuse Sebelum Create**:
   - Selalu cek apakah helper, warna tema, atau komponen sudah ada di `com.example.ui.components` atau `com.example.ui.theme`.
   - Gunakan komponen yang sudah ada (`HseBrandLogo`, `HseBrandBadge`, `StandardActionScreen`).
3. **Surgical Diffs**:
   - Jangan menulis ulang seluruh file jika hanya mengubah satu fungsi.
   - Gunakan tool penggantian berbasis baris spesifik (`replace_file_content` atau `multi_replace_file_content`).
4. **Zero Regressions**:
   - Sebelum mengubah tanda tangan fungsi (*signature*), gunakan `find_usages` untuk memverifikasi seluruh pemanggilnya.

---

## 2. Peta Direktori & Navigasi Cepat

| Jalur File | Deskripsi & Tanggung Jawab |
| :--- | :--- |
| `app/src/main/java/com/example/MainActivity.kt` | Entry point aplikasi, `enableEdgeToEdge()`, dan pengikatan `AppThemeMode`. |
| `app/src/main/java/com/example/ui/LsrInspectionApp.kt` | Shell navigasi 4 tab (`Beranda`, `Aktivitas`, `Temuan`, `Pengaturan`) dan `SurveyView` inspeksi. |
| `app/src/main/java/com/example/ui/LsrViewModel.kt` | ViewModel terpusat, filter multi-dimensi, pesan/reminder lintas pengguna, tema & preferensi notifikasi. |
| `app/src/main/java/com/example/ui/components/ActivityFilterSheet.kt` | `SleekSearchFilterBar`, `ActiveFilterSummaryStrip`, dan `ActivityFilterBottomSheet` (Tanggal, User, LSR, Perusahaan, Lokasi). |
| `app/src/main/java/com/example/ui/screens/` | Layar fitur (`LoginGateScreen`, `DashboardScreen`, `FeedScreen`, `FindingsScreen`, `FindingDetailScreen`, `SettingsScreen`, `ProfileScreen`). |
| `app/src/main/java/com/example/data/AppDatabase.kt` | Database Room v5 dengan `MIGRATION_3_4` dan `MIGRATION_4_5` non-destruktif. |
| `app/src/main/java/com/example/data/AppLanguage.kt` | Kamus lokalisasi dwibahasa terpusat (`AppStrings` untuk `ID` & `EN`). |
| `app/src/main/java/com/example/data/LsrChecklist.kt` | Definisi 42 butir aturan Life-Saving Rules (7 modul IOGP/OSHA) dalam dwibahasa (ID & EN). |
| `app/src/main/java/com/example/data/LsrModel.kt` | Model data Room SQLite & filter (`ActivityFilterCriteria`, `AppThemeMode`, `FindingItem`, `AppNotification`). |
| `app/src/main/java/com/example/data/LsrDao.kt` | Kueri database Room lokal untuk CRUD draf, riwayat ringkas, temuan, log aksi, dan notifikasi. |
| `app/src/main/java/com/example/data/FirestoreManager.kt` | Koneksi real-time Firestore (cache 100 MB), listener notifikasi lintas pengguna, FCM token sync, & `action_logs`. |
| `app/src/main/java/com/example/data/LsrMessagingService.kt` | Penerima pesan FCM & `NotificationHelper.kt` (saluran alert & pengingat). |
| `app/src/main/java/com/example/data/EvidencePhotoHelper.kt` | Kompresi foto bukti lapangan, upload Firebase Storage, serta `ReportExporter.kt` (PDF A4 & CSV) & `SyncWorker.kt`. |
| `app/src/main/java/com/example/data/FirebaseAuthManager.kt` | Pengelola autentikasi Firebase (`com.lowerzakum.lsr`) dan resolusi profil personil. |
| `app/src/main/java/com/example/data/LsrRepository.kt` | Pipeline sinkronisasi Room, pesan/pengingat lintas pengguna, Google Apps Script, dan Firebase RTDB. |
| `app/src/main/java/com/example/ui/theme/` | Sistem desain Material 3 (`Color.kt` dengan palet gelap obsidian kontras tinggi, `Theme.kt`, `Type.kt`). |
| `firebase.json` / `firestore.indexes.json` / `firestore.rules` | Konfigurasi Firebase CLI, indeks komposit `notifications` & `findings`, serta prototipe Security Rules. |
| `app/src/test/java/com/example/ScaleFeaturesUnitTest.kt` | Unit test SLA temuan, CSV escape, filter multi-dimensi, `AppThemeMode`, dan paritas lokalisasi. |

---

## 3. Resep Implementasi Fitur Baru (*How-To Recipes*)

### Resep A: Menambahkan Butir Aturan Checklist K3 Baru
1. Buka [`app/src/main/java/com/example/data/LsrChecklist.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/data/LsrChecklist.kt).
2. Tambahkan objek `LsrItem` ke dalam `defaultLsrItems`:
   ```kotlin
   LsrItem(
       id = "lsr_custom_01",
       category = "Kategori Baru (ID)",
       category_en = "New Category (EN)",
       item_text = "Pernyataan aturan keselamatan dalam Bahasa Indonesia",
       item_text_en = "Safety rule statement in English",
       standard_ref = "IOGP Report 459, OSHA 1910",
       is_critical = true // true jika pelanggaran memicu alarm risiko kritis
   )
   ```
3. UI survei dan kalkulasi skor akan otomatis menyertakan butir aturan baru tanpa perlu modifikasi ViewModel.

### Resep B: Menambahkan State Baru ke `LsrViewModel`
1. Di [`LsrViewModel.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/LsrViewModel.kt), deklarasikan `MutableStateFlow` privat dan `StateFlow` publik:
   ```kotlin
   private val _myNewFeatureState = MutableStateFlow<String>("")
   val myNewFeatureState: StateFlow<String> = _myNewFeatureState.asStateFlow()
   ```
2. Buat fungsi pembaruan state:
   ```kotlin
   fun updateMyFeature(newValue: String) {
       _myNewFeatureState.value = newValue
   }
   ```
3. Konsumsi di Composable menggunakan `collectAsState()`:
   ```kotlin
   val featureState by viewModel.myNewFeatureState.collectAsState()
   ```

### Resep C: Menambahkan Layar Baru ke Bottom Navigation
1. Tambahkan entri baru pada enum `AppNavTab` di [`LsrViewModel.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/LsrViewModel.kt).
2. Tambahkan string terjemahan untuk label tab di [`AppLanguage.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/data/AppLanguage.kt) pada `idStrings` dan `enStrings` secara simetris.
3. Tambahkan `NavigationBarItem` pada blok `bottomBar` di [`LsrInspectionApp.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/LsrInspectionApp.kt).
4. Buat file composable baru di `app/src/main/java/com/example/ui/screens/MyNewScreen.kt`.
5. Tambahkan cabang `when (tab)` di [`LsrInspectionApp.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/LsrInspectionApp.kt) untuk menampilkan layar baru tersebut.

### Resep D: Menambahkan Sub-Halaman Baru di Pusat Pengaturan (Settings Hub)
1. Tambahkan entri baru pada enum `SettingsSection` di [`LsrViewModel.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/LsrViewModel.kt).
2. Tambahkan judul & deskripsi menu di [`AppLanguage.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/data/AppLanguage.kt) (`idStrings` & `enStrings`).
3. Tambahkan item `SettingsMenuItem(...)` di `SettingsHubContent` dan cabang baru pada `when (section)` di [`SettingsScreen.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/screens/SettingsScreen.kt) menggunakan `SettingsSubPageScaffold` agar tombol *Back* bekerja otomatis.

---

## 4. Perintah Validasi & Build
Gunakan Gradle task bawaan untuk memastikan stabilitas kode:

- **Kompilasi & Install Debug**:
  ```bash
  ./gradlew :app:installDebug
  ```
- **Jalankan Unit Tests**:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
- **Jalankan Roborazzi Screenshot Tests**:
  ```bash
  ./gradlew recordRoborazziDebug
  ```
- **Inspeksi Antarmuka di Emulator**:
  ```bash
  adb shell am start -n com.lowerzakum.lsr/com.example.MainActivity
  ```
