package com.example.data

object GoogleAppsScriptSnippet {
    val CODE_GS: String = """
/**
 * ========================================================================
 * HSE LIFE-SAVING RULES (LSR) GOOGLE APPS SCRIPT WEBHOOK BACKEND (v2.0)
 * ========================================================================
 * Fitur:
 * 1. Menerima Laporan Inspeksi & Skor Kepatuhan -> Disimpan ke sheet "LSR_Inspections"
 * 2. Merekam Khusus Semua Temuan Ketidakpatuhan (Non-Compliance) -> Disimpan ke sheet "LSR_Findings"
 * 3. Otomatis membuat format Header & Styling kolom jika sheet baru dibuat
 * 4. Mendukung validasi koneksi (Ping)
 *
 * CARA DEPLOY:
 * 1. Buka Google Sheets baru -> Ekstensi (Extensions) -> Apps Script
 * 2. Hapus seluruh kode lama di Code.gs, lalu tempel (paste) kode ini.
 * 3. Klik "Terapkan" (Deploy) -> "Penerapan Baru" (New Deployment).
 * 4. Pilih tipe: "Aplikasi Web" (Web App).
 * 5. Set "Jalankan sebagai" (Execute as): "Saya" (Me).
 * 6. Set "Yang memiliki akses" (Who has access): "Siapa saja" (Anyone).
 * 7. Klik "Terapkan" (Deploy), berikan izin Google, lalu salin Web App URL (berakhiran /exec).
 * 8. Tempelkan URL tersebut di menu "Profil & Cloud" atau "Pengaturan" pada aplikasi Android.
 */

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error",
        message: "No post data received"
      })).setMimeType(ContentService.MimeType.JSON);
    }

    var data = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();

    // 1. Health check ping
    if (data.action === "ping") {
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        message: "Google Apps Script connected successfully",
        timestamp: new Date().toISOString()
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // 2. Submit Inspection Action
    var inspectionSheet = getOrCreateSheet(ss, "LSR_Inspections", [
      "Timestamp",
      "Date & Time",
      "Inspection ID",
      "Inspector Name",
      "Area / Location",
      "Compliance Score (%)",
      "Total Rules",
      "Compliant (YES)",
      "Non-Compliant (NO)",
      "Not Applicable (NA)",
      "Raw Answers JSON"
    ]);

    var now = new Date();
    var formattedDate = Utilities.formatDate(now, ss.getSpreadsheetTimeZone() || "GMT+7", "yyyy-MM-dd HH:mm:ss");

    var inspectionId = data.inspectionId || ("LSR-" + now.getTime());
    var inspectorName = data.inspectorName || "-";
    var area = data.area || "-";
    var score = data.score !== undefined ? data.score : 0;
    var totalQuestions = data.totalQuestions || (data.answers ? data.answers.length : 0);
    var yesCount = data.yesCount || 0;
    var noCount = data.noCount || 0;
    var naCount = data.naCount || 0;
    var rawJson = JSON.stringify(data.answers || []);

    // Append to LSR_Inspections
    inspectionSheet.appendRow([
      now.toISOString(),
      formattedDate,
      inspectionId,
      inspectorName,
      area,
      score + "%",
      totalQuestions,
      yesCount,
      noCount,
      naCount,
      rawJson
    ]);

    // 3. Record Non-Compliance Findings to "LSR_Findings"
    var findingsSheet = getOrCreateSheet(ss, "LSR_Findings", [
      "Timestamp",
      "Date & Time",
      "Inspection ID",
      "Inspector Name",
      "Area / Location",
      "Rule ID",
      "Category",
      "Violation Question",
      "Standard Reference",
      "Severity (Critical/Standard)",
      "Inspector Observation Notes",
      "Corrective Action",
      "Status"
    ]);

    if (data.findings && Array.isArray(data.findings) && data.findings.length > 0) {
      for (var i = 0; i < data.findings.length; i++) {
        var f = data.findings[i];
        findingsSheet.appendRow([
          now.toISOString(),
          formattedDate,
          inspectionId,
          f.inspectorName || inspectorName,
          f.area || area,
          f.ruleId || "-",
          f.category || "-",
          f.itemText || "-",
          f.standardRef || "-",
          f.isCritical ? "CRITICAL (Fatal Risk)" : "Standard",
          f.inspectorNotes || "-",
          f.correctiveAction || "-",
          f.status || "OPEN"
        ]);
      }
    } else if (data.answers && Array.isArray(data.answers)) {
      // Fallback: extract NO answers if findings array wasn't explicitly structured
      for (var j = 0; j < data.answers.length; j++) {
        var ans = data.answers[j];
        if (ans.answer === "NO") {
          findingsSheet.appendRow([
            now.toISOString(),
            formattedDate,
            inspectionId,
            inspectorName,
            area,
            ans.id || "-",
            ans.category || "General Safety",
            ans.item_text || "-",
            ans.standard_ref || "IOGP LSR",
            ans.is_critical ? "CRITICAL (Fatal Risk)" : "Standard",
            ans.notes || "-",
            "-",
            "OPEN"
          ]);
        }
      }
    }

    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      message: "Inspection & Findings saved successfully",
      inspectionId: inspectionId
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  return ContentService.createTextOutput(JSON.stringify({
    status: "online",
    service: "HSE Life-Saving Rules Webhook",
    message: "Ready to accept POST inspection payloads."
  })).setMimeType(ContentService.MimeType.JSON);
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
""".trimIndent()
}
