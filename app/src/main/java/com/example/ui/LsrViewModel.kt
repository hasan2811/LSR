package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppNavTab {
    DASHBOARD, FEED, FINDINGS, PROFILE
}

class LsrViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("lsr_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = LsrRepository(database.lsrDao())
    val checklistStore = CustomChecklistStore(application)
    val authManager = FirebaseAuthManager(application)

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
        sharedPrefs.getString("firebase_url", "https://hssetech-e1710-default-rtdb.asia-southeast1.firebasedatabase.app")
            ?: "https://hssetech-e1710-default-rtdb.asia-southeast1.firebasedatabase.app"
    )
    val firebaseUrl: StateFlow<String> = _firebaseUrl.asStateFlow()

    private val _isFirebaseSyncEnabled = MutableStateFlow(sharedPrefs.getBoolean("is_firebase_sync_enabled", true))
    val isFirebaseEnabled: StateFlow<Boolean> = _isFirebaseSyncEnabled.asStateFlow()

    // Inspection Survey State
    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Active answers (Mapped by Question ID)
    private val _activeAnswers = MutableStateFlow<Map<String, AnswerData>>(emptyMap())
    val activeAnswers: StateFlow<Map<String, AnswerData>> = _activeAnswers.asStateFlow()

    // History logs
    val historyRecords: StateFlow<List<InspectionRecord>> = repository.inspectionRecordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Non-Compliance Findings list
    val findings: StateFlow<List<FindingItem>> = repository.findingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notifications & Audit History Logs
    val notifications: StateFlow<List<AppNotification>> = repository.notificationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = repository.unreadNotificationsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allActionLogs: StateFlow<List<ActionHistoryLog>> = repository.allActionLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getActionLogsFlow(findingId: Int): Flow<List<ActionHistoryLog>> = repository.getActionLogsFlow(findingId)

    // Pre-registered enterprise team roster for PIC selection
    val teamRoster: List<TeamRosterMember> = listOf(
        TeamRosterMember("Ahmad Fauzi", "ahmad.fauzi@company.com", "Rig Mechanical Supervisor", "Maintenance & Drilling"),
        TeamRosterMember("Budi Santoso", "budi.santoso@company.com", "Lead Electrical Engineer", "Electrical & Instrumentation"),
        TeamRosterMember("Siti Rahma", "siti.rahma@company.com", "Scaffolding Inspector & Lead", "Civil & Scaffolding"),
        TeamRosterMember("Doni Prasetyo", "doni.prasetyo@company.com", "Production Field Supervisor", "Field Operations"),
        TeamRosterMember("Hendra Wijaya", "hendra.wijaya@company.com", "Safety & Fire Officer", "HSSE Department"),
        TeamRosterMember("Rian Kurniawan", "rian.kurniawan@company.com", "Lifting Specialist", "Logistics & Material Handling"),
        TeamRosterMember("Eko Wahyudi", "eko.wahyudi@company.com", "Confined Space Lead", "Safety & Operations")
    )

    // UI Status
    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    init {
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
                }
            }
        }
    }

    fun selectTab(tab: AppNavTab) {
        _currentTab.value = tab
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
        viewModelScope.launch {
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
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Temuan berhasil ditugaskan kepada PIC: $picName"
            } else {
                "Finding successfully assigned to PIC: $picName"
            }
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
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Tindakan perbaikan berhasil diajukan untuk review auditor."
            } else {
                "Corrective action submitted for auditor review."
            }
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
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Temuan berhasil diverifikasi dan DITUTUP dengan Rating $rating ⭐"
            } else {
                "Finding verified and CLOSED with Rating $rating ⭐"
            }
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
            val msg = if (_appLanguage.value == AppLanguage.ID) {
                "Temuan ditolak dan dibuka kembali untuk perbaikan ulang."
            } else {
                "Finding rejected and reopened for corrective revision."
            }
            _uiMessage.emit(msg)
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
                userEmail = user?.email ?: ""
            )

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
