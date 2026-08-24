package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppLanguage
import com.example.data.GoogleAppsScriptSnippet
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.SafetyBlue
import com.example.ui.theme.SafetyTeal

@Composable
fun GasGuideDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onCodeCopied: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SafetyBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = SafetyBlue)
                            }
                        }
                        Column {
                            Text(
                                text = if (language == AppLanguage.ID) "Panduan Google Apps Script" else "Google Apps Script Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (language == AppLanguage.ID) "Sinkronisasi Sheets & Findings Otomatis" else "Auto-sync Sheets & Findings",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (language == AppLanguage.ID) {
                            "Langkah-langkah Menghubungkan ke Google Sheets:"
                        } else {
                            "Steps to Connect to Google Sheets:"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val steps = if (language == AppLanguage.ID) listOf(
                        "1. Buka spreadsheet baru di Google Sheets (sheets.new).",
                        "2. Klik menu Ekstensi (Extensions) > Apps Script.",
                        "3. Hapus seluruh isi Code.gs, lalu klik tombol 'Salin Kode Code.gs' di bawah dan tempelkan (paste).",
                        "4. Klik tombol Simpan (ikon disket), lalu klik 'Terapkan' (Deploy) > 'Penerapan Baru' (New Deployment).",
                        "5. Pilih jenis 'Aplikasi Web' (Web App), set 'Jalankan sebagai: Saya' dan 'Akses: Siapa Saja (Anyone)'.",
                        "6. Klik 'Terapkan', berikan izin (Authorize), lalu salin URL Web App yang berakhiran /exec.",
                        "7. Tempelkan URL tersebut di menu Profil & Cloud pada aplikasi ini."
                    ) else listOf(
                        "1. Create a new spreadsheet at sheets.new.",
                        "2. Click Extensions > Apps Script.",
                        "3. Replace existing Code.gs content with the script copied below.",
                        "4. Save and click 'Deploy' > 'New Deployment'.",
                        "5. Select type 'Web App', set 'Execute as: Me', and 'Who has access: Anyone'.",
                        "6. Click Deploy, Authorize access, and copy the Web App URL ending in /exec.",
                        "7. Paste the URL into the Profile & Cloud tab in this app."
                    )

                    steps.forEach { stepText ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = stepText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Source Code Code.gs (v2.0 with Findings Tracker):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Code snippet box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkNavySurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = GoogleAppsScriptSnippet.CODE_GS,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF80D8FF)
                            ),
                            modifier = Modifier
                                .padding(12.dp)
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom copy button
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Google Apps Script Code", GoogleAppsScriptSnippet.CODE_GS)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                        onCodeCopied()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (copied) SafetyTeal else SafetyBlue)
                ) {
                    Icon(
                        if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (copied) {
                            if (language == AppLanguage.ID) "Kode Code.gs Berhasil Disalin!" else "Code.gs Copied to Clipboard!"
                        } else {
                            if (language == AppLanguage.ID) "Salin Kode Code.gs Lengkap" else "Copy Full Code.gs Script"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
