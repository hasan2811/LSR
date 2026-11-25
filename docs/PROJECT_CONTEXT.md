# Project Context & Vibecoding Handover Snapshot

Dokumen ini adalah **ringkasan konteks hidup (*living snapshot*)** agar setiap sesi *vibecoding* baru (Antigravity, Cursor, Claude Code, Copilot, Gemini CLI) langsung memahami status terkini aplikasi **HSE LSR Inspector** tanpa harus menelusuri seluruh codebase dari awal.

---

## 1. Snapshot Baseline Saat Ini

| Komponen | Status Terkini |
| :--- | :--- |
| **App ID & Firebase Project** | `com.lowerzakum.lsr` -> Firebase Project `lowerzakum-482af` |
| **GitHub Repository (`origin`)** | `https://github.com/hasan2811/LSR.git` (branch utama: `main` — selalu push ke remote ini) |
| **Database Lokal** | Room `2.7.0` (`AppDatabase` **v6**, migrasi non-destruktif `MIGRATION_3_4`, `MIGRATION_4_5`, `MIGRATION_5_6`) |
| **UI & Theme** | Jetpack Compose M3 + `AppThemeMode` (`SYSTEM`, `LIGHT`, `DARK` obsidian kontras tinggi) |
| **Navigasi Utama** | 4 tab (`DASHBOARD`, `FEED`, `FINDINGS`, `SETTINGS`) di `LsrInspectionApp.kt` |
| **Testing** | `ScaleFeaturesUnitTest.kt` (**21 unit tests**: SLA, CSV anti-injection, RBAC/SoD, score parity, 25s lock, GAS v3.2, Filter, Theme, L1 cap, TTL safety, paritas ID/EN) |
| **Credential & Backup Safety** | `.gitignore` memblokir `google-services.json`, `.env`, keystores, `local.properties`; `MainActivity.kt` bebas hardcoded key; `backup_rules.xml` & `data_extraction_rules.xml` mengecualikan `lsr_auth_prefs.xml` & `lsr_prefs.xml` |

---

## 2. Fitur yang Sudah Selesai & Terintegrasi

1. **Inspeksi 42 Rules IOGP 459 / OSHA**:
   - Auto-save draf ke Room, tombol lapangan ergonomis (`56dp`, single-line), opsi *haptic feedback*, dan skor kepatuhan real-time.
2. **Temuan, PIC, Foto Bukti & SLA Tracking**:
   - Siklus hidup `OPEN` -> `IN_REVIEW` -> `CLOSED` / `REOPENED` + rating auditor 1–5 bintang + *Audit Trail Timeline*.
   - Indikator SLA otomatis: **24 jam** untuk risiko Kritis, **72 jam** untuk risiko Tinggi.
   - Lampiran foto bukti (`EvidencePhotoHelper` kompresi max 1280px + upload Firebase Storage).
3. **Push Notification & Komunikasi Langsung Lintas Pengguna**:
   - Token FCM perangkat disinkronkan ke `users/{uid}` (`FirestoreManager.syncFcmToken`).
   - Saluran `projects/{id}/notifications` (`startScopedNotificationsListener`) dengan deduplikasi `remoteId` di Room (`LsrRepository.ingestRemoteNotifications`).
   - Kartu **Komunikasi & Pengingat Langsung** di `FindingDetailScreen.kt` untuk kirim pesan (`COMMENT`) dan tombol **Kirim Pengingat** (`REMINDER_PING`).
4. **Pencarian & Multi-Filter Modern (`ActivityFilterSheet.kt`)**:
   - `SleekSearchFilterBar` menggunakan `BasicTextField` (bebas *text clipping*) + tombol Filter obsidian dengan badge jumlah filter aktif.
   - `ActivityFilterBottomSheet` menyaring berdasarkan **Tanggal**, **Pengguna (Inspektur/PIC)**, **Tipe LSR**, **Perusahaan**, dan **Lokasi** pada tab `FeedScreen` & `FindingsScreen`.
5. **Skala, Cursor Pagination & Multi-Layer Smart Caching**:
   - **Cursor-Based Infinite Scroll**: Keyset `(timestamp, id)` di Room L2 + `startAfter` & `FieldPath.documentId()` di Firestore dengan batch `25` item dan **75% scroll prefetching** (`FindingsScreen`, `FeedScreen`).
   - **L1 + L2 Cache & TTL Eviction**: Bounded window `250` item + `LruCache(200)` di L1, Room v6 + `PersistentCacheSettings` **100 MB** di L2, Stale-While-Revalidate delta sync (throttle 30 detik), serta `CacheEvictionWorker` 24 jam (TTL 7 hari temuan `CLOSED` tersinkronisasi, 30 hari notifikasi dibaca, cap foto 50 MB).
   - `WorkManager` (`SyncWorker`, `DraftReminderWorker`, `CacheEvictionWorker`) dan ekspor **PDF A4** serta **CSV** (`ReportExporter`).
6. **Pusat Pengaturan & Tema (`SettingsScreen.kt`)**:
   - Sub-halaman terstruktur: Profil & Akun, Modul LSR, Cloud & Workspace, Bahasa (`ID`/`EN`), Tema (`Sistem`/`Terang`/`Gelap`), Notifikasi & Pengingat, Bantuan, dan Tentang Aplikasi.
7. **Google Apps Script Blueprint v3.2 ([`docs/APPS_SCRIPT_BLUEPRINT.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/APPS_SCRIPT_BLUEPRINT.md))**:
   - Sinkronisasi 2 arah (`doGet` + `doPost`) dengan `LockService.getScriptLock()`, `SpreadsheetApp.flush()`, *dynamic header mapping*, `sanitizeSheetText()`, anti-cache `?t=`, **25-second Mutation Lock** di `LsrRepository`, tab `user` terpusat + `setupSheets()`, proteksi opsional `APP_API_TOKEN`, dan silent foreground polling 20 detik dengan *exponential backoff* (`20s -> 40s -> 80s -> 180s`).
8. **Hardening Keamanan, RBAC & Segregation of Duties ([`docs/ARCHITECTURE_AND_RULES.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/ARCHITECTURE_AND_RULES.md), [`CHANGELOG.md`](file:///Users/rullyberiandi/StudioProjects/LSR/CHANGELOG.md))**:
   - `LoginGateScreen` wajib autentikasi kata sandi (tanpa bypass klik akun); menu Admin Database Setup dilindungi lockout **5x salah -> 30 detik**.
   - Penegakan RBAC & *Segregation of Duties* di `LsrViewModel`, `FindingDetailScreen`, dan `firestore.rules` (PIC tidak dapat menutup temuannya sendiri).
   - Perlindungan CSV formula injection (`ReportExporter.csvEscape`), anti-double-submit (`_isSubmitting`), dan retry `SyncWorker` dengan payload `findings` lengkap.

---

## 3. Aturan Penting & *Gotchas* Teknis (Jangan Dilanggar)

1. **Jangan Pakai `OutlinedTextField` dengan Tinggi Tetap (`50.dp`)**:
   - Material 3 memotong teks jika `OutlinedTextField` dipaksa tinggi sempit. Gunakan [`SleekSearchFilterBar`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/components/ActivityFilterSheet.kt) (`BasicTextField` + `Surface`).
2. **Keamanan Query Firestore Sebelum Index Deploy**:
   - `startScopedNotificationsListener` sengaja memakai `.orderBy("timestamp", DESCENDING)` (indeks tunggal bawaan) lalu memfilter penerima di klien agar aplikasi tidak crash `FAILED_PRECONDITION` bila indeks komposit di `firestore.indexes.json` belum di-deploy.
3. **Zero Hardcoded Credentials**:
   - Jangan pernah menulis API key atau `FirebaseOptions` hardcoded di `MainActivity.kt`. Plugin `google-services` membaca `app/google-services.json` lokal yang di-ignore `.gitignore`.
4. **Database Room Migrations**:
   - Jika menambah kolom/tabel di `LsrModel.kt`, **wajib** naikkan `version` di `AppDatabase.kt` dan tulis `MIGRATION_X_Y` non-destruktif (dilarang `fallbackToDestructiveMigration` agar data inspeksi lapangan tidak hilang).
5. **Paritas Bahasa ID & EN**:
   - Setiap key UI baru wajib ditambahkan simetris di `idStrings` dan `enStrings` pada `AppLanguage.kt`, dan diuji di `ScaleFeaturesUnitTest.kt`.

---

## 4. Tindakan Operasional Cloud yang Masih Opsional / Menunggu

- **Deploy Indeks & Rules Firestore ke `lowerzakum-482af`**:
  Saat siap mengaktifkan indeks komposit & rules di server Firebase:
  ```bash
  npx -y firebase-tools@latest login
  npx -y firebase-tools@latest deploy --only firestore:indexes,firestore:rules --project lowerzakum-482af
  ```
- **Custom Claim `admin: true` untuk `system/config`**:
  Penulisan dokumen global `system/config` di Firestore memerlukan custom claim `admin == true` pada token Firebase user yang melakukan publikasi URL GAS.
- **Tambahkan `storage.rules` & Review PII `users`**:
  Siapkan aturan `storage.rules` untuk membatasi upload `evidence/**` dan tinjau isolasi PII koleksi `users` sebelum rilis publik skala luas.

---

## 5. Template Prompt untuk Melanjutkan Vibecoding di Sesi Baru

Kapan pun Anda membuka chat baru untuk lanjut mengembangkan fitur, cukup salin dan tempel kalimat pembuka ini bersama permintaan baru Anda:

> *"Baca `AGENTS.md`, `docs/PROJECT_CONTEXT.md`, dan `docs/ARCHITECTURE_AND_RULES.md` terlebih dahulu untuk memahami arsitektur, baseline Room v6, Firebase `lowerzakum-482af`, RBAC/SoD, cursor pagination, dan aturan Ponytail Mode. Setelah itu bantu saya kerjakan: [TULIS FITUR / PERBAIKAN BARU DI SINI]."*
