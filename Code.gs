/**
 * Google Apps Script (Code.gs) untuk Aplikasi HSE LSR Inspection
 * 
 * CARA PENGGUNAAN:
 * 1. Buka Google Sheets baru (atau yang sudah ada).
 * 2. Klik menu "Ekstensi" -> "Apps Script" (Extensions -> Apps Script).
 * 3. Hapus semua kode default, lalu tempel (paste) seluruh kode di bawah ini.
 * 4. Klik tombol "Simpan" (Ctrl+S / Cmd+S).
 * 5. Klik tombol "Terapkan" -> "Penerapan Baru" (Deploy -> New deployment).
 * 6. Pada bagian "Pilih Jenis" (Select type), pilih "Aplikasi Web" (Web app).
 * 7. Atur konfigurasi berikut:
 *    - Deskripsi: LSR Inspection Webhook
 *    - Jalankan sebagai (Execute as): Diri Anda (Me / email@gmail.com)
 *    - Yang memiliki akses (Who has access): Siapa saja (Anyone) -> *PENTING agar aplikasi Android dapat mengirim data tanpa login Google*
 * 8. Klik "Terapkan" (Deploy), lalu izinkan akses (Grant access).
 * 9. Salin URL Aplikasi Web (Web App URL) yang dihasilkan (berakhiran `/exec`).
 * 10. Buka aplikasi HSE LSR Inspector di Android, masuk ke Pengaturan (ikon Roda Gigi), dan tempel URL tersebut.
 */

function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("Inspeksi_LSR");
    
    // Jika sheet belum ada, buat sheet baru
    if (!sheet) {
      sheet = ss.insertSheet("Inspeksi_LSR");
    }
    
    // Periksa dan buat Header jika sheet masih kosong
    if (sheet.getLastRow() === 0) {
      var headers = [
        "Waktu Input",
        "Timestamp (ms)",
        "Nama Inspektur",
        "Area / Lokasi",
        "Skor Kepatuhan (%)",
        "Detail Jawaban (JSON)",
        "Ringkasan Temuan / Catatan"
      ];
      sheet.appendRow(headers);
      
      // Format Header
      var headerRange = sheet.getRange(1, 1, 1, headers.length);
      headerRange.setBackground("#0052CC");
      headerRange.setFontColor("#FFFFFF");
      headerRange.setFontWeight("bold");
      headerRange.setHorizontalAlignment("center");
      sheet.setFrozenRows(1);
    }
    
    // Parse data JSON dari body request Android
    var contents = e.postData.contents;
    var data = JSON.parse(contents);
    
    var inspectorName = data.inspectorName || "Tanpa Nama";
    var area = data.area || "Tanpa Area";
    var timestampMs = data.timestamp || new Date().getTime();
    var score = (data.score !== undefined) ? data.score : 0;
    var answers = data.answers || [];
    
    // Waktu keterbacaan manusia (WIB/Lokal)
    var formattedDate = Utilities.formatDate(new Date(timestampMs), "Asia/Jakarta", "yyyy-MM-dd HH:mm:ss");
    
    // Format ringkasan catatan/temuan
    var notesSummary = [];
    for (var i = 0; i < answers.length; i++) {
      var item = answers[i];
      if (item.notes && item.notes.trim() !== "") {
        notesSummary.push("[" + item.id + " - " + item.answer + "]: " + item.notes.trim());
      } else if (item.answer === "NO") {
        notesSummary.push("[" + item.id + " - NO]: (Tanpa Catatan)");
      }
    }
    var notesText = notesSummary.length > 0 ? notesSummary.join("\n") : "Tidak ada catatan ketidaksesuaian.";
    
    // Baris data yang akan disimpan
    var rowData = [
      formattedDate,
      timestampMs,
      inspectorName,
      area,
      score + "%",
      JSON.stringify(answers),
      notesText
    ];
    
    sheet.appendRow(rowData);
    
    // Auto fit kolom agar rapi
    sheet.autoResizeColumns(1, 5);
    
    return ContentService.createTextOutput(JSON.stringify({
      "status": "success",
      "message": "Data inspeksi LSR berhasil disimpan ke Google Sheets!",
      "timestamp": formattedDate
    })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({
      "status": "error",
      "message": error.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  return ContentService.createTextOutput(JSON.stringify({
    "status": "online",
    "service": "HSE LSR Inspection Webhook",
    "message": "Send a POST request with inspection payload to submit data."
  })).setMimeType(ContentService.MimeType.JSON);
}
