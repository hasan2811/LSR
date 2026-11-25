package com.example.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private const val PAGE_WIDTH = 595 // A4 points at 72 DPI
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun exportAuditPdf(
        context: Context,
        record: InspectionRecord,
        checklistItems: List<LsrItem>,
        language: AppLanguage
    ): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "HSE_LSR_Audit_${record.id}.pdf")

        val pdf = PdfDocument()
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subHeaderPaint = Paint().apply {
            color = Color.rgb(147, 197, 253)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerBannerPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            style = Paint.Style.FILL
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10.5f
            isAntiAlias = true
        }
        val dangerPaint = Paint().apply {
            color = Color.rgb(185, 28, 28)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        val parsedAnswers = parseAnswers(record.answersJson)
        val evaluated = if (parsedAnswers.isNotEmpty()) {
            checklistItems.filter { parsedAnswers.containsKey(it.id) }
        } else {
            checklistItems
        }

        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(record.timestamp))
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas

        // Top executive banner
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 92f, headerBannerPaint)
        canvas.drawText("HSSE LIFE-SAVING RULES AUDIT REPORT", MARGIN, 38f, titlePaint)
        canvas.drawText("IOGP Report 459 & OSHA 1910/1926 Compliance Record #${record.id}", MARGIN, 60f, subHeaderPaint)
        canvas.drawText("Generated: $dateStr", MARGIN, 78f, subHeaderPaint)

        var y = 120f
        canvas.drawText(if (language == AppLanguage.ID) "Ringkasan Inspeksi" else "Inspection Summary", MARGIN, y, sectionPaint)
        y += 20f
        canvas.drawText("${if (language == AppLanguage.ID) "Lokasi / Area" else "Area"}: ${record.area}", MARGIN, y, bodyPaint)
        y += 16f
        canvas.drawText("${if (language == AppLanguage.ID) "Inspektur" else "Inspector"}: ${record.inspectorName}", MARGIN, y, bodyPaint)
        y += 16f
        val nonCompliantCount = evaluated.count { parsedAnswers[it.id]?.first == "NO" }
        canvas.drawText(
            "${if (language == AppLanguage.ID) "Skor Kepatuhan" else "Compliance Score"}: ${record.score}%  |  ${if (language == AppLanguage.ID) "Total Aturan" else "Evaluated"}: ${evaluated.size}  |  ${if (language == AppLanguage.ID) "Temuan" else "Findings"}: $nonCompliantCount",
            MARGIN,
            y,
            if (record.score < 80) dangerPaint else sectionPaint
        )
        y += 16f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 22f

        canvas.drawText(
            if (language == AppLanguage.ID) "Rincian Evaluasi Checklist" else "Checklist Evaluation Details",
            MARGIN,
            y,
            sectionPaint
        )
        y += 18f

        evaluated.forEach { item ->
            val ans = parsedAnswers[item.id]
            val status = ans?.first ?: "UNANSWERED"
            val note = ans?.second ?: ""

            if (y > PAGE_HEIGHT - 80f) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 48f
            }

            val badgeText = "[${item.id}] [$status] ${item.getLocalizedCategory(language)}"
            canvas.drawText(badgeText, MARGIN, y, if (status == "NO") dangerPaint else sectionPaint.apply { textSize = 10.5f })
            y += 14f

            val wrapped = wrapText(item.getLocalizedText(language), 82)
            wrapped.forEach { line ->
                if (y > PAGE_HEIGHT - 60f) {
                    pdf.finishPage(page)
                    pageNumber++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    canvas = page.canvas
                    y = 48f
                }
                canvas.drawText(line, MARGIN + 8f, y, bodyPaint)
                y += 13f
            }

            if (note.isNotBlank()) {
                val noteLines = wrapText("Note: $note", 78)
                noteLines.forEach { nLine ->
                    canvas.drawText(nLine, MARGIN + 8f, y, if (status == "NO") dangerPaint else bodyPaint)
                    y += 13f
                }
            }
            y += 8f
        }

        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportBatchCsv(
        context: Context,
        summaries: List<InspectionSummary>,
        findings: List<FindingItem>
    ): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "HSE_LSR_Batch_Export_${System.currentTimeMillis()}.csv")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val sb = StringBuilder()
        sb.appendLine("TYPE,ID,TIMESTAMP,AREA,INSPECTOR,SCORE_OR_SEVERITY,STATUS_OR_SYNC,RULE_ID,PIC,DETAILS")

        summaries.forEach { s ->
            sb.appendLine(
                listOf(
                    "AUDIT",
                    s.id.toString(),
                    dateFormat.format(Date(s.timestamp)),
                    csvEscape(s.area),
                    csvEscape(s.inspectorName),
                    "${s.score}%",
                    if (s.isSynced) "SYNCED" else "PENDING_LOCAL",
                    "-",
                    "-",
                    "LSR Compliance Audit"
                ).joinToString(",")
            )
        }

        findings.forEach { f ->
            val slaState = if (f.isOverdue()) "OVERDUE" else f.status
            sb.appendLine(
                listOf(
                    "FINDING",
                    f.id.toString(),
                    dateFormat.format(Date(f.timestamp)),
                    csvEscape(f.area),
                    csvEscape(f.inspectorName),
                    if (f.isCritical) "CRITICAL" else "HIGH",
                    slaState,
                    csvEscape(f.ruleId),
                    csvEscape(f.assignedPicName),
                    csvEscape(f.itemText)
                ).joinToString(",")
            )
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun parseAnswers(answersJson: String): Map<String, Pair<String, String>> {
        val map = mutableMapOf<String, Pair<String, String>>()
        runCatching {
            val root = JSONObject(answersJson)
            val answersObj = if (root.has("answers")) root.optJSONObject("answers") ?: JSONObject() else root
            val keys = answersObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "applicableModules") continue
                val obj = answersObj.optJSONObject(key) ?: continue
                map[key] = obj.optString("answer", "UNANSWERED") to obj.optString("notes", "")
            }
        }
        return map
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.length + word.length + 1 > maxChars) {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    internal fun csvEscape(value: String): String {
        val clean = value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")
        val firstChar = clean.trimStart().firstOrNull()
        val safe = if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || clean.startsWith("\t")) {
            "'$clean"
        } else {
            clean
        }
        return "\"$safe\""
    }
}
