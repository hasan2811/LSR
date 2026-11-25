# Permissions, Authority & Security Matrix

## Overview
Aplikasi **HSE LSR Inspector** beroperasi pada lingkungan industri dengan regulasi ketat (*Oil & Gas, Petrochemical, Mining*). Dokumen ini menguraikan model otoritas berbasis peran (**Role-Based Access Control / RBAC**), aturan keamanan Cloud Firestore, perizinan runtime Android, dan integritas data audit.

---

## 1. Role-Based Access Control (RBAC) & Authority Matrix

Setiap pengguna yang login dipetakan ke profil kerja terverifikasi. Otoritas tindakan audit diatur berdasarkan matriks berikut:

| Peran (Role) | Deskripsi Personil | Cakupan Izin & Otoritas |
| :--- | :--- | :--- |
| **HSSE Operations Lead** | Pimpinan operasi HSE proyek | **Full Authority**: Mengelola semua proyek workspace (`lowerzakum-482af`), membuat/menutup temuan, verifikasi & rating akhir (*Sign-off*), dan mengonfigurasi konektor cloud di `Settings -> Cloud & Workspace`. |
| **Lead HSE Inspector** | Auditor keselamatan utama | **Audit Lead**: Menjalankan inspeksi penuh 42 rules, mencatat temuan deviasi K3, menugaskan PIC, menyimpan draf, dan mengirimkan laporan. |
| **Rig / Field Supervisor** | Pengawas mekanikal, elektrikal, & operasi lapangan | **Field Supervisor**: Mengakses audit di areanya, melihat temuan terbuka (*Open Findings*), menambahkan catatan tindakan perbaikan, dan mengajukan penutupan temuan (*IN_REVIEW*). |
| **Specialist Inspector** | Inspektur spesialis (*Working at Height, Confined Space, Lifting*) | **Category Specialist**: Memilih modul LSR spesifik sesuai aktivitas kerja dan menandai kepatuhan sub-kategori. |
| **Contractor Field PIC** | Penanggung jawab perbaikan dari kontraktor | **Remediation PIC**: Melihat temuan yang ditugaskan kepadanya, mengunggah catatan bukti tindakan korektif untuk direview auditor. **Dilarang** memverifikasi/menutup, membuka kembali, mengganti PIC, atau menghapus temuan. |

### Segregation of Duties (SoD) — Penegakan Ganda (UI + Backend)
- **Client Enforcement ([`LsrViewModel.canManageOrVerifyFinding`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/LsrViewModel.kt))**: Aksi *Assign/Ganti PIC*, *Verify & Close*, *Reopen*, dan *Delete Finding* hanya diizinkan untuk peran berwenang (`HSSE Operations Lead`, `Lead HSE Inspector`, `Auditor`, `Supervisor`, `Inspector`, `Officer`, `Admin`) **dan** ditolak bila email pengguna sama dengan `assignedPicEmail` pada temuan terkait. [`FindingDetailScreen.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/screens/FindingDetailScreen.kt) otomatis menyembunyikan tombol sensitif dan menampilkan banner info SoD.
- **Backend Enforcement ([`firestore.rules`](file:///Users/rullyberiandi/StudioProjects/LSR/firestore.rules))**: Fungsi `isNotSelfPicClosure()` memblokir perubahan `status == 'CLOSED'` oleh UID atau email yang sama dengan PIC yang ditugaskan, sedangkan penghapusan (`delete`) inspeksi maupun temuan dikunci ketat oleh `isProjectAdminOrLead(projectId)`.

---

## 2. Aturan Keamanan Cloud Firestore (*Security Rules*)

Prototipe aturan keamanan lengkap dikelola di [`firestore.rules`](file:///Users/rullyberiandi/StudioProjects/LSR/firestore.rules) menggunakan **Validator Function Pattern** (validasi tipe data, allowlist field schema, batas panjang string, serta proteksi keanggotaan proyek dan field immutable `ownerUid`/`createdAt`):

- **`users/{userId}`**: Baca untuk pengguna terautentikasi (dibutuhkan untuk resolusi daftar tim & pemilihan PIC), tulis hanya oleh pemilik `request.auth.uid == userId` melalui `isValidUserWrite()`. *(Catatan: tinjau isolasi PII sebelum rilis publik skala luas).*
- **`projects/{projectId}`**: Baca/buat untuk pengguna terautentikasi; update/hapus dibatasi untuk `ownerUid` atau `memberUids`.
- **`projects/{projectId}/inspections/{inspectionId}`**: Baca/tulis untuk anggota proyek (`isProjectMember`) dengan validasi skema `isValidInspectionWrite()`; hapus hanya untuk `isProjectAdminOrLead(projectId)`.
- **`projects/{projectId}/findings/{findingId}`**: Akses dibatasi untuk anggota proyek (`isProjectMember`) dengan validasi skema `isValidFindingWrite()` (termasuk `actionEvidenceNotes`, `reopenReason`, `ruleId`), penegakan `isNotSelfPicClosure()` saat penutupan, dan hapus khusus `isProjectAdminOrLead(projectId)`.
- **`projects/{projectId}/action_logs/{logId}`**: Bersifat *append-only* (`allow update, delete: if false`) untuk menjaga keutuhan jejak audit hukum.
- **`projects/{projectId}/notifications/{notificationId}`**: Pengiriman tugas, chat, dan pengingat lintas pengguna dalam proyek yang sama (`isValidNotificationWrite()`).
- **`system/config` & `system/ping`**: `system/config` dapat dibaca publik untuk bootstrap URL GAS di layar login, namun penulisan dikunci khusus token dengan custom claim **`request.auth.token.admin == true`**. `system/ping` hanya untuk pengguna terautentikasi (`ping`, `user`).

---

## 3. Izin Runtime Android (*Android Manifest & Runtime Permissions*)

Aplikasi meminta izin seminimal mungkin (*Least Privilege Principle*):

| Izin Android | Tujuan & Penggunaan | Waktu Permintaan |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Menghubungi Firebase Auth, Firestore, FCM, Storage, dan Google Apps Script. | Diberikan otomatis saat instalasi. |
| `android.permission.ACCESS_NETWORK_STATE` | Mendeteksi ketersediaan koneksi internet untuk `WorkManager` outbox sync. | Diberikan otomatis saat instalasi. |
| `android.permission.POST_NOTIFICATIONS` *(Android 13+)* | Menampilkan push notification FCM tugas PIC, chat langsung, dan pengingat. | Runtime prompt saat pengguna login. |
| `android.permission.CAMERA` *(Opsional)* | Mengambil foto bukti deviasi K3 langsung di lapangan via `FileProvider`. | Runtime prompt saat membuka fitur kamera. |
| `android.permission.READ_MEDIA_IMAGES` *(Opsional)* | Mengunggah foto bukti dari galeri perangkat (Android 13+). | Runtime prompt saat memilih file lampiran. |

---

## 4. Keamanan Keystore & Tanda Tangan Digital (*Signing Config*)

Konfigurasi build Android memisahkan secara ketat lingkungan pengembangan lokal dan rilis produksi:

1. **Lingkungan Debug Lokal (`debugConfig`)**:
   - Menggunakan `debug.keystore` lokal yang di-ignore oleh `.gitignore`.
2. **Lingkungan Rilis Produksi (`release`)**:
   - Mengambil kredensial signing dari Environment Variables sistem (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).
   - Sertifikat produksi (`*.jks`, `*.keystore`, `*.p12`) dan `.env` selalu diblokir di `.gitignore`.

---

## 5. Kebersihan Repository, Backup Exclusion & Proteksi Kredensial

- [`MainActivity.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/MainActivity.kt) **tidak menyimpan hardcoded API key**; inisialisasi Firebase ditangani otomatis oleh `com.google.gms.google-services` dari `app/google-services.json` lokal.
- [`.gitignore`](file:///Users/rullyberiandi/StudioProjects/LSR/.gitignore) memblokir seluruh `google-services.json`, `*-firebase-adminsdk-*.json`, `.env`, `local.properties`, dan file keystore agar push GitHub tetap bersih.
- **Zero-Passwordless Login & Anti-Brute-Force**: [`LoginGateScreen.kt`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/ui/screens/LoginGateScreen.kt) mewajibkan verifikasi kata sandi pada setiap login (pemilihan akun dari tab `Akun Terdaftar` hanya mengisi kolom email). Pembukaan kunci *Setup Database Apps Script* dilindungi verifikasi SHA-256 dan lockout **30 detik** setelah **5 kali** kegagalan berturut-turut.
- **Proteksi Backup & Transfer Perangkat**: [`backup_rules.xml`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/res/xml/backup_rules.xml) dan [`data_extraction_rules.xml`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/res/xml/data_extraction_rules.xml) mengecualikan `lsr_auth_prefs.xml` dan `lsr_prefs.xml` dari Auto Backup dan Cloud Device Transfer.
- **Perlindungan Ekspor CSV & Google Sheets**: [`ReportExporter.csvEscape`](file:///Users/rullyberiandi/StudioProjects/LSR/app/src/main/java/com/example/data/ReportExporter.kt) dan `sanitizeSheetText()` pada `Code.gs` menetralkan karakter awal formula (`=`, `+`, `-`, `@`, `\t`, `\r`) dengan prefix `'`.
- **Token Opsional Google Apps Script (`APP_API_TOKEN`)**: Jika `APP_API_TOKEN` diset pada Script Properties Google Apps Script, `doGet` dan `doPost` wajib menyertakan token yang cocok (`?token=...` atau `data.appToken`).

---

## 6. Integritas Data & Anti-Tamper Audit Trail

Untuk memenuhi standar pembuktian hukum dan audit investigasi insiden (*Incident Investigation*):
- **Timestamp ISO-8601 Permanen**: Setiap sesi audit dan temuan dicatat dengan waktu epoch milidetik lokal serta waktu server cloud.
- **Identitas Inspektur Terikat UID**: Temuan deviasi K3 menyematkan UID autentikasi, nama, dan alamat email inspektur penemu secara permanen.
- **ACID Transactions, Safe Cache Eviction & Anti-Double-Submit**: Perubahan state draf aktif diisolasi dalam transaksi Room v6 lokal (`MIGRATION_4_5`, `MIGRATION_5_6`), [`CacheEvictionWorker`](class://com.example.data.CacheEvictionWorker) hanya memangkas temuan berstatus `CLOSED` yang sudah tersinkronisasi (`isSafeToEvictFinding`) serta melindungi foto bukti aktif/belum sinkron, dan `submitReport()` dilindungi guard `_isSubmitting` untuk mencegah pengiriman ganda.
