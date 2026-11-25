package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.ui.components.HseBrandBadge
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LsrInspectionApp(
    viewModel: LsrViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegroundChanged(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.onAppForegroundChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val appLanguage by viewModel.appLanguage.collectAsState()
    val checklistItems by viewModel.checklistItems.collectAsState()
    val filteredChecklistItems by viewModel.filteredChecklistItems.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val isInspecting by viewModel.isInspecting.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val activeAnswers by viewModel.activeAnswers.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()
    val findings by viewModel.findings.collectAsState()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsState()
    val teamRoster = viewModel.teamRoster
    val firestoreUsers by viewModel.firestoreUsers.collectAsState()
    val firestoreProjects by viewModel.firestoreProjects.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val combinedTeamRoster by viewModel.teamRosterFlow.collectAsState()

    val inspectorName by viewModel.inspectorName.collectAsState()
    val area by viewModel.area.collectAsState()
    val companyName by viewModel.companyName.collectAsState()
    val inspectorRole by viewModel.inspectorRole.collectAsState()
    val inspectorCert by viewModel.inspectorCert.collectAsState()
    val scriptUrl by viewModel.scriptUrl.collectAsState()
    val isGasEnabled by viewModel.isGasEnabled.collectAsState()
    val isDatabaseAdminUnlocked by viewModel.isDatabaseAdminUnlocked.collectAsState()
    val firebaseUrl by viewModel.firebaseUrl.collectAsState()
    val isFirebaseEnabled by viewModel.isFirebaseEnabled.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val settingsSection by viewModel.settingsSection.collectAsState()
    val notifPicAlerts by viewModel.notifPicAlerts.collectAsState()
    val notifCriticalAlerts by viewModel.notifCriticalAlerts.collectAsState()
    val notifDraftReminder by viewModel.notifDraftReminder.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val feedSummaries by viewModel.feedSummaries.collectAsState()
    val totalAuditsCount by viewModel.totalAuditsCount.collectAsState()
    val dashboardMetrics by viewModel.dashboardMetrics.collectAsState()
    val dashboardPerspective by viewModel.dashboardPerspective.collectAsState()
    val notificationsWindow by viewModel.notificationsWindow.collectAsState()
    val totalNotificationsCount by viewModel.totalNotificationsCount.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsState()
    val lastCloudSyncAt by viewModel.lastCloudSyncAt.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    val feedFilterCriteria by viewModel.feedFilterCriteria.collectAsState()
    val findingsFilterCriteria by viewModel.findingsFilterCriteria.collectAsState()
    val pagedFindingsState by viewModel.pagedFindingsState.collectAsState()
    val pagedFeedState by viewModel.pagedFeedState.collectAsState()

    // Firebase Auth states
    val currentUser by viewModel.currentUser.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authErrorMessage by viewModel.authErrorMessage.collectAsState()

    var showAuthDialog by remember { mutableStateOf(false) }
    var showProjectSelectDialog by remember { mutableStateOf(false) }
    var showChecklistManagerDialog by remember { mutableStateOf(false) }
    var showGasGuideDialog by remember { mutableStateOf(false) }
    var showSubmitConfirmationDialog by remember { mutableStateOf(false) }
    var showRecordDetailDialog by remember { mutableStateOf<InspectionRecord?>(null) }
    var requestedNotifPermission by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled gracefully by NotificationHelper */ }

    // Count open findings for the tab badge
    val openFindingsCount = remember(findings) {
        findings.count { it.status.equals("OPEN", ignoreCase = true) }
    }

    // Collect UI messages
    LaunchedEffect(Unit) {
        viewModel.uiMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Request Android 13+ notification permission once logged in
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null && !requestedNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestedNotifPermission = true
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // MANDATORY AUTHENTICATION GATE AT APP LAUNCH
    if (currentUser == null) {
        LoginGateScreen(
            language = appLanguage,
            isLoading = isAuthLoading,
            errorMessage = authErrorMessage,
            firestoreUsers = firestoreUsers,
            firestoreProjects = firestoreProjects,
            onLogin = { email, pass ->
                viewModel.login(email, pass)
            },
            onRegister = { email, pass, name, comp, role, cert ->
                viewModel.register(email, pass, name, comp, role, cert)
            },
            onResetPassword = { email ->
                viewModel.sendPasswordReset(email)
            },
            onClearError = {
                viewModel.clearAuthError()
            },
            onToggleLanguage = {
                viewModel.toggleLanguage()
            },
            scriptUrl = scriptUrl,
            isGasEnabled = isGasEnabled,
            isDatabaseAdminUnlocked = isDatabaseAdminUnlocked,
            onUnlockDatabaseAdmin = { pass -> viewModel.unlockDatabaseAdmin(pass) },
            onSaveAndTestDatabaseSetup = { adminPass, newUrl, gasEn, pubGlobal, onRes ->
                viewModel.saveAndTestDatabaseSetupFromLogin(adminPass, newUrl, gasEn, pubGlobal, onRes)
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!isInspecting) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 10.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        // Tab 1: Dashboard
                        val isDashSelected = currentTab == AppNavTab.DASHBOARD
                        NavigationBarItem(
                            selected = isDashSelected,
                            onClick = { viewModel.selectTab(AppNavTab.DASHBOARD) },
                            icon = {
                                Icon(
                                    if (isDashSelected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                                    contentDescription = AppStrings.get("tab_dashboard", appLanguage),
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = AppStrings.get("tab_dashboard", appLanguage),
                                    fontWeight = if (isDashSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SafetyBlue,
                                selectedTextColor = SafetyBlue,
                                indicatorColor = SafetyBlue.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        // Tab 2: Feed & Notifications (with unread badge)
                        val isFeedSelected = currentTab == AppNavTab.FEED
                        NavigationBarItem(
                            selected = isFeedSelected,
                            onClick = { viewModel.selectTab(AppNavTab.FEED) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (unreadNotificationsCount > 0) {
                                            Badge(
                                                containerColor = SafetyBlue,
                                                contentColor = Color.White
                                            ) {
                                                Text("$unreadNotificationsCount", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (isFeedSelected) Icons.Filled.DynamicFeed else Icons.Outlined.DynamicFeed,
                                        contentDescription = AppStrings.get("tab_feed", appLanguage),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = AppStrings.get("tab_feed", appLanguage),
                                    fontWeight = if (isFeedSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SafetyBlue,
                                selectedTextColor = SafetyBlue,
                                indicatorColor = SafetyBlue.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        // Tab 3: Findings (with badge)
                        val isFindingsSelected = currentTab == AppNavTab.FINDINGS
                        NavigationBarItem(
                            selected = isFindingsSelected,
                            onClick = { viewModel.selectTab(AppNavTab.FINDINGS) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (openFindingsCount > 0) {
                                            Badge(
                                                containerColor = DangerRed,
                                                contentColor = Color.White
                                            ) {
                                                Text("$openFindingsCount", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (isFindingsSelected) Icons.Filled.Warning else Icons.Outlined.Warning,
                                        contentDescription = AppStrings.get("tab_findings", appLanguage),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = AppStrings.get("tab_findings", appLanguage),
                                    fontWeight = if (isFindingsSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DangerRed,
                                selectedTextColor = DangerRed,
                                indicatorColor = DangerRed.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        // Tab 4: Centralized Settings Hub
                        val isSettingsSelected = currentTab == AppNavTab.SETTINGS
                        NavigationBarItem(
                            selected = isSettingsSelected,
                            onClick = { viewModel.selectTab(AppNavTab.SETTINGS) },
                            icon = {
                                Icon(
                                    if (isSettingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = AppStrings.get("tab_settings", appLanguage),
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = AppStrings.get("tab_settings", appLanguage),
                                    fontWeight = if (isSettingsSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SafetyBlue,
                                selectedTextColor = SafetyBlue,
                                indicatorColor = SafetyBlue.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = isInspecting,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "ScreenTransition"
            ) { inspecting ->
                if (inspecting && filteredChecklistItems.isNotEmpty()) {
                    val safeIndex = currentIndex.coerceIn(0, filteredChecklistItems.lastIndex)
                    val currentQuestion = filteredChecklistItems[safeIndex]
                    val currentAnswerData = activeAnswers[currentQuestion.id] ?: AnswerData(currentQuestion.id, AnswerType.UNANSWERED, "")

                    SurveyView(
                        language = appLanguage,
                        checklistItems = filteredChecklistItems,
                        activeAnswers = activeAnswers,
                        currentQuestion = currentQuestion,
                        answerData = currentAnswerData,
                        teamRoster = teamRoster,
                        currentIndex = safeIndex,
                        totalQuestions = filteredChecklistItems.size,
                        inspectorInitials = if (inspectorName.isNotBlank()) inspectorName.take(2).uppercase() else "IN",
                        onSelectQuestionIndex = { idx -> viewModel.selectQuestionIndex(idx) },
                        hapticFeedbackEnabled = hapticFeedbackEnabled,
                        onAnswerSelected = { type ->
                            viewModel.updateAnswer(currentQuestion.id, type, currentAnswerData.notes)
                        },
                        onNotesChanged = { notes ->
                            viewModel.updateAnswer(currentQuestion.id, currentAnswerData.answer, notes)
                        },
                        onPicSelected = { member ->
                            viewModel.setPicForQuestion(currentQuestion.id, member)
                        },
                        onPrevClicked = { viewModel.prevQuestion() },
                        onNextClicked = {
                            if (safeIndex >= filteredChecklistItems.lastIndex) {
                                showSubmitConfirmationDialog = true
                            } else {
                                viewModel.nextQuestion()
                            }
                        },
                        onSaveDraft = { viewModel.saveDraft() },
                        onBackClicked = { viewModel.exitInspection() },
                        nextQuestionPreview = if (safeIndex < filteredChecklistItems.lastIndex) {
                            filteredChecklistItems[safeIndex + 1]
                        } else null,
                        onPreviewClicked = { viewModel.nextQuestion() }
                    )
                } else {
                    // Switch between 4 bottom tabs with directional sliding
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                slideInHorizontally { width -> width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> -width } + fadeOut()
                            } else {
                                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> width } + fadeOut()
                            }
                        },
                        label = "TabDirectionalTransition"
                    ) { tab ->
                        when (tab) {
                            AppNavTab.DASHBOARD -> {
                                DashboardScreen(
                                    language = appLanguage,
                                    checklistItems = checklistItems,
                                    inspectorName = inspectorName,
                                    area = area,
                                    activeAnswers = activeAnswers,
                                    historyRecords = historyRecords,
                                    dashboardMetrics = dashboardMetrics,
                                    pendingSyncCount = pendingSyncCount,
                                    dashboardPerspective = dashboardPerspective,
                                    onPerspectiveChanged = { viewModel.setDashboardPerspective(it) },
                                    findings = findings,
                                    currentUser = currentUser,
                                    activeProject = activeProject,
                                    onOpenProjectDialog = { showProjectSelectDialog = true },
                                    selectedCategories = selectedCategories,
                                    availableCategories = availableCategories,
                                    onToggleCategory = { viewModel.toggleCategory(it) },
                                    onSelectAllCategories = { viewModel.selectAllCategories() },
                                    onOpenAuthDialog = { showAuthDialog = true },
                                    onOpenProfileSettings = { viewModel.openSettingsSection(SettingsSection.PROFILE) },
                                    onInspectorDetailsChanged = { name, loc ->
                                        viewModel.setInspectorDetails(name, loc)
                                    },
                                    onStartClicked = { viewModel.startInspection() },
                                    onResetDraftClicked = { viewModel.resetDraft() },
                                    onNavigateToTab = { targetTab -> viewModel.selectTab(targetTab) },
                                    isCloudSyncing = isCloudSyncing,
                                    lastCloudSyncAt = lastCloudSyncAt,
                                    onSyncNow = { viewModel.refreshAllData() }
                                )
                            }
                            AppNavTab.FEED -> {
                                val resolvedRosterNames = remember(combinedTeamRoster, teamRoster) {
                                    (combinedTeamRoster.ifEmpty { teamRoster }).map { it.name }.distinct()
                                }
                                FeedScreen(
                                    language = appLanguage,
                                    feedSummaries = feedSummaries,
                                    totalAuditsCount = totalAuditsCount,
                                    notifications = notificationsWindow,
                                    totalNotificationsCount = totalNotificationsCount,
                                    unreadCount = unreadNotificationsCount,
                                    pendingSyncCount = pendingSyncCount,
                                    isRefreshing = isRefreshing,
                                    currentInspectorName = inspectorName,
                                    filterCriteria = feedFilterCriteria,
                                    onFilterCriteriaChange = { viewModel.setFeedFilterCriteria(it) },
                                    availableUsers = resolvedRosterNames,
                                    availableLsrTypes = availableCategories,
                                    availableCompanies = activeProject?.customCompanies ?: emptyList(),
                                    availableLocations = activeProject?.customLocations ?: emptyList(),
                                    onRecordClicked = { recordId ->
                                        viewModel.loadRecordDetail(recordId) { loaded ->
                                            showRecordDetailDialog = loaded
                                        }
                                    },
                                    onLoadMoreFeed = { viewModel.loadMoreFeed() },
                                    onLoadMoreNotifications = { viewModel.loadMoreNotifications() },
                                    onRefresh = { viewModel.refreshAllData() },
                                    onRetrySync = { viewModel.retryPendingSync() },
                                    onExportCsv = {
                                        viewModel.exportBatchCsv { uri ->
                                            shareExportedFile(
                                                context = context,
                                                uri = uri,
                                                mimeType = "text/csv",
                                                chooserTitle = if (appLanguage == AppLanguage.ID) "Ekspor Batch CSV" else "Export Batch CSV"
                                            )
                                        }
                                    },
                                    onMarkNotificationRead = { id -> viewModel.markNotificationRead(id) },
                                    onMarkAllNotificationsRead = { viewModel.markAllNotificationsRead() },
                                    onDeleteReadNotifications = { viewModel.deleteReadNotifications() },
                                    isCloudSyncing = isCloudSyncing,
                                    lastCloudSyncAt = lastCloudSyncAt,
                                    pagedFeedState = pagedFeedState
                                )
                            }
                            AppNavTab.FINDINGS -> {
                                FindingsScreen(
                                    language = appLanguage,
                                    findings = findings,
                                    teamRoster = combinedTeamRoster.ifEmpty { teamRoster },
                                    isRefreshing = isRefreshing,
                                    filterCriteria = findingsFilterCriteria,
                                    onFilterCriteriaChange = { viewModel.setFindingsFilterCriteria(it) },
                                    availableCompanies = activeProject?.customCompanies ?: emptyList(),
                                    availableLocations = activeProject?.customLocations ?: emptyList(),
                                    onRefresh = { viewModel.refreshAllData() },
                                    onExportCsv = {
                                        viewModel.exportBatchCsv { uri ->
                                            shareExportedFile(
                                                context = context,
                                                uri = uri,
                                                mimeType = "text/csv",
                                                chooserTitle = if (appLanguage == AppLanguage.ID) "Ekspor Temuan CSV" else "Export Findings CSV"
                                            )
                                        }
                                    },
                                    onAttachEvidencePhoto = { findingId, sourceUri ->
                                        viewModel.attachFindingEvidencePhoto(findingId, sourceUri)
                                    },
                                    onSendFindingMessage = { findingId, msg ->
                                        viewModel.sendFindingMessage(findingId, msg)
                                    },
                                    onSendReminderPing = { findingId ->
                                        viewModel.sendReminderPing(findingId)
                                    },
                                    onGetActionLogs = { findingId -> viewModel.getActionLogsFlow(findingId) },
                                    onAssignPic = { findingId, name, email, role ->
                                        viewModel.assignPicToFinding(findingId, name, email, role)
                                    },
                                    onSubmitPicAction = { findingId, action, evidence ->
                                        viewModel.submitPicAction(findingId, action, evidence)
                                    },
                                    onVerifyAndClose = { findingId, rating, comment ->
                                        viewModel.verifyAndCloseFinding(findingId, rating, comment)
                                    },
                                    onReopenFinding = { findingId, reason ->
                                        viewModel.reopenFinding(findingId, reason)
                                    },
                                    onUpdateStatus = { id, status, action ->
                                        viewModel.updateFindingStatus(id, status, action)
                                    },
                                    onDeleteFinding = { id ->
                                        viewModel.deleteFinding(id)
                                    },
                                    canManageFinding = { finding ->
                                        viewModel.canManageOrVerifyFinding(finding)
                                    },
                                    pagedState = pagedFindingsState,
                                    onPagedSearchChanged = { q -> viewModel.onFindingsSearchChanged(q) },
                                    onPagedFilterChanged = { status -> viewModel.onFindingsFilterTabChanged(status) },
                                    onLoadNextPage = { viewModel.loadNextFindingsPage() },
                                    onRetryAppend = { viewModel.retryFindingsAppend() }
                                )
                            }
                            AppNavTab.SETTINGS -> {
                                SettingsScreen(
                                    currentSection = settingsSection,
                                    onSelectSection = { section -> viewModel.openSettingsSection(section) },
                                    language = appLanguage,
                                    inspectorName = inspectorName,
                                    companyName = companyName,
                                    inspectorRole = inspectorRole,
                                    inspectorCert = inspectorCert,
                                    currentUser = currentUser,
                                    activeProject = activeProject,
                                    checklistItems = checklistItems,
                                    availableCategories = availableCategories,
                                    selectedCategories = selectedCategories,
                                    onToggleCategory = { viewModel.toggleCategory(it) },
                                    onSelectAllCategories = { viewModel.selectAllCategories() },
                                    scriptUrl = scriptUrl,
                                    isGasEnabled = isGasEnabled,
                                    firebaseUrl = firebaseUrl,
                                    isFirebaseEnabled = isFirebaseEnabled,
                                    notifPicAlerts = notifPicAlerts,
                                    notifCriticalAlerts = notifCriticalAlerts,
                                    notifDraftReminder = notifDraftReminder,
                                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                                    pendingSyncCount = pendingSyncCount,
                                    onRetryPendingSync = { viewModel.retryPendingSync() },
                                    isCloudSyncing = isCloudSyncing,
                                    lastCloudSyncAt = lastCloudSyncAt,
                                    themeMode = appThemeMode,
                                    onThemeModeChange = { viewModel.setAppThemeMode(it) },
                                    onToggleNotifPic = { viewModel.setNotifPicAlerts(it) },
                                    onToggleNotifCritical = { viewModel.setNotifCriticalAlerts(it) },
                                    onToggleNotifDraft = { viewModel.setNotifDraftReminder(it) },
                                    onToggleHaptic = { viewModel.setHapticFeedbackEnabled(it) },
                                    onLanguageChange = { viewModel.setLanguage(it) },
                                    onSaveProfile = { comp, role, cert ->
                                        viewModel.saveProfileDetails(comp, role, cert)
                                    },
                                    onSaveCloudSettings = { sUrl, gEn, fUrl, fEn, pubGlobal ->
                                        viewModel.saveCloudSettings(sUrl, gEn, fUrl, fEn, pubGlobal)
                                    },
                                    isDatabaseAdminUnlocked = isDatabaseAdminUnlocked,
                                    onUnlockDatabaseAdmin = { pass -> viewModel.unlockDatabaseAdmin(pass) },
                                    onLockDatabaseAdmin = { viewModel.lockDatabaseAdmin() },
                                    onTestGas = { viewModel.testGasConnection() },
                                    onTestFirebase = { viewModel.testFirebaseConnection() },
                                    onRefreshCloud = { viewModel.refreshFirestoreData() },
                                    onOpenProjectDialog = { showProjectSelectDialog = true },
                                    onOpenGasGuide = { showGasGuideDialog = true },
                                    onOpenChecklistManager = { showChecklistManagerDialog = true },
                                    onOpenAuthDialog = { showAuthDialog = true },
                                    onSignOut = { viewModel.signOut() }
                                )
                            }
                        }
                    }
                }
            }

            // Progress bar overlay while submitting
            if (isSubmitting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = SafetyBlue,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.ID) "Mengirim Laporan Inspeksi & Sinkronisasi..." else "Submitting Inspection & Syncing...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.ID) "Menyimpan ke database lokal, Google Sheets & Firebase..." else "Saving to local database, Google Sheets & Firebase...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Dialogs
    if (showGasGuideDialog) {
        GasGuideDialog(
            language = appLanguage,
            onDismiss = { showGasGuideDialog = false },
            onCodeCopied = { /* Handled in dialog with toast */ }
        )
    }

    if (showChecklistManagerDialog) {
        ChecklistManagerDialog(
            language = appLanguage,
            currentChecklist = checklistItems,
            onExport = { viewModel.exportChecklistJson() },
            onImport = { json -> viewModel.importChecklistJson(json) },
            onResetToDefault = { viewModel.resetChecklistToDefault() },
            onGetSampleTemplate = { viewModel.getSampleTemplateJson() },
            onDismiss = { showChecklistManagerDialog = false }
        )
    }

    if (showSubmitConfirmationDialog) {
        val totalCount = filteredChecklistItems.size
        val answeredCount = activeAnswers.values.count { it.answer != AnswerType.UNANSWERED }
        val unansweredCount = (totalCount - answeredCount).coerceAtLeast(0)

        SubmitConfirmationDialog(
            language = appLanguage,
            answeredCount = answeredCount,
            unansweredCount = unansweredCount,
            totalCount = totalCount,
            scriptUrl = scriptUrl,
            isSubmitting = isSubmitting,
            onSubmit = {
                showSubmitConfirmationDialog = false
                viewModel.submitReport()
            },
            onDismiss = {
                if (!isSubmitting) showSubmitConfirmationDialog = false
            }
        )
    }

    showRecordDetailDialog?.let { record ->
        RecordDetailDialog(
            language = appLanguage,
            record = record,
            checklistItems = checklistItems,
            onExportPdf = {
                viewModel.exportRecordToPdf(record) { uri ->
                    shareExportedFile(
                        context = context,
                        uri = uri,
                        mimeType = "application/pdf",
                        chooserTitle = if (appLanguage == AppLanguage.ID) "Bagikan PDF Laporan K3" else "Share HSE Audit PDF"
                    )
                }
            },
            onDismiss = { showRecordDetailDialog = null }
        )
    }

    if (showAuthDialog) {
        AuthDialog(
            language = appLanguage,
            isLoading = isAuthLoading,
            errorMessage = authErrorMessage,
            onClearError = { viewModel.clearAuthError() },
            onLogin = { email, pass ->
                viewModel.login(email, pass, onSuccess = { showAuthDialog = false })
            },
            onRegister = { email, pass, name, comp, role, cert ->
                viewModel.register(email, pass, name, comp, role, cert, onSuccess = { showAuthDialog = false })
            },
            onResetPassword = { email ->
                viewModel.sendPasswordReset(email)
            },
            onDismiss = {
                viewModel.clearAuthError()
                showAuthDialog = false
            }
        )
    }

    if (showProjectSelectDialog) {
        val currentFirestoreUser = firestoreUsers.find {
            it.uid == currentUser?.uid || it.email.equals(currentUser?.email, ignoreCase = true)
        }
        val accessibleProjects = remember(firestoreProjects, currentUser, currentFirestoreUser) {
            val uid = currentUser?.uid ?: ""
            val userEmail = currentUser?.email ?: ""
            val filtered = firestoreProjects.filter { proj ->
                proj.isOpen ||
                proj.ownerUid == uid ||
                proj.memberUids.contains(uid) ||
                (currentFirestoreUser?.projectIds?.contains(proj.id) == true)
            }
            if (filtered.isNotEmpty()) filtered else firestoreProjects
        }

        ProjectSelectionDialog(
            language = appLanguage,
            currentUser = currentUser,
            currentFirestoreUser = currentFirestoreUser,
            projects = accessibleProjects,
            activeProject = activeProject,
            onSelectProject = { proj ->
                viewModel.selectActiveProject(proj)
            },
            onRefresh = {
                viewModel.refreshFirestoreData()
            },
            onDismiss = {
                showProjectSelectDialog = false
            }
        )
    }
}

// ==========================================
// 1. SURVEY / AUDIT INSPECTION VIEW (ENHANCED UI/UX)
// ==========================================
@Composable
fun SurveyView(
    language: AppLanguage,
    checklistItems: List<LsrItem>,
    activeAnswers: Map<String, AnswerData>,
    currentQuestion: LsrItem,
    answerData: AnswerData,
    teamRoster: List<TeamRosterMember> = emptyList(),
    currentIndex: Int,
    totalQuestions: Int,
    inspectorInitials: String,
    hapticFeedbackEnabled: Boolean = true,
    onSelectQuestionIndex: (Int) -> Unit,
    onAnswerSelected: (AnswerType) -> Unit,
    onNotesChanged: (String) -> Unit,
    onPicSelected: (TeamRosterMember) -> Unit = {},
    onPrevClicked: () -> Unit,
    onNextClicked: () -> Unit,
    onSaveDraft: () -> Unit,
    onBackClicked: () -> Unit,
    nextQuestionPreview: LsrItem?,
    onPreviewClicked: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }

    // Calculate live survey stats
    val answeredCount = remember(activeAnswers) {
        activeAnswers.values.count { it.answer != AnswerType.UNANSWERED }
    }
    val yesCount = remember(activeAnswers) {
        activeAnswers.values.count { it.answer == AnswerType.YES }
    }
    val noCount = remember(activeAnswers) {
        activeAnswers.values.count { it.answer == AnswerType.NO }
    }
    val evaluatedCount = yesCount + noCount
    val liveScore = remember(yesCount, evaluatedCount) {
        if (evaluatedCount > 0) ((yesCount.toDouble() / evaluatedCount) * 100).toInt() else 100
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = AppStrings.get("exit_confirm_title", language),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = AppStrings.get("exit_confirm_desc", language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        onBackClicked()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue)
                ) {
                    Text(AppStrings.get("exit_confirm_btn", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(AppStrings.get("cancel_btn", language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Survey Top Bar with Live Score Badge & Inspector Avatar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { showExitDialog = true },
                        modifier = Modifier.testTag("survey_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = currentQuestion.getLocalizedCategory(language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${AppStrings.get("rule_progress", language)} ${currentIndex + 1}/$totalQuestions",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (liveScore >= 85) SafetyTeal.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "$liveScore% HSE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (liveScore >= 85) SafetyTeal else DangerRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onSaveDraft,
                            modifier = Modifier.testTag("save_draft_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Draft",
                                tint = SafetyBlue
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = SafetyBlue.copy(alpha = 0.15f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = inspectorInitials,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SafetyBlue
                                )
                            }
                        }
                    }
                }

                // Question Quick-Jumper Matrix Strip
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(checklistItems) { index, item ->
                        val ans = activeAnswers[item.id]?.answer ?: AnswerType.UNANSWERED
                        val isCurrent = index == currentIndex
                        val (chipBg, chipFg) = when (ans) {
                            AnswerType.YES -> SafetyTeal to Color.White
                            AnswerType.NO -> DangerRed to Color.White
                            AnswerType.NA -> Color(0xFF64748B) to Color.White
                            AnswerType.UNANSWERED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) SafetyBlue else chipBg.copy(alpha = if (ans == AnswerType.UNANSWERED) 0.6f else 1f),
                            border = if (isCurrent) BorderStroke(2.dp, SafetyBlue) else null,
                            modifier = Modifier
                                .clickable { onSelectQuestionIndex(index) }
                                .size(width = 34.dp, height = 28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                    color = if (isCurrent) Color.White else chipFg
                                )
                            }
                        }
                    }
                }

                // Linear Progress Indicator
                val progress = if (totalQuestions > 0) (currentIndex + 1).toFloat() / totalQuestions.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = SafetyBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Scrollable Survey Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Question Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("question_card_${currentQuestion.id}")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ID & Critical Tag Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = currentQuestion.id,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        if (currentQuestion.is_critical) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CriticalOrange.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, CriticalOrange.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Critical",
                                        tint = CriticalOrange,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = AppStrings.get("critical_badge", language),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CriticalOrange
                                    )
                                }
                            }
                        }
                    }

                    // Question Text with High Readability
                    Text(
                        text = currentQuestion.getLocalizedText(language),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp
                    )

                    // Tri-State Answer Selector Buttons (glove-friendly 56dp, zero wrapping)
                    val haptic = LocalHapticFeedback.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // YES Button
                        val isYes = answerData.answer == AnswerType.YES
                        Button(
                            onClick = {
                                if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAnswerSelected(AnswerType.YES)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("btn_answer_yes"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isYes) SafetyTeal else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isYes) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = if (isYes) ButtonDefaults.buttonElevation(defaultElevation = 4.dp) else ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Icon(
                                imageVector = if (isYes) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = "Yes",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == AppLanguage.ID) "YA" else "YES",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // NO Button
                        val isNo = answerData.answer == AnswerType.NO
                        Button(
                            onClick = {
                                if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAnswerSelected(AnswerType.NO)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("btn_answer_no"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isNo) DangerRed else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isNo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = if (isNo) ButtonDefaults.buttonElevation(defaultElevation = 4.dp) else ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Icon(
                                imageVector = if (isNo) Icons.Filled.Cancel else Icons.Outlined.Cancel,
                                contentDescription = "No",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == AppLanguage.ID) "TIDAK" else "NO",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // NA Button
                        val isNa = answerData.answer == AnswerType.NA
                        OutlinedButton(
                            onClick = {
                                if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAnswerSelected(AnswerType.NA)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("btn_answer_na"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isNa) Color(0xFF475569) else Color.Transparent,
                                contentColor = if (isNa) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = if (isNa) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "N/A",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Quick HSE Observation Suggestions
                    val quickTags = if (language == AppLanguage.ID) {
                        listOf("Sesuai SOP", "APD Lengkap", "Barikade Kurang", "Izin Kerja Valid", "Butuh Tindakan Cepat")
                    } else {
                        listOf("Compliant SOP", "Full PPE", "Inadequate Barricade", "Valid PTW", "Needs Immediate Action")
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (language == AppLanguage.ID) "Catatan Lapangan & Rekomendasi:" else "Field Observations & Actions:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Suggestion tags row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(quickTags) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable {
                                        val newNotes = if (answerData.notes.isBlank()) tag else "${answerData.notes}, $tag"
                                        onNotesChanged(newNotes)
                                    }
                                ) {
                                    Text(
                                        text = "+ $tag",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Notes Field
                        OutlinedTextField(
                            value = answerData.notes,
                            onValueChange = onNotesChanged,
                            label = { Text(AppStrings.get("notes_label", language)) },
                            placeholder = { Text(AppStrings.get("notes_placeholder", language)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("survey_notes_field"),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2,
                            maxLines = 4
                        )

                        // PIC Assignment Block for Non-Compliant (NO) Findings
                        if (answerData.answer == AnswerType.NO) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = DangerRed.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.PersonSearch, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = if (language == AppLanguage.ID) "Penugasan PIC Tindakan Koreksi (Wajib):" else "Assigned PIC for Corrective Action (Required):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DangerRed
                                        )
                                    }

                                    if (answerData.assignedPicName.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = DangerRed,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "PIC: ${answerData.assignedPicName} (${answerData.assignedPicRole})",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = answerData.assignedPicEmail,
                                                        color = Color.White.copy(alpha = 0.85f),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = if (language == AppLanguage.ID) "Pilih salah satu PIC penanggung jawab di bawah:" else "Select an assigned PIC below:",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Team Roster Quick Chips
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(teamRoster) { member ->
                                            val isSelected = answerData.assignedPicEmail == member.email
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) DangerRed else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, if (isSelected) DangerRed else MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier.clickable { onPicSelected(member) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = if (isSelected) Color.White else DangerRed,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = member.name,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = member.role,
                                                            fontSize = 8.sp,
                                                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
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
                }
            }

            // Next Question Preview
            nextQuestionPreview?.let { nextQ ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPreviewClicked() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == AppLanguage.ID) "SELANJUTNYA: ${nextQ.getLocalizedCategory(language)}" else "UP NEXT: ${nextQ.getLocalizedCategory(language)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = nextQ.getLocalizedText(language),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Bottom Navigation Actions
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrevClicked,
                    enabled = currentIndex > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("survey_prev_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AppStrings.get("prev_btn", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                val isLast = currentIndex >= totalQuestions - 1
                Button(
                    onClick = onNextClicked,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp)
                        .testTag("survey_next_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLast) SafetyTeal else SafetyBlue
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = if (isLast) AppStrings.get("submit_btn", language) else AppStrings.get("next_btn", language),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isLast) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. CHECKLIST MANAGER & IMPORT/EXPORT MODAL
// ==========================================
@Composable
fun ChecklistManagerDialog(
    language: AppLanguage,
    currentChecklist: List<LsrItem>,
    onExport: () -> String,
    onImport: (String) -> Boolean,
    onResetToDefault: () -> Unit,
    onGetSampleTemplate: () -> String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var jsonInputText by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }
    var exportCopySuccess by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = AppStrings.get("reset_confirm_title", language),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = AppStrings.get("reset_confirm_desc", language)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetToDefault()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(AppStrings.get("reset_default_btn", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(AppStrings.get("cancel_btn", language))
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = SafetyBlue)
                            }
                        }
                        Column {
                            Text(
                                text = AppStrings.get("manage_checklist_title", language),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${currentChecklist.size} ${AppStrings.get("active_items_count", language)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Tab Switcher
                TabRow(
                    selectedTabIndex = activeTab,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text(AppStrings.get("export_btn", language), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text(AppStrings.get("import_btn", language), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                // Body content based on tab
                if (activeTab == 0) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = AppStrings.get("manage_checklist_subtitle", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppStrings.get("export_dialog_title", language),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Button(
                                        onClick = {
                                            val json = onExport()
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("LSR_Checklist_JSON", json)
                                            clipboard.setPrimaryClip(clip)
                                            exportCopySuccess = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (exportCopySuccess) AppStrings.get("copied_success", language) else AppStrings.get("copy_clipboard", language),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkNavySurface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = onExport().take(300) + "\n... (Total ${currentChecklist.size} items)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color(0xFF80D8FF)
                                        ),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.get("reset_default_btn", language), fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // TAB 1: Import JSON
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = AppStrings.get("import_instructions", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalButton(
                            onClick = {
                                jsonInputText = onGetSampleTemplate()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.get("load_sample_template", language), fontSize = 12.sp)
                        }

                        OutlinedTextField(
                            value = jsonInputText,
                            onValueChange = { jsonInputText = it },
                            placeholder = { Text(AppStrings.get("paste_json_placeholder", language), fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        )

                        Button(
                            onClick = {
                                val success = onImport(jsonInputText.trim())
                                if (success) onDismiss()
                            },
                            enabled = jsonInputText.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyTeal),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.get("apply_checklist_btn", language), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. SUBMIT CONFIRMATION DIALOG
// ==========================================
@Composable
fun SubmitConfirmationDialog(
    language: AppLanguage,
    answeredCount: Int,
    unansweredCount: Int,
    totalCount: Int,
    scriptUrl: String,
    isSubmitting: Boolean = false,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = SafetyTeal,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = AppStrings.get("submit_confirm_title", language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = AppStrings.get("submit_confirm_desc", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (language == AppLanguage.ID) "Item Terisi:" else "Completed Items:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$answeredCount / $totalCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafetyTeal)
                        }
                        if (unansweredCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (language == AppLanguage.ID) "Belum Dijawab:" else "Unanswered:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$unansweredCount item", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Text(AppStrings.get("cancel_btn", language))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSubmit,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyTeal),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(AppStrings.get("submit_now_btn", language), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun shareExportedFile(
    context: Context,
    uri: Uri,
    mimeType: String,
    chooserTitle: String
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
}

// ==========================================
// 4. FULL-PAGE RECORD DETAIL DIALOG
// ==========================================
@Composable
fun RecordDetailDialog(
    language: AppLanguage,
    record: InspectionRecord,
    checklistItems: List<LsrItem>,
    onExportPdf: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "NO", "YES", "NA"

    // Parse answers and applicable modules from JSON
    val parsedData = remember(record.answersJson) {
        val modules = mutableListOf<String>()
        val answersMap = mutableMapOf<String, Pair<String, String>>() // id -> (answer, notes)
        try {
            val root = JSONObject(record.answersJson)
            if (root.has("applicableModules")) {
                val arr = root.optJSONArray("applicableModules")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        modules.add(arr.optString(i))
                    }
                }
            }
            val answersObj = if (root.has("answers")) root.optJSONObject("answers") ?: JSONObject() else root
            val keys = answersObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "applicableModules") continue
                val obj = answersObj.optJSONObject(key)
                if (obj != null) {
                    val ans = obj.optString("answer", "UNANSWERED")
                    val notes = obj.optString("notes", "")
                    answersMap[key] = Pair(ans, notes)
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        Pair(modules, answersMap)
    }

    val applicableModules = parsedData.first
    val recordedAnswers = parsedData.second

    // Evaluated items: items present in recordedAnswers, or all checklistItems if no specific map
    val evaluatedItems = remember(checklistItems, recordedAnswers) {
        if (recordedAnswers.isNotEmpty()) {
            checklistItems.filter { recordedAnswers.containsKey(it.id) }
        } else {
            checklistItems
        }
    }

    val yesItems = evaluatedItems.filter { (recordedAnswers[it.id]?.first ?: "UNANSWERED") in listOf("YES", "NA") }
    val noItems = evaluatedItems.filter { (recordedAnswers[it.id]?.first ?: "UNANSWERED") == "NO" }
    val naItems = evaluatedItems.filter { (recordedAnswers[it.id]?.first ?: "UNANSWERED") == "NA" }

    val displayedItems = when (selectedFilter) {
        "NO" -> noItems
        "YES" -> evaluatedItems.filter { (recordedAnswers[it.id]?.first ?: "UNANSWERED") == "YES" }
        "NA" -> naItems
        else -> evaluatedItems
    }

    val dateStr = remember(record.timestamp) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Surface(
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (language == AppLanguage.ID) "Detail Laporan Audit Lapangan" else "Field HSE Audit Report",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${record.area} • $dateStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val summary = buildString {
                                        appendLine("📋 *HSSE LIFE-SAVING RULES AUDIT REPORT*")
                                        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                        appendLine("📍 Lokasi / Area : ${record.area}")
                                        appendLine("👤 Inspektur     : ${record.inspectorName}")
                                        appendLine("📅 Tanggal       : $dateStr")
                                        appendLine("📊 Kepatuhan     : ${record.score}% (${if (record.score >= 80) "SESUAI / AMAN" else "PERLU TINDAKAN KOREKTIF"})")
                                        appendLine("📝 Evaluasi      : ${evaluatedItems.size} Aturan • ${noItems.size} Temuan")
                                        if (applicableModules.isNotEmpty()) {
                                            appendLine("🎯 Modul Relevan : ${applicableModules.joinToString(", ")}")
                                        }
                                        if (noItems.isNotEmpty()) {
                                            appendLine("\n🚨 *DAFTAR TEMUAN NON-COMPLIANCE (NO):*")
                                            noItems.forEachIndexed { i, itm ->
                                                val note = recordedAnswers[itm.id]?.second
                                                appendLine("${i + 1}. [${itm.id}] ${itm.getLocalizedCategory(language)}: ${itm.getLocalizedText(language)}")
                                                if (!note.isNullOrBlank()) appendLine("   Catatan: $note")
                                            }
                                        }
                                        appendLine("\nGenerated via HSSE Tech Life-Saving Rules Inspection App")
                                    }
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, summary)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share HSE Audit Report"))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = SafetyBlue
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onExportPdf,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = AppStrings.get("export_pdf_btn", language),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue)
                            ) {
                                Text(if (language == AppLanguage.ID) "Tutup Laporan" else "Close Report", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    // 1. Executive Compliance Score Card
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            elevation = CardDefaults.cardElevation(4.dp),
                            modifier = Modifier.fillMaxWidth()
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
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (language == AppLanguage.ID) "TINGKAT KEPATUHAN K3" else "HSE COMPLIANCE SCORE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF93C5FD)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${record.score}%",
                                                fontSize = 38.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (record.score >= 80) SafetyTeal else DangerRed
                                            )
                                            Text(
                                                text = if (record.score >= 80) (if (language == AppLanguage.ID) "SESUAI / AMAN" else "COMPLIANT") else (if (language == AppLanguage.ID) "PERLU TINDAKAN" else "ACTION REQ."),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (record.score >= 80) SafetyTeal else DangerRed,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (record.isSynced) SafetyTeal.copy(alpha = 0.2f) else AmberGold.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, if (record.isSynced) SafetyTeal.copy(alpha = 0.4f) else AmberGold.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (record.isSynced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                                contentDescription = null,
                                                tint = if (record.isSynced) SafetyTeal else AmberGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (record.isSynced) (if (language == AppLanguage.ID) "Tersinkron Cloud" else "Cloud Synced") else (if (language == AppLanguage.ID) "Lokal Saja" else "Local Only"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (record.isSynced) SafetyTeal else AmberGold
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                                // Grid stats
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(if (language == AppLanguage.ID) "Inspektur" else "Inspector", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Text(record.inspectorName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Column {
                                        Text(if (language == AppLanguage.ID) "Lokasi / Area" else "Area", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Text(record.area, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Column {
                                        Text(if (language == AppLanguage.ID) "Temuan K3" else "Findings", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Text("${noItems.size} Item", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (noItems.isNotEmpty()) DangerRed else SafetyTeal)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Applicable Modules Scope Card (if available)
                    if (applicableModules.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Category, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = if (language == AppLanguage.ID) "Modul LSR Relevan di Area Ini:" else "Applicable LSR Modules in Area:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(applicableModules) { mod ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = SafetyTeal.copy(alpha = 0.12f),
                                                border = BorderStroke(1.dp, SafetyTeal.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = "✓ $mod",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SafetyTeal,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Filter Tabs (All, NO, YES, NA)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilter == "ALL",
                                onClick = { selectedFilter = "ALL" },
                                label = { Text(if (language == AppLanguage.ID) "Semua (${evaluatedItems.size})" else "All (${evaluatedItems.size})", fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SafetyBlue.copy(alpha = 0.2f),
                                    selectedLabelColor = SafetyBlue
                                )
                            )

                            FilterChip(
                                selected = selectedFilter == "NO",
                                onClick = { selectedFilter = "NO" },
                                label = { Text("🚨 Temuan (${noItems.size})", fontSize = 11.sp, fontWeight = if (noItems.isNotEmpty()) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DangerRed.copy(alpha = 0.2f),
                                    selectedLabelColor = DangerRed
                                )
                            )

                            FilterChip(
                                selected = selectedFilter == "YES",
                                onClick = { selectedFilter = "YES" },
                                label = { Text("✅ Sesuai (${yesItems.size - naItems.size})", fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SafetyTeal.copy(alpha = 0.2f),
                                    selectedLabelColor = SafetyTeal
                                )
                            )

                            if (naItems.isNotEmpty()) {
                                FilterChip(
                                    selected = selectedFilter == "NA",
                                    onClick = { selectedFilter = "NA" },
                                    label = { Text("N/A (${naItems.size})", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // 4. Evaluated Checklist Item Cards
                    items(displayedItems) { item ->
                        val ansData = recordedAnswers[item.id]
                        val ansType = ansData?.first ?: "UNANSWERED"
                        val notes = ansData?.second ?: ""

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.dp,
                                when (ansType) {
                                    "NO" -> DangerRed.copy(alpha = 0.5f)
                                    "YES" -> SafetyTeal.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(2.dp),
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
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SafetyBlue.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = item.id,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SafetyBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = item.getLocalizedCategory(language),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Answer Status Pill
                                    when (ansType) {
                                        "YES" -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = SafetyTeal.copy(alpha = 0.18f),
                                                border = BorderStroke(1.dp, SafetyTeal.copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyTeal, modifier = Modifier.size(14.dp))
                                                    Text(if (language == AppLanguage.ID) "SESUAI" else "COMPLIANT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SafetyTeal)
                                                }
                                            }
                                        }
                                        "NO" -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = DangerRed.copy(alpha = 0.18f),
                                                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(14.dp))
                                                    Text(if (language == AppLanguage.ID) "NON-COMPLIANCE" else "NON-COMPLIANT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                                                }
                                            }
                                        }
                                        "NA" -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text("N/A", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                        else -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(if (language == AppLanguage.ID) "Belum Dijawab" else "Unanswered", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = item.getLocalizedText(language),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )

                                // Standard reference pill
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = item.standard_ref,
                                        fontSize = 10.sp,
                                        color = SafetyBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Inspector Notes if present
                                if (notes.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (ansType == "NO") DangerRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, if (ansType == "NO") DangerRed.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EditNote,
                                                contentDescription = null,
                                                tint = if (ansType == "NO") DangerRed else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = if (language == AppLanguage.ID) "Catatan Lapangan & Temuan:" else "Field Observation Notes:",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (ansType == "NO") DangerRed else MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = notes,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
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
        }
    }
}
