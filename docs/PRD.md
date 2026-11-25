# Product Requirements Document (PRD)

## Project Title: HSE Life-Saving Rules (LSR) Inspector
**Classification**: Enterprise Safety Management & Field Audit System  
**Baseline Version**: 2.0 (Centralized Navigation, Settings Hub & Live Firebase `lowerzakum-482af`)  
**Standard Compliance**: IOGP Report 459 / OSHA 1910 & 1926 Standards  
**Application ID**: `com.lowerzakum.lsr`  
**Maintainer**: Lower Zakum HSSE Operations Technology

---

## 1. Executive Summary & Problem Statement

### 1.1 Background
Dalam industri berisiko tinggi seperti minyak & gas (*upstream/downstream*), pertambangan, dan konstruksi berat, pelanggaran terhadap aturan keselamatan dasar adalah penyebab utama 90% insiden fatalitas dan cacat tetap (*Lost Time Injury* / LTI). International Association of Oil & Gas Producers (IOGP) merumuskan **9 Life-Saving Rules (LSR)** untuk memitigasi risiko tersebut.

### 1.2 Core Problem
1. **Audit Kertas Lambat & Rawan Hilang**: Inspeksi berbasis formulir fisik memakan waktu berhari-hari untuk direkap dan seringkali data temuan kritis terlambat dieskalasi ke manajemen rig/fasilitas.
2. **Ketiadaan Konektivitas Lapangan**: Lokasi operasi (*offshore platform, tank farm, remote wellhead*) minim sinyal internet, menyebabkan aplikasi berbasis web murni gagal bekerja.
3. **Ketiadaan Akuntabilitas PIC**: Temuan K3 (*non-conformance*) seringkali tidak memiliki batas waktu remediasi dan penanggung jawab (*Person in Charge*) yang jelas.

### 1.3 Solution
**HSE LSR Inspector** adalah aplikasi Android *offline-first* dengan sinkronisasi dual-cloud yang memandu personil K3 melakukan audit kepatuhan 42 poin standar IOGP/OSHA secara cepat, mencatat deviasi lapangan secara instan beserta bukti foto, serta mengkalkulasi indeks keselamatan fasilitas secara real-time.

---

## 2. User Personas & Target Audience

| Persona | Peran | Kebutuhan Utama |
| :--- | :--- | :--- |
| **Lead HSE Inspector** | Pemimpin audit keselamatan fasilitas | Memerlukan alur survei cepat, tombol jawaban lapangan ergonomis, pencatatan deviasi, penugasan PIC, dan laporan otomatis. |
| **Rig / Field Supervisor** | Pengawas mekanikal/listrik rig | Memerlukan transparansi daftar temuan di areanya, notifikasi risiko kritis, dan kemampuan memperbarui bukti penutupan temuan (*Close Finding*). |
| **Safety Officer / Assessor** | Auditor harian | Melakukan survei berkala, memilih proyek aktif (`Lower Zakum Offshore Operations`), dan menyimpan draf lokal saat koneksi terputus. |
| **Corporate HSSE Management** | Manajemen eksekutif | Memerlukan ringkasan kepatuhan (Compliance Score Index), statistik risiko terbuka, dan ekspor spreadsheet terpadu via Google Apps Script. |

---

## 3. Product Scope & Functional Requirements

### Module 1: Enterprise Gateway & Identity (`LoginGateScreen`)
- **FR-1.1**: Autentikasi Kredensial nyata via Firebase Authentication (`lowerzakum-482af` Email/Password) yang tersinkronisasi otomatis ke koleksi Firestore `/users`.
- **FR-1.2**: Daftar Akun Terdaftar real-time dari Cloud Firestore (tanpa data dummy bawaan; menampilkan status kosong saat belum ada akun terdaftar).
- **FR-1.3**: Pendaftaran akun personil baru dengan atribut: Nama Lengkap, Email, Perusahaan, Jabatan, dan Sertifikasi K3 (NEBOSH/AK3/OSHA).
- **FR-1.4**: Reset password instan via Firebase Auth email.
- **FR-1.5**: Pengalih bahasa pra-login (ID 🇮🇩 / EN 🇬🇧) di gateway autentikasi.

### Module 2: Beranda & Workspace Management (`DashboardScreen`)
- **FR-2.1**: Header ringkas bebas tumpang-tindih (Logo, Judul, dan satu *Account Pill* yang menavigasi langsung ke `Pengaturan -> Profil & Akun` atau dialog autentikasi).
- **FR-2.2**: Workspace Proyek Firestore aktif (`Lower Zakum Offshore Operations` dibuat otomatis jika koleksi `projects` masih kosong) beserta *Project Selection Dialog*.
- **FR-2.3**: 4 Kartu KPI Utama: Total Audit, Rata-rata Skor Kepatuhan, Temuan Terbuka (*Open Findings*), dan Risiko Kritis (*Critical Hazards*).
- **FR-2.4**: Form metadata inspeksi: Nama Inspektur dan Area/Lokasi Kerja.
- **FR-2.5**: Pemilih Cakupan Modul LSR (*Applicable LSR Scope*): Filter audit berdasarkan modul spesifik atau pilih semua (7 modul / 42 Rules).
- **FR-2.6**: Deteksi Draf Aktif: Menyediakan opsi "Lanjutkan Inspeksi Aktif" atau "Reset Draft".

### Module 3: Survey & Audit Engine (`SurveyView`)
- **FR-3.1**: Navigasi berurutan pertanyaan checklist dengan *Progress Bar* dan *Quick-Jumper Matrix Strip*.
- **FR-3.2**: Tombol jawaban tri-state ergonomis (`56dp`, *single-line non-wrapping*): **YA (Patuh)**, **TIDAK (Temuan)**, dan **N/A** dengan dukungan *haptic feedback* opsional.
- **FR-3.3**: Real-time Compliance Score Calculation: Skor kepatuhan dihitung dinamis `(Total YES / (Total YES + Total NO)) * 100%`.
- **FR-3.4**: Catatan Deviasi, Quick Observation Tags & Penugasan PIC dari daftar anggota proyek.
- **FR-3.5**: Konfirmasi Selesai & Ringkasan Hasil sebelum penyimpanan permanen ke Room DB & Cloud.

### Module 4: Non-Conformance & Findings Engine (`FindingsScreen` & `FindingDetailScreen`)
- **FR-4.1**: Pencatatan otomatis setiap jawaban "NO" menjadi item temuan K3.
- **FR-4.2**: Klasifikasi aturan kritis (*Critical Hazard*), indikator **SLA otomatis** (24 jam Kritis / 72 jam Tinggi), serta pencarian & multi-filter (`Date`, `User`, `Type LSR`, `Company`, `Location`).
- **FR-4.3**: Siklus Hidup Status Temuan: `OPEN` -> `ACTION_SUBMITTED / IN_REVIEW` -> `CLOSED` / `REOPENED`.
- **FR-4.4**: Penugasan PIC, lampiran foto bukti lapangan (`EvidencePhotoHelper`), pengajuan tindakan korektif, serta verifikasi & rating kepuasan auditor (1–5 bintang).
- **FR-4.5**: **Komunikasi & Pengingat Langsung Lintas Pengguna**: Mengirim pesan chat (`COMMENT`) dan *push reminder* (`REMINDER_PING`) ke PIC/Auditor yang tersinkronisasi real-time lewat Firestore & FCM.
- **FR-4.6**: **Ekspor Laporan**: Ekspor PDF A4 per audit dan ekspor batch CSV temuan via `FileProvider`.

### Module 5: Aktivitas & Riwayat Audit (`FeedScreen`)
- **FR-5.1**: Sub-tab **Notifikasi & Alert** untuk pemberitahuan tugas PIC, verifikasi, pesan langsung, dan pengingat real-time dengan lencana (*badge*) belum dibaca & deduplikasi `remoteId`.
- **FR-5.2**: Sub-tab **Aktivitas / Laporan Audit** untuk riwayat inspeksi dengan paginasi, pengelompokan tanggal (`Hari Ini` / `Minggu Ini` / `Sebelumnya`), dan banner antrean sinkronisasi offline (*Sync Now*).
- **FR-5.3**: **Sleek Search & Multi-Dimensional Filter (`ActivityFilterBottomSheet`)**: Pencarian teks + penyaringan berdasarkan Rentang Tanggal, Pengguna, Tipe LSR, Perusahaan, dan Lokasi.

### Module 6: Centralized Settings Hub (`SettingsScreen` & `ProfileScreen`)
Pusat pengaturan terstruktur standar Android (`AppNavTab.SETTINGS`) yang memisahkan sub-halaman secara rapi:
- **FR-6.1 — Profil & Akun Inspektur (`ProfileAccountContent`)**: Status Firebase Auth, ganti akun, keluar akun (*Sign Out*), dan edit Perusahaan, Jabatan, serta Sertifikasi K3.
- **FR-6.2 — Modul Inspeksi LSR**: Aktivasi/nonaktif per modul LSR secara terpusat dan akses ke pengelola checklist kustom JSON (Import/Export/Reset).
- **FR-6.3 — Cloud & Workspace**: Switcher Workspace Proyek Firestore, sinkronisasi ulang data cloud, konfigurasi Google Apps Script Webhook URL + *Setup Guide*, dan Firebase Realtime Database (`lowerzakum-482af`) + uji koneksi.
- **FR-6.4 — Bahasa Aplikasi & Tema (`AppThemeMode`)**: Pemilih bahasa (`🇮🇩 Indonesia` / `🇬🇧 English`) serta pemilih tema (`Sistem` / `Terang` / `Gelap` obsidian kontras tinggi) di dalam Settings.
- **FR-6.5 — Notifikasi & Pengingat**: Toggle terpersisten untuk alert penugasan PIC, peringatan risiko fatal, pengingat draft aktif, dan *haptic feedback* saat survei.
- **FR-6.6 — Pusat Bantuan & Dukungan**: Panduan Google Apps Script, ringkasan standar IOGP 459 & OSHA, salin info diagnostik sistem, dan kontak dukungan HSE.
- **FR-6.7 — Tentang Aplikasi & Versi**: Informasi versi dinamis (`BuildConfig.VERSION_NAME` & `VERSION_CODE`), Application ID, Firebase Project ID, arsitektur penyimpanan, dan catatan rilis.

---

## 4. Kepatuhan Standar K3: 42 Rules Checklist Taxonomy

Inspeksi mencakup **7 modul aktif (42 aturan)** berbasis standar internasional IOGP Report 459 & OSHA:
1. **Bekerja di Ketinggian (*Working at Height*)** — 6 aturan (Full body harness, scaffolding green tag, guardrail, tool lanyard, drop zone barricade, rescue plan).
2. **Ruang Terbatas (*Confined Space Entry*)** — 6 aturan (Entry permit & blinding, atmospheric gas test, standby attendant, ventilasi mekanis, peralatan rescue, komunikasi dua arah).
3. **Isolasi Energi / LOTO (*Energy Isolation*)** — 6 aturan (Isolation plan, LOTO pribadi, zero energy verification, energy release, verifikasi peralatan pengujian, manajemen perubahan isolasi).
4. **Pengangkatan Mekanis (*Safe Mechanical Lifting*)** — 6 aturan (Lifting plan & sertifikat operator/rigger, inspeksi crane & rigging gear, barikade radius ayun, tag line, batas angin/cuaca, larangan melintas di bawah beban).
5. **Pekerjaan Galian (*Excavation Safety*)** — 6 aturan (Izin galian & deteksi utilitas bawah tanah, shoring/sloping/benching, akses tangga aman, jarak penumpukan tanah, inspeksi harian pasca hujan, kontrol lalu lintas sekitar galian).
6. **Pekerjaan Panas (*Hot Work*)** — 6 aturan (Hot work permit, uji gas mudah terbakar, pembersihan bahan mudah terbakar 11m/fire blanket, fire watch terlatih, APAR siap pakai, kelayakan kabel/peralatan las).
7. **Pembukaan Jalur & Bypass (*Line Break & Bypassing Safety Controls*)** — 6 aturan (Izin pembukaan jalur & depresurisasi, APD khusus zat berbahaya, otorisasi manajemen untuk bypass kontrol kritis, log & tanda peringatan bypass aktif, pemulihan sistem pasca kerja, komunikasi lintas shift).

---

## 5. Non-Functional Requirements (NFR)

- **Performance**: Waktu rendering antar layar `< 16ms` (60fps mulus pada Compose). Cold startup `< 1.5 detik`, dilengkapi `PersistentCacheSettings` 100 MB untuk load Feed instan.
- **Offline Reliability**: 100% fungsionalitas inspeksi, penyimpanan draf, dan antrean outbox `WorkManager` berjalan tanpa internet.
- **Data Integrity**: Menggunakan SQLite Room v5 ACID transaction (`MIGRATION_4_5`) untuk mencegah inkonsistensi saat crash.
- **Accessibility**: Mendukung ukuran teks dinamis, rasio kontras warna min **4.5:1 (WCAG AA)** di mode terang & gelap obsidian, dan target sentuh min **48x48dp**.
