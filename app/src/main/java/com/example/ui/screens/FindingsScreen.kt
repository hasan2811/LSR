package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingsScreen(
    language: AppLanguage,
    findings: List<FindingItem>,
    teamRoster: List<TeamRosterMember>,
    onGetActionLogs: (findingId: Int) -> Flow<List<ActionHistoryLog>>,
    onAssignPic: (findingId: Int, name: String, email: String, role: String) -> Unit,
    onSubmitPicAction: (findingId: Int, action: String, evidence: String) -> Unit,
    onVerifyAndClose: (findingId: Int, rating: Int, comment: String) -> Unit,
    onReopenFinding: (findingId: Int, reason: String) -> Unit,
    onUpdateStatus: (id: Int, status: String, correctiveAction: String) -> Unit,
    onDeleteFinding: (id: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "OPEN", "IN_REVIEW", "CLOSED", "REOPENED"
    var selectedFindingForDetail by remember { mutableStateOf<FindingItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // If detail is active, find updated finding from list
    val currentActiveFinding = remember(selectedFindingForDetail, findings) {
        if (selectedFindingForDetail == null) null
        else findings.find { it.id == selectedFindingForDetail!!.id } ?: selectedFindingForDetail
    }

    if (currentActiveFinding != null) {
        FindingDetailScreen(
            language = language,
            finding = currentActiveFinding,
            teamRoster = teamRoster,
            actionLogsFlow = onGetActionLogs(currentActiveFinding.id),
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
            onDeleteFinding = {
                onDeleteFinding(currentActiveFinding.id)
                selectedFindingForDetail = null
            }
        )
        return
    }

    val filteredFindings = remember(findings, selectedFilter, searchQuery) {
        findings.filter { finding ->
            val statusNorm = finding.status.uppercase()
            val matchesFilter = when (selectedFilter) {
                "OPEN" -> statusNorm == "OPEN"
                "IN_REVIEW" -> statusNorm == "ACTION_SUBMITTED" || statusNorm == "IN_REVIEW" || statusNorm == "IN_PROGRESS"
                "CLOSED" -> statusNorm == "CLOSED"
                "REOPENED" -> statusNorm == "REOPENED"
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                finding.itemText.contains(searchQuery, ignoreCase = true) ||
                        finding.category.contains(searchQuery, ignoreCase = true) ||
                        finding.ruleId.contains(searchQuery, ignoreCase = true) ||
                        finding.area.contains(searchQuery, ignoreCase = true) ||
                        finding.inspectorName.contains(searchQuery, ignoreCase = true) ||
                        finding.assignedPicName.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
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
    val criticalCount = remember(findings) { findings.count { it.isCritical && !it.status.equals("CLOSED", ignoreCase = true) } }

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

        Spacer(modifier = Modifier.height(14.dp))

        // Mini KPI row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FindingKpiBadge(
                label = AppStrings.get("filter_open", language),
                count = openCount,
                color = DangerRed,
                modifier = Modifier.weight(1f)
            )
            FindingKpiBadge(
                label = "In Review",
                count = inReviewCount,
                color = AmberGold,
                modifier = Modifier.weight(1f)
            )
            FindingKpiBadge(
                label = AppStrings.get("filter_resolved", language),
                count = closedCount,
                color = SafetyTeal,
                modifier = Modifier.weight(1f)
            )
            FindingKpiBadge(
                label = "Reopen",
                count = reopenedCount,
                color = CriticalOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari temuan, PIC, area, atau aturan...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("${AppStrings.get("filter_all", language)} (${findings.size})") }
            )
            FilterChip(
                selected = selectedFilter == "OPEN",
                onClick = { selectedFilter = "OPEN" },
                label = { Text("Open ($openCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DangerRed.copy(alpha = 0.15f))
            )
            FilterChip(
                selected = selectedFilter == "IN_REVIEW",
                onClick = { selectedFilter = "IN_REVIEW" },
                label = { Text("In Review ($inReviewCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberGold.copy(alpha = 0.15f))
            )
            FilterChip(
                selected = selectedFilter == "CLOSED",
                onClick = { selectedFilter = "CLOSED" },
                label = { Text("Closed ($closedCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SafetyTeal.copy(alpha = 0.15f))
            )
            FilterChip(
                selected = selectedFilter == "REOPENED",
                onClick = { selectedFilter = "REOPENED" },
                label = { Text("Reopened ($reopenedCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CriticalOrange.copy(alpha = 0.15f))
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Findings List
        if (filteredFindings.isEmpty()) {
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
                            color = SafetyTeal.copy(alpha = 0.15f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SafetyTeal,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = AppStrings.get("no_findings_title", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = AppStrings.get("no_findings_desc", language),
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
                items(filteredFindings, key = { it.id }) { finding ->
                    EnterpriseFindingCard(
                        language = language,
                        finding = finding,
                        onClick = { selectedFindingForDetail = finding },
                        onDeleteClicked = { onDeleteFinding(finding.id) }
                    )
                }
            }
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
                        Text("Kelola & Rincian →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SafetyBlue)
                    }
                }
            }
        }
    }
}
