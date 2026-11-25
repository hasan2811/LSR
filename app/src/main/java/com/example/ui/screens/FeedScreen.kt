package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.PagedFeedUiState
import com.example.ui.components.ActiveFilterSummaryStrip
import com.example.ui.components.ActivityFilterBottomSheet
import com.example.ui.components.SleekSearchFilterBar
import com.example.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    language: AppLanguage,
    feedSummaries: List<InspectionSummary>,
    totalAuditsCount: Int,
    notifications: List<AppNotification>,
    totalNotificationsCount: Int,
    unreadCount: Int,
    pendingSyncCount: Int,
    isRefreshing: Boolean,
    currentInspectorName: String,
    filterCriteria: ActivityFilterCriteria = ActivityFilterCriteria(),
    onFilterCriteriaChange: (ActivityFilterCriteria) -> Unit = {},
    availableUsers: List<String> = emptyList(),
    availableLsrTypes: List<String> = emptyList(),
    availableCompanies: List<String> = emptyList(),
    availableLocations: List<String> = emptyList(),
    onRecordClicked: (Int) -> Unit,
    onLoadMoreFeed: () -> Unit,
    onLoadMoreNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onRetrySync: () -> Unit,
    onExportCsv: () -> Unit,
    onMarkNotificationRead: (Int) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onDeleteReadNotifications: () -> Unit,
    isCloudSyncing: Boolean = false,
    lastCloudSyncAt: Long = 0L,
    pagedFeedState: PagedFeedUiState = PagedFeedUiState(),
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Notifications & Alerts, 1: Audit Summaries
    var searchQuery by remember { mutableStateOf("") }
    var notifFilter by remember { mutableStateOf("ALL") } // ALL, UNREAD, ASSIGNMENT, CRITICAL, CLOSED, REMINDER
    var auditFilter by remember { mutableStateOf("ALL") } // ALL, LOW_SCORE, UNSYNCED, MINE
    var showFilterSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isId = language == AppLanguage.ID

    val averageScore = remember(feedSummaries) {
        if (feedSummaries.isNotEmpty()) feedSummaries.map { it.score }.average().toInt() else 0
    }

    val filteredNotifications = remember(notifications, searchQuery, notifFilter, filterCriteria) {
        val q = searchQuery.trim()
        notifications.filter { n ->
            val matchesFilter = when (notifFilter) {
                "UNREAD" -> !n.isRead
                "ASSIGNMENT" -> n.type == "ASSIGNMENT" || n.type == "PIC_ASSIGNMENT"
                "CRITICAL" -> n.type == "CRITICAL" || n.type == "REOPENED"
                "REMINDER" -> n.type == "REMINDER" || n.type == "CHAT_MESSAGE"
                "CLOSED" -> n.type == "CLOSED" || n.type == "VERIFICATION_CLOSED"
                else -> true
            }
            val matchesQuery = q.isBlank() ||
                n.title.contains(q, ignoreCase = true) ||
                n.message.contains(q, ignoreCase = true) ||
                n.ruleId.contains(q, ignoreCase = true) ||
                n.recipientName.contains(q, ignoreCase = true) ||
                n.senderName.contains(q, ignoreCase = true)
            val matchesCriteria = filterCriteria.matches(
                timestamp = n.timestamp,
                users = listOf(n.recipientName, n.senderName),
                lsrTypes = "${n.ruleId} ${n.title} ${n.message}",
                itemCompany = n.message,
                itemLocation = n.message
            )
            matchesFilter && matchesQuery && matchesCriteria
        }
    }

    val filteredAudits = remember(feedSummaries, searchQuery, auditFilter, currentInspectorName, filterCriteria) {
        val q = searchQuery.trim()
        feedSummaries.filter { s ->
            val matchesFilter = when (auditFilter) {
                "LOW_SCORE" -> s.score < 80
                "UNSYNCED" -> !s.isSynced
                "MINE" -> currentInspectorName.isNotBlank() && s.inspectorName.equals(currentInspectorName, ignoreCase = true)
                else -> true
            }
            val matchesQuery = q.isBlank() ||
                s.area.contains(q, ignoreCase = true) ||
                s.inspectorName.contains(q, ignoreCase = true) ||
                s.company.contains(q, ignoreCase = true) ||
                s.lsrCategories.contains(q, ignoreCase = true) ||
                s.id.toString().contains(q)
            val matchesCriteria = filterCriteria.matches(
                timestamp = s.timestamp,
                users = listOf(s.inspectorName),
                lsrTypes = s.lsrCategories,
                itemCompany = s.company,
                itemLocation = s.area
            )
            matchesFilter && matchesQuery && matchesCriteria
        }
    }

    val resolvedUsers = remember(availableUsers, feedSummaries, notifications) {
        (availableUsers + feedSummaries.map { it.inspectorName } + notifications.flatMap { listOf(it.recipientName, it.senderName) })
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val resolvedCompanies = remember(availableCompanies, feedSummaries) {
        (availableCompanies + feedSummaries.map { it.company })
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val resolvedLocations = remember(availableLocations, feedSummaries) {
        (availableLocations + feedSummaries.map { it.area })
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val groupedNotifs = remember(filteredNotifications, language) {
        groupByTimeBucket(filteredNotifications, language) { it.timestamp }
    }
    val groupedAudits = remember(filteredAudits, language) {
        groupByTimeBucket(filteredAudits, language) { it.timestamp }
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

            if (activeSubTab == 1 && feedSummaries.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalIconButton(onClick = onExportCsv) {
                        Icon(
                            imageVector = Icons.Default.TableView,
                            contentDescription = AppStrings.get("export_csv_btn", language),
                            tint = SafetyTeal
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            val sb = StringBuilder()
                            sb.append("📋 *REKAPITULASI AUDIT K3 & LSR (LIFE-SAVING RULES)*\n")
                            sb.append("Total Inspeksi Terlaksana: $totalAuditsCount\n")
                            sb.append("Rata-rata Skor Kepatuhan: $averageScore%\n\n")
                            feedSummaries.take(15).forEachIndexed { idx, r ->
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
            } else if (activeSubTab == 0 && notifications.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (unreadCount > 0) {
                        TextButton(onClick = onMarkAllNotificationsRead) {
                            Text(
                                text = if (isId) "Tandai Dibaca" else "Mark Read",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (notifications.any { it.isRead }) {
                        IconButton(onClick = onDeleteReadNotifications) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = AppStrings.get("feed_clear_read", language),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Sleek Non-Blocking Instant Cache & Live Sync Strip
        val syncActive = isCloudSyncing || isRefreshing
        val syncAnim = rememberInfiniteTransition(label = "feedSync")
        val syncAngle by syncAnim.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(950, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "feedSyncAngle"
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (syncActive) SafetyBlue.copy(alpha = 0.1f) else SafetyTeal.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, if (syncActive) SafetyBlue.copy(alpha = 0.35f) else SafetyTeal.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRefresh() }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (syncActive) Icons.Default.Sync else Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (syncActive) SafetyBlue else SafetyTeal,
                            modifier = Modifier
                                .size(14.dp)
                                .then(if (syncActive) Modifier.rotate(syncAngle) else Modifier)
                        )
                        Text(
                            text = if (syncActive) {
                                AppStrings.get("sync_state_syncing", language)
                            } else {
                                "${AppStrings.get("sync_state_instant", language)} • ${AppStrings.formatRelativeSync(lastCloudSyncAt, language)}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (syncActive) SafetyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (isId) "Sync" else "Sync",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SafetyBlue
                    )
                }
                AnimatedVisibility(visible = syncActive) {
                    LinearProgressIndicator(
                        color = SafetyBlue,
                        trackColor = SafetyBlue.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        }

        // Pending Offline Sync Queue Banner
        if (pendingSyncCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AmberGold.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRetrySync() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${AppStrings.get("feed_pending_sync", language)}: $pendingSyncCount item",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = onRetrySync,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = AppStrings.get("feed_sync_now", language),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SafetyBlue
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sub-Tab Switcher
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
                        Text(
                            text = if (isId) "Notifikasi & Alert" else "Notifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        Text("${AppStrings.get("tab_feed", language)} ($totalAuditsCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sleek High-Contrast Search + Filter Button
        SleekSearchFilterBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = AppStrings.get("feed_search_placeholder", language),
            activeFilterCount = filterCriteria.activeCount,
            onOpenFilterSheet = { showFilterSheet = true }
        )

        if (filterCriteria.activeCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            ActiveFilterSummaryStrip(
                language = language,
                criteria = filterCriteria,
                onUpdate = onFilterCriteriaChange,
                onResetAll = { onFilterCriteriaChange(ActivityFilterCriteria()) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Contextual Filter Chips per Sub-Tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeSubTab == 0) {
                FilterChip(
                    selected = notifFilter == "ALL",
                    onClick = { notifFilter = "ALL" },
                    label = { Text(AppStrings.get("filter_all", language), fontSize = 11.sp) }
                )
                FilterChip(
                    selected = notifFilter == "UNREAD",
                    onClick = { notifFilter = "UNREAD" },
                    label = { Text(if (isId) "Belum Dibaca ($unreadCount)" else "Unread ($unreadCount)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = notifFilter == "ASSIGNMENT",
                    onClick = { notifFilter = "ASSIGNMENT" },
                    label = { Text(if (isId) "Penugasan PIC" else "PIC Tasks", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = notifFilter == "REMINDER",
                    onClick = { notifFilter = "REMINDER" },
                    label = { Text(if (isId) "Pesan & Reminder" else "Chat & Reminders", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = notifFilter == "CRITICAL",
                    onClick = { notifFilter = "CRITICAL" },
                    label = { Text(if (isId) "Kritis / Ulang" else "Critical / Reopen", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = notifFilter == "CLOSED",
                    onClick = { notifFilter = "CLOSED" },
                    label = { Text(if (isId) "Verifikasi Selesai" else "Verified", fontSize = 11.sp) }
                )
            } else {
                FilterChip(
                    selected = auditFilter == "ALL",
                    onClick = { auditFilter = "ALL" },
                    label = { Text(AppStrings.get("filter_all", language), fontSize = 11.sp) }
                )
                FilterChip(
                    selected = auditFilter == "LOW_SCORE",
                    onClick = { auditFilter = "LOW_SCORE" },
                    label = { Text(if (isId) "Skor < 80%" else "Score < 80%", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = auditFilter == "UNSYNCED",
                    onClick = { auditFilter = "UNSYNCED" },
                    label = { Text(if (isId) "Antrean Sync" else "Unsynced", fontSize = 11.sp) }
                )
                if (currentInspectorName.isNotBlank()) {
                    FilterChip(
                        selected = auditFilter == "MINE",
                        onClick = { auditFilter = "MINE" },
                        label = { Text(if (isId) "Audit Saya" else "My Audits", fontSize = 11.sp) }
                    )
                }
            }
        }

        if (showFilterSheet) {
            ActivityFilterBottomSheet(
                language = language,
                currentCriteria = filterCriteria,
                availableUsers = resolvedUsers,
                availableLsrTypes = availableLsrTypes,
                availableCompanies = resolvedCompanies,
                availableLocations = resolvedLocations,
                onApply = onFilterCriteriaChange,
                onDismiss = { showFilterSheet = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pull-to-Refresh Wrapper around Feed Lists
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (activeSubTab == 0) {
                if (filteredNotifications.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = SafetyBlue.copy(alpha = 0.14f),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = SafetyBlue,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = AppStrings.get("no_notifications", language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = AppStrings.get("no_notifications_desc", language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    val notifListState = rememberLazyListState()
                    val hasMoreNotifs = !pagedFeedState.notifEndReached && notifications.size < totalNotificationsCount
                    LaunchedEffect(
                        notifListState,
                        notifications.size,
                        pagedFeedState.isNotifAppending,
                        hasMoreNotifs
                    ) {
                        snapshotFlow { notifListState.layoutInfo }
                            .map { info ->
                                val total = info.totalItemsCount
                                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                total > 0 && lastVisible >= ceil(total * 0.75f).toInt()
                            }
                            .distinctUntilChanged()
                            .filter { it && hasMoreNotifs && !pagedFeedState.isNotifAppending }
                            .collect { onLoadMoreNotifications() }
                    }

                    LazyColumn(
                        state = notifListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Smart Notification Grouping Summary Banner when unread volume is high
                        if (unreadCount >= 3 && notifFilter == "ALL") {
                            item(key = "notif_batch_banner") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SafetyBlue.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, SafetyBlue.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isId) "Ringkasan Cepat: $unreadCount alert baru" else "Quick Digest: $unreadCount unread alerts",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SafetyBlue
                                            )
                                            Text(
                                                text = if (isId) "Tandai semua dibaca untuk merapikan feed" else "Mark all read to declutter your feed",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = onMarkAllNotificationsRead,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(if (isId) "Tandai Dibaca" else "Mark Read", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        groupedNotifs.forEach { (sectionLabel, itemsInBucket) ->
                            item(key = "notif_header_$sectionLabel") {
                                Text(
                                    text = sectionLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.7.sp,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                            items(itemsInBucket, key = { "notif_${it.id}" }) { notif ->
                                NotificationCard(
                                    notification = notif,
                                    onMarkRead = { onMarkNotificationRead(notif.id) }
                                )
                            }
                        }

                        if (pagedFeedState.isNotifAppending) {
                            item(key = "notif_append_spinner") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = AppStrings.get("paging_loading_more", language),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (pagedFeedState.notifAppendError != null) {
                            item(key = "notif_append_error") {
                                OutlinedButton(
                                    onClick = onLoadMoreNotifications,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${AppStrings.get("paging_retry_btn", language)}: ${pagedFeedState.notifAppendError}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (!hasMoreNotifs && notifications.isNotEmpty()) {
                            item(key = "notif_end_of_list") {
                                Text(
                                    text = "${AppStrings.get("paging_end_notifications", language)} (${notifications.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // TAB 1: Lightweight Windowed Inspection Summaries
                if (filteredAudits.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = SafetyBlue.copy(alpha = 0.14f),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.ContentPasteSearch,
                                            contentDescription = null,
                                            tint = SafetyBlue,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = AppStrings.get("no_records_title", language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
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
                    val auditListState = rememberLazyListState()
                    val hasMoreAudits = !pagedFeedState.feedEndReached && feedSummaries.size < totalAuditsCount
                    LaunchedEffect(
                        auditListState,
                        feedSummaries.size,
                        pagedFeedState.isFeedAppending,
                        hasMoreAudits
                    ) {
                        snapshotFlow { auditListState.layoutInfo }
                            .map { info ->
                                val total = info.totalItemsCount
                                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                total > 0 && lastVisible >= ceil(total * 0.75f).toInt()
                            }
                            .distinctUntilChanged()
                            .filter { it && hasMoreAudits && !pagedFeedState.isFeedAppending }
                            .collect { onLoadMoreFeed() }
                    }

                    LazyColumn(
                        state = auditListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item(key = "audit_stat_bar") {
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
                                            text = if (isId) "Total: $totalAuditsCount Inspeksi" else "Total: $totalAuditsCount Audits",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = if (isId) "Rata-rata: $averageScore% Kepatuhan" else "Avg: $averageScore% Compliance",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (averageScore >= 85) SafetyTeal else if (averageScore >= 60) AmberGold else DangerRed
                                    )
                                }
                            }
                        }

                        groupedAudits.forEach { (sectionLabel, itemsInBucket) ->
                            item(key = "audit_header_$sectionLabel") {
                                Text(
                                    text = sectionLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.7.sp,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                            items(itemsInBucket, key = { "audit_${it.id}" }) { summary ->
                                InspectionSummaryCard(
                                    language = language,
                                    summary = summary,
                                    onClick = { onRecordClicked(summary.id) }
                                )
                            }
                        }

                        if (pagedFeedState.isFeedAppending) {
                            item(key = "audits_append_spinner") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = AppStrings.get("paging_loading_more", language),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (pagedFeedState.feedAppendError != null) {
                            item(key = "audits_append_error") {
                                OutlinedButton(
                                    onClick = onLoadMoreFeed,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${AppStrings.get("paging_retry_btn", language)}: ${pagedFeedState.feedAppendError}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (!hasMoreAudits && feedSummaries.isNotEmpty()) {
                            item(key = "audits_end_of_list") {
                                Text(
                                    text = "${AppStrings.get("paging_end_feed", language)} (${feedSummaries.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun <T> groupByTimeBucket(
    items: List<T>,
    language: AppLanguage,
    timestampSelector: (T) -> Long
): List<Pair<String, List<T>>> {
    val now = Calendar.getInstance()
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfWeek = startOfToday - (6L * 86_400_000L)
    val todayLabel = AppStrings.get("feed_today", language)
    val weekLabel = AppStrings.get("feed_this_week", language)
    val olderLabel = AppStrings.get("feed_older", language)

    val buckets = linkedMapOf<String, MutableList<T>>()
    items.forEach { item ->
        val ts = timestampSelector(item)
        val label = when {
            ts >= startOfToday && ts <= now.timeInMillis + 86_400_000L -> todayLabel
            ts >= startOfWeek -> weekLabel
            else -> olderLabel
        }
        buckets.getOrPut(label) { mutableListOf() }.add(item)
    }
    return buckets.entries.map { it.key to it.value }
}

@Composable
fun NotificationCard(
    notification: AppNotification,
    onMarkRead: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(notification.timestamp) { dateFormat.format(Date(notification.timestamp)) }

    val (badgeColor, iconVector) = when (notification.type) {
        "ASSIGNMENT", "PIC_ASSIGNMENT" -> Pair(SafetyBlue, Icons.Default.PersonAdd)
        "ACTION_SUBMITTED" -> Pair(AmberGold, Icons.Default.Build)
        "CLOSED", "VERIFICATION_CLOSED" -> Pair(SafetyTeal, Icons.Default.Verified)
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
fun InspectionSummaryCard(
    language: AppLanguage,
    summary: InspectionSummary,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(summary.timestamp) { dateFormat.format(Date(summary.timestamp)) }

    val scoreColor = when {
        summary.score >= 85 -> SafetyTeal
        summary.score >= 60 -> AmberGold
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
            .testTag("inspection_record_card_${summary.id}")
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
                            text = "${summary.score}%",
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = summary.area,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${AppStrings.get("audit_by", language)}: ${summary.inspectorName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (summary.isSynced) SafetyTeal.copy(alpha = 0.12f) else AmberGold.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (summary.isSynced) AppStrings.get("synced", language) else AppStrings.get("local_only", language),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.isSynced) SafetyTeal else AmberGold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Detail",
                tint = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
