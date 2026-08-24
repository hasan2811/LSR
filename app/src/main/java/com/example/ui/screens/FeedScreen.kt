package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    language: AppLanguage,
    historyRecords: List<InspectionRecord>,
    checklistItems: List<LsrItem>,
    notifications: List<AppNotification>,
    unreadCount: Int,
    onRecordClicked: (InspectionRecord) -> Unit,
    onMarkNotificationRead: (Int) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onClearNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Notifications & Alerts, 1: Audit Records
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
                    text = if (language == AppLanguage.ID) "Notifikasi alur kerja, log audit, dan riwayat inspeksi" else "Workflow notifications, audit logs & inspection history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (historyRecords.isNotEmpty() && activeSubTab == 1) {
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
            } else if (activeSubTab == 0 && notifications.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onMarkAllNotificationsRead) {
                        Text("Tandai Baca", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sub-Tab Switcher (Pemberitahuan vs Riwayat Audit)
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Notifikasi & Alert", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (unreadCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = DangerRed,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            )

            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.HistoryEdu, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("${AppStrings.get("tab_feed", language)} ($totalAudits)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeSubTab == 0) {
            // TAB 0: Notifications & Alerts Feed
            if (notifications.isEmpty()) {
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SafetyBlue.copy(alpha = 0.15f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = SafetyBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Belum Ada Notifikasi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pemberitahuan penugasan PIC, pengajuan tindakan koreksi, dan verifikasi rating akan muncul di sini secara real-time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        NotificationCard(
                            notification = notif,
                            onMarkRead = { onMarkNotificationRead(notif.id) }
                        )
                    }
                }
            }
        } else {
            // TAB 1: Inspection Records Feed
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
                                color = SafetyBlue.copy(alpha = 0.15f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.ContentPasteSearch,
                                        contentDescription = null,
                                        tint = SafetyBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Text(
                                text = AppStrings.get("no_records_title", language),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = AppStrings.get("no_records_desc", language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Summary Stat Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Total: $totalAudits Inspeksi",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Rata-rata: $averageScore% Kepatuhan",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (averageScore >= 85) SafetyTeal else if (averageScore >= 60) AmberGold else DangerRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(historyRecords, key = { it.id }) { record ->
                        InspectionRecordCard(
                            language = language,
                            record = record,
                            onClick = { onRecordClicked(record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: AppNotification,
    onMarkRead: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(notification.timestamp) { dateFormat.format(Date(notification.timestamp)) }

    val (badgeColor, iconVector) = when (notification.type) {
        "PIC_ASSIGNMENT" -> Pair(SafetyBlue, Icons.Default.PersonAdd)
        "ACTION_SUBMITTED" -> Pair(AmberGold, Icons.Default.Build)
        "VERIFICATION_CLOSED" -> Pair(SafetyTeal, Icons.Default.Verified)
        "REOPENED" -> Pair(CriticalOrange, Icons.Default.Replay)
        else -> Pair(DangerRed, Icons.Default.Notifications)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface else badgeColor.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, if (notification.isRead) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else badgeColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMarkRead() }
            .testTag("notification_item_${notification.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(iconVector, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontWeight = if (!notification.isRead) FontWeight.Black else FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = notification.message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                if (notification.recipientEmail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ditujukan kepada: ${notification.recipientEmail}",
                        fontSize = 10.sp,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
fun InspectionRecordCard(
    language: AppLanguage,
    record: InspectionRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    val scoreColor = when {
        record.score >= 85 -> SafetyTeal
        record.score >= 60 -> AmberGold
        else -> DangerRed
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("inspection_record_card_${record.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Score Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = scoreColor.copy(alpha = 0.12f),
                border = BorderStroke(1.5.dp, scoreColor),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${record.score}%",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = scoreColor
                        )
                        Text(
                            text = "SKOR",
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
                            color = scoreColor
                        )
                    }
                }
            }

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.area,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Inspektur: ${record.inspectorName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Detail",
                tint = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
