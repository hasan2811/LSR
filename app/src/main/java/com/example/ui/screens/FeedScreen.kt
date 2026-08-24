package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLanguage
import com.example.data.AppStrings
import com.example.data.InspectionRecord
import com.example.data.LsrItem
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FeedScreen(
    language: AppLanguage,
    historyRecords: List<InspectionRecord>,
    checklistItems: List<LsrItem>,
    onRecordClicked: (InspectionRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalAudits = historyRecords.size
    val averageScore = remember(historyRecords) {
        if (historyRecords.isNotEmpty()) {
            historyRecords.map { it.score }.average().toInt()
        } else 0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Feed Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppStrings.get("feed_title", language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get("feed_subtitle", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (historyRecords.isNotEmpty()) {
                FilledTonalIconButton(
                    onClick = {
                        val sb = StringBuilder()
                        sb.append("📋 *REKAPITULASI AUDIT K3 & LSR (LIFE-SAVING RULES)*\n")
                        sb.append("Total Inspeksi Terlaksana: $totalAudits\n")
                        sb.append("Rata-rata Skor Kepatuhan: $averageScore%\n\n")
                        historyRecords.take(15).forEachIndexed { idx, r ->
                            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(r.timestamp))
                            sb.append("${idx + 1}. [${r.score}%] ${r.area}\n")
                            sb.append("   Inspektur: ${r.inspectorName} | Waktu: $dateStr\n\n")
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, sb.toString())
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Bagikan Rekapitulasi Audit"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Audit History",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Feed stats card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalAudits",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = AppStrings.get("total_records_count", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (totalAudits > 0) "$averageScore%" else "-",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (averageScore >= 90) SafetyTeal else if (averageScore >= 75) AmberGold else DangerRed
                    )
                    Text(
                        text = AppStrings.get("kpi_avg_score", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (historyRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SafetyBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = SafetyBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = AppStrings.get("no_history_title", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.get("no_history_desc", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyRecords, key = { it.id }) { record ->
                    InspectionFeedCard(
                        language = language,
                        record = record,
                        onClick = { onRecordClicked(record) }
                    )
                }
            }
        }
    }
}

@Composable
fun InspectionFeedCard(
    language: AppLanguage,
    record: InspectionRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }
    val formattedDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    val scoreColor = when {
        record.score >= 90 -> SafetyTeal
        record.score >= 75 -> AmberGold
        else -> DangerRed
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Area & Sync status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SafetyBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = record.area,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Sync badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (record.isSynced) SafetyTeal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (record.isSynced) SafetyTeal.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (record.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (record.isSynced) SafetyTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (record.isSynced) AppStrings.get("synced", language) else AppStrings.get("local_only", language),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (record.isSynced) SafetyTeal else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Body: Inspector details & Score Circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${AppStrings.get("audit_by", language)}: ${record.inspectorName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Score Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scoreColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, scoreColor.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${record.score}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor
                        )
                        Text(
                            text = AppStrings.get("score_label", language),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = scoreColor
                        )
                    }
                }
            }

            // Click prompt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${AppStrings.get("view_full_report", language)} →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
