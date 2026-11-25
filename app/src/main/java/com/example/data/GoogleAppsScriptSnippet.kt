package com.example.data

object GoogleAppsScriptSnippet {
    const val DEFAULT_WEBHOOK_URL: String =
        "https://script.google.com/macros/s/AKfycbzjSqa_Glj2UTJ3Y2Dpp78h7Ga2V6QZsLLJNJ1lkNZpMZZrcPIieniRmopB6FRClf63hw/exec"

    val CODE_GS: String = """
/**
 * ============================================================================
 * HSE LIFE-SAVING RULES (LSR) — GOOGLE APPS SCRIPT BI-DIRECTIONAL WEBHOOK v3.2
 * ============================================================================
 * Fitur & Blueprint Safeguards:
 * - Manajemen User terpusat di Sheet "user" (name, email, role, company, password)
 * - Login & Register terintegrasi dengan sheet "user" (mendukung password admin langsung atau hash SHA-256)
 * - Fungsi setupSheets() untuk membuat otomatis sheet: LSR_Inspections, LSR_Findings, & user
 * - LockService.getScriptLock() + SpreadsheetApp.flush() untuk atomicity multi-user
 * - Dynamic Header Column Mapping (case-insensitive, aman jika urutan kolom digeser)
 * - sanitizeSheetText() anti-Formula Injection (=, +, -, @)
 * - doGet(e) & doPost(e) sinkronisasi dua arah dengan anti-cache ?t=
 *
 * CARA SETUP AWAL / UPDATE DEPLOYMENT (URL /exec TETAP SAMA):
 * 1. Buka Google Sheets -> Ekstensi -> Apps Script.
 * 2. Ganti seluruh isi Code.gs dengan kode ini, lalu Simpan (Ctrl+S / Cmd+S).
 * 3. Untuk membuat tab Sheet otomatis: pilih fungsi "setupSheets" di dropdown atas -> klik "Run".
 * 4. Klik "Terapkan" (Deploy) -> "Kelola penerapan" (Manage deployments).
 * 5. Klik ikon Pensil (Edit) -> pilih "Versi baru" (New version).
 * 6. Pastikan "Yang memiliki akses" = "Siapa saja" (Anyone), lalu klik "Terapkan" (Deploy).
 */

var SHEET_INSPECTIONS = "LSR_Inspections";
var SHEET_INSPECTIONS_LEGACY = "Inspeksi_LSR";
var SHEET_FINDINGS = "LSR_Findings";
var SHEET_USERS = "user";

var INSPECTION_HEADERS = [
  "Timestamp (ISO)",
  "Timestamp (ms)",
  "Inspection ID",
  "Inspector Name",
  "Company",
  "Area / Location",
  "Compliance Score (%)",
  "LSR Categories",
  "Total Rules",
  "YES",
  "NO",
  "NA",
  "Raw Answers JSON"
];

var FINDINGS_HEADERS = [
  "Timestamp (ISO)",
  "Timestamp (ms)",
  "Finding Key",
  "Inspection ID",
  "Inspector Name",
  "Company",
  "Area / Location",
  "Rule ID",
  "Category",
  "Violation Question",
  "Standard Reference",
  "Severity",
  "PIC Name",
  "PIC Email",
  "Observation Notes",
  "Corrective Action",
  "Status",
  "Rating",
  "Updated At (ms)"
];

var USER_HEADERS = [
  "name",
  "email",
  "role",
  "company",
  "password",
  "uid",
  "created_at",
  "updated_at"
];

/**
 * Jalankan fungsi ini secara manual di editor Apps Script (Run -> setupSheets)
 * untuk membuat otomatis sheet LSR_Inspections, LSR_Findings, dan user beserta header-nya.
 */
function setupSheets() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  getOrCreateSheet(ss, SHEET_INSPECTIONS, INSPECTION_HEADERS);
  getOrCreateSheet(ss, SHEET_FINDINGS, FINDINGS_HEADERS);
  getUsersSheet(ss, true);
  SpreadsheetApp.flush();
  Logger.log("Setup selesai: Sheet LSR_Inspections, LSR_Findings, dan user siap digunakan.");
  return "Setup selesai: LSR_Inspections, LSR_Findings, dan user siap.";
}

function doGet(e) {
  try {
    var params = (e && e.parameter) ? e.parameter : {};
    if (!validateRequestToken(params.token || "")) {
      return jsonResponse({
        status: "error",
        version: "3.2",
        code: "UNAUTHORIZED",
        message: "Token aplikasi tidak valid."
      });
    }
    var action = (params.action || "sync_all").toString();
    var limit = Math.min(Math.max(parseInt(params.limit || "60", 10) || 60, 1), 200);
    var since = parseInt(params.since || "0", 10) || 0;

    if (action === "ping") {
      return jsonResponse({
        status: "online",
        version: "3.2",
        supportsPull: true,
        supportsUserAuth: true,
        lockProtected: true,
        appTokenProtected: getConfiguredAppToken() !== "",
        service: "HSE LSR Inspection Webhook v3.2",
        serverTime: new Date().getTime(),
        message: "doGet & doPost active (Sheet 'user' + LockService + dynamic mapping ready)."
      });
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();

    if (action === "setup") {
      setupSheets();
      return jsonResponse({
        status: "online",
        version: "3.2",
        message: "Sheets initialized: LSR_Inspections, LSR_Findings, user",
        serverTime: new Date().getTime()
      });
    }

    var inspections = [];
    var findings = [];
    var users = [];

    if (action === "sync_all" || action === "getFeed" || action === "inspections") {
      inspections = readInspectionsFromSheet(ss, limit, since);
    }
    if (action === "sync_all" || action === "getFeed" || action === "findings") {
      findings = readFindingsFromSheet(ss, limit, since);
    }
    if (action === "sync_all" || action === "getFeed" || action === "users") {
      users = readUsersFromSheet(ss, 200);
    }

    return jsonResponse({
      status: "online",
      version: "3.2",
      supportsPull: true,
      supportsUserAuth: true,
      lockProtected: true,
      service: "HSE LSR Inspection Webhook v3.2",
      serverTime: new Date().getTime(),
      counts: {
        inspections: inspections.length,
        findings: findings.length,
        users: users.length
      },
      inspections: inspections,
      findings: findings,
      users: users
    });
  } catch (err) {
    return jsonResponse({
      status: "error",
      version: "3.2",
      message: err.toString()
    });
  }
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  var lockAcquired = false;
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse({ status: "error", message: "No post data received" });
    }

    var data = JSON.parse(e.postData.contents);
    var paramToken = (e && e.parameter && e.parameter.token) ? e.parameter.token : "";
    if (!validateRequestToken(data.appToken || paramToken)) {
      return jsonResponse({
        status: "error",
        version: "3.2",
        code: "UNAUTHORIZED",
        message: "Token aplikasi tidak valid."
      });
    }
    var action = (data.action || "submit_inspection").toString();

    if (action === "ping") {
      return jsonResponse({
        status: "success",
        version: "3.2",
        supportsPull: true,
        supportsUserAuth: true,
        lockProtected: true,
        appTokenProtected: getConfiguredAppToken() !== "",
        message: "Google Apps Script v3.2 connected (Sheet 'user' + doGet + doPost + LockService ready)",
        serverTime: new Date().getTime()
      });
    }

    // Acquire script lock (up to 25s) to prevent concurrent row collision
    lockAcquired = lock.tryLock(25000);
    if (!lockAcquired) {
      return jsonResponse({
        status: "error",
        version: "3.2",
        message: "Server sibuk memproses sinkronisasi lain, silakan coba beberapa detik lagi."
      });
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();

    if (action === "register_user") {
      var regResult = registerUserInSheet(ss, data);
      SpreadsheetApp.flush();
      return jsonResponse(regResult);
    }

    if (action === "login_user") {
      var loginResult = loginUserInSheet(ss, data);
      SpreadsheetApp.flush();
      return jsonResponse(loginResult);
    }

    if (action === "sync_user" || action === "upsert_user") {
      var syncedUser = upsertUserInSheet(ss, data);
      SpreadsheetApp.flush();
      return jsonResponse({
        status: "success",
        version: "3.2",
        action: "sync_user",
        user: syncedUser,
        serverTime: new Date().getTime()
      });
    }

    if (action === "update_finding") {
      var updated = upsertFindingRow(ss, data);
      SpreadsheetApp.flush();
      return jsonResponse({
        status: "success",
        version: "3.2",
        action: "update_finding",
        updated: updated,
        serverTime: new Date().getTime()
      });
    }

    // Default: submit_inspection
    var inspectionSheet = getOrCreateSheet(ss, SHEET_INSPECTIONS, INSPECTION_HEADERS);
    var findingsSheet = getOrCreateSheet(ss, SHEET_FINDINGS, FINDINGS_HEADERS);

    var timestampMs = Number(data.timestamp || new Date().getTime());
    var dateObj = new Date(timestampMs);
    var tz = ss.getSpreadsheetTimeZone() || "Asia/Jakarta";
    var formattedIso = Utilities.formatDate(dateObj, tz, "yyyy-MM-dd HH:mm:ss");

    var inspectionId = (data.inspectionId || ("LSR-" + timestampMs)).toString();
    var inspectorName = (data.inspectorName || "-").toString();
    var company = (data.company || "-").toString();
    var area = (data.area || "-").toString();
    var score = data.score !== undefined ? Number(data.score) : 0;
    var lsrCategories = Array.isArray(data.applicableModules)
      ? data.applicableModules.join(", ")
      : (data.lsrCategories || "").toString();
    var totalQuestions = Number(data.totalQuestions || (data.answers ? data.answers.length : 0));
    var yesCount = Number(data.yesCount || 0);
    var noCount = Number(data.noCount || 0);
    var naCount = Number(data.naCount || 0);
    var rawJson = JSON.stringify(data.answers || []);

    if (!hasInspectionSignature(inspectionSheet, timestampMs, inspectorName, area)) {
      appendRowByHeaders(inspectionSheet, INSPECTION_HEADERS, {
        "Timestamp (ISO)": formattedIso,
        "Timestamp (ms)": timestampMs,
        "Inspection ID": sanitizeSheetText(inspectionId),
        "Inspector Name": sanitizeSheetText(inspectorName),
        "Company": sanitizeSheetText(company),
        "Area / Location": sanitizeSheetText(area),
        "Compliance Score (%)": score + "%",
        "LSR Categories": sanitizeSheetText(lsrCategories),
        "Total Rules": totalQuestions,
        "YES": yesCount,
        "NO": noCount,
        "NA": naCount,
        "Raw Answers JSON": rawJson
      });
    }

    var findingsList = Array.isArray(data.findings) ? data.findings : [];
    for (var i = 0; i < findingsList.length; i++) {
      var f = findingsList[i];
      upsertFindingRow(ss, {
        timestamp: f.timestamp || timestampMs,
        findingKey: f.findingKey || (timestampMs + "_" + (f.ruleId || "") + "_" + (f.area || area)),
        inspectionId: inspectionId,
        inspectorName: f.inspectorName || inspectorName,
        company: f.company || company,
        area: f.area || area,
        ruleId: f.ruleId || "-",
        category: f.category || "-",
        itemText: f.itemText || "-",
        standardRef: f.standardRef || "IOGP LSR",
        isCritical: Boolean(f.isCritical),
        picName: f.picName || f.assignedPicName || "Unassigned",
        picEmail: f.picEmail || f.assignedPicEmail || "",
        notes: f.inspectorNotes || f.notes || "-",
        correctiveAction: f.correctiveAction || "-",
        status: normalizeStatusServer(f.status || "OPEN"),
        rating: f.rating || 0
      }, findingsSheet);
    }

    SpreadsheetApp.flush();

    return jsonResponse({
      status: "success",
      version: "3.2",
      message: "Inspection & Findings saved atomically to Google Sheets",
      inspectionId: inspectionId,
      serverTime: new Date().getTime()
    });
  } catch (err) {
    return jsonResponse({
      status: "error",
      version: "3.2",
      message: err.toString()
    });
  } finally {
    if (lockAcquired) {
      try { lock.releaseLock(); } catch (ignore) {}
    }
  }
}

// ============================================================================
// USER SHEET MANAGEMENT ("user": name, email, role, company, password)
// ============================================================================

function getUsersSheet(ss, createIfMissing) {
  var sheet = ss.getSheetByName(SHEET_USERS);
  if (!sheet) {
    var allSheets = ss.getSheets();
    for (var i = 0; i < allSheets.length; i++) {
      var sName = allSheets[i].getName().trim().toLowerCase();
      if (sName === "user" || sName === "users") {
        sheet = allSheets[i];
        break;
      }
    }
  }
  if (!sheet && createIfMissing) {
    sheet = getOrCreateSheet(ss, SHEET_USERS, USER_HEADERS);
  }
  return sheet;
}

function readUsersFromSheet(ss, limit) {
  var sheet = getUsersSheet(ss, false);
  if (!sheet || sheet.getLastRow() <= 1) return [];

  var values = sheet.getDataRange().getValues();
  var headerMap = getHeaderMapFromRow(values[0]);
  var results = [];
  var maxRows = limit || 200;

  for (var i = 1; i < values.length && results.length < maxRows; i++) {
    var row = values[i];
    var email = unsanitizeText(getColValue(row, headerMap, "email", 1)).trim();
    if (email === "") continue;

    var name = unsanitizeText(getColValue(row, headerMap, "name", 0)).trim();
    var role = unsanitizeText(getColValue(row, headerMap, "role", 2)).trim();
    var company = unsanitizeText(getColValue(row, headerMap, "company", 3)).trim();
    var uid = unsanitizeText(getColValue(row, headerMap, "uid", 5)).trim();
    var updatedAt = String(getColValue(row, headerMap, "updated_at", 7) || "");

    // Password sengaja TIDAK pernah dikembalikan di doGet demi keamanan
    results.push({
      name: name || email.split("@")[0],
      email: email,
      role: role || "HSE Officer",
      company: company || "HSSE Operations",
      uid: uid,
      updatedAt: updatedAt
    });
  }
  return results;
}

function registerUserInSheet(ss, data) {
  var sheet = getUsersSheet(ss, true);
  var email = String(data.email || "").trim().toLowerCase();
  var password = String(data.password || "");
  var name = String(data.name || data.displayName || "").trim();
  var role = String(data.role || "Lead HSE Inspector").trim();
  var company = String(data.company || "HSSE Tech Operations").trim();
  var uid = String(data.uid || ("gas_" + new Date().getTime())).trim();

  if (email === "" || password === "") {
    return {
      status: "error",
      version: "3.2",
      code: "INVALID_INPUT",
      message: "Email dan kata sandi wajib diisi."
    };
  }

  var lastRow = sheet.getLastRow();
  var headerMap = getHeaderMapFromRow(sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0]);

  if (lastRow > 1) {
    var values = sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).getValues();
    for (var i = 0; i < values.length; i++) {
      var rowEmail = unsanitizeText(getColValue(values[i], headerMap, "email", 1)).trim().toLowerCase();
      if (rowEmail === email) {
        return {
          status: "error",
          version: "3.2",
          code: "EMAIL_EXISTS",
          message: "Email ini sudah terdaftar di sheet 'user'. Silakan masuk."
        };
      }
    }
  }

  var tz = ss.getSpreadsheetTimeZone() || "Asia/Jakarta";
  var nowIso = Utilities.formatDate(new Date(), tz, "yyyy-MM-dd HH:mm:ss");
  var finalName = name || email.split("@")[0];

  appendRowByHeaders(sheet, USER_HEADERS, {
    "name": sanitizeSheetText(finalName),
    "email": sanitizeSheetText(email),
    "role": sanitizeSheetText(role),
    "company": sanitizeSheetText(company),
    "password": sanitizeSheetText(hashPasswordServer(password)),
    "uid": sanitizeSheetText(uid),
    "created_at": nowIso,
    "updated_at": nowIso
  });

  return {
    status: "success",
    version: "3.2",
    action: "register_user",
    user: {
      name: finalName,
      email: email,
      role: role,
      company: company,
      uid: uid
    },
    serverTime: new Date().getTime()
  };
}

function loginUserInSheet(ss, data) {
  var sheet = getUsersSheet(ss, true);
  var email = String(data.email || "").trim().toLowerCase();
  var password = String(data.password || "");

  if (email === "" || password === "") {
    return {
      status: "error",
      version: "3.2",
      code: "INVALID_INPUT",
      message: "Email dan kata sandi wajib diisi."
    };
  }

  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) {
    return {
      status: "error",
      version: "3.2",
      code: "USER_NOT_FOUND",
      message: "Belum ada akun di sheet 'user'."
    };
  }

  var headerMap = getHeaderMapFromRow(sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0]);
  var values = sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).getValues();

  for (var i = 0; i < values.length; i++) {
    var row = values[i];
    var rowEmail = unsanitizeText(getColValue(row, headerMap, "email", 1)).trim().toLowerCase();
    if (rowEmail === email) {
      var storedPassword = getColValue(row, headerMap, "password", 4);
      if (!verifyPasswordServer(password, storedPassword)) {
        return {
          status: "error",
          version: "3.2",
          code: "INVALID_PASSWORD",
          message: "Kata sandi salah. Periksa kembali password Anda di sheet 'user'."
        };
      }

      var r = i + 2;
      var tz = ss.getSpreadsheetTimeZone() || "Asia/Jakarta";
      var nowIso = Utilities.formatDate(new Date(), tz, "yyyy-MM-dd HH:mm:ss");
      var existingUid = unsanitizeText(getColValue(row, headerMap, "uid", 5)).trim();
      var uidToUse = existingUid || String(data.uid || ("gas_" + new Date().getTime()));

      setCellByHeader(sheet, headerMap, r, "updated_at", nowIso);
      if (!existingUid && uidToUse !== "") {
        setCellByHeader(sheet, headerMap, r, "uid", sanitizeSheetText(uidToUse));
      }

      return {
        status: "success",
        version: "3.2",
        action: "login_user",
        user: {
          name: unsanitizeText(getColValue(row, headerMap, "name", 0)).trim() || email.split("@")[0],
          email: rowEmail,
          role: unsanitizeText(getColValue(row, headerMap, "role", 2)).trim() || "HSE Officer",
          company: unsanitizeText(getColValue(row, headerMap, "company", 3)).trim() || "HSSE Operations",
          uid: uidToUse
        },
        serverTime: new Date().getTime()
      };
    }
  }

  return {
    status: "error",
    version: "3.2",
    code: "USER_NOT_FOUND",
    message: "Email belum terdaftar di sheet 'user'."
  };
}

function upsertUserInSheet(ss, data) {
  var sheet = getUsersSheet(ss, true);
  var email = String(data.email || "").trim().toLowerCase();
  if (email === "") return null;

  var name = String(data.name || data.displayName || email.split("@")[0]).trim();
  var role = String(data.role || "HSE Officer").trim();
  var company = String(data.company || "HSSE Operations").trim();
  var uid = String(data.uid || "").trim();
  var rawPassword = String(data.password || "");

  var tz = ss.getSpreadsheetTimeZone() || "Asia/Jakarta";
  var nowIso = Utilities.formatDate(new Date(), tz, "yyyy-MM-dd HH:mm:ss");
  var lastRow = sheet.getLastRow();
  var headerMap = getHeaderMapFromRow(sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0]);

  if (lastRow > 1) {
    var values = sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).getValues();
    for (var i = 0; i < values.length; i++) {
      var rowEmail = unsanitizeText(getColValue(values[i], headerMap, "email", 1)).trim().toLowerCase();
      if (rowEmail === email) {
        var r = i + 2;
        if (name !== "") setCellByHeader(sheet, headerMap, r, "name", sanitizeSheetText(name));
        if (role !== "") setCellByHeader(sheet, headerMap, r, "role", sanitizeSheetText(role));
        if (company !== "") setCellByHeader(sheet, headerMap, r, "company", sanitizeSheetText(company));
        if (uid !== "") setCellByHeader(sheet, headerMap, r, "uid", sanitizeSheetText(uid));
        // Password existing user TIDAK boleh ditimpa lewat sync_user demi keamanan akun
        setCellByHeader(sheet, headerMap, r, "updated_at", nowIso);
        return { name: name, email: email, role: role, company: company, uid: uid };
      }
    }
  }

  var finalUid = uid || ("gas_" + new Date().getTime());
  appendRowByHeaders(sheet, USER_HEADERS, {
    "name": sanitizeSheetText(name),
    "email": sanitizeSheetText(email),
    "role": sanitizeSheetText(role),
    "company": sanitizeSheetText(company),
    "password": rawPassword !== "" ? sanitizeSheetText(hashPasswordServer(rawPassword)) : "",
    "uid": sanitizeSheetText(finalUid),
    "created_at": nowIso,
    "updated_at": nowIso
  });

  return { name: name, email: email, role: role, company: company, uid: finalUid };
}

function hashPasswordServer(plain) {
  var s = String(plain || "");
  if (s.indexOf("sha256:") === 0) return s;
  var digest = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, s, Utilities.Charset.UTF_8);
  var hex = "";
  for (var i = 0; i < digest.length; i++) {
    var byteVal = digest[i];
    if (byteVal < 0) byteVal += 256;
    var h = byteVal.toString(16);
    if (h.length === 1) h = "0" + h;
    hex += h;
  }
  return "sha256:" + hex;
}

function verifyPasswordServer(inputPass, storedPass) {
  var input = String(inputPass || "");
  var stored = unsanitizeText(storedPass);
  if (stored === "" || input === "") return false;
  if (stored.indexOf("sha256:") === 0) {
    return hashPasswordServer(input) === stored;
  }
  // Mendukung password yang diketik langsung oleh admin di Google Sheets
  return input === stored;
}

// ============================================================================
// INSPECTIONS & FINDINGS READ/WRITE
// ============================================================================

function readInspectionsFromSheet(ss, limit, since) {
  var sheet = ss.getSheetByName(SHEET_INSPECTIONS);
  var isLegacy = false;
  if (!sheet || sheet.getLastRow() <= 1) {
    var legacy = ss.getSheetByName(SHEET_INSPECTIONS_LEGACY);
    if (legacy && legacy.getLastRow() > 1) {
      sheet = legacy;
      isLegacy = true;
    }
  }
  if (!sheet || sheet.getLastRow() <= 1) return [];

  var values = sheet.getDataRange().getValues();
  var headerMap = getHeaderMapFromRow(values[0]);
  var results = [];

  for (var i = values.length - 1; i >= 1 && results.length < limit; i--) {
    var row = values[i];
    if (isLegacy) {
      var tsLegacy = Number(row[1] || 0);
      if (since > 0 && tsLegacy <= since) continue;
      results.push({
        inspectionId: String(tsLegacy),
        timestamp: tsLegacy,
        dateFormatted: String(row[0] || ""),
        inspectorName: unsanitizeText(row[2]),
        company: "",
        area: unsanitizeText(row[3]),
        score: parseInt(String(row[4] || "0").replace("%", ""), 10) || 0,
        lsrCategories: "",
        answersJson: String(row[5] || "[]")
      });
    } else {
      var ts = Number(getColValue(row, headerMap, "Timestamp (ms)", 1) || 0);
      if (since > 0 && ts <= since) continue;
      var rawScore = String(getColValue(row, headerMap, "Compliance Score (%)", 6) || "0").replace("%", "");
      results.push({
        inspectionId: unsanitizeText(getColValue(row, headerMap, "Inspection ID", 2) || ts),
        timestamp: ts,
        dateFormatted: String(getColValue(row, headerMap, "Timestamp (ISO)", 0) || ""),
        inspectorName: unsanitizeText(getColValue(row, headerMap, "Inspector Name", 3)),
        company: unsanitizeText(getColValue(row, headerMap, "Company", 4)),
        area: unsanitizeText(getColValue(row, headerMap, "Area / Location", 5)),
        score: parseInt(rawScore, 10) || 0,
        lsrCategories: unsanitizeText(getColValue(row, headerMap, "LSR Categories", 7)),
        answersJson: String(getColValue(row, headerMap, "Raw Answers JSON", 12) || "[]")
      });
    }
  }
  return results;
}

function readFindingsFromSheet(ss, limit, since) {
  var sheet = ss.getSheetByName(SHEET_FINDINGS);
  if (!sheet || sheet.getLastRow() <= 1) return [];

  var values = sheet.getDataRange().getValues();
  var headerMap = getHeaderMapFromRow(values[0]);
  var results = [];

  for (var i = values.length - 1; i >= 1 && results.length < limit; i--) {
    var row = values[i];
    var ts = Number(getColValue(row, headerMap, "Timestamp (ms)", 1) || 0);
    var updatedAt = Number(getColValue(row, headerMap, "Updated At (ms)", 18) || ts);
    if (since > 0 && updatedAt <= since) continue;

    results.push({
      timestamp: ts,
      dateFormatted: String(getColValue(row, headerMap, "Timestamp (ISO)", 0) || ""),
      findingKey: unsanitizeText(getColValue(row, headerMap, "Finding Key", 2)),
      inspectionId: unsanitizeText(getColValue(row, headerMap, "Inspection ID", 3)),
      inspectorName: unsanitizeText(getColValue(row, headerMap, "Inspector Name", 4)),
      company: unsanitizeText(getColValue(row, headerMap, "Company", 5)),
      area: unsanitizeText(getColValue(row, headerMap, "Area / Location", 6)),
      ruleId: unsanitizeText(getColValue(row, headerMap, "Rule ID", 7)),
      category: unsanitizeText(getColValue(row, headerMap, "Category", 8)),
      itemText: unsanitizeText(getColValue(row, headerMap, "Violation Question", 9)),
      standardRef: unsanitizeText(getColValue(row, headerMap, "Standard Reference", 10)),
      isCritical: String(getColValue(row, headerMap, "Severity", 11) || "").indexOf("CRITICAL") !== -1,
      picName: unsanitizeText(getColValue(row, headerMap, "PIC Name", 12)),
      picEmail: unsanitizeText(getColValue(row, headerMap, "PIC Email", 13)),
      notes: unsanitizeText(getColValue(row, headerMap, "Observation Notes", 14)),
      correctiveAction: unsanitizeText(getColValue(row, headerMap, "Corrective Action", 15)),
      status: normalizeStatusServer(getColValue(row, headerMap, "Status", 16) || "OPEN"),
      rating: Number(getColValue(row, headerMap, "Rating", 17) || 0),
      updatedAt: updatedAt
    });
  }
  return results;
}

function upsertFindingRow(ss, payload, existingSheet) {
  var sheet = existingSheet || getOrCreateSheet(ss, SHEET_FINDINGS, FINDINGS_HEADERS);
  var ts = Number(payload.timestamp || new Date().getTime());
  var now = new Date().getTime();
  var tz = ss.getSpreadsheetTimeZone() || "Asia/Jakarta";
  var formattedIso = Utilities.formatDate(new Date(ts), tz, "yyyy-MM-dd HH:mm:ss");
  var key = (payload.findingKey || (ts + "_" + (payload.ruleId || "") + "_" + (payload.area || ""))).toString();

  var lastRow = sheet.getLastRow();
  var headerMap = getHeaderMapFromRow(sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0]);

  if (lastRow > 1) {
    var values = sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).getValues();
    for (var i = 0; i < values.length; i++) {
      var row = values[i];
      var rowKey = unsanitizeText(getColValue(row, headerMap, "Finding Key", 2));
      var rowTs = Number(getColValue(row, headerMap, "Timestamp (ms)", 1) || 0);
      var rowRule = unsanitizeText(getColValue(row, headerMap, "Rule ID", 7));
      var rowArea = unsanitizeText(getColValue(row, headerMap, "Area / Location", 6));

      if ((key !== "" && rowKey === key) || (rowTs === ts && rowRule === String(payload.ruleId || "") && rowArea === String(payload.area || ""))) {
        var r = i + 2;
        if (payload.picName !== undefined && payload.picName !== "") setCellByHeader(sheet, headerMap, r, "PIC Name", sanitizeSheetText(payload.picName));
        if (payload.picEmail !== undefined && payload.picEmail !== "") setCellByHeader(sheet, headerMap, r, "PIC Email", sanitizeSheetText(payload.picEmail));
        if (payload.correctiveAction !== undefined && payload.correctiveAction !== "") setCellByHeader(sheet, headerMap, r, "Corrective Action", sanitizeSheetText(payload.correctiveAction));
        if (payload.status !== undefined && payload.status !== "") setCellByHeader(sheet, headerMap, r, "Status", normalizeStatusServer(payload.status));
        if (payload.rating !== undefined && Number(payload.rating) > 0) setCellByHeader(sheet, headerMap, r, "Rating", Number(payload.rating));
        setCellByHeader(sheet, headerMap, r, "Updated At (ms)", now);
        return true;
      }
    }
  }

  appendRowByHeaders(sheet, FINDINGS_HEADERS, {
    "Timestamp (ISO)": formattedIso,
    "Timestamp (ms)": ts,
    "Finding Key": sanitizeSheetText(key),
    "Inspection ID": sanitizeSheetText((payload.inspectionId || "-").toString()),
    "Inspector Name": sanitizeSheetText((payload.inspectorName || "-").toString()),
    "Company": sanitizeSheetText((payload.company || "-").toString()),
    "Area / Location": sanitizeSheetText((payload.area || "-").toString()),
    "Rule ID": sanitizeSheetText((payload.ruleId || "-").toString()),
    "Category": sanitizeSheetText((payload.category || "-").toString()),
    "Violation Question": sanitizeSheetText((payload.itemText || "-").toString()),
    "Standard Reference": sanitizeSheetText((payload.standardRef || "IOGP LSR").toString()),
    "Severity": payload.isCritical ? "CRITICAL (Fatal Risk)" : "Standard",
    "PIC Name": sanitizeSheetText((payload.picName || "Unassigned").toString()),
    "PIC Email": sanitizeSheetText((payload.picEmail || "").toString()),
    "Observation Notes": sanitizeSheetText((payload.notes || "-").toString()),
    "Corrective Action": sanitizeSheetText((payload.correctiveAction || "-").toString()),
    "Status": normalizeStatusServer(payload.status || "OPEN"),
    "Rating": Number(payload.rating || 0),
    "Updated At (ms)": now
  });
  return true;
}

function hasInspectionSignature(sheet, ts, inspectorName, area) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return false;
  var values = sheet.getDataRange().getValues();
  var headerMap = getHeaderMapFromRow(values[0]);
  for (var i = values.length - 1; i >= 1; i--) {
    var row = values[i];
    var rowTs = Number(getColValue(row, headerMap, "Timestamp (ms)", 1) || 0);
    var rowInsp = unsanitizeText(getColValue(row, headerMap, "Inspector Name", 3));
    var rowArea = unsanitizeText(getColValue(row, headerMap, "Area / Location", 5));
    if (rowTs === ts && isSamePersonServer(rowInsp, inspectorName) && rowArea === area) {
      return true;
    }
  }
  return false;
}

function getHeaderMapFromRow(headerRow) {
  var map = {};
  if (!headerRow) return map;
  for (var c = 0; c < headerRow.length; c++) {
    var name = String(headerRow[c] || "").trim();
    if (name !== "") {
      map[name] = c;
      map[name.toLowerCase()] = c;
    }
  }
  return map;
}

function getColValue(row, headerMap, colName, fallbackIdx) {
  var lower = colName.toLowerCase();
  var idx = headerMap[colName] !== undefined
    ? headerMap[colName]
    : (headerMap[lower] !== undefined ? headerMap[lower] : fallbackIdx);
  return (idx >= 0 && idx < row.length) ? row[idx] : "";
}

function setCellByHeader(sheet, headerMap, rowIndex, colName, value) {
  var lower = colName.toLowerCase();
  var idx = headerMap[colName] !== undefined ? headerMap[colName] : headerMap[lower];
  if (idx !== undefined && idx >= 0) {
    sheet.getRange(rowIndex, idx + 1).setValue(value);
  }
}

function appendRowByHeaders(sheet, defaultHeaders, obj) {
  var lastCol = sheet.getLastColumn();
  var headers = lastCol > 0 ? sheet.getRange(1, 1, 1, lastCol).getValues()[0] : defaultHeaders;
  var lowerObj = {};
  for (var k in obj) {
    if (obj.hasOwnProperty(k)) lowerObj[k.toLowerCase()] = obj[k];
  }
  var row = [];
  for (var i = 0; i < headers.length; i++) {
    var h = String(headers[i] || "").trim();
    var hLower = h.toLowerCase();
    if (obj[h] !== undefined) {
      row.push(obj[h]);
    } else if (lowerObj[hLower] !== undefined) {
      row.push(lowerObj[hLower]);
    } else {
      row.push("");
    }
  }
  sheet.appendRow(row);
}

function sanitizeSheetText(val) {
  if (val === null || val === undefined) return "";
  var s = String(val);
  if (s === "-" || s === "") return s;
  var first = s.charAt(0);
  if (first === "=" || first === "+" || first === "-" || first === "@") {
    return "'" + s;
  }
  return s;
}

function unsanitizeText(val) {
  if (val === null || val === undefined) return "";
  var s = String(val);
  if (s.length > 1 && s.charAt(0) === "'") {
    var second = s.charAt(1);
    if (second === "=" || second === "+" || second === "-" || second === "@") {
      return s.substring(1);
    }
  }
  return s;
}

function normalizeStatusServer(raw) {
  var s = String(raw || "OPEN").trim().toUpperCase();
  if (s === "PENDING" || s === "IN_REVIEW") return "ACTION_SUBMITTED";
  if (s === "OPEN" || s === "ACTION_SUBMITTED" || s === "CLOSED" || s === "REOPENED") return s;
  return "OPEN";
}

function isSamePersonServer(a, b) {
  var norm = function(str) {
    return String(str || "").split("(")[0].trim().toLowerCase().replace(/\s+/g, " ");
  };
  var na = norm(a);
  var nb = norm(b);
  return na !== "" && na === nb;
}

function getOrCreateSheet(spreadsheet, sheetName, headers) {
  var sheet = spreadsheet.getSheetByName(sheetName);
  if (!sheet) {
    sheet = spreadsheet.insertSheet(sheetName);
    sheet.appendRow(headers);
    var headerRange = sheet.getRange(1, 1, 1, headers.length);
    headerRange.setBackground("#0052CC");
    headerRange.setFontColor("#FFFFFF");
    headerRange.setFontWeight("bold");
    headerRange.setHorizontalAlignment("center");
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function getConfiguredAppToken() {
  try {
    return String(PropertiesService.getScriptProperties().getProperty("APP_API_TOKEN") || "").trim();
  } catch (e) {
    return "";
  }
}

function validateRequestToken(tokenCandidate) {
  var expected = getConfiguredAppToken();
  if (expected === "") return true;
  return String(tokenCandidate || "").trim() === expected;
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
    """.trimIndent()
}
