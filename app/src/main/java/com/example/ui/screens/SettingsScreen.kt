package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.*
import com.example.ui.SettingsSection
import com.example.ui.components.HseBrandBadge
import com.example.ui.components.HseBrandLogo
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    currentSection: SettingsSection,
    onSelectSection: (SettingsSection) -> Unit,
    language: AppLanguage,
    inspectorName: String,
    companyName: String,
    inspectorRole: String,
    inspectorCert: String,
    currentUser: UserProfile?,
    activeProject: FirestoreProject?,
    checklistItems: List<LsrItem>,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onSelectAllCategories: () -> Unit,
    scriptUrl: String,
    isGasEnabled: Boolean,
    firebaseUrl: String,
    isFirebaseEnabled: Boolean,
    notifPicAlerts: Boolean,
    notifCriticalAlerts: Boolean,
    notifDraftReminder: Boolean,
    hapticFeedbackEnabled: Boolean,
    pendingSyncCount: Int = 0,
    onRetryPendingSync: () -> Unit = {},
    isCloudSyncing: Boolean = false,
    lastCloudSyncAt: Long = 0L,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onToggleNotifPic: (Boolean) -> Unit,
    onToggleNotifCritical: (Boolean) -> Unit,
    onToggleNotifDraft: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onSaveProfile: (company: String, role: String, cert: String) -> Unit,
    onSaveCloudSettings: (scriptUrl: String, isGasEnabled: Boolean, firebaseUrl: String, isFirebaseEnabled: Boolean, publishGlobal: Boolean) -> Unit,
    isDatabaseAdminUnlocked: Boolean = false,
    onUnlockDatabaseAdmin: (String) -> Boolean = { false },
    onLockDatabaseAdmin: () -> Unit = {},
    onTestGas: () -> Unit,
    onTestFirebase: () -> Unit,
    onRefreshCloud: () -> Unit,
    onOpenProjectDialog: () -> Unit,
    onOpenGasGuide: () -> Unit,
    onOpenChecklistManager: () -> Unit,
    onOpenAuthDialog: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Intercept system back button when inside any Settings sub-page
    BackHandler(enabled = currentSection != SettingsSection.HUB) {
        onSelectSection(SettingsSection.HUB)
    }

    AnimatedContent(
        targetState = currentSection,
        transitionSpec = {
            if (targetState != SettingsSection.HUB) {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
            } else {
                slideInHorizontally { -it / 3 } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "SettingsSectionTransition",
        modifier = modifier.fillMaxSize()
    ) { section ->
        when (section) {
            SettingsSection.HUB -> SettingsHubContent(
                language = language,
                inspectorName = inspectorName,
                companyName = companyName,
                inspectorRole = inspectorRole,
                currentUser = currentUser,
                activeProject = activeProject,
                checklistItems = checklistItems,
                availableCategories = availableCategories,
                selectedCategories = selectedCategories,
                isGasEnabled = isGasEnabled,
                isFirebaseEnabled = isFirebaseEnabled,
                notifPicAlerts = notifPicAlerts,
                notifCriticalAlerts = notifCriticalAlerts,
                notifDraftReminder = notifDraftReminder,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onNavigate = onSelectSection,
                onOpenLanguageDialog = { showLanguageDialog = true }
            )

            SettingsSection.PROFILE -> SettingsSubPageScaffold(
                title = AppStrings.get("settings_profile_title", language),
                onBack = { onSelectSection(SettingsSection.HUB) }
            ) {
                ProfileAccountContent(
                    language = language,
                    inspectorName = inspectorName,
                    companyName = companyName,
                    inspectorRole = inspectorRole,
                    inspectorCert = inspectorCert,
                    currentUser = currentUser,
                    onSaveProfile = onSaveProfile,
                    onOpenAuthDialog = onOpenAuthDialog,
                    onSignOut = onSignOut
                )
            }

            SettingsSection.MODULES -> SettingsSubPageScaffold(
                title = AppStrings.get("settings_modules_title", language),
                onBack = { onSelectSection(SettingsSection.HUB) }
            ) {
                ModulesSettingsContent(
                    language = language,
                    checklistItems = checklistItems,
                    availableCategories = availableCategories,
                    selectedCategories = selectedCategories,
                    onToggleCategory = onToggleCategory,
                    onSelectAllCategories = onSelectAllCategories,
                    onOpenChecklistManager = onOpenChecklistManager
                )
            }

            SettingsSection.CLOUD -> SettingsSubPageScaffold(
                title = AppStrings.get("settings_cloud_title", language),
                onBack = { onSelectSection(SettingsSection.HUB) }
            ) {
                CloudSettingsContent(
                    language = language,
                    activeProject = activeProject,
                    pendingSyncCount = pendingSyncCount,
                    onRetryPendingSync = onRetryPendingSync,
                    isCloudSyncing = isCloudSyncing,
                    lastCloudSyncAt = lastCloudSyncAt,
                    scriptUrl = scriptUrl,
                    isGasEnabled = isGasEnabled,
                    firebaseUrl = firebaseUrl,
                    isFirebaseEnabled = isFirebaseEnabled,
                    isDatabaseAdminUnlocked = isDatabaseAdminUnlocked,
                    onUnlockDatabaseAdmin = onUnlockDatabaseAdmin,
                    onLockDatabaseAdmin = onLockDatabaseAdmin,
                    onOpenProjectDialog = onOpenProjectDialog,
                    onRefreshCloud = onRefreshCloud,
                    onSaveCloudSettings = onSaveCloudSettings,
                    onTestGas = onTestGas,
                    onTestFirebase = onTestFirebase,
                    onOpenGasGuide = onOpenGasGuide
                )
            }

            SettingsSection.NOTIFICATIONS -> SettingsSubPageScaffold(
                title = AppStrings.get("settings_notifications_title", language),
                onBack = { onSelectSection(SettingsSection.HUB) }
            ) {
                NotificationSettingsContent(
                    language = language,
                    notifPicAlerts = notifPicAlerts,
                    notifCriticalAlerts = notifCriticalAlerts,
                    notifDraftReminder = notifDraftReminder,
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    onToggleNotifPic = onToggleNotifPic,
                    onToggleNotifCritical = onToggleNotifCritical,
                    onToggleNotifDraft = onToggleNotifDraft,
                    onToggleHaptic = onToggleHaptic
                )
            }

            SettingsSection.SUPPORT -> SettingsSubPageScaffold(
                title = AppStrings.get("settings_support_title", language),
                onBack = { onSelectSection(SettingsSection.HUB) }
            ) {
                SupportSettingsContent(
                    language = language,
                    currentUser = currentUser,
                    activeProject = activeProject,
                    onOpenGasGuide = onOpenGasGuide
                )
            }

            SettingsSection.ABOUT -> SettingsSubPageScaffold(
                title = AppStrings.get("settings_about_title", language),
                onBack = { onSelectSection(SettingsSection.HUB) }
            ) {
                AboutSettingsContent(language = language)
            }
        }
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            currentLanguage = language,
            onSelect = {
                onLanguageChange(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun SettingsSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsHubContent(
    language: AppLanguage,
    inspectorName: String,
    companyName: String,
    inspectorRole: String,
    currentUser: UserProfile?,
    activeProject: FirestoreProject?,
    checklistItems: List<LsrItem>,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    isGasEnabled: Boolean,
    isFirebaseEnabled: Boolean,
    notifPicAlerts: Boolean,
    notifCriticalAlerts: Boolean,
    notifDraftReminder: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onOpenLanguageDialog: () -> Unit
) {
    val isId = language == AppLanguage.ID
    val activeModuleCount = if (selectedCategories.isEmpty()) availableCategories.size else selectedCategories.size
    val activeRuleCount = if (selectedCategories.isEmpty()) checklistItems.size else checklistItems.count { selectedCategories.contains(it.category) }
    val enabledNotifCount = listOf(notifPicAlerts, notifCriticalAlerts, notifDraftReminder).count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hub Header
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = AppStrings.get("settings_hub_title", language),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = AppStrings.get("settings_hub_subtitle", language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Top Profile & Account Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
            border = BorderStroke(1.dp, SafetyBlue.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(SettingsSection.PROFILE) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SafetyBlue,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val initials = (currentUser?.displayName ?: inspectorName)
                            .ifBlank { "IN" }
                            .take(2)
                            .uppercase()
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = currentUser?.displayName?.ifBlank { inspectorName.ifBlank { "Inspector HSE" } } ?: "Inspector HSE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${inspectorRole.ifBlank { "HSE Inspector" }} • ${companyName.ifBlank { "Operations" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (currentUser != null) SafetyTeal else AmberGold)
                        )
                        Text(
                            text = if (currentUser != null) currentUser.email else (if (isId) "Belum masuk akun" else "Not signed in"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentUser != null) SafetyTeal else AmberGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Section 1: Operasional & Inspeksi
        SettingsGroupHeader(title = AppStrings.get("settings_section_inspection", language))
        SettingsGroupCard {
            SettingsMenuItem(
                icon = Icons.Outlined.Tune,
                iconTint = SafetyTeal,
                title = AppStrings.get("settings_modules_title", language),
                subtitle = if (isId) "$activeModuleCount/${availableCategories.size} modul aktif • $activeRuleCount aturan" else "$activeModuleCount/${availableCategories.size} active modules • $activeRuleCount rules",
                badgeText = "$activeRuleCount",
                onClick = { onNavigate(SettingsSection.MODULES) }
            )
            SettingsDivider()
            val cloudBadge = when {
                isFirebaseEnabled && isGasEnabled -> "Firebase + GAS"
                isFirebaseEnabled -> "Firebase"
                isGasEnabled -> "GAS"
                else -> if (isId) "Lokal" else "Local"
            }
            SettingsMenuItem(
                icon = Icons.Outlined.CloudSync,
                iconTint = SafetyBlue,
                title = AppStrings.get("settings_cloud_title", language),
                subtitle = activeProject?.name ?: (if (isId) "Atur workspace, Firebase & Google Sheets" else "Configure workspace, Firebase & Google Sheets"),
                badgeText = cloudBadge,
                onClick = { onNavigate(SettingsSection.CLOUD) }
            )
        }

        // Section 2: Preferensi & Notifikasi
        SettingsGroupHeader(title = AppStrings.get("settings_section_preferences", language))
        SettingsGroupCard {
            SettingsMenuItem(
                icon = Icons.Outlined.Language,
                iconTint = SafetyBlue,
                title = AppStrings.get("settings_language_title", language),
                subtitle = "${language.flag} ${language.label}",
                badgeText = language.code.uppercase(),
                onClick = onOpenLanguageDialog
            )
            SettingsDivider()
            SettingsMenuItem(
                icon = Icons.Outlined.Notifications,
                iconTint = AmberGold,
                title = AppStrings.get("settings_notifications_title", language),
                subtitle = if (isId) "$enabledNotifCount dari 3 pengingat aktif" else "$enabledNotifCount of 3 alerts active",
                onClick = { onNavigate(SettingsSection.NOTIFICATIONS) }
            )
            SettingsDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Contrast, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(20.dp))
                    Text(
                        text = AppStrings.get("theme_mode_title", language),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        AppThemeMode.SYSTEM to AppStrings.get("theme_system", language),
                        AppThemeMode.LIGHT to AppStrings.get("theme_light", language),
                        AppThemeMode.DARK to AppStrings.get("theme_dark", language)
                    )
                    modes.forEach { (mode, label) ->
                        val selected = themeMode == mode
                        Surface(
                            onClick = { onThemeModeChange(mode) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) DarkNavySurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) DarkNavySurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Bantuan & Informasi
        SettingsGroupHeader(title = AppStrings.get("settings_section_support", language))
        SettingsGroupCard {
            SettingsMenuItem(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                iconTint = SafetyTeal,
                title = AppStrings.get("settings_support_title", language),
                subtitle = AppStrings.get("settings_support_desc", language),
                onClick = { onNavigate(SettingsSection.SUPPORT) }
            )
            SettingsDivider()
            SettingsMenuItem(
                icon = Icons.Outlined.Info,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = AppStrings.get("settings_about_title", language),
                subtitle = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • lowerzakum-482af",
                onClick = { onNavigate(SettingsSection.ABOUT) }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badgeText != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconTint.copy(alpha = 0.12f)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = iconTint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    currentLanguage: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Language, contentDescription = null, tint = SafetyBlue) },
        title = {
            Text(
                text = AppStrings.get("select_lang_label", currentLanguage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == currentLanguage
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) SafetyBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) SafetyBlue else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(lang) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(text = lang.flag, fontSize = 20.sp)
                                Column {
                                    Text(
                                        text = lang.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (lang == AppLanguage.ID) "Bahasa standar operasional Indonesia" else "International IOGP / OSHA English",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyBlue)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.get("cancel_btn", currentLanguage))
            }
        }
    )
}

@Composable
private fun ModulesSettingsContent(
    language: AppLanguage,
    checklistItems: List<LsrItem>,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onSelectAllCategories: () -> Unit,
    onOpenChecklistManager: () -> Unit
) {
    val isId = language == AppLanguage.ID
    val isAllActive = selectedCategories.isEmpty() || selectedCategories.size == availableCategories.size
    val activeRulesCount = if (selectedCategories.isEmpty()) checklistItems.size else checklistItems.count { selectedCategories.contains(it.category) }

    // Overview Card
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SafetyTeal.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, SafetyTeal.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isId) "Cakupan Modul Inspeksi Aktif" else "Active Inspection Module Scope",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isId)
                        "$activeRulesCount pertanyaan dari ${if (isAllActive) availableCategories.size else selectedCategories.size} modul siap diaudit"
                    else
                        "$activeRulesCount questions across ${if (isAllActive) availableCategories.size else selectedCategories.size} modules ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onSelectAllCategories,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SafetyTeal.copy(alpha = 0.18f),
                    contentColor = SafetyTeal
                )
            ) {
                Text(
                    text = if (isId) "Aktifkan Semua" else "Enable All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Per-module switches
    SettingsGroupCard {
        availableCategories.forEachIndexed { index, category ->
            val isEnabled = selectedCategories.isEmpty() || selectedCategories.contains(category)
            val itemsInCat = checklistItems.filter { it.category == category }
            val localizedName = itemsInCat.firstOrNull()?.getLocalizedCategory(language) ?: category
            val criticalCount = itemsInCat.count { it.is_critical }
            val emoji = LsrChecklist.getCategoryIcon(category)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCategory(category) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isEnabled) SafetyBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isId) "${itemsInCat.size} aturan • $criticalCount kritis" else "${itemsInCat.size} rules • $criticalCount critical",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleCategory(category) }
                )
            }
            if (index < availableCategories.lastIndex) {
                SettingsDivider()
            }
        }
    }

    // Custom JSON Checklist Manager CTA
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = AppStrings.get("manage_checklist_title", language),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = AppStrings.get("manage_checklist_subtitle", language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenChecklistManager,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.get("checklist_manager_btn", language), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CloudSettingsContent(
    language: AppLanguage,
    activeProject: FirestoreProject?,
    pendingSyncCount: Int,
    onRetryPendingSync: () -> Unit,
    isCloudSyncing: Boolean = false,
    lastCloudSyncAt: Long = 0L,
    scriptUrl: String,
    isGasEnabled: Boolean,
    firebaseUrl: String,
    isFirebaseEnabled: Boolean,
    isDatabaseAdminUnlocked: Boolean,
    onUnlockDatabaseAdmin: (String) -> Boolean,
    onLockDatabaseAdmin: () -> Unit,
    onOpenProjectDialog: () -> Unit,
    onRefreshCloud: () -> Unit,
    onSaveCloudSettings: (scriptUrl: String, isGasEnabled: Boolean, firebaseUrl: String, isFirebaseEnabled: Boolean, publishGlobal: Boolean) -> Unit,
    onTestGas: () -> Unit,
    onTestFirebase: () -> Unit,
    onOpenGasGuide: () -> Unit
) {
    val isId = language == AppLanguage.ID
    var inputScriptUrl by remember(scriptUrl) { mutableStateOf(scriptUrl) }
    var inputGasEnabled by remember(isGasEnabled) { mutableStateOf(isGasEnabled) }
    var inputFirebaseUrl by remember(firebaseUrl) { mutableStateOf(firebaseUrl) }
    var inputFirebaseEnabled by remember(isFirebaseEnabled) { mutableStateOf(isFirebaseEnabled) }
    var publishGlobal by remember { mutableStateOf(true) }
    var showUnlockBox by remember { mutableStateOf(false) }
    var adminPassInput by remember { mutableStateOf("") }
    var adminPassVisible by remember { mutableStateOf(false) }
    var adminUnlockError by remember { mutableStateOf<String?>(null) }

    // Outbox Sync Queue & Instant Cache Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (pendingSyncCount > 0) AmberGold.copy(alpha = 0.12f) else SafetyTeal.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, if (pendingSyncCount > 0) AmberGold.copy(alpha = 0.4f) else SafetyTeal.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (pendingSyncCount > 0)
                            "${AppStrings.get("feed_pending_sync", language)}: $pendingSyncCount item"
                        else
                            AppStrings.get("sync_state_instant", language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${AppStrings.get("sync_last_at", language)}: ${AppStrings.formatRelativeSync(lastCloudSyncAt, language)} • Room + 100MB Cache",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (pendingSyncCount > 0) {
                    FilledTonalButton(onClick = onRetryPendingSync) {
                        Text(AppStrings.get("feed_sync_now", language), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = SafetyTeal)
                }
            }
            if (isCloudSyncing) {
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

    // Workspace Project Card
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = BorderStroke(1.dp, SafetyBlue.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = SafetyBlue)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isId) "Workspace Proyek Firestore" else "Firestore Project Workspace",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = activeProject?.name ?: (if (isId) "Belum Dipilih" else "None Selected"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenProjectDialog,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SafetyBlue.copy(alpha = 0.25f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isId) "Ganti Proyek" else "Switch Project", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onRefreshCloud,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isId) "Sinkronkan" else "Sync Now", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // Google Apps Script Card (Admin Lock Protected)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isDatabaseAdminUnlocked) SafetyTeal.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.get("gas_section_title", language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.get("gas_section_desc", language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = inputGasEnabled,
                    enabled = isDatabaseAdminUnlocked,
                    onCheckedChange = { inputGasEnabled = it }
                )
            }

            // Admin Lock Status / Unlock Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = (if (isDatabaseAdminUnlocked) SafetyTeal else AmberGold).copy(alpha = 0.1f),
                border = BorderStroke(
                    1.dp,
                    (if (isDatabaseAdminUnlocked) SafetyTeal else AmberGold).copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isDatabaseAdminUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isDatabaseAdminUnlocked) SafetyTeal else AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDatabaseAdminUnlocked)
                                    (if (isId) "Mode Admin Terbuka (dapat edit URL)" else "Admin Unlocked (URL editable)")
                                else
                                    (if (isId) "URL database dikunci (khusus Admin)" else "Database URL locked (Admin only)"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (isDatabaseAdminUnlocked) {
                            TextButton(
                                onClick = {
                                    onLockDatabaseAdmin()
                                    showUnlockBox = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(if (isId) "Kunci" else "Lock", fontSize = 11.sp)
                            }
                        } else {
                            TextButton(
                                onClick = { showUnlockBox = !showUnlockBox },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(if (isId) "Buka Kunci" else "Unlock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (showUnlockBox && !isDatabaseAdminUnlocked) {
                        OutlinedTextField(
                            value = adminPassInput,
                            onValueChange = {
                                adminPassInput = it
                                adminUnlockError = null
                            },
                            label = { Text(if (isId) "Kata Sandi Admin" else "Admin Password") },
                            trailingIcon = {
                                IconButton(onClick = { adminPassVisible = !adminPassVisible }) {
                                    Icon(
                                        imageVector = if (adminPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (adminPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            isError = adminUnlockError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!adminUnlockError.isNullOrBlank()) {
                            Text(adminUnlockError!!, fontSize = 11.sp, color = DangerRed)
                        }
                        Button(
                            onClick = {
                                if (onUnlockDatabaseAdmin(adminPassInput)) {
                                    adminPassInput = ""
                                    showUnlockBox = false
                                    adminUnlockError = null
                                } else {
                                    adminUnlockError = if (isId) "Kata sandi Admin salah." else "Incorrect Admin password."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isId) "Verifikasi & Buka Kunci" else "Verify & Unlock", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (inputGasEnabled) {
                OutlinedTextField(
                    value = inputScriptUrl,
                    onValueChange = { inputScriptUrl = it },
                    enabled = isDatabaseAdminUnlocked,
                    label = { Text(AppStrings.get("gas_url_label", language)) },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isDatabaseAdminUnlocked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { publishGlobal = !publishGlobal },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = publishGlobal, onCheckedChange = { publishGlobal = it })
                        Text(
                            text = if (isId) "Terapkan ke Config Global Firestore (semua perangkat)" else "Publish to Global Firestore Config (all devices)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTestGas,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.get("test_connection", language), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onOpenGasGuide,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.get("gas_guide_btn", language), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                FilledTonalButton(
                    onClick = {
                        if (isDatabaseAdminUnlocked) {
                            onSaveCloudSettings(inputScriptUrl, inputGasEnabled, inputFirebaseUrl, inputFirebaseEnabled, publishGlobal)
                        }
                        onRetryPendingSync()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SafetyBlue.copy(alpha = 0.12f),
                        contentColor = SafetyBlue
                    )
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.get("sync_pull_gas_btn", language), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Firebase Realtime DB & Firestore Card
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.get("firebase_section_title", language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.get("firebase_section_desc", language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = inputFirebaseEnabled, onCheckedChange = { inputFirebaseEnabled = it })
            }

            if (inputFirebaseEnabled) {
                OutlinedTextField(
                    value = inputFirebaseUrl,
                    onValueChange = { inputFirebaseUrl = it },
                    label = { Text(AppStrings.get("firebase_url_label", language)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SafetyBlue.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Firebase Console: lowerzakum-482af",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafetyBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                OutlinedButton(
                    onClick = onTestFirebase,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${AppStrings.get("test_connection", language)} Firebase")
                }
            }
        }
    }

    Button(
        onClick = {
            onSaveCloudSettings(inputScriptUrl, inputGasEnabled, inputFirebaseUrl, inputFirebaseEnabled, publishGlobal)
        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(AppStrings.get("save_cloud_btn", language), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NotificationSettingsContent(
    language: AppLanguage,
    notifPicAlerts: Boolean,
    notifCriticalAlerts: Boolean,
    notifDraftReminder: Boolean,
    hapticFeedbackEnabled: Boolean,
    onToggleNotifPic: (Boolean) -> Unit,
    onToggleNotifCritical: (Boolean) -> Unit,
    onToggleNotifDraft: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit
) {
    val isId = language == AppLanguage.ID

    SettingsGroupHeader(title = if (isId) "Notifikasi Audit & Alur Kerja" else "Audit & Workflow Alerts")
    SettingsGroupCard {
        SettingsToggleRow(
            icon = Icons.Outlined.AssignmentInd,
            iconTint = SafetyBlue,
            title = if (isId) "Notifikasi Penugasan & Verifikasi PIC" else "PIC Assignment & Verification Alerts",
            subtitle = if (isId) "Pemberitahuan saat temuan ditugaskan atau dinilai auditor" else "Notify when findings are assigned or verified",
            checked = notifPicAlerts,
            onCheckedChange = onToggleNotifPic
        )
        SettingsDivider()
        SettingsToggleRow(
            icon = Icons.Outlined.WarningAmber,
            iconTint = DangerRed,
            title = if (isId) "Peringatan Aturan Kritis (Fatal Risk)" else "Critical Rule (Fatal Risk) Alerts",
            subtitle = if (isId) "Sorot temuan berisiko fatal secara prioritas di feed" else "Prioritize fatal risk deviations in activity feed",
            checked = notifCriticalAlerts,
            onCheckedChange = onToggleNotifCritical
        )
        SettingsDivider()
        SettingsToggleRow(
            icon = Icons.Outlined.EditNotifications,
            iconTint = AmberGold,
            title = if (isId) "Pengingat Draft Inspeksi Aktif" else "Active Draft Inspection Reminder",
            subtitle = if (isId) "Ingatkan jika ada audit lapangan yang belum dikirim" else "Remind when field audits remain unsubmitted",
            checked = notifDraftReminder,
            onCheckedChange = onToggleNotifDraft
        )
    }

    SettingsGroupHeader(title = if (isId) "Interaksi Lapangan" else "Field Interaction")
    SettingsGroupCard {
        SettingsToggleRow(
            icon = Icons.Outlined.Vibration,
            iconTint = SafetyTeal,
            title = if (isId) "Umpan Balik Getar (Haptic)" else "Haptic Tactile Feedback",
            subtitle = if (isId) "Getaran halus saat memilih status kepatuhan di lapangan" else "Subtle tactile confirmation when answering rules",
            checked = hapticFeedbackEnabled,
            onCheckedChange = onToggleHaptic
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SupportSettingsContent(
    language: AppLanguage,
    currentUser: UserProfile?,
    activeProject: FirestoreProject?,
    onOpenGasGuide: () -> Unit
) {
    val context = LocalContext.current
    val isId = language == AppLanguage.ID
    var copiedDiagnostics by remember { mutableStateOf(false) }

    val diagnosticsText = remember(currentUser, activeProject) {
        buildString {
            appendLine("HSE LSR Inspector Diagnostics")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Firebase Project: lowerzakum-482af")
            appendLine("Workspace: ${activeProject?.name ?: "None"} (${activeProject?.id ?: "-"})")
            appendLine("User UID: ${currentUser?.uid ?: "Offline/Guest"}")
        }
    }

    SettingsGroupHeader(title = if (isId) "Panduan & Dokumentasi" else "Guides & Documentation")
    SettingsGroupCard {
        SettingsMenuItem(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            iconTint = SafetyBlue,
            title = AppStrings.get("gas_guide_btn", language),
            subtitle = if (isId) "Langkah pasang Webhook Spreadsheet Google Apps Script" else "Step-by-step Google Apps Script Spreadsheet setup",
            onClick = onOpenGasGuide
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Outlined.ContentCopy,
            iconTint = SafetyTeal,
            title = if (isId) "Salin Info Diagnostik Sistem" else "Copy System Diagnostics",
            subtitle = if (copiedDiagnostics)
                (if (isId) "Info diagnostik disalin ke clipboard!" else "Diagnostics copied to clipboard!")
            else
                (if (isId) "Versi aplikasi, ID proyek & status workspace" else "App version, project ID & workspace status"),
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("HSE_LSR_Diagnostics", diagnosticsText))
                copiedDiagnostics = true
            }
        )
    }

    SettingsGroupHeader(title = if (isId) "Standar Referensi Keselamatan" else "Safety Standard References")
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isId) "IOGP Report 459 & OSHA 1910/1926" else "IOGP Report 459 & OSHA 1910/1926",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SafetyBlue
            )
            Text(
                text = if (isId)
                    "Aplikasi ini mengacu pada 9 Life-Saving Rules internasional (Bekerja di Ketinggian, Ruang Terbatas, LOTO, Pekerjaan Panas, Pengangkatan, Mengemudi, Penggalian, Line Break, dan Bypass Kontrol Keselamatan)."
                else
                    "This application follows the 9 global Life-Saving Rules (Working at Height, Confined Space, Energy Isolation, Hot Work, Lifting, Safe Driving, Excavation, Line Break, and Bypassing Safety Controls).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:hse-support@lowerzakum.com".toUri()
                putExtra(Intent.EXTRA_SUBJECT, "HSE LSR Inspector Support (v${BuildConfig.VERSION_NAME})")
                putExtra(Intent.EXTRA_TEXT, diagnosticsText)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, if (isId) "Hubungi Dukungan HSE" else "Contact HSE Support")) }
        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isId) "Hubungi Tim Dukungan HSE" else "Contact HSE Support Team",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AboutSettingsContent(language: AppLanguage) {
    val isId = language == AppLanguage.ID

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HseBrandLogo(size = 64.dp)
            Text(
                text = "HSE LSR Inspector",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            HseBrandBadge()
            Text(
                text = if (isId) "Versi ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})" else "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF93C5FD)
            )
        }
    }

    SettingsGroupCard {
        AboutDetailRow(label = "Application ID", value = BuildConfig.APPLICATION_ID)
        SettingsDivider()
        AboutDetailRow(label = "Firebase Project", value = "lowerzakum-482af")
        SettingsDivider()
        AboutDetailRow(
            label = if (isId) "Standar Kepatuhan" else "Compliance Standard",
            value = "IOGP Report 459 & OSHA"
        )
        SettingsDivider()
        AboutDetailRow(
            label = if (isId) "Arsitektur Penyimpanan" else "Storage Architecture",
            value = "Offline-First Room + Cloud Firestore"
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isId) "Catatan Rilis Terbaru" else "Release Notes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isId)
                    "• Pusat Pengaturan terpadu (Profil, Modul, Cloud, Notifikasi, Bantuan & Versi)\n• Integrasi penuh Firebase Auth & Firestore (lowerzakum-482af)\n• Alur Verifikasi & Penilaian PIC Temuan"
                else
                    "• Centralized Settings Hub (Profile, Modules, Cloud, Alerts, Support & Version)\n• Full Firebase Auth & Firestore integration (lowerzakum-482af)\n• Enterprise PIC Verification & Rating workflow",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
