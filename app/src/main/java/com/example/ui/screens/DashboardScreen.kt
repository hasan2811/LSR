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
    findings: List<FindingItem>,
    currentUser: UserProfile?,
    activeProject: FirestoreProject? = null,
    onOpenProjectDialog: () -> Unit = {},
    selectedCategories: Set<String>,
    availableCategories: List<String>,
    onToggleCategory: (String) -> Unit,
    onSelectAllCategories: () -> Unit,
    onLanguageToggle: () -> Unit,
    onOpenAuthDialog: () -> Unit,
    onInspectorDetailsChanged: (name: String, loc: String) -> Unit,
    onStartClicked: () -> Unit,
    onResetDraftClicked: () -> Unit,
    onNavigateToTab: (AppNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputName by remember(inspectorName) { mutableStateOf(inspectorName) }
    var inputArea by remember(area) { mutableStateOf(area) }

    val answeredCount = remember(activeAnswers) {
        activeAnswers.values.count { it.answer != AnswerType.UNANSWERED }
    }
    val hasActiveDraft = answeredCount > 0

    val openFindingsCount = remember(findings) {
        findings.count { it.status.equals("OPEN", ignoreCase = true) }
    }
    val criticalRiskCount = remember(findings) {
        findings.count { it.isCritical && !it.status.equals("CLOSED", ignoreCase = true) }
    }
    val avgScore = remember(historyRecords) {
        if (historyRecords.isNotEmpty()) historyRecords.map { it.score }.average().toInt() else 100
    }

    val categories = remember(checklistItems) {
        checklistItems.groupBy { it.getLocalizedCategory(language) }
    }

    // Pulse animation for live beacon
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Top Bar / Header with Safety Radar Beacon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HseBrandLogo(size = 44.dp)
                Column {
                    Text(
                        text = AppStrings.get("app_title", language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = AppStrings.get("app_subtitle", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right action buttons (Language & Firebase Auth)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Firebase Auth Quick Access
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (currentUser != null) SafetyTeal.copy(alpha = 0.15f) else SafetyBlue.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (currentUser != null) SafetyTeal.copy(alpha = 0.4f) else SafetyBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable { onOpenAuthDialog() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (currentUser != null) Icons.Default.AccountCircle else Icons.Default.Login,
                            contentDescription = "Auth",
                            tint = if (currentUser != null) SafetyTeal else SafetyBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (currentUser != null) currentUser.displayName.take(8) else "Masuk",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (currentUser != null) SafetyTeal else SafetyBlue
                        )
                    }
                }

                // Language Switcher Chip
                FilledTonalButton(
                    onClick = onLanguageToggle,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${language.flag} ${language.code.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Live System Beacon Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(SafetyTeal.copy(alpha = beaconAlpha))
                    )
                    Text(
                        text = if (language == AppLanguage.ID) "SISTEM AUDIT AKTIF • IOGP & OSHA" else "AUDIT SYSTEM ACTIVE • IOGP & OSHA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "${checklistItems.size} RULES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = SafetyBlue
                )
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
                                text = "👥 ${activeProject.memberUids.size} Personil • 📍 ${activeProject.customLocations.size} Lokasi",
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
                                text = "${AppStrings.get("tab_feed", language)} (${historyRecords.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4 KPI Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardKpiCard(
                title = AppStrings.get("kpi_total_audits", language),
                value = "${historyRecords.size}",
                icon = Icons.Default.Assessment,
                color = SafetyBlue,
                onClick = { onNavigateToTab(AppNavTab.FEED) },
                modifier = Modifier.weight(1f)
            )

            DashboardKpiCard(
                title = AppStrings.get("kpi_avg_score", language),
                value = if (historyRecords.isNotEmpty()) "$avgScore%" else "100%",
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
                title = AppStrings.get("kpi_critical_alerts", language),
                value = "$criticalRiskCount",
                icon = Icons.Default.Shield,
                color = if (criticalRiskCount > 0) CriticalOrange else SafetyTeal,
                onClick = { onNavigateToTab(AppNavTab.FINDINGS) },
                modifier = Modifier.weight(1f)
            )
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
                    text = "${categories.size} Kategori",
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
