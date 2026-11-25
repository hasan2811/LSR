# AI Agent Guidelines & Vibecoding Ruleset

This document defines the operating rules, architectural boundaries, and coding standards for all AI agents (Antigravity, Cursor, Claude Code, Copilot, Gemini CLI) collaborating on the **HSE LSR Inspector** codebase.

---

## 1. Core Operating Philosophy: Ponytail Mode (Lazy Senior Dev)

> *"The best code is the code never written. Solve the whole problem with the smallest, cleanest, most robust change."*

1. **YAGNI (You Aren't Gonna Need It)**:
   - Do not invent speculative abstractions, premature generic interfaces, or boilerplate wrappers for hypothetical future requirements.
   - Always prefer standard Kotlin / Jetpack Compose / Android SDK constructs over custom reinventions.
2. **Minimal Working Diffs**:
   - Touch only the lines, callers, and tests required to fulfill the user's intent.
   - Clean, readable, surgical code changes beat 500-line rewrites every time.
3. **Preserve Existing Architecture**:
   - Adhere to the established MVI/MVVM StateFlow pattern, Room DAO contracts, and Firestore Manager structures.
   - Reuse existing UI components (`HseBrandLogo`, `HseBrandBadge`, `StandardActionScreen`) rather than building duplicates.
4. **Safety & Zero Data Loss**:
   - Never skip error boundaries, trust validations, or Room database transactions.
   - In field safety inspections, audit data loss is a critical incident. Always ensure optimistic local persistence before remote sync.

---

## 2. Technical Stack Snapshot

| Layer | Technologies |
| :--- | :--- |
| **Platform** | Android (Min SDK 24, Target SDK 36, Compile SDK 36) |
| **Language & Tooling** | Kotlin 2.2.10, AGP 9.5.2, KSP 2.3.6 |
| **UI Toolkit** | Jetpack Compose (BOM 2024.09.00), Material 3 |
| **Local Storage** | Room 2.7.0 (`AppDatabase` v6, `LsrDao`, `MIGRATION_3_4`, `MIGRATION_4_5`, `MIGRATION_5_6`), SharedPreferences |
| **Cloud & Realtime** | Firebase Firestore (100 MB persistent cache, `startAfter` cursor + `updatedAt` delta), Firebase Auth, Storage, FCM, WorkManager (`SyncWorker`, `CacheEvictionWorker`), Google Apps Script v3.2 |
| **Serialization** | Moshi Kotlin Codegen 1.15.2 |
| **Architecture** | Single-Activity (`MainActivity`), StateFlow, AndroidViewModel |

---

## 3. Tool Usage Guardrails for AI

1. **File Edits**:
   - **Never** use shell commands (`sed`, `awk`, `cat >`, `echo`) to modify source code files.
   - Always use IDE-integrated editing tools (`replace_file_content`, `multi_replace_file_content`, `write_file`) to preserve IDE memory buffers.
2. **File Inspection**:
   - Always use `read_file`, `find_files`, `find_declaration`, and `grep`.
   - Never run raw shell `find` or `grep` when native tools are available.
3. **Build & Test Verification**:
   - After modifying Kotlin code or Gradle files, trigger `:app:installDebug` or unit tests to ensure zero compilation regressions.

---

## 4. Documentation Map for AI Context

Before writing code or answering structural questions, consult in this order:
- [`docs/PROJECT_CONTEXT.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/PROJECT_CONTEXT.md) – **Start here**: living baseline, completed features, technical gotchas, and handover snapshot.
- [`docs/ARCHITECTURE_AND_RULES.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/ARCHITECTURE_AND_RULES.md) – Single source of truth: Room v6, dual-pipeline sync, cursor pagination, RBAC + SoD matrix, and tech debt.
- [`CHANGELOG.md`](file:///Users/rullyberiandi/StudioProjects/LSR/CHANGELOG.md) – Versioned release notes (`v2.2.0`).
- [`docs/PRD.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/PRD.md) – Functional requirements, persona workflows, IOGP 459 & OSHA compliance rules.
- [`docs/ARCHITECTURE.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/ARCHITECTURE.md) – Technical data flow, Compose hierarchy, Room & Firestore schemas.
- [`docs/APPS_SCRIPT_BLUEPRINT.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/APPS_SCRIPT_BLUEPRINT.md) – Google Apps Script v3.2 architecture, `LockService`, 25s mutation lock, and sheet schema.
- [`docs/PERMISSIONS_AND_SECURITY.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/PERMISSIONS_AND_SECURITY.md) – Role-based access control, SoD, security rules, signing keys.
- [`docs/AI_DEVELOPMENT_GUIDE.md`](file:///Users/rullyberiandi/StudioProjects/LSR/docs/AI_DEVELOPMENT_GUIDE.md) – Step-by-step recipes for adding rules, endpoints, and screens.

---

## 5. UI/UX & Production Baseline Rules (v2.0+)

- **Package & Cloud**: `applicationId` is `com.lowerzakum.lsr`, connected to Firebase project `lowerzakum-482af` with zero hardcoded dummy users, zero hardcoded API keys (`MainActivity.kt`), and strict `.gitignore` credential hygiene.
- **Canonical GitHub Repository**: Remote `origin` is always `https://github.com/hasan2811/LSR.git` (default branch `main`). Always verify `git remote -v` points to `https://github.com/hasan2811/LSR.git` and push to `origin main` after confirming zero tracked secrets.
- **Navigation Structure**: 4 primary bottom tabs (`DASHBOARD`, `FEED`, `FINDINGS`, `SETTINGS`) defined in `AppNavTab` (`LsrViewModel.kt`). Cloud Sync, Language, Theme (`AppThemeMode`), Modules, Notifications, Support, and About live inside `SettingsScreen` (`SettingsSection`), NOT in the Dashboard header or bottom bar.
- **Localization Parity**: Never display raw underscore keys. Every `AppStrings.get(key, language)` call must exist in both `idStrings` and `enStrings` in `AppLanguage.kt`.
- **Ergonomic Field Controls & Inputs**: Survey answer buttons (`YA` / `TIDAK` / `N/A`) must remain single-line (`maxLines = 1, softWrap = false`), minimum `56.dp` height, empty states must center-align both title and description, and compact search inputs must use `SleekSearchFilterBar` (`BasicTextField`) rather than height-constrained M3 `OutlinedTextField`.
