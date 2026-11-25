package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.example.data.*
import com.example.ui.PagedFindingsUiState
import com.example.ui.components.ActiveFilterSummaryStrip
import com.example.ui.components.ActivityFilterBottomSheet
import com.example.ui.components.SleekSearchFilterBar
import com.example.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingsScreen(
    language: AppLanguage,
    findings: List<FindingItem>,
    teamRoster: List<TeamRosterMember>,
    isRefreshing: Boolean = false,
    filterCriteria: ActivityFilterCriteria = ActivityFilterCriteria(),
    onFilterCriteriaChange: (ActivityFilterCriteria) -> Unit = {},
    availableCompanies: List<String> = emptyList(),
    availableLocations: List<String> = emptyList(),
    onRefresh: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onAttachEvidencePhoto: (findingId: Int, sourceUri: Uri) -> Unit = { _, _ -> },
    onSendFindingMessage: (findingId: Int, message: String) -> Unit = { _, _ -> },
    onSendReminderPing: (findingId: Int) -> Unit = {},
    onGetActionLogs: (findingId: Int) -> Flow<List<ActionHistoryLog>>,
    onAssignPic: (findingId: Int, name: String, email: String, role: String) -> Unit,
    onSubmitPicAction: (findingId: Int, action: String, evidence: String) -> Unit,
    onVerifyAndClose: (findingId: Int, rating: Int, comment: String) -> Unit,
    onReopenFinding: (findingId: Int, reason: String) -> Unit,
    onUpdateStatus: (id: Int, status: String, correctiveAction: String) -> Unit,
    onDeleteFinding: (id: Int) -> Unit,
    canManageFinding: (FindingItem) -> Boolean = { true },
    pagedState: PagedFindingsUiState? = null,
    onPagedSearchChanged: (String) -> Unit = {},
    onPagedFilterChanged: (String) -> Unit = {},
    onLoadNextPage: () -> Unit = {},
    onRetryAppend: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var localFilter by remember { mutableStateOf("ALL") } // "ALL", "OPEN", "IN_REVIEW", "CLOSED", "REOPENED", "OVERDUE"
    var localSearchQuery by remember { mutableStateOf("") }
    val selectedFilter = pagedState?.statusFilter ?: localFilter
    val searchQuery = pagedState?.searchQuery ?: localSearchQuery

    var selectedFindingForDetail by remember { mutableStateOf<FindingItem?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // If detail is active, find updated finding from paged cache or full list
    val currentActiveFinding = remember(selectedFindingForDetail, findings, pagedState?.items) {
        if (selectedFindingForDetail == null) null
        else pagedState?.items?.find { it.id == selectedFindingForDetail!!.id }
            ?: findings.find { it.id == selectedFindingForDetail!!.id }
            ?: selectedFindingForDetail
    }

    if (currentActiveFinding != null) {
        FindingDetailScreen(
            language = language,
            finding = currentActiveFinding,
            teamRoster = teamRoster,
            actionLogsFlow = onGetActionLogs(currentActiveFinding.id),
            canManageFinding = canManageFinding(currentActiveFinding),
            onBack = { selectedFindingForDetail = null },
            onAssignPic = { name, email, role ->
                onAssignPic(currentActiveFinding.id, name, email, role)
            },
            onSubmitAction = { action, evidence ->
                onSubmitPicAction(currentActiveFinding.id, action, evidence)
            },
            onVerifyAndClose = { rating, comment ->
                onVerifyAndClose(currentActiveFinding.id, rating, comment)
            },
            onReopen = { reason ->
                onReopenFinding(currentActiveFinding.id, reason)
            },
            onAttachEvidencePhoto = { uri ->
                onAttachEvidencePhoto(currentActiveFinding.id, uri)
            },
            onSendMessage = { msg ->
                onSendFindingMessage(currentActiveFinding.id, msg)
            },
            onSendReminder = {
                onSendReminderPing(currentActiveFinding.id)
            },
            onDeleteFinding = {
                onDeleteFinding(currentActiveFinding.id)
                selectedFindingForDetail = null
            }
        )
        return
    }

    val filteredFindings = remember(findings, selectedFilter, searchQuery, filterCriteria, pagedState?.items) {
        if (pagedState != null) {
            pagedState.items
        } else {
            findings.filter { finding ->
                val statusNorm = finding.status.uppercase()
                val matchesFilter = when (selectedFilter) {
                    "OPEN" -> statusNorm == "OPEN"
                    "IN_REVIEW" -> statusNorm == "ACTION_SUBMITTED" || statusNorm == "IN_REVIEW" || statusNorm == "IN_PROGRESS"
                    "CLOSED" -> statusNorm == "CLOSED"
                    "REOPENED" -> statusNorm == "REOPENED"
                    "OVERDUE" -> finding.isOverdue()
                    else -> true
                }
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    finding.itemText.contains(searchQuery, ignoreCase = true) ||
                        finding.category.contains(searchQuery, ignoreCase = true) ||
                        finding.ruleId.contains(searchQuery, ignoreCase = true) ||
                        finding.area.contains(searchQuery, ignoreCase = true) ||
                        finding.company.contains(searchQuery, ignoreCase = true) ||
                        finding.inspectorName.contains(searchQuery, ignoreCase = true) ||
                        finding.assignedPicName.contains(searchQuery, ignoreCase = true)
                }
                val matchesCriteria = filterCriteria.matches(
                    timestamp = finding.timestamp,
                    users = listOf(finding.inspectorName, finding.assignedPicName),
                    lsrTypes = "${finding.category} ${finding.ruleId}",
                    itemCompany = finding.company,
                    itemLocation = finding.area
                )
                matchesFilter && matchesSearch && matchesCriteria
            }
        }
    }

    val resolvedUsers = remember(teamRoster, findings) {
        (teamRoster.map { it.name } + findings.flatMap { listOf(it.inspectorName, it.assignedPicName) })
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "Unassigned / HSE Field Team" }
            .distinct()
            .sorted()
    }
    val resolvedLsrTypes = remember(findings) {
        findings.map { it.category }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val resolvedCompanies = remember(availableCompanies, findings, teamRoster) {
        (availableCompanies + findings.map { it.company } + teamRoster.map { it.department })
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val resolvedLocations = remember(availableLocations, findings) {
        (availableLocations + findings.map { it.area })
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val openCount = remember(findings) { findings.count { it.status.equals("OPEN", ignoreCase = true) } }
    val inReviewCount = remember(findings) {
        findings.count {
            val s = it.status.uppercase()
            s == "ACTION_SUBMITTED" || s == "IN_REVIEW" || s == "IN_PROGRESS"
        }
    }
    val closedCount = remember(findings) { findings.count { it.status.equals("CLOSED", ignoreCase = true) } }
    val reopenedCount = remember(findings) { findings.count { it.status.equals("REOPENED", ignoreCase = true) } }
    val overdueCount = remember(findings) { findings.count { it.isOverdue() } }

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppStrings.get("findings_title", language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get("findings_subtitle", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (findings.isNotEmpty()) {
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
                            val openItems = findings.filter { !it.status.equals("CLOSED", ignoreCase = true) }
                            val sb = StringBuilder()
                            sb.append("⚠️ *HSE NON-COMPLIANCE & FINDINGS ACTION REGISTER*\n")
                            sb.append("Total Temuan Aktif: ${openItems.size} item\n\n")
                            openItems.forEachIndexed { idx, f ->
                                sb.append("${idx + 1}. [${f.status.uppercase()}] ${f.ruleId} - ${f.category}\n")
                                sb.append("   Area: ${f.area} | Inspektur: ${f.inspectorName}\n")
                                sb.append("   PIC: ${f.assignedPicName} (${f.assignedPicRole})\n")
                                sb.append("   Isu: ${f.itemText}\n")
                                if (f.correctiveAction.isNotBlank()) {
                                    sb.append("   Tindakan Koreksi: ${f.correctiveAction}\n")
                                }
                                if (f.satisfactionRating > 0) {
                                    sb.append("   Rating: ${f.satisfactionRating}/5 ⭐\n")
                                }
                                sb.append("\n")
                            }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, sb.toString())
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Bagikan Register Temuan HSE"))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Findings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mini KPI row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FindingKpiBadge(
                label = if (language == AppLanguage.ID) "Terbuka" else "Open",
                count = openCount,
                color = DangerRed,
                modifier = Modifier.weight(1f)
            )
            FindingKpiBadge(
                label = if (language == AppLanguage.ID) "Ditinjau" else "In Review",
                count = inReviewCount,
                color = AmberGold,
                modifier = Modifier.weight(1f)
            )
            FindingKpiBadge(
                label = if (language == AppLanguage.ID) "Selesai" else "Closed",
                count = closedCount,
                color = SafetyTeal,
                modifier = Modifier.weight(1f)
            )
            FindingKpiBadge(
                label = if (language == AppLanguage.ID) "Ulang" else "Reopen",
                count = reopenedCount,
                color = CriticalOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sleek High-Contrast Search + Filter Button
        SleekSearchFilterBar(
            query = searchQuery,
            onQueryChange = { q ->
                if (pagedState != null) onPagedSearchChanged(q) else localSearchQuery = q
            },
            placeholder = if (language == AppLanguage.ID) "Cari temuan, PIC, area, atau aturan..." else "Search findings, PIC, area, or rule...",
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

        if (showFilterSheet) {
            ActivityFilterBottomSheet(
                language = language,
                currentCriteria = filterCriteria,
                availableUsers = resolvedUsers,
                availableLsrTypes = resolvedLsrTypes,
                availableCompanies = resolvedCompanies,
                availableLocations = resolvedLocations,
                onApply = onFilterCriteriaChange,
                onDismiss = { showFilterSheet = false }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips
        val selectStatus: (String) -> Unit = { statusKey ->
            if (pagedState != null) onPagedFilterChanged(statusKey) else localFilter = statusKey
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectStatus("ALL") },
                label = { Text("${AppStrings.get("filter_all", language)} (${findings.size})") }
            )
            FilterChip(
                selected = selectedFilter == "OPEN",
                onClick = { selectStatus("OPEN") },
                label = { Text(if (language == AppLanguage.ID) "Terbuka ($openCount)" else "Open ($openCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DangerRed.copy(alpha = 0.15f))
            )
            FilterChip(
                selected = selectedFilter == "IN_REVIEW",
                onClick = { selectStatus("IN_REVIEW") },
                label = { Text(if (language == AppLanguage.ID) "Ditinjau ($inReviewCount)" else "In Review ($inReviewCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberGold.copy(alpha = 0.15f))
            )
            FilterChip(
                selected = selectedFilter == "CLOSED",
                onClick = { selectStatus("CLOSED") },
                label = { Text(if (language == AppLanguage.ID) "Selesai ($closedCount)" else "Closed ($closedCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SafetyTeal.copy(alpha = 0.15f))
            )
            FilterChip(
                selected = selectedFilter == "REOPENED",
                onClick = { selectStatus("REOPENED") },
                label = { Text(if (language == AppLanguage.ID) "Dibuka Ulang ($reopenedCount)" else "Reopened ($reopenedCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CriticalOrange.copy(alpha = 0.15f))
            )
            FilterChip(
                selected = selectedFilter == "OVERDUE",
                onClick = { selectStatus("OVERDUE") },
                label = { Text("${AppStrings.get("sla_overdue", language)} ($overdueCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DangerRed.copy(alpha = 0.18f))
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val findingsListState = rememberLazyListState()
        val isInitialSkeleton = pagedState?.isInitialLoading == true && filteredFindings.isEmpty()

        // 75% Prefetching for Cursor-Based Pagination
        if (pagedState != null) {
            LaunchedEffect(
                findingsListState,
                filteredFindings.size,
                pagedState.isInitialLoading,
                pagedState.isAppending,
                pagedState.endReached,
                pagedState.appendError
            ) {
                snapshotFlow { findingsListState.layoutInfo }
                    .map { info ->
                        val total = info.totalItemsCount
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= ceil(total * 0.75f).toInt()
                    }
                    .distinctUntilChanged()
                    .filter { shouldPrefetch ->
                        shouldPrefetch &&
                            filteredFindings.isNotEmpty() &&
                            !pagedState.isInitialLoading &&
                            !pagedState.isAppending &&
                            !pagedState.endReached &&
                            pagedState.appendError == null
                    }
                    .collect { onLoadNextPage() }
            }
        }

        // Findings List with Pull-to-Refresh + 4 Paging States
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isInitialSkeleton) {
                // State 1: Initial Skeleton Loading
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(5, key = { "skeleton_$it" }) {
                        FindingSkeletonCard()
                    }
                }
            } else if (filteredFindings.isEmpty()) {
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
                                color = SafetyTeal.copy(alpha = 0.14f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SafetyTeal,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Text(
                                text = AppStrings.get("no_findings_title", language),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = AppStrings.get("no_findings_desc", language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = findingsListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(
                        items = filteredFindings,
                        key = { it.id },
                        contentType = { "finding_card" }
                    ) { finding ->
                        EnterpriseFindingCard(
                            language = language,
                            finding = finding,
                            onClick = { selectedFindingForDetail = finding },
                            onDeleteClicked = { onDeleteFinding(finding.id) }
                        )
                    }

                    // State 2: Append Loading
                    if (pagedState?.isAppending == true) {
                        item(key = "findings_append_loading", contentType = "footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.get("paging_loading_more", language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // State 3: Append Error + Retry Button
                    val appendErr = pagedState?.appendError
                    if (appendErr != null && pagedState.isAppending == false) {
                        item(key = "findings_append_error", contentType = "footer") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = DangerRed.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = appendErr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(onClick = onRetryAppend) {
                                        Text(
                                            text = AppStrings.get("paging_retry_btn", language),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // State 4: End of List
                    if (pagedState?.endReached == true && pagedState.isAppending == false) {
                        item(key = "findings_end_of_list", contentType = "footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${AppStrings.get("paging_end_findings", language)} (${filteredFindings.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingSkeletonCard() {
    val pulse = rememberInfiniteTransition(label = "findingSkeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "findingSkeletonAlpha"
    )
    val blockColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.width(78.dp).height(18.dp).background(blockColor, RoundedCornerShape(6.dp)))
                Box(modifier = Modifier.width(74.dp).height(18.dp).background(blockColor, RoundedCornerShape(6.dp)))
            }
            Box(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp).background(blockColor, RoundedCornerShape(6.dp)))
            Box(modifier = Modifier.fillMaxWidth().height(32.dp).background(blockColor, RoundedCornerShape(8.dp)))
            Box(modifier = Modifier.fillMaxWidth().height(24.dp).background(blockColor, RoundedCornerShape(8.dp)))
        }
    }
}

@Composable
fun FindingKpiBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EnterpriseFindingCard(
    language: AppLanguage,
    finding: FindingItem,
    onClick: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(finding.timestamp) { dateFormat.format(Date(finding.timestamp)) }

    val statusNormalized = finding.status.uppercase()
    val statusColor = when (statusNormalized) {
        "CLOSED" -> SafetyTeal
        "ACTION_SUBMITTED", "IN_REVIEW", "IN_PROGRESS" -> AmberGold
        "REOPENED" -> CriticalOrange
        else -> DangerRed
    }

    val statusLabel = when (statusNormalized) {
        "CLOSED" -> if (language == AppLanguage.ID) "Selesai (Closed)" else "Closed"
        "ACTION_SUBMITTED", "IN_REVIEW", "IN_PROGRESS" -> if (language == AppLanguage.ID) "Menunggu Review" else "In Review"
        "REOPENED" -> if (language == AppLanguage.ID) "Dibuka Kembali" else "Reopened"
        else -> if (language == AppLanguage.ID) "Terbuka (Open)" else "Open"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("finding_item_card_${finding.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Rule ID + Critical Badge + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = finding.ruleId,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (finding.isCritical) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CriticalOrange.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CriticalOrange.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "CRITICAL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = CriticalOrange,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (finding.isOverdue()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DangerRed.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = AppStrings.get("sla_overdue", language),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = DangerRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (!finding.status.equals("CLOSED", ignoreCase = true)) {
                        val remHours = finding.slaRemainingHours().coerceAtLeast(0L)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AmberGold.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${AppStrings.get("sla_remaining", language)}: ${remHours}j",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Category & Item Text
            Text(
                text = finding.category,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = finding.itemText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // PIC & Area Details
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (finding.assignedPicName.isNotBlank()) finding.assignedPicName else "Belum Ada PIC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                        Text(
                            text = finding.area,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Rating or Action summary
            if (finding.satisfactionRating > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (1..finding.satisfactionRating).forEach {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = " ${finding.satisfactionRating}/5 ⭐",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                    }
                    Text(
                        text = "Verifikasi: ${finding.verifiedBy.ifBlank { "Auditor" }}",
                        fontSize = 11.sp,
                        color = SafetyTeal,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (finding.correctiveAction.isNotBlank()) {
                Text(
                    text = "Aksi Diajukan: ${finding.correctiveAction}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = onClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (language == AppLanguage.ID) "Kelola & Rincian →" else "Manage & Details →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafetyBlue
                        )
                    }
                }
            }
        }
    }
}
