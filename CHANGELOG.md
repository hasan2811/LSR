# Changelog

Seluruh perubahan penting pada **HSE LSR Inspector** (`com.lowerzakum.lsr`) didokumentasikan di file ini mengikuti standar [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [2.2.0] - 2026-11-25 — Cursor-Based Infinite Scroll & Multi-Layer Smart Caching

### Added
- **Cursor-Based Pagination (Keyset, bukan `OFFSET`)**:
  - Room L2 menggunakan keyset cursor `(timestamp, id)` dengan batch size `25` item dan indeks komposit pada `inspection_records`, `finding_items`, dan `notifications` (`LsrDao`, `LsrModel`).
  - Cloud Firestore menggunakan `startAfter(lastVisibleSnapshot)` dengan tie-breaker `FieldPath.documentId()` (`FirestoreManager.fetchFindingsPageByCursor`) dan indeks komposit baru di `firestore.indexes.json`.
  - Compose UI (`FindingsScreen`, `FeedScreen`) menambahkan **75% scroll threshold prefetching** berbasis `snapshotFlow`, skeleton loader (*Initial Loading*), spinner bawah (*Append Loading*), banner *Append Error + Retry* tanpa menghapus data yang sudah tampil, serta footer *End-of-List*.
- **Multi-Layer Smart Caching & TTL Eviction (`LsrRepository`, `AppDatabase` v6, `CacheEvictionWorker`)**:
  - **L1 Memory Cache**: Bounded UI window (`MAX_L1_WINDOW_ITEMS = 250`) via `LsrRepository.capL1Window` + `LruCache<Int, FindingItem>(200)`.
  - **L2 Local Single Source of Truth**: `AppDatabase` ditingkatkan ke **v6** dengan migrasi non-destruktif `MIGRATION_5_6` (kolom `remoteDocId`, `updatedAt`, `cachedAt`, plus 10 indeks SQLite).
  - **Stale-While-Revalidate Delta Sync**: `revalidateFindingsDeltaIfStale` menarik dokumen Firestore dengan `updatedAt > MAX(updatedAt)` dan throttle 30 detik.
  - **Smart TTL Eviction (`CacheEvictionWorker`)**: Terjadwal setiap 24 jam via `WorkManager` untuk membersihkan temuan `CLOSED` yang sudah tersinkronisasi > 7 hari (cap 5.000 baris), notifikasi dibaca > 30 hari, dan cache foto lokal > 50 MB sambil melindungi seluruh foto temuan belum tersinkronisasi/belum `CLOSED`.
- **Resilience & Unit Tests (`LsrViewModel`, `ScaleFeaturesUnitTest`)**:
  - Search debounce 300ms dengan pembatalan `Job` aktif saat filter/pencarian/proyek berubah serta guard anti-double-fetch.
  - Penambahan cakupan test hingga **21 unit tests** (`l1WindowCap_retainsBoundedItemsWithoutDuplicates`, `cacheEviction_neverEvictsUnsyncedOrOpenFindings`, serta 6 key lokalisasi `paging_*`).

---

## [2.1.0] - 2026-11-25 — Enterprise Security Hardening, RBAC & GAS v3.2

### Added
- **Segregation of Duties (SoD) & RBAC (`LsrViewModel`, `FindingDetailScreen`, `FindingsScreen`, `firestore.rules`)**:
  - Penambahan `LsrViewModel.canManageOrVerifyFinding(...)` untuk membatasi aksi *Assign/Ganti PIC*, *Verify & Close*, *Reopen*, dan *Delete* hanya untuk peran berwenang (`HSSE Operations Lead`, `Lead HSE Inspector`, `Auditor`, `Supervisor`, `Inspector`, `Officer`, `Admin`) sekaligus melarang PIC yang ditugaskan memverifikasi atau menutup temuannya sendiri.
  - UI `FindingDetailScreen` menyembunyikan tombol hapus/ubah PIC dan menampilkan banner info Segregation of Duties untuk pengguna non-otoritas atau self-PIC.
- **Google Apps Script v3.2 Token Protection & Sheet User Auth (`Code.gs`, `GoogleAppsScriptSnippet.kt`)**:
  - Dukungan opsional `APP_API_TOKEN` melalui `PropertiesService.getScriptProperties()` (`validateRequestToken` pada `doGet` & `doPost`), serta indikator `appTokenProtected` pada respons `ping`.
  - Penambahan tab `user` terpusat + `setupSheets()`, `register_user`, `login_user`, dan hashing SHA-256 server-side.
- **Global Config Sync via Firestore (`system/config`) & Admin Database Lock (`LoginGateScreen`, `SettingsScreen`)**:
  - Konfigurasi URL `/exec` dapat dikunci dengan password admin SHA-256 dan dipublikasikan ke Firestore `system/config` oleh pengguna dengan custom claim `admin == true`.
- **Unit Tests (`ScaleFeaturesUnitTest.kt`)**:
  - Penambahan cakupan test hingga **19 unit tests** (CSV formula injection, paritas rumus skor kepatuhan, RBAC + Segregation of Duties, Admin password hash, 25s Mutation Lock, GAS v3.2 security invariants, filter multi-dimensi, dan paritas lokalisasi).

### Changed
- **Sync & Retry Resilience (`SyncWorker.kt`, `LsrDao.kt`, `LsrViewModel.kt`)**:
  - `SyncWorker.doWork` kini merekonstruksi payload penuh (`answers`, `applicableModules`, counts, dan `findings` via `LsrDao.getFindingsByInspectionRecordId`) saat melakukan retry `submit_inspection` ke GAS.
  - Polling latar belakang GAS (`silentBackgroundSync` & loop foreground) menerapkan *exponential backoff* (`20s -> 40s -> 80s -> 180s`) saat terjadi kegagalan beruntun.
- **Login Flow Security (`LoginGateScreen.kt`)**:
  - Tab `Akun Terdaftar` kini mengisi email ke formulir `Masuk`, mengosongkan input kata sandi, dan menampilkan banner pengingat agar pengguna selalu terautentikasi melalui kata sandi yang valid.

### Fixed
- **Audit Temuan Kepatuhan & Skor (`LsrRepository.kt`)**:
  - Penyelarasan rumus skor kepatuhan melalui `LsrRepository.calculateComplianceScore(yesCount, noCount)` (`YES / (YES + NO)`, `100%` jika 0 item dievaluasi) agar tidak menghitung item `NA` ke dalam pembilang/penyebut.
- **Double-Submit Protection (`LsrViewModel.kt`, `LsrInspectionApp.kt`)**:
  - Penambahan guard `_isSubmitting` pada `submitReport()` serta penonaktifan tombol konfirmasi/batal pada `SubmitConfirmationDialog` selama proses pengiriman berlangsung.
- **Firestore Path & Allowlist Alignment (`LsrRepository.kt`, `SyncWorker.kt`, `firestore.rules`)**:
  - Penghapusan penulisan ke koleksi root `findings` yang tidak memiliki matcher; seluruh operasi inspeksi dan temuan kini konsisten di bawah `projects/{projectId}/inspections` dan `projects/{projectId}/findings`.
  - Penambahan field `actionEvidenceNotes`, `reopenReason`, dan `ruleId` pada `isValidFindingWrite` di `firestore.rules` agar sync tidak ditolak `PERMISSION_DENIED`.
- **Admin Feedback Transparency (`LsrViewModel.kt`)**:
  - Kegagalan publikasi ke Firestore `system/config` (misalnya karena akun belum memiliki custom claim `admin`) kini menampilkan pesan peringatan yang jelas alih-alih tersimpan diam-diam.

### Security
- **Menghapus Celah Login Tanpa Password**: Penghapusan `onQuickLogin` dan `loginWithFirestoreUser` dari `LoginGateScreen` dan `LsrViewModel`.
- **Anti-Brute-Force Admin Unlock**: Penambahan lockout **30 detik** setelah **5 kali** percobaan password salah pada menu *Setup Database Apps Script* (`unlockDatabaseAdmin` & `saveAndTestDatabaseSetupFromLogin`).
- **Proteksi Password User Sheet (`Code.gs`)**: Penghapusan penimpaan kolom `password` pada `upsertUserInSheet` sehingga sinkronisasi profil (`sync_user` / `upsert_user`) tidak pernah menimpa kredensial pengguna yang sudah terdaftar.
- **Firestore Rules Hardening (`firestore.rules`)**:
  - Penambahan `isNotSelfPicClosure()` untuk memblokir penutupan temuan (`status == 'CLOSED'`) oleh PIC yang ditugaskan.
  - Pengetatan penghapusan inspeksi/temuan hanya untuk `isProjectAdminOrLead(projectId)`.
  - Pemisahan `/system/config` (tulis khusus `request.auth.token.admin == true`) dan `/system/ping`.
- **CSV Formula Injection Protection (`ReportExporter.kt`)**: `csvEscape()` kini menambahkan prefix `'` sebelum nilai yang diawali karakter formula (`=`, `+`, `-`, `@`, `\t`, `\r`).
- **Device Backup & Transfer Exclusion (`backup_rules.xml`, `data_extraction_rules.xml`)**: Mengecualikan `lsr_auth_prefs.xml` dan `lsr_prefs.xml` dari Auto Backup dan Device Transfer Android.

---

## [2.0.0] - 2026-10-15 — Dual-Pipeline Cloud, Multi-Filter & Settings Hub

### Added
- **Firebase Production Baseline (`lowerzakum-482af`)**: Autentikasi terverifikasi, `PersistentCacheSettings` 100 MB, upload foto bukti terkompresi (`EvidencePhotoHelper`), dan notifikasi FCM lintas pengguna (`LsrMessagingService`).
- **Google Apps Script 2-Way Connector (`v3.1`)**: `LockService.getScriptLock()`, `SpreadsheetApp.flush()`, dynamic header mapping, `sanitizeSheetText()`, dan **25-second Mutation Lock** di klien.
- **4-Tab Navigation & Settings Hub**: `DASHBOARD`, `FEED`, `FINDINGS`, `SETTINGS` dengan tema `SYSTEM`, `LIGHT`, dan `DARK` obsidian kontras tinggi.
- **Multi-Dimensional Filtering & Export**: `SleekSearchFilterBar` + `ActivityFilterBottomSheet` (Tanggal, Pengguna, Tipe LSR, Perusahaan, Lokasi) serta ekspor PDF A4 & CSV (`ReportExporter`).

---

## [1.0.0] - 2026-08-01 — Initial Offline-First Field Audit Engine

### Added
- **42 Rules IOGP Report 459 & OSHA Audit Engine**: 7 modul LSR dwibahasa (`ID`/`EN`) dengan tombol lapangan ergonomis `56dp`.
- **Room Persistence (`AppDatabase`) & SLA Engine**: Penyimpanan draf otomatis, manajemen siklus temuan (`OPEN` -> `IN_REVIEW` -> `CLOSED` / `REOPENED`), dan SLA otomatis 24 jam (Kritis) / 72 jam (Tinggi).
