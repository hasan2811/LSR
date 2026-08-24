package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.AppLanguage
import com.example.data.AppStrings
import com.example.data.FindingItem
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingsScreen(
    language: AppLanguage,
    findings: List<FindingItem>,
    onUpdateStatus: (id: Int, status: String, correctiveAction: String) -> Unit,
    onDeleteFinding: (id: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "OPEN", "IN_PROGRESS", "CLOSED"
    var selectedFindingForEdit by remember { mutableStateOf<FindingItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredFindings = remember(findings, selectedFilter, searchQuery) {
        findings.filter { finding ->
            val matchesFilter = when (selectedFilter) {
                "OPEN" -> finding.status.equals("OPEN", ignoreCase = true)
                "IN_PROGRESS" -> finding.status.equals("IN_PROGRESS", ignoreCase = true)
                "CLOSED" -> finding.status.equals("CLOSED", ignoreCase = true)
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                finding.itemText.contains(searchQuery, ignoreCase = true) ||
                        finding.category.contains(searchQuery, ignoreCase = true) ||
                        finding.ruleId.contains(searchQuery, ignoreCase = true) ||
                        finding.area.contains(searchQuery, ignoreCase = true) ||
                        finding.inspectorName.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    val openCount = remember(findings) { findings.count { it.status.equals("OPEN", ignoreCase = true) } }
    val inProgressCount = remember(findings) { findings.count { it.status.equals("IN_PROGRESS", ignoreCase = true) } }
    val closedCount = remember(findings) { findings.count { it.status.equals("CLOSED", ignoreCase = true) } }
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
                            sb.append("   Isu: ${f.itemText}\n")
                            if (f.correctiveAction.isNotBlank()) {
                                sb.append("   Tindakan Koreksi: ${f.correctiveAction}\n")
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FindingKpiBadge(
                label = AppStrings.get("filter_open", language),
                count = openCount,
                color = DangerRed,
                modifier = Modifier.weight(1f)
            )
            FindingKpiBadge(
                label = AppStrings.get("filter_in_progress", language),
                count = inProgressCount,
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
                label = "Kritis",
                count = criticalCount,
                color = CriticalOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chips (Horizontally scrollable for all screen widths)
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
                selected = selectedFilter == "IN_PROGRESS",
                onClick = { selectedFilter = "IN_PROGRESS" },
                label = { Text("In Progress ($inProgressCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberGold.copy(alpha = 0.15f))
            )
            FilterChip(
                selected = selectedFilter == "CLOSED",
                onClick = { selectedFilter = "CLOSED" },
                label = { Text("Closed ($closedCount)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SafetyTeal.copy(alpha = 0.15f))
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
                    FindingCard(
                        language = language,
                        finding = finding,
                        onEditClicked = { selectedFindingForEdit = finding },
                        onDeleteClicked = { onDeleteFinding(finding.id) }
                    )
                }
            }
        }
    }

    // Edit Corrective Action & Status Dialog
    selectedFindingForEdit?.let { finding ->
        FindingDetailEditDialog(
            language = language,
            finding = finding,
            onDismiss = { selectedFindingForEdit = null },
            onSave = { newStatus, newAction ->
                onUpdateStatus(finding.id, newStatus, newAction)
                selectedFindingForEdit = null
            }
        )
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
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
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
fun FindingCard(
    language: AppLanguage,
    finding: FindingItem,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(finding.timestamp) { dateFormat.format(Date(finding.timestamp)) }

    val statusColor = when (finding.status.uppercase()) {
        "CLOSED" -> SafetyTeal
        "IN_PROGRESS" -> AmberGold
        else -> DangerRed
    }

    val statusLabel = when (finding.status.uppercase()) {
        "CLOSED" -> if (language == AppLanguage.ID) "Selesai (Closed)" else "Closed"
        "IN_PROGRESS" -> if (language == AppLanguage.ID) "Dalam Proses" else "In Progress"
        else -> if (language == AppLanguage.ID) "Terbuka (Open)" else "Open"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Rule ID + Severity & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = finding.ruleId,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (finding.isCritical) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CriticalOrange.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CriticalOrange.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "KRITIS / FATAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = CriticalOrange,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Category & Question Text
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = finding.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = finding.itemText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Inspector Observation
            if (finding.notes.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DangerRed.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = if (language == AppLanguage.ID) "Catatan Temuan Inspektur:" else "Inspector Observation:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = DangerRed
                            )
                            Text(
                                text = finding.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Corrective Action if available
            if (finding.correctiveAction.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SafetyTeal.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, SafetyTeal.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = SafetyTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = AppStrings.get("corrective_action_label", language),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SafetyTeal
                            )
                            Text(
                                text = finding.correctiveAction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Footer info & Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📍 ${finding.area} • 👤 ${finding.inspectorName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onEditClicked,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.ID) "Aksi & Status" else "Action & Status",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingDetailEditDialog(
    language: AppLanguage,
    finding: FindingItem,
    onDismiss: () -> Unit,
    onSave: (newStatus: String, newAction: String) -> Unit
) {
    var status by remember { mutableStateOf(finding.status) }
    var actionNotes by remember { mutableStateOf(finding.correctiveAction) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = AppStrings.get("finding_detail_title", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${finding.ruleId} • ${finding.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                HorizontalDivider()

                // Finding text recap
                Text(
                    text = finding.itemText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status selector
                Text(
                    text = AppStrings.get("status_label", language),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("OPEN", "IN_PROGRESS", "CLOSED").forEach { option ->
                        val isSelected = status.equals(option, ignoreCase = true)
                        val color = when (option) {
                            "OPEN" -> DangerRed
                            "IN_PROGRESS" -> AmberGold
                            else -> SafetyTeal
                        }
                        val label = when (option) {
                            "OPEN" -> "Open"
                            "IN_PROGRESS" -> "In Progress"
                            else -> "Closed"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { status = option },
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.2f),
                                selectedLabelColor = color
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Corrective action input field
                OutlinedTextField(
                    value = actionNotes,
                    onValueChange = { actionNotes = it },
                    label = { Text(AppStrings.get("corrective_action_label", language)) },
                    placeholder = { Text(AppStrings.get("corrective_action_placeholder", language)) },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Save button
                Button(
                    onClick = { onSave(status, actionNotes) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get("update_status_btn", language),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
