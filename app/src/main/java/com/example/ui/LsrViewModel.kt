package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppNavTab {
    DASHBOARD, FEED, FINDINGS, SETTINGS
}

enum class SettingsSection {
    HUB, PROFILE, MODULES, CLOUD, NOTIFICATIONS, SUPPORT, ABOUT
}

class LsrViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("lsr_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = LsrRepository(database.lsrDao())
    val checklistStore = CustomChecklistStore(application)
    val authManager = FirebaseAuthManager(application)
    val firestoreManager = FirestoreManager(application)

    // Firestore Users & Projects
    val firestoreUsers: StateFlow<List<FirestoreUser>> = firestoreManager.users
    val firestoreProjects: StateFlow<List<FirestoreProject>> = firestoreManager.projects
    val activeProject: StateFlow<FirestoreProject?> = firestoreManager.activeProject

    // Dashboard Perspective / Role Filter
    private val _dashboardPerspective = MutableStateFlow(DashboardPerspective.ALL)
    val dashboardPerspective: StateFlow<DashboardPerspective> = _dashboardPerspective.asStateFlow()

    fun setDashboardPerspective(perspective: DashboardPerspective) {
        _dashboardPerspective.value = perspective
    }

    // Debounced User Search in Circle
    private val _userSearchQuery = MutableStateFlow("")
    val userSearchQuery: StateFlow<String> = _userSearchQuery.asStateFlow()

    fun setUserSearchQuery(query: String) {
        _userSearchQuery.value = query
    }

    // Circle Members (Strict project isolation)
    val circleUsersFlow: StateFlow<List<FirestoreUser>> = combine(
        firestoreManager.users,
        firestoreManager.activeProject
    ) { users, activeProj ->
        val projId = activeProj?.id ?: ""
        if (projId.isBlank() || activeProj?.isOpen == true) {
            users
        } else {
            users.filter { u ->
                (activeProj?.memberUids?.contains(u.uid) == true) ||
                activeProj?.ownerUid == u.uid ||
                u.projectIds.contains(projId)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Debounced Searched Users Flow (300ms debounce)
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val searchedCircleUsers: StateFlow<List<FirestoreUser>> = _userSearchQuery
        .debounce(300)
        .combine(circleUsersFlow) { query, circleUsers ->
            val clean = query.trim().lowercase()
            if (clean.isBlank()) {
                circleUsers
            } else {
                circleUsers.filter { u ->
                    u.displayName.lowercase().contains(clean) ||
                    u.email.lowercase().contains(clean) ||
                    u.position.lowercase().contains(clean) ||
                    u.company.lowercase().contains(clean)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic combined team roster for PIC selection (Strictly isolated by active project circle)
    val teamRosterFlow: StateFlow<List<TeamRosterMember>> = circleUsersFlow.map { fUsers ->
        fUsers.map { u ->
            TeamRosterMember(
                name = u.displayName.ifBlank { u.email.substringBefore("@") },
                email = u.email,
                role = u.getEffectiveRole(),
                department = u.getEffectiveCompany()
            )
        }.distinctBy { it.email.ifBlank { it.name } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Backward compatibility property
    val teamRoster: List<TeamRosterMember>
        get() = teamRosterFlow.value.ifEmpty { firestoreManager.getTeamRosterFromUsers() }

    // Current Bottom Navigation Tab
    private val _currentTab = MutableStateFlow(AppNavTab.DASHBOARD)
    val currentTab: StateFlow<AppNavTab> = _currentTab.asStateFlow()

    // Firebase Auth State
    val currentUser: StateFlow<UserProfile?> = authManager.currentUser
    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    // Language state
    private val _appLanguage = MutableStateFlow(
        if (sharedPrefs.getString("app_lang", "id") == "en") AppLanguage.EN else AppLanguage.ID
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // Dynamic Checklist items (Standard or Custom imported)
    val checklistItems: StateFlow<List<LsrItem>> = checklistStore.currentChecklist

    // Modular Checklist Filtering (Applicable Work Activities in specific Area)
    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    val availableCategories: StateFlow<List<String>> = checklistItems.map { items ->
        items.map { it.category }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredChecklistItems: StateFlow<List<LsrItem>> = combine(checklistItems, _selectedCategories) { items, selectedCats ->
        if (selectedCats.isEmpty()) {
            items
        } else {
            items.filter { selectedCats.contains(it.category) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Inspector Details & Profile
    private val _inspectorName = MutableStateFlow(sharedPrefs.getString("inspector_name", "") ?: "")
    val inspectorName: StateFlow<String> = _inspectorName.asStateFlow()

    private val _area = MutableStateFlow(sharedPrefs.getString("area_name", "") ?: "")
    val area: StateFlow<String> = _area.asStateFlow()

    private val _companyName = MutableStateFlow(sharedPrefs.getString("company_name", "HSSE Tech Operations") ?: "HSSE Tech Operations")
    val companyName: StateFlow<String> = _companyName.asStateFlow()

    private val _inspectorRole = MutableStateFlow(sharedPrefs.getString("inspector_role", "Lead HSE Inspector") ?: "Lead HSE Inspector")
    val inspectorRole: StateFlow<String> = _inspectorRole.asStateFlow()

    private val _inspectorCert = MutableStateFlow(sharedPrefs.getString("inspector_cert", "NEBOSH / AK3 Umum / OSHA 30") ?: "NEBOSH / AK3 Umum / OSHA 30")
    val inspectorCert: StateFlow<String> = _inspectorCert.asStateFlow()

    // Cloud Integration Settings
    private val _scriptUrl = MutableStateFlow(sharedPrefs.getString("script_url", "") ?: "")
    val scriptUrl: StateFlow<String> = _scriptUrl.asStateFlow()

    private val _isGasSyncEnabled = MutableStateFlow(sharedPrefs.getBoolean("is_gas_sync_enabled", true))
    val isGasEnabled: StateFlow<Boolean> = _isGasSyncEnabled.asStateFlow()

    private val _firebaseUrl = MutableStateFlow(
        sharedPrefs.getString("firebase_url", "https://lowerzakum-482af-default-rtdb.firebaseio.com")
            ?: "https://lowerzakum-482af-default-rtdb.firebaseio.com"
    )
    val firebaseUrl: StateFlow<String> = _firebaseUrl.asStateFlow()

    private val _isFirebaseSyncEnabled = MutableStateFlow(sharedPrefs.getBoolean("is_firebase_sync_enabled", true))
    val isFirebaseEnabled: StateFlow<Boolean> = _isFirebaseSyncEnabled.asStateFlow()

    // Centralized Settings Navigation & Preferences
    private val _settingsSection = MutableStateFlow(SettingsSection.HUB)
    val settingsSection: StateFlow<SettingsSection> = _settingsSection.asStateFlow()

    private val _notifPicAlerts = MutableStateFlow(sharedPrefs.getBoolean("notif_pic_alerts", true))
    val notifPicAlerts: StateFlow<Boolean> = _notifPicAlerts.asStateFlow()

    private val _notifCriticalAlerts = MutableStateFlow(sharedPrefs.getBoolean("notif_critical_alerts", true))
    val notifCriticalAlerts: StateFlow<Boolean> = _notifCriticalAlerts.asStateFlow()

    private val _notifDraftReminder = MutableStateFlow(sharedPrefs.getBoolean("notif_draft_reminder", true))
    val notifDraftReminder: StateFlow<Boolean> = _notifDraftReminder.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(sharedPrefs.getBoolean("haptic_feedback_enabled", true))
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _appThemeMode = MutableStateFlow(
        when (sharedPrefs.getString("app_theme_mode", "SYSTEM")) {
            "LIGHT" -> AppThemeMode.LIGHT
            "DARK" -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    )
    val appThemeMode: StateFlow<AppThemeMode> = _appThemeMode.asStateFlow()

    private val _feedFilterCriteria = MutableStateFlow(ActivityFilterCriteria())
    val feedFilterCriteria: StateFlow<ActivityFilterCriteria> = _feedFilterCriteria.asStateFlow()

    private val _findingsFilterCriteria = MutableStateFlow(ActivityFilterCriteria())
    val findingsFilterCriteria: StateFlow<ActivityFilterCriteria> = _findingsFilterCriteria.asStateFlow()

    // Inspection Survey State
    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Active answers (Mapped by Question ID)
    private val _activeAnswers = MutableStateFlow<Map<String, AnswerData>>(emptyMap())
    val activeAnswers: StateFlow<Map<String, AnswerData>> = _activeAnswers.asStateFlow()

    // Windowed / Paginated Feed & Notifications
    private val _feedLimit = MutableStateFlow(25)
    private val _notifLimit = MutableStateFlow(25)

    @OptIn(ExperimentalCoroutinesApi::class)
    val feedSummaries: StateFlow<List<InspectionSummary>> = _feedLimit
        .flatMapLatest { limit -> repository.getInspectionSummariesFlow(limit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalAuditsCount: StateFlow<Int> = repository.totalInspectionCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dashboardMetrics: StateFlow<DashboardMetrics> = repository.dashboardMetricsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    // History logs (retained for detail & analytics)
    val historyRecords: StateFlow<List<InspectionRecord>> = repository.inspectionRecordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Non-Compliance Findings list
    val findings: StateFlow<List<FindingItem>> = repository.findingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Windowed Notifications & Audit History Logs
    @OptIn(ExperimentalCoroutinesApi::class)
    val notificationsWindow: StateFlow<List<AppNotification>> = _notifLimit
        .flatMapLatest { limit -> repository.getNotificationsWindowFlow(limit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalNotificationsCount: StateFlow<Int> = repository.totalNotificationsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val notifications: StateFlow<List<AppNotification>> = repository.notificationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = repository.unreadNotificationsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allActionLogs: StateFlow<List<ActionHistoryLog>> = repository.allActionLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getActionLogsFlow(findingId: Int): Flow<List<ActionHistoryLog>> {
        val localFlow = repository.getActionLogsFlow(findingId)
        val projId = activeProject.value?.id ?: ""
        if (projId.isBlank()) return localFlow

        val remoteFlow = firestoreManager.observeFindingActionLogs(projId, "finding_$findingId")
            .catch { emit(emptyList()) }

        return combine(localFlow, remoteFlow) { localLogs, remoteLogs ->
            val mappedRemote = remoteLogs.map { r ->
                ActionHistoryLog(
                    id = 0,
                    findingId = findingId,
                    ruleId = "",
                    actorName = r.actorName,
                    actorEmail = "",
                    actorRole = r.actorRole,
                    actionType = r.actionType,
                    oldStatus = r.oldStatus,
                    newStatus = r.newStatus,
                    notes = r.remarks,
                    rating = r.rating,
                    timestamp = r.timestamp,
                    isSynced = true
                )
            }
            val merged = (localLogs + mappedRemote)
                .distinctBy { "${it.actionType}|${it.actorName}|${it.notes}|${it.timestamp / 1000L}" }
                .sortedBy { it.timestamp }
            merged
        }
    }

    // UI Status
    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Retention policy: auto-purge read notifications older than 30 days & schedule background workers
        viewModelScope.launch {
            repository.purgeOldReadNotifications(days = 30)
            SyncScheduler.scheduleDraftReminder(application, _notifDraftReminder.value)
            SyncScheduler.enqueueImmediateSync(application)
        }

        // Start cross-user realtime notifications listener when user & active project are ready
        viewModelScope.launch {
            combine(currentUser, activeProject) { user, proj -> user to proj }
                .collect { (user, proj) ->
                    if (user != null && proj != null && proj.id.isNotBlank()) {
                        firestoreManager.syncFcmToken(user.uid)
                        firestoreManager.startScopedNotificationsListener(
                            projectId = proj.id,
                            userUid = user.uid,
                            userEmail = user.email
                        )
                    }
                }
        }

        // Ingest incoming remote notifications into Room and trigger system push notifications
        viewModelScope.launch {
            firestoreManager.remoteNotifications.collect { remoteNotifs ->
                val uid = currentUser.value?.uid ?: ""
                if (remoteNotifs.isNotEmpty() && uid.isNotBlank()) {
                    val fresh = repository.ingestRemoteNotifications(
                        remoteList = remoteNotifs,
                        currentUserUid = uid,
                        notifPicAlerts = _notifPicAlerts.value,
                        notifCriticalAlerts = _notifCriticalAlerts.value
                    )
                    fresh.forEach { n ->
                        NotificationHelper.showNotification(
                            context = getApplication(),
                            notificationId = n.id.hashCode(),
                            title = n.title,
                            message = n.message,
                            isCritical = n.isCritical
                        )
                    }
                }
            }
        }

        // Load active answers from Room on startup to restore drafts mapped against current checklist
        viewModelScope.launch {
            combine(repository.activeAnswersFlow, checklistItems) { answersList, items ->
                items.associate { item ->
                    val saved = answersList.find { it.questionId == item.id }
                    val answerType = when (saved?.answer) {
                        "YES" -> AnswerType.YES
                        "NO" -> AnswerType.NO
                        "NA" -> AnswerType.NA
                        else -> AnswerType.UNANSWERED
                    }
                    item.id to AnswerData(
                        id = item.id,
                        answer = answerType,
                        notes = saved?.notes ?: "",
                        assignedPicName = saved?.assignedPicName ?: "",
                        assignedPicEmail = saved?.assignedPicEmail ?: "",
                        assignedPicRole = saved?.assignedPicRole ?: ""
                    )
                }
            }.collect { map ->
                _activeAnswers.value = map
            }
        }

        // Auto-sync profile info when Firebase user logs in or updates
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    if (user.displayName.isNotBlank()) {
                        _inspectorName.value = user.displayName
                    }
                    if (user.company.isNotBlank()) {
                        _companyName.value = user.company
                    }
                    if (user.role.isNotBlank()) {
                        _inspectorRole.value = user.role
                    }
                    if (user.certification.isNotBlank()) {
                        _inspectorCert.value = user.certification
                    }
                    viewModelScope.launch {
                        firestoreManager.syncUserToFirestore(
                            uid = user.uid,
                            displayName = user.displayName,
                            email = user.email,
                            company = user.company,
                            position = user.role
                        )
                        firestoreManager.ensureDefaultWorkspace(
                            creatorUid = user.uid,
                            creatorEmail = user.email,
                            creatorCompany = user.company
                        )
                    }
                }
            }
        }
    }

    fun selectTab(tab: AppNavTab) {
        if (tab != AppNavTab.SETTINGS) {
            _settingsSection.value = SettingsSection.HUB
        }
        _currentTab.value = tab
    }

    fun openSettingsSection(section: SettingsSection) {
        _settingsSection.value = section
        _currentTab.value = AppNavTab.SETTINGS
    }

    fun setNotifPicAlerts(enabled: Boolean) {
        _notifPicAlerts.value = enabled
        sharedPrefs.edit().putBoolean("notif_pic_alerts", enabled).apply()
    }

    fun setNotifCriticalAlerts(enabled: Boolean) {
        _notifCriticalAlerts.value = enabled
        sharedPrefs.edit().putBoolean("notif_critical_alerts", enabled).apply()
    }

    fun setNotifDraftReminder(enabled: Boolean) {
        _notifDraftReminder.value = enabled
        sharedPrefs.edit().putBoolean("notif_draft_reminder", enabled).apply()
        SyncScheduler.scheduleDraftReminder(getApplication(), enabled)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        _hapticFeedbackEnabled.value = enabled
        sharedPrefs.edit().putBoolean("haptic_feedback_enabled", enabled).apply()
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        _appThemeMode.value = mode
        sharedPrefs.edit().putString("app_theme_mode", mode.name).apply()
    }

    fun setFeedFilterCriteria(criteria: ActivityFilterCriteria) {
        _feedFilterCriteria.value = criteria
    }

    fun resetFeedFilterCriteria() {
        _feedFilterCriteria.value = ActivityFilterCriteria()
    }

    fun setFindingsFilterCriteria(criteria: ActivityFilterCriteria) {
        _findingsFilterCriteria.value = criteria
    }

    fun resetFindingsFilterCriteria() {
        _findingsFilterCriteria.value = ActivityFilterCriteria()
    }

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
        sharedPrefs.edit().putString("app_lang", language.code).apply()
    }

    fun toggleLanguage() {
        val next = if (_appLanguage.value == AppLanguage.ID) AppLanguage.EN else AppLanguage.ID
        setLanguage(next)
    }

    fun setInspectorDetails(name: String, area: String) {
        _inspectorName.value = name
        _area.value = area
        sharedPrefs.edit().apply {
            putString("inspector_name", name)
            putString("area_name", area)
            apply()
        }
    }

    fun saveProfileDetails(company: String, role: String, cert: String) {
        _companyName.value = company
        _inspectorRole.value = role
        _inspectorCert.value = cert
        sharedPrefs.edit().apply {
            putString("company_name", company)
            putString("inspector_role", role)
            putString("inspector_cert", cert)
            apply()
        }
        authManager.updateProfileLocal(company, role, cert)
        val current = currentUser.value
        viewModelScope.launch {
            if (current != null) {
                firestoreManager.syncUserToFirestore(
                    uid = current.uid,
                    displayName = current.displayName,
                    email = current.email,
                    company = company,
                    position = role
                )
            }
            _uiMessage.emit(if (_appLanguage.value == AppLanguage.ID) "Profil berhasil diperbarui." else "Profile updated successfully.")
        }
    }

    // Authentication Actions
    fun login(email: String, pass: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = authManager.login(email, pass, _appLanguage.value)
            _isAuthLoading.value = false
            if (result.isSuccess) {
                val profile = result.getOrNull()
                if (profile != null) {
                    firestoreManager.syncUserToFirestore(
                        uid = profile.uid,
                        displayName = profile.displayName,
                        email = profile.email,
                        company = profile.company,
                        position = profile.role
                    )
                    firestoreManager.ensureDefaultWorkspace(
                        creatorUid = profile.uid,
                        creatorEmail = profile.email,
                        creatorCompany = profile.company
                    )
                }
                val greeting = if (_appLanguage.value == AppLanguage.ID) {
                    "Selamat datang, ${profile?.displayName ?: "Inspektur"}!"
                } else {
                    "Welcome back, ${profile?.displayName ?: "Inspector"}!"
                }
                _uiMessage.emit(greeting)
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Login gagal."
                _authErrorMessage.value = errorMsg
                _uiMessage.emit(errorMsg)
            }
        }
    }

    fun register(
        email: String,
        pass: String,
        displayName: String,
        company: String,
        role: String,
        certification: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = authManager.register(
                email = email,
                pass = pass,
                displayName = displayName,
                company = company,
                role = role,
                certification = certification,
                language = _appLanguage.value
            )
            _isAuthLoading.value = false
            if (result.isSuccess) {
                val profile = result.getOrNull()
                if (profile != null) {
                    firestoreManager.syncUserToFirestore(
                        uid = profile.uid,
                        displayName = profile.displayName,
                        email = profile.email,
                        company = profile.company,
                        position = profile.role
                    )
                    firestoreManager.ensureDefaultWorkspace(
                        creatorUid = profile.uid,
                        creatorEmail = profile.email,
                        creatorCompany = profile.company
                    )
                }
                val msg = if (_appLanguage.value == AppLanguage.ID) {
                    "Akun HSE berhasil dibuat untuk ${profile?.displayName ?: displayName}!"
                } else {
                    "HSE Account successfully created for ${profile?.displayName ?: displayName}!"
                }
                _uiMessage.emit(msg)
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Registrasi gagal."
                _authErrorMessage.value = errorMsg
                _uiMessage.emit(errorMsg)
            }
        }
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = authManager.sendPasswordReset(email, _appLanguage.value)
            _isAuthLoading.value = false
            if (result.isSuccess) {
                val msg = if (_appLanguage.value == AppLanguage.ID) {
                    "Email reset password telah dikirim ke $email"
                } else {
                    "Password reset email sent to $email"
                }
                _uiMessage.emit(msg)
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Gagal mengirim link reset."
                _authErrorMessage.value = errorMsg
                _uiMessage.emit(errorMsg)
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        viewModelScope.launch {
            val msg = if (_appLanguage.value == AppLanguage.ID) "Berhasil keluar dari akun Firebase." else "Signed out of Firebase account."
            _uiMessage.emit(msg)
        }
    }

    fun loginWithFirestoreUser(user: FirestoreUser, onSuccess: () -> Unit = {}) {
        val profile = UserProfile(
            uid = user.uid,
            displayName = user.displayName.ifBlank { user.email.substringBefore("@") },
            email = user.email,
            company = user.getEffectiveCompany(),
            role = user.getEffectiveRole(),
            certification = "NEBOSH / AK3 Umum / IOGP"
        )
        authManager.loginDirectly(profile)

        // Select first accessible project for this user
        val accessibleProjects = firestoreManager.getAccessibleProjectsForUser(user.uid, user.email)
        if (accessibleProjects.isNotEmpty()) {
            val defaultProj = accessibleProjects.first()
            firestoreManager.selectActiveProject(defaultProj)
            if (defaultProj.customLocations.isNotEmpty()) {
                _area.value = defaultProj.customLocations.first()
            }
            if (defaultProj.customCompanies.isNotEmpty()) {
                _companyName.value = defaultProj.customCompanies.first()
            }
        }

        viewModelScope.launch {
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Masuk sebagai ${profile.displayName} (${profile.role})"
            } else {
                "Signed in as ${profile.displayName} (${profile.role})"
            }
            _uiMessage.emit(msg)
            onSuccess()
        }
    }

    fun selectActiveProject(project: FirestoreProject) {
        firestoreManager.selectActiveProject(project)
        if (project.customLocations.isNotEmpty() && _area.value.isBlank()) {
            _area.value = project.customLocations.first()
        }
        if (project.customCompanies.isNotEmpty() && _companyName.value.isBlank()) {
            _companyName.value = project.customCompanies.first()
        }
        viewModelScope.launch {
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Workspace aktif: ${project.name}"
            } else {
                "Active workspace: ${project.name}"
            }
            _uiMessage.emit(msg)
        }
    }

    fun refreshFirestoreData() {
        firestoreManager.fetchAllNow()
        viewModelScope.launch {
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Data user & proyek Firestore diperbarui."
            } else {
                "Firestore users & projects refreshed."
            }
            _uiMessage.emit(msg)
        }
    }

    fun getAccessibleProjects(): List<FirestoreProject> {
        val user = currentUser.value ?: return emptyList()
        return firestoreManager.getAccessibleProjectsForUser(user.uid, user.email)
    }

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    fun saveCloudSettings(
        scriptUrl: String,
        isGasEnabled: Boolean,
        firebaseUrl: String,
        isFirebaseEnabled: Boolean
    ) {
        _scriptUrl.value = scriptUrl
        _isGasSyncEnabled.value = isGasEnabled
        _firebaseUrl.value = firebaseUrl
        _isFirebaseSyncEnabled.value = isFirebaseEnabled

        sharedPrefs.edit().apply {
            putString("script_url", scriptUrl)
            putBoolean("is_gas_sync_enabled", isGasEnabled)
            putString("firebase_url", firebaseUrl)
            putBoolean("is_firebase_sync_enabled", isFirebaseEnabled)
            apply()
        }

        viewModelScope.launch {
            _uiMessage.emit(if (_appLanguage.value == AppLanguage.ID) "Pengaturan sinkronisasi cloud disimpan." else "Cloud synchronization settings saved.")
        }
    }

    fun setScriptUrl(url: String) {
        _scriptUrl.value = url
        sharedPrefs.edit().putString("script_url", url).apply()
    }

    fun testGasConnection() {
        viewModelScope.launch {
            val result = repository.testGasConnection(_scriptUrl.value)
            if (result.isSuccess) {
                _uiMessage.emit("✅ " + (result.getOrNull() ?: "Google Apps Script OK"))
            } else {
                _uiMessage.emit("❌ " + (result.exceptionOrNull()?.message ?: "Gagal terhubung ke GAS"))
            }
        }
    }

    fun testFirebaseConnection() {
        viewModelScope.launch {
            val result = repository.testFirebaseConnection(_firebaseUrl.value)
            if (result.isSuccess) {
                _uiMessage.emit("✅ " + (result.getOrNull() ?: "Firebase Realtime DB OK"))
            } else {
                _uiMessage.emit("❌ " + (result.exceptionOrNull()?.message ?: "Gagal terhubung ke Firebase"))
            }
        }
    }

    fun toggleCategory(category: String) {
        val current = _selectedCategories.value.toMutableSet()
        if (current.isEmpty()) {
            // First toggle after default 'all': start with all minus this, or if wanting to select specific
            val all = availableCategories.value.toMutableSet()
            if (all.contains(category)) {
                all.remove(category)
                _selectedCategories.value = all
            }
        } else {
            if (current.contains(category)) {
                current.remove(category)
            } else {
                current.add(category)
            }
            _selectedCategories.value = current
        }
    }

    fun selectAllCategories() {
        _selectedCategories.value = emptySet() // emptySet means All
    }

    fun selectOnlyCategories(categories: Set<String>) {
        _selectedCategories.value = categories
    }

    fun clearCategories() {
        _selectedCategories.value = emptySet()
    }

    fun startInspection() {
        if (_inspectorName.value.isBlank() || _area.value.isBlank()) {
            viewModelScope.launch {
                val msg = AppStrings.get("fill_inspector_details", _appLanguage.value)
                _uiMessage.emit(msg)
            }
            return
        }
        if (filteredChecklistItems.value.isEmpty()) {
            viewModelScope.launch {
                val msg = if (_appLanguage.value == AppLanguage.ID) {
                    "Pilih setidaknya 1 modul LSR untuk memulai inspeksi."
                } else {
                    "Select at least 1 LSR module to start inspection."
                }
                _uiMessage.emit(msg)
            }
            return
        }
        _currentIndex.value = 0
        _isInspecting.value = true
    }

    fun exitInspection() {
        _isInspecting.value = false
    }

    fun updateAnswer(
        questionId: String,
        answer: AnswerType,
        notes: String,
        picName: String = "",
        picEmail: String = "",
        picRole: String = ""
    ) {
        val currentMap = _activeAnswers.value.toMutableMap()
        val existing = currentMap[questionId]
        val resolvedPicName = if (picName.isNotBlank()) picName else (existing?.assignedPicName ?: "")
        val resolvedPicEmail = if (picEmail.isNotBlank()) picEmail else (existing?.assignedPicEmail ?: "")
        val resolvedPicRole = if (picRole.isNotBlank()) picRole else (existing?.assignedPicRole ?: "")

        currentMap[questionId] = AnswerData(
            id = questionId,
            answer = answer,
            notes = notes,
            assignedPicName = resolvedPicName,
            assignedPicEmail = resolvedPicEmail,
            assignedPicRole = resolvedPicRole
        )
        _activeAnswers.value = currentMap

        // Save immediately to Room draft
        viewModelScope.launch {
            repository.saveActiveAnswer(
                questionId = questionId,
                answer = answer.name,
                notes = notes,
                picName = resolvedPicName,
                picEmail = resolvedPicEmail,
                picRole = resolvedPicRole
            )
        }
    }

    fun setPicForQuestion(questionId: String, member: TeamRosterMember) {
        val currentMap = _activeAnswers.value.toMutableMap()
        val current = currentMap[questionId] ?: AnswerData(questionId, AnswerType.UNANSWERED, "")
        val updated = current.copy(
            assignedPicName = member.name,
            assignedPicEmail = member.email,
            assignedPicRole = member.role
        )
        currentMap[questionId] = updated
        _activeAnswers.value = currentMap

        viewModelScope.launch {
            repository.saveActiveAnswer(
                questionId = questionId,
                answer = updated.answer.name,
                notes = updated.notes,
                picName = member.name,
                picEmail = member.email,
                picRole = member.role
            )
        }
    }

    fun assignPicToFinding(
        findingId: Int,
        picName: String,
        picEmail: String,
        picRole: String
    ) {
        viewModelScope.launch {
            val user = currentUser.value
            val actor = if (_inspectorName.value.isNotBlank()) _inspectorName.value else (user?.displayName ?: "Inspector")
            val email = user?.email ?: ""
            val role = if (_inspectorRole.value.isNotBlank()) _inspectorRole.value else "Lead Auditor"

            repository.assignFindingPic(
                findingId = findingId,
                picName = picName,
                picEmail = picEmail,
                picRole = picRole,
                actorName = actor,
                actorEmail = email,
                actorRole = role
            )

            // Sync denormalized finding and audit log to Firestore
            val projId = activeProject.value?.id ?: "default_proj"
            firestoreManager.saveFindingToFirestore(
                FirestoreFindingDocument(
                    id = "finding_$findingId",
                    projectId = projId,
                    picName = picName,
                    picEmail = picEmail,
                    picRole = picRole,
                    creatorName = actor,
                    creatorEmail = email,
                    creatorRole = role,
                    status = "OPEN"
                )
            )
            firestoreManager.recordAuditActionLog(
                FirestoreActionLog(
                    findingId = "finding_$findingId",
                    projectId = projId,
                    actorUid = user?.uid ?: "",
                    actorName = actor,
                    actorRole = role,
                    actionType = "ASSIGNED_TO_PIC",
                    oldStatus = "OPEN",
                    newStatus = "OPEN",
                    remarks = "Assigned to PIC $picName ($picRole)"
                )
            )
            val findingItem = findings.value.find { it.id == findingId }
            firestoreManager.pushCrossUserNotification(
                FirestoreNotification(
                    projectId = projId,
                    findingId = findingId.toString(),
                    recipientEmail = picEmail,
                    senderUid = user?.uid ?: "",
                    senderName = actor,
                    senderEmail = email,
                    title = "⚠️ Tugas Temuan Baru: ${findingItem?.ruleId ?: "#$findingId"}",
                    message = "Anda ditunjuk oleh $actor sebagai PIC di ${findingItem?.area ?: "Area Lapangan"}.",
                    type = "ASSIGNMENT",
                    isCritical = findingItem?.isCritical ?: false
                )
            )

            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Temuan berhasil ditugaskan kepada PIC: $picName"
            } else {
                "Finding successfully assigned to PIC: $picName"
            }
            SyncScheduler.enqueueImmediateSync(getApplication())
            _uiMessage.emit(msg)
        }
    }

    fun submitPicAction(
        findingId: Int,
        correctiveAction: String,
        evidenceNotes: String
    ) {
        viewModelScope.launch {
            val user = currentUser.value
            val actor = if (_inspectorName.value.isNotBlank()) _inspectorName.value else (user?.displayName ?: "Field PIC")
            val email = user?.email ?: ""
            val role = if (_inspectorRole.value.isNotBlank()) _inspectorRole.value else "PIC / Field Team"

            repository.submitPicAction(
                findingId = findingId,
                correctiveAction = correctiveAction,
                evidenceNotes = evidenceNotes,
                submittedBy = actor,
                submittedByEmail = email,
                actorRole = role
            )

            val projId = activeProject.value?.id ?: "default_proj"
            firestoreManager.saveFindingToFirestore(
                FirestoreFindingDocument(
                    id = "finding_$findingId",
                    projectId = projId,
                    actionTaken = correctiveAction,
                    status = "IN_REVIEW",
                    actionSubmittedByUid = user?.uid ?: "",
                    actionSubmittedTimestamp = System.currentTimeMillis()
                )
            )
            firestoreManager.recordAuditActionLog(
                FirestoreActionLog(
                    findingId = "finding_$findingId",
                    projectId = projId,
                    actorUid = user?.uid ?: "",
                    actorName = actor,
                    actorRole = role,
                    actionType = "ACTION_SUBMITTED",
                    oldStatus = "OPEN",
                    newStatus = "IN_REVIEW",
                    remarks = correctiveAction
                )
            )
            val findingItem = findings.value.find { it.id == findingId }
            firestoreManager.pushCrossUserNotification(
                FirestoreNotification(
                    projectId = projId,
                    findingId = findingId.toString(),
                    recipientUid = findingItem?.inspectorUid ?: "",
                    recipientEmail = findingItem?.inspectorEmail ?: "",
                    senderUid = user?.uid ?: "",
                    senderName = actor,
                    senderEmail = email,
                    title = "🛠️ Perbaikan Diajukan: ${findingItem?.ruleId ?: "#$findingId"}",
                    message = "$actor mengajukan tindakan perbaikan di ${findingItem?.area ?: "Area"}. Silakan verifikasi.",
                    type = "ACTION_SUBMITTED",
                    isCritical = false
                )
            )

            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Tindakan perbaikan berhasil diajukan untuk review auditor."
            } else {
                "Corrective action submitted for auditor review."
            }
            SyncScheduler.enqueueImmediateSync(getApplication())
            _uiMessage.emit(msg)
        }
    }

    fun verifyAndCloseFinding(
        findingId: Int,
        rating: Int,
        comment: String
    ) {
        viewModelScope.launch {
            val user = currentUser.value
            val actor = if (_inspectorName.value.isNotBlank()) _inspectorName.value else (user?.displayName ?: "Lead Auditor")
            val email = user?.email ?: ""
            val role = if (_inspectorRole.value.isNotBlank()) _inspectorRole.value else "Lead HSE Inspector"

            repository.verifyAndCloseFinding(
                findingId = findingId,
                verifiedBy = actor,
                verifiedByEmail = email,
                rating = rating,
                comment = comment,
                actorRole = role
            )

            val projId = activeProject.value?.id ?: "default_proj"
            firestoreManager.saveFindingToFirestore(
                FirestoreFindingDocument(
                    id = "finding_$findingId",
                    projectId = projId,
                    status = "CLOSED",
                    picRating = rating,
                    verificationRemarks = comment,
                    verifiedByUid = user?.uid ?: "",
                    verifiedTimestamp = System.currentTimeMillis()
                )
            )
            firestoreManager.recordAuditActionLog(
                FirestoreActionLog(
                    findingId = "finding_$findingId",
                    projectId = projId,
                    actorUid = user?.uid ?: "",
                    actorName = actor,
                    actorRole = role,
                    actionType = "VERIFIED_AND_CLOSED",
                    oldStatus = "IN_REVIEW",
                    newStatus = "CLOSED",
                    remarks = comment,
                    rating = rating
                )
            )
            val findingItem = findings.value.find { it.id == findingId }
            firestoreManager.pushCrossUserNotification(
                FirestoreNotification(
                    projectId = projId,
                    findingId = findingId.toString(),
                    recipientEmail = findingItem?.assignedPicEmail ?: "",
                    senderUid = user?.uid ?: "",
                    senderName = actor,
                    senderEmail = email,
                    title = "✅ Temuan Ditutup ($rating/5 ⭐): ${findingItem?.ruleId ?: "#$findingId"}",
                    message = "Audit di ${findingItem?.area ?: "Area"} telah diverifikasi & ditutup oleh $actor.",
                    type = "CLOSED",
                    isCritical = false
                )
            )

            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Temuan berhasil diverifikasi dan DITUTUP dengan Rating $rating ⭐"
            } else {
                "Finding verified and CLOSED with Rating $rating ⭐"
            }
            SyncScheduler.enqueueImmediateSync(getApplication())
            _uiMessage.emit(msg)
        }
    }

    fun reopenFinding(
        findingId: Int,
        reopenReason: String
    ) {
        viewModelScope.launch {
            val user = currentUser.value
            val actor = if (_inspectorName.value.isNotBlank()) _inspectorName.value else (user?.displayName ?: "Lead Auditor")
            val email = user?.email ?: ""
            val role = if (_inspectorRole.value.isNotBlank()) _inspectorRole.value else "Lead HSE Inspector"

            repository.reopenFinding(
                findingId = findingId,
                reopenReason = reopenReason,
                actorName = actor,
                actorEmail = email,
                actorRole = role
            )

            val projId = activeProject.value?.id ?: "default_proj"
            firestoreManager.saveFindingToFirestore(
                FirestoreFindingDocument(
                    id = "finding_$findingId",
                    projectId = projId,
                    status = "REJECTED"
                )
            )
            firestoreManager.recordAuditActionLog(
                FirestoreActionLog(
                    findingId = "finding_$findingId",
                    projectId = projId,
                    actorUid = user?.uid ?: "",
                    actorName = actor,
                    actorRole = role,
                    actionType = "REOPENED_BY_INSPECTOR",
                    oldStatus = "IN_REVIEW",
                    newStatus = "REJECTED",
                    remarks = reopenReason
                )
            )
            val findingItem = findings.value.find { it.id == findingId }
            firestoreManager.pushCrossUserNotification(
                FirestoreNotification(
                    projectId = projId,
                    findingId = findingId.toString(),
                    recipientEmail = findingItem?.assignedPicEmail ?: "",
                    senderUid = user?.uid ?: "",
                    senderName = actor,
                    senderEmail = email,
                    title = "🔄 Perbaikan Ditolak: ${findingItem?.ruleId ?: "#$findingId"}",
                    message = "Catatan $actor: $reopenReason",
                    type = "REOPENED",
                    isCritical = true
                )
            )
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Temuan ditolak dan dibuka kembali untuk perbaikan ulang."
            } else {
                "Finding rejected and reopened for corrective revision."
            }
            SyncScheduler.enqueueImmediateSync(getApplication())
            _uiMessage.emit(msg)
        }
    }

    fun sendFindingMessage(findingId: Int, message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            val user = currentUser.value
            val actor = if (_inspectorName.value.isNotBlank()) _inspectorName.value else (user?.displayName ?: "User")
            val email = user?.email ?: ""
            val role = if (_inspectorRole.value.isNotBlank()) _inspectorRole.value else "HSE Team"
            val projId = activeProject.value?.id ?: "lower-zakum-operations"

            val docs = repository.sendFindingCommentOrReminder(
                findingId = findingId,
                actorUid = user?.uid ?: "",
                actorName = actor,
                actorEmail = email,
                actorRole = role,
                message = message,
                isReminder = false,
                projectId = projId
            )
            if (docs != null) {
                firestoreManager.recordAuditActionLog(docs.first)
                firestoreManager.pushCrossUserNotification(docs.second)
                _uiMessage.emit(if (_appLanguage.value == AppLanguage.ID) "Pesan terkirim ke tim terkait." else "Message sent to assignee.")
            }
        }
    }

    fun sendReminderPing(findingId: Int) {
        viewModelScope.launch {
            val user = currentUser.value
            val actor = if (_inspectorName.value.isNotBlank()) _inspectorName.value else (user?.displayName ?: "Inspector")
            val email = user?.email ?: ""
            val role = if (_inspectorRole.value.isNotBlank()) _inspectorRole.value else "HSE Team"
            val projId = activeProject.value?.id ?: "lower-zakum-operations"

            val defaultReminder = if (_appLanguage.value == AppLanguage.ID) {
                "🔔 Pengingat Prioritas dari $actor: Mohon segera perbarui status tindak lanjut temuan ini."
            } else {
                "🔔 Priority Reminder from $actor: Please update corrective action progress on this finding."
            }

            val docs = repository.sendFindingCommentOrReminder(
                findingId = findingId,
                actorUid = user?.uid ?: "",
                actorName = actor,
                actorEmail = email,
                actorRole = role,
                message = defaultReminder,
                isReminder = true,
                projectId = projId
            )
            if (docs != null) {
                firestoreManager.recordAuditActionLog(docs.first)
                firestoreManager.pushCrossUserNotification(docs.second)
                _uiMessage.emit(if (_appLanguage.value == AppLanguage.ID) "🔔 Reminder push notification dikirim!" else "🔔 Reminder push notification sent!")
            }
        }
    }

    fun loadMoreFeed() {
        _feedLimit.value += 25
    }

    fun loadMoreNotifications() {
        _notifLimit.value += 25
    }

    fun loadRecordDetail(recordId: Int, onLoaded: (InspectionRecord) -> Unit) {
        viewModelScope.launch {
            val record = repository.getInspectionRecordById(recordId)
            if (record != null) {
                onLoaded(record)
            }
        }
    }

    fun refreshAllData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            firestoreManager.refreshData()
            firestoreManager.syncDeltaForActiveProject()
            SyncScheduler.enqueueImmediateSync(getApplication())
            _isRefreshing.value = false
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Sinkronisasi delta & antrean cloud selesai."
            } else {
                "Delta sync & cloud queue refreshed."
            }
            _uiMessage.emit(msg)
        }
    }

    fun retryPendingSync() {
        SyncScheduler.enqueueImmediateSync(getApplication())
        viewModelScope.launch {
            firestoreManager.syncDeltaForActiveProject()
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Antrean sinkronisasi dijalankan di latar belakang."
            } else {
                "Background sync queue triggered."
            }
            _uiMessage.emit(msg)
        }
    }

    fun deleteReadNotifications() {
        viewModelScope.launch {
            repository.deleteReadNotifications()
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Notifikasi yang sudah dibaca dibersihkan."
            } else {
                "Read notifications cleared."
            }
            _uiMessage.emit(msg)
        }
    }

    fun attachFindingEvidencePhoto(findingId: Int, sourceUri: Uri) {
        viewModelScope.launch {
            val result = EvidencePhotoHelper.compressAndSaveEvidence(
                context = getApplication(),
                sourceUri = sourceUri,
                findingId = findingId
            )
            if (result.isSuccess) {
                val file = result.getOrThrow()
                repository.attachFindingEvidencePhoto(findingId, file.absolutePath)
                SyncScheduler.enqueueImmediateSync(getApplication())
                val msg = if (_appLanguage.value == AppLanguage.ID) {
                    "Foto bukti dikompresi (${file.length() / 1024} KB) & masuk antrean unggah."
                } else {
                    "Evidence photo compressed (${file.length() / 1024} KB) & queued for upload."
                }
                _uiMessage.emit(msg)
            } else {
                val msg = if (_appLanguage.value == AppLanguage.ID) {
                    "Gagal memproses foto bukti."
                } else {
                    "Failed to process evidence photo."
                }
                _uiMessage.emit(msg)
            }
        }
    }

    fun exportRecordToPdf(record: InspectionRecord, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ReportExporter.exportAuditPdf(
                    context = getApplication(),
                    record = record,
                    checklistItems = checklistItems.value,
                    language = _appLanguage.value
                )
            }
            onReady(uri)
        }
    }

    fun exportBatchCsv(onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ReportExporter.exportBatchCsv(
                    context = getApplication(),
                    summaries = feedSummaries.value,
                    findings = findings.value
                )
            }
            onReady(uri)
        }
    }

    fun markNotificationRead(id: Int) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead()
            val msg = if (_appLanguage.value == AppLanguage.ID) "Semua notifikasi ditandai telah dibaca." else "All notifications marked as read."
            _uiMessage.emit(msg)
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            repository.clearNotifications()
        }
    }

    fun nextQuestion() {
        val listSize = filteredChecklistItems.value.size
        if (_currentIndex.value < listSize - 1) {
            _currentIndex.value += 1
        }
    }

    fun prevQuestion() {
        if (_currentIndex.value > 0) {
            _currentIndex.value -= 1
        }
    }

    fun selectQuestionIndex(index: Int) {
        if (index in filteredChecklistItems.value.indices) {
            _currentIndex.value = index
        }
    }

    fun saveDraft() {
        viewModelScope.launch {
            val msg = AppStrings.get("draft_saved_msg", _appLanguage.value)
            _uiMessage.emit(msg)
            _isInspecting.value = false
        }
    }

    fun resetDraft() {
        viewModelScope.launch {
            repository.clearActiveAnswers()
            _currentIndex.value = 0
            val msg = AppStrings.get("draft_cleared_msg", _appLanguage.value)
            _uiMessage.emit(msg)
        }
    }

    fun updateFindingStatus(findingId: Int, status: String, correctiveAction: String) {
        viewModelScope.launch {
            repository.updateFinding(findingId, status, correctiveAction)
            SyncScheduler.enqueueImmediateSync(getApplication())
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Status temuan berhasil diubah menjadi $status"
            } else {
                "Finding status updated to $status"
            }
            _uiMessage.emit(msg)
        }
    }

    fun deleteFinding(findingId: Int) {
        viewModelScope.launch {
            repository.deleteFinding(findingId)
            val msg = if (_appLanguage.value == AppLanguage.ID) "Temuan dihapus." else "Finding deleted."
            _uiMessage.emit(msg)
        }
    }

    fun exportChecklistJson(): String {
        return checklistStore.exportAsJson()
    }

    fun getSampleTemplateJson(): String {
        return checklistStore.getSampleTemplateJson()
    }

    fun importChecklistJson(jsonStr: String): Boolean {
        val result = checklistStore.importFromJson(jsonStr)
        return if (result.isSuccess) {
            viewModelScope.launch {
                val count = result.getOrNull()?.size ?: 0
                val prefix = AppStrings.get("import_success", _appLanguage.value)
                val suffix = AppStrings.get("import_items_count", _appLanguage.value)
                _uiMessage.emit("$prefix $count $suffix")
            }
            true
        } else {
            viewModelScope.launch {
                val errorMsg = AppStrings.get("import_error", _appLanguage.value)
                _uiMessage.emit(errorMsg)
            }
            false
        }
    }

    fun resetChecklistToDefault() {
        checklistStore.resetToDefault()
        viewModelScope.launch {
            val lang = _appLanguage.value
            _uiMessage.emit(if (lang == AppLanguage.ID) "Checklist di-reset ke 42 aturan standar IOGP & OSHA." else "Checklist reset to 42 standard IOGP & OSHA rules.")
        }
    }

    fun submitReport() {
        viewModelScope.launch {
            _isSubmitting.value = true
            val applicableItems = filteredChecklistItems.value
            val applicableItemIds = applicableItems.map { it.id }.toSet()

            val activeList = _activeAnswers.value.values
                .filter { applicableItemIds.contains(it.id) }
                .map {
                    ActiveAnswer(
                        questionId = it.id,
                        answer = it.answer.name,
                        notes = it.notes,
                        assignedPicName = it.assignedPicName,
                        assignedPicEmail = it.assignedPicEmail,
                        assignedPicRole = it.assignedPicRole
                    )
                }

            val chosenModules = if (_selectedCategories.value.isEmpty()) {
                availableCategories.value
            } else {
                _selectedCategories.value.toList()
            }

            val user = currentUser.value
            val projId = activeProject.value?.id ?: ""
            val result = repository.submitInspection(
                inspectorName = _inspectorName.value,
                area = _area.value,
                activeAnswers = activeList,
                checklistItems = applicableItems,
                applicableModules = chosenModules,
                scriptUrl = _scriptUrl.value,
                isGasSyncEnabled = _isGasSyncEnabled.value,
                firebaseUrl = _firebaseUrl.value,
                isFirebaseSyncEnabled = _isFirebaseSyncEnabled.value,
                userUid = user?.uid ?: "",
                userEmail = user?.email ?: "",
                projectId = projId,
                company = _companyName.value,
                notifPicAlerts = _notifPicAlerts.value,
                notifCriticalAlerts = _notifCriticalAlerts.value
            )

            // Push cross-user notifications for newly assigned PICs & critical alerts
            if (projId.isNotBlank()) {
                val itemMap = applicableItems.associateBy { it.id }
                activeList.filter { it.answer == AnswerType.NO.name }.forEach { ans ->
                    val rule = itemMap[ans.questionId]
                    if (ans.assignedPicName.isNotBlank() && ans.assignedPicEmail.isNotBlank()) {
                        firestoreManager.pushCrossUserNotification(
                            FirestoreNotification(
                                projectId = projId,
                                findingId = ans.questionId,
                                recipientEmail = ans.assignedPicEmail,
                                senderUid = user?.uid ?: "",
                                senderName = _inspectorName.value,
                                senderEmail = user?.email ?: "",
                                title = "⚠️ Tugas Temuan Baru: ${ans.questionId}",
                                message = "Ditugaskan oleh ${_inspectorName.value} di ${_area.value}: ${rule?.item_text ?: ""}",
                                type = "ASSIGNMENT",
                                isCritical = rule?.is_critical ?: false
                            )
                        )
                    } else if (rule?.is_critical == true) {
                        firestoreManager.pushCrossUserNotification(
                            FirestoreNotification(
                                projectId = projId,
                                findingId = ans.questionId,
                                senderUid = user?.uid ?: "",
                                senderName = _inspectorName.value,
                                senderEmail = user?.email ?: "",
                                title = "🚨 Temuan Kritis LSR: ${ans.questionId}",
                                message = "Risiko fatal di ${_area.value} (${_companyName.value}): ${rule.item_text}",
                                type = "CRITICAL",
                                isCritical = true,
                                isBroadcast = true
                            )
                        )
                    }
                }
            }

            SyncScheduler.enqueueImmediateSync(getApplication())

            _isSubmitting.value = false
            if (result.isSuccess) {
                _uiMessage.emit(AppStrings.get("inspection_success_msg", _appLanguage.value))
                _isInspecting.value = false
                _currentIndex.value = 0
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Gagal mengirim data."
                _uiMessage.emit(errorMsg)
                _isInspecting.value = false
                _currentIndex.value = 0
            }
        }
    }
}
