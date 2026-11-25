package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppNavTab
import com.example.ui.components.HseBrandBadge
import com.example.ui.components.HseBrandLogo
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    language: AppLanguage,
    checklistItems: List<LsrItem>,
    inspectorName: String,
    area: String,
    activeAnswers: Map<String, AnswerData>,
    historyRecords: List<InspectionRecord>,
    dashboardMetrics: DashboardMetrics,
    pendingSyncCount: Int,
    dashboardPerspective: DashboardPerspective,
    onPerspectiveChanged: (DashboardPerspective) -> Unit,
    findings: List<FindingItem>,
    currentUser: UserProfile?,
    activeProject: FirestoreProject? = null,
    onOpenProjectDialog: () -> Unit = {},
    selectedCategories: Set<String>,
    availableCategories: List<String>,
    onToggleCategory: (String) -> Unit,
    onSelectAllCategories: () -> Unit,
    onOpenAuthDialog: () -> Unit,
    onOpenProfileSettings: () -> Unit,
    onInspectorDetailsChanged: (name: String, loc: String) -> Unit,
    onStartClicked: () -> Unit,
    onResetDraftClicked: () -> Unit,
    onNavigateToTab: (AppNavTab) -> Unit,
    isCloudSyncing: Boolean = false,
    lastCloudSyncAt: Long = 0L,
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputName by remember(inspectorName) { mutableStateOf(inspectorName) }
    var inputArea by remember(area) { mutableStateOf(area) }

    val answeredCount = remember(activeAnswers) {
        activeAnswers.values.count { it.answer != AnswerType.UNANSWERED }
    }
    val hasActiveDraft = answeredCount > 0

    val userEmail = currentUser?.email ?: ""
    val scopedFindings = remember(findings, dashboardPerspective, userEmail, inspectorName) {
        when (dashboardPerspective) {
            DashboardPerspective.ALL -> findings
            DashboardPerspective.PIC_TASKS -> findings.filter { f ->
                !f.status.equals("CLOSED", ignoreCase = true) && (
                    (userEmail.isNotBlank() && f.assignedPicEmail.equals(userEmail, ignoreCase = true)) ||
                    (inspectorName.isNotBlank() && f.assignedPicName.equals(inspectorName, ignoreCase = true))
                )
            }
            DashboardPerspective.INSPECTOR_VERIFY -> findings.filter { f ->
                f.status.equals("ACTION_SUBMITTED", ignoreCase = true) ||
                    f.status.equals("IN_REVIEW", ignoreCase = true)
            }
            DashboardPerspective.HIGH_RISK -> findings.filter { f ->
                !f.status.equals("CLOSED", ignoreCase = true) && (f.isCritical || f.isOverdue())
            }
        }
    }

    val openFindingsCount = remember(scopedFindings) {
        scopedFindings.count { !it.status.equals("CLOSED", ignoreCase = true) }
    }
    val criticalRiskCount = remember(scopedFindings) {
        scopedFindings.count { (it.isCritical || it.isOverdue()) && !it.status.equals("CLOSED", ignoreCase = true) }
    }
    val overdueCount = remember(findings) {
        findings.count { it.isOverdue() }
    }
    val avgScore = if (dashboardMetrics.totalAudits > 0) dashboardMetrics.avgScore else 100
    val totalAuditsCount = if (dashboardMetrics.totalAudits > 0) dashboardMetrics.totalAudits else historyRecords.size

    val topRecurringCategories = remember(findings) {
        findings.groupBy { it.category }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(3)
    }

    val categories = remember(checklistItems) {
        checklistItems.groupBy { it.getLocalizedCategory(language) }
    }

    // Pulse animation for live beacon & smooth rotation for background sync
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncRotation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Top Bar / Header (Clean, zero overlap)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HseBrandLogo(size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.get("app_title", language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = AppStrings.get("app_subtitle", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Single Compact Account Pill (Opens Profile Settings or Auth Dialog)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (currentUser != null) SafetyTeal.copy(alpha = 0.12f) else SafetyBlue.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (currentUser != null) SafetyTeal.copy(alpha = 0.35f) else SafetyBlue.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .widthIn(max = 136.dp)
                    .clickable {
                        if (currentUser != null) onOpenProfileSettings() else onOpenAuthDialog()
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = if (currentUser != null) Icons.Default.AccountCircle else Icons.Default.Login,
                        contentDescription = "Account",
                        tint = if (currentUser != null) SafetyTeal else SafetyBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = currentUser?.displayName?.ifBlank { currentUser.email.substringBefore("@") }
                            ?: if (language == AppLanguage.ID) "Masuk" else "Sign In",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentUser != null) SafetyTeal else SafetyBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Live System Beacon & Instant Sync Pill
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isCloudSyncing) SafetyBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(
                1.dp,
                if (isCloudSyncing) SafetyBlue.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSyncNow() }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isCloudSyncing) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = SafetyBlue,
                                modifier = Modifier
                                    .size(14.dp)
                                    .rotate(syncRotation)
                            )
                            Text(
                                text = AppStrings.get("sync_state_syncing", language),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SafetyBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(SafetyTeal.copy(alpha = beaconAlpha))
                            )
                            Text(
                                text = "${if (language == AppLanguage.ID) "CACHE INSTAN" else "INSTANT CACHE"} • ${AppStrings.formatRelativeSync(lastCloudSyncAt, language)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pendingSyncCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AmberGold.copy(alpha = 0.18f),
                                modifier = Modifier.clickable { onNavigateToTab(AppNavTab.FEED) }
                            ) {
                                Text(
                                    text = "⏳ $pendingSyncCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberGold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${checklistItems.size} RULES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = SafetyBlue
                        )
                    }
                }
                AnimatedVisibility(visible = isCloudSyncing) {
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

        // Active Firestore Project Workspace Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (activeProject != null) SafetyBlue.copy(alpha = 0.08f) else AmberGold.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, if (activeProject != null) SafetyBlue.copy(alpha = 0.35f) else AmberGold.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenProjectDialog() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (activeProject != null) SafetyBlue else AmberGold,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (language == AppLanguage.ID) "Workspace Proyek Firestore" else "Firestore Project Workspace",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = activeProject?.name ?: (if (language == AppLanguage.ID) "Pilih Proyek..." else "Select Project..."),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeProject != null) {
                            Text(
                                text = if (language == AppLanguage.ID)
                                    "👥 ${activeProject.memberUids.size} Personil • 📍 ${activeProject.customLocations.size} Lokasi"
                                else
                                    "👥 ${activeProject.memberUids.size} Personnel • 📍 ${activeProject.customLocations.size} Locations",
                                fontSize = 10.sp,
                                color = SafetyTeal
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = onOpenProjectDialog,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SafetyBlue.copy(alpha = 0.15f),
                        contentColor = SafetyBlue
                    )
                ) {
                    Text(
                        text = if (language == AppLanguage.ID) "Ganti" else "Switch",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Executive Hero Banner Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
            elevation = CardDefaults.cardElevation(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E293B),
                                DarkNavySurface
                            ),
                            radius = 600f
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SafetyBlue.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, SafetyBlue.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "🛡️ LIFE-SAVING RULES AUDIT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF93C5FD),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        HseBrandBadge()
                    }

                    Text(
                        text = AppStrings.get("hero_title", language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 26.sp
                    )

                    Text(
                        text = AppStrings.get("hero_desc", language),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 18.sp
                    )

                    // Quick Action Jump Buttons inside Hero
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onNavigateToTab(AppNavTab.FINDINGS) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (openFindingsCount > 0) DangerRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                                contentColor = if (openFindingsCount > 0) Color(0xFFFF8A80) else Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${AppStrings.get("tab_findings", language)} ($openFindingsCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = { onNavigateToTab(AppNavTab.FEED) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${AppStrings.get("tab_feed", language)} ($totalAuditsCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Role-Based Perspective Switcher
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(DashboardPerspective.entries) { perspective ->
                val isSelected = perspective == dashboardPerspective
                FilterChip(
                    selected = isSelected,
                    onClick = { onPerspectiveChanged(perspective) },
                    label = {
                        Text(
                            text = if (language == AppLanguage.ID) perspective.titleId else perspective.titleEn,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SafetyBlue.copy(alpha = 0.16f),
                        selectedLabelColor = SafetyBlue
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // 4 KPI Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardKpiCard(
                title = AppStrings.get("kpi_total_audits", language),
                value = "$totalAuditsCount",
                icon = Icons.Default.Assessment,
                color = SafetyBlue,
                onClick = { onNavigateToTab(AppNavTab.FEED) },
                modifier = Modifier.weight(1f)
            )

            DashboardKpiCard(
                title = AppStrings.get("kpi_avg_score", language),
                value = if (totalAuditsCount > 0) "$avgScore%" else "100%",
                icon = Icons.Default.VerifiedUser,
                color = if (avgScore >= 90) SafetyTeal else AmberGold,
                onClick = { onNavigateToTab(AppNavTab.FEED) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardKpiCard(
                title = AppStrings.get("kpi_open_findings", language),
                value = "$openFindingsCount",
                icon = Icons.Default.Warning,
                color = if (openFindingsCount > 0) DangerRed else SafetyTeal,
                onClick = { onNavigateToTab(AppNavTab.FINDINGS) },
                modifier = Modifier.weight(1f)
            )

            DashboardKpiCard(
                title = if (overdueCount > 0) AppStrings.get("sla_overdue", language) else AppStrings.get("kpi_critical_alerts", language),
                value = if (overdueCount > 0) "$overdueCount" else "$criticalRiskCount",
                icon = Icons.Default.Shield,
                color = if (overdueCount > 0 || criticalRiskCount > 0) CriticalOrange else SafetyTeal,
                onClick = { onNavigateToTab(AppNavTab.FINDINGS) },
                modifier = Modifier.weight(1f)
            )
        }

        // Enterprise Trend & Top Recurring Violations Card (shown when data exists)
        if (historyRecords.isNotEmpty() || topRecurringCategories.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == AppLanguage.ID) "Tren Kepatuhan & Top Deviasi LSR" else "Compliance Trend & Top LSR Deviations",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (language == AppLanguage.ID) "${historyRecords.take(7).size} Audit Terakhir" else "Last ${historyRecords.take(7).size} Audits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (historyRecords.isNotEmpty()) {
                        val recent = historyRecords.take(7).reversed()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            recent.forEach { rec ->
                                val barFraction = (rec.score / 100f).coerceIn(0.15f, 1f)
                                val barColor = if (rec.score >= 85) SafetyTeal else if (rec.score >= 60) AmberGold else DangerRed
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text(
                                        text = "${rec.score}%",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = barColor
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((30 * barFraction).dp)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(barColor.copy(alpha = 0.85f))
                                    )
                                }
                            }
                        }
                    }

                    if (topRecurringCategories.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        topRecurringCategories.forEach { (cat, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${LsrChecklist.getCategoryIcon(cat)} $cat",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DangerRed.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "$count ${if (language == AppLanguage.ID) "temuan" else "findings"}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DangerRed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Inspector & Area Setup Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SafetyBlue.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = AppStrings.get("inspector_card_title", language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedTextField(
                    value = inputName,
                    onValueChange = {
                        inputName = it
                        onInspectorDetailsChanged(it, inputArea)
                    },
                    label = { Text(AppStrings.get("inspector_name_label", language)) },
                    placeholder = { Text(AppStrings.get("inspector_name_placeholder", language)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = SafetyBlue) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = inputArea,
                    onValueChange = {
                        inputArea = it
                        onInspectorDetailsChanged(inputName, it)
                    },
                    label = { Text(AppStrings.get("area_label", language)) },
                    placeholder = { Text(AppStrings.get("area_placeholder", language)) },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = SafetyBlue) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Dynamic LSR Module Scope Selector for Location
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = SafetyTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (language == AppLanguage.ID) "Cakupan Modul LSR di Area Ini" else "Applicable LSR Scope in Area",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val isAllSelected = selectedCategories.isEmpty() || selectedCategories.size == availableCategories.size
                            TextButton(
                                onClick = onSelectAllCategories,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isAllSelected) (if (language == AppLanguage.ID) "Semua Aktif" else "All Active") else (if (language == AppLanguage.ID) "Pilih Semua" else "Select All"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAllSelected) SafetyTeal else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = if (language == AppLanguage.ID)
                                "Pilih aktivitas kerja yang relevan di lokasi ini. Checklist akan disesuaikan otomatis agar tidak perlu mengisi modul yang tidak ada."
                            else
                                "Choose relevant high-risk activities occurring in this area. Unrelated modules will be excluded from the audit.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Module Selection Chips (Horizontally Scrollable or Flow)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableCategories) { category ->
                                val isSelected = selectedCategories.isEmpty() || selectedCategories.contains(category)
                                val count = checklistItems.count { it.category == category }
                                val icon = LsrChecklist.getCategoryIcon(category)
                                
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onToggleCategory(category) },
                                    label = {
                                        Text(
                                            text = "$icon $category ($count)",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SafetyTeal.copy(alpha = 0.2f),
                                        selectedLabelColor = SafetyTeal,
                                        selectedLeadingIconColor = SafetyTeal
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        // Status badge of active scope
                        val activeCount = if (selectedCategories.isEmpty()) availableCategories.size else selectedCategories.size
                        val activeItemCount = if (selectedCategories.isEmpty()) checklistItems.size else checklistItems.count { selectedCategories.contains(it.category) }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SafetyBlue.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎯 ${if (language == AppLanguage.ID) "Modul Aktif:" else "Active Modules:"} $activeCount / ${availableCategories.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SafetyBlue
                                )
                                Text(
                                    text = "$activeItemCount ${if (language == AppLanguage.ID) "Pertanyaan" else "Rules"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Active draft banner if available
                if (hasActiveDraft) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AmberGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "📝 ${AppStrings.get("active_draft_alert", language)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$answeredCount / ${checklistItems.size} ${AppStrings.get("draft_status", language)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(onClick = onResetDraftClicked) {
                                Text(
                                    text = AppStrings.get("clear_draft", language),
                                    color = DangerRed,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // Primary Start Inspection Button
                Button(
                    onClick = onStartClicked,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasActiveDraft) AmberGold else SafetyBlue
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        if (hasActiveDraft) Icons.Default.PlayArrow else Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasActiveDraft) {
                            AppStrings.get("resume_inspection", language)
                        } else {
                            AppStrings.get("start_inspection", language)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hasActiveDraft) DarkNavySurface else Color.White
                    )
                }
            }
        }

        // LSR Modules & Standards Breakdown Summary
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.get("summary_title", language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (language == AppLanguage.ID) "${categories.size} Kategori" else "${categories.size} Categories",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories.entries.toList(), key = { it.key }) { entry ->
                    val catName = entry.key
                    val catItems = entry.value
                    val criticalCount = catItems.count { it.is_critical }
                    val catIcon = getCategoryEmoji(catName)

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.width(210.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SafetyBlue.copy(alpha = 0.12f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = catIcon, fontSize = 14.sp)
                                    }
                                }
                                Text(
                                    text = catName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = "${catItems.size} ${AppStrings.get("total_rules", language).lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (criticalCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CriticalOrange.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "⚠️ $criticalCount Kritis",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CriticalOrange,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryEmoji(category: String): String {
    val lower = category.lowercase()
    return when {
        lower.contains("isolasi") || lower.contains("isolation") || lower.contains("energy") -> "⚡"
        lower.contains("ruang terbatas") || lower.contains("confined") -> "🕳️"
        lower.contains("pengangkatan") || lower.contains("lifting") -> "🏗️"
        lower.contains("panas") || lower.contains("hot work") -> "🔥"
        lower.contains("mengemudi") || lower.contains("driving") || lower.contains("kendaraan") -> "🚗"
        lower.contains("ketinggian") || lower.contains("height") -> "🧗"
        lower.contains("galian") || lower.contains("excavation") -> "⛏️"
        lower.contains("zat") || lower.contains("chemical") || lower.contains("line break") -> "🧪"
        lower.contains("bypass") || lower.contains("safety critical") -> "⚙️"
        else -> "🛡️"
    }
}

@Composable
fun DashboardKpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.12f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}
