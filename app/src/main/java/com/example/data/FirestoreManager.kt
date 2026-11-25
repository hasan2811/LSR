package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreManager(private val context: Context) {

    private val tag = "FirestoreManager"
    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            runCatching {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(100L * 1024 * 1024)
                            .build()
                    )
                    .build()
            }
        }
    }

    private val _users = MutableStateFlow<List<FirestoreUser>>(emptyList())
    val users: StateFlow<List<FirestoreUser>> = _users.asStateFlow()

    private val _projects = MutableStateFlow<List<FirestoreProject>>(emptyList())
    val projects: StateFlow<List<FirestoreProject>> = _projects.asStateFlow()

    private val _activeProject = MutableStateFlow<FirestoreProject?>(null)
    val activeProject: StateFlow<FirestoreProject?> = _activeProject.asStateFlow()

    private val _remoteFindings = MutableStateFlow<List<FirestoreFindingDocument>>(emptyList())
    val remoteFindings: StateFlow<List<FirestoreFindingDocument>> = _remoteFindings.asStateFlow()

    private val _remoteNotifications = MutableStateFlow<List<FirestoreNotification>>(emptyList())
    val remoteNotifications: StateFlow<List<FirestoreNotification>> = _remoteNotifications.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?> (null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var usersListener: ListenerRegistration? = null
    private var projectsListener: ListenerRegistration? = null
    private var findingsListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null

    private val prefs = context.getSharedPreferences("lsr_firestore_prefs", Context.MODE_PRIVATE)

    init {
        // Load fallback/initial data from cache
        loadCachedData()
        startRealtimeListeners()
    }

    private fun loadCachedData() {
        val cachedActiveProjectId = prefs.getString("active_project_id", "") ?: ""
        val cachedActiveProjectName = prefs.getString("active_project_name", "") ?: ""

        // Production state: No hardcoded dummy data!
        _users.value = emptyList()
        _projects.value = emptyList()

        if (cachedActiveProjectId.isNotBlank() && cachedActiveProjectName.isNotBlank()) {
            _activeProject.value = FirestoreProject(
                id = cachedActiveProjectId,
                name = cachedActiveProjectName
            )
        } else {
            _activeProject.value = null
        }
    }

    fun startRealtimeListeners() {
        try {
            // Listen to `users` collection (bounded to 100 docs to control reads at scale)
            usersListener?.remove()
            usersListener = firestore.collection("users")
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(tag, "Listen failed for users collection: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val parsedUsers = snapshot.documents.mapNotNull { doc ->
                            try {
                                val uid = doc.getString("uid") ?: doc.id
                                val displayName = doc.getString("displayName") ?: (doc.getString("email")?.substringBefore("@") ?: "User")
                                val email = doc.getString("email") ?: ""
                                val company = doc.getString("company") ?: "Unassigned"
                                val position = doc.getString("position") ?: "Not Set"
                                val photoURL = doc.getString("photoURL")
                                val aiEnabled = doc.getBoolean("aiEnabled") ?: false

                                @Suppress("UNCHECKED_CAST")
                                val projectIds = when (val p = doc.get("projectIds")) {
                                    is List<*> -> p.mapNotNull { it?.toString() }
                                    is String -> listOf(p)
                                    else -> emptyList()
                                }

                                FirestoreUser(
                                    uid = uid,
                                    displayName = displayName,
                                    email = email,
                                    company = company,
                                    position = position,
                                    photoURL = photoURL,
                                    aiEnabled = aiEnabled,
                                    projectIds = projectIds
                                )
                            } catch (e: Exception) {
                                Log.e(tag, "Error parsing user doc ${doc.id}", e)
                                null
                            }
                        }
                        _users.value = parsedUsers
                        Log.d(tag, "Firestore Realtime: Loaded ${parsedUsers.size} users")
                    }
                }

            // Listen to `projects` collection (bounded to 50 workspaces)
            projectsListener?.remove()
            projectsListener = firestore.collection("projects")
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(tag, "Listen failed for projects collection: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val parsedProjects = snapshot.documents.mapNotNull { doc ->
                            try {
                                val id = doc.getString("id") ?: doc.id
                                val name = doc.getString("name") ?: "Project $id"
                                val createdAt = doc.getString("createdAt") ?: ""
                                val ownerUid = doc.getString("ownerUid") ?: ""
                                val isOpen = doc.getBoolean("isOpen") ?: false

                                @Suppress("UNCHECKED_CAST")
                                val memberUids = when (val m = doc.get("memberUids")) {
                                    is List<*> -> m.mapNotNull { it?.toString() }
                                    else -> emptyList()
                                }

                                @Suppress("UNCHECKED_CAST")
                                val customCompanies = when (val c = doc.get("customCompanies")) {
                                    is List<*> -> c.mapNotNull { it?.toString() }
                                    else -> emptyList()
                                }

                                @Suppress("UNCHECKED_CAST")
                                val customLocations = when (val l = doc.get("customLocations")) {
                                    is List<*> -> l.mapNotNull { it?.toString() }
                                    else -> emptyList()
                                }

                                @Suppress("UNCHECKED_CAST")
                                val customObservationCategories = when (val o = doc.get("customObservationCategories")) {
                                    is List<*> -> o.mapNotNull { it?.toString() }
                                    else -> emptyList()
                                }

                                @Suppress("UNCHECKED_CAST")
                                val roles = (doc.get("roles") as? Map<String, Any>) ?: emptyMap()
                                val totalFindings = (doc.getLong("totalFindings") ?: 0L).toInt()
                                val openFindings = (doc.getLong("openFindings") ?: 0L).toInt()
                                val closedFindings = (doc.getLong("closedFindings") ?: 0L).toInt()

                                FirestoreProject(
                                    id = id,
                                    name = name,
                                    createdAt = createdAt,
                                    ownerUid = ownerUid,
                                    isOpen = isOpen,
                                    memberUids = memberUids,
                                    customCompanies = customCompanies,
                                    customLocations = customLocations,
                                    customObservationCategories = customObservationCategories,
                                    roles = roles,
                                    totalFindings = totalFindings,
                                    openFindings = openFindings,
                                    closedFindings = closedFindings
                                )
                            } catch (e: Exception) {
                                Log.e(tag, "Error parsing project doc ${doc.id}", e)
                                null
                            }
                        }
                        _projects.value = parsedProjects
                        Log.d(tag, "Firestore Realtime: Loaded ${parsedProjects.size} projects")

                        // If active project is not set or not in list, pick appropriate
                        val current = _activeProject.value
                        if (current == null || parsedProjects.none { it.id == current.id }) {
                            val cachedId = prefs.getString("active_project_id", "") ?: ""
                            val match = parsedProjects.find { it.id == cachedId } ?: parsedProjects.firstOrNull()
                            if (match != null) {
                                selectActiveProject(match)
                            }
                        } else {
                            // Refresh current project data with new snapshot
                            parsedProjects.find { it.id == current.id }?.let {
                                _activeProject.value = it
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(tag, "Error starting Firestore listeners", e)
        }
    }

    suspend fun ensureDefaultWorkspace(
        creatorUid: String,
        creatorEmail: String,
        creatorCompany: String
    ): Result<FirestoreProject> {
        return withContext(Dispatchers.IO) {
            try {
                val existing = firestore.collection("projects").get().await()
                if (!existing.isEmpty) {
                    val firstDoc = existing.documents.first()
                    val p = FirestoreProject(
                        id = firstDoc.id,
                        name = firstDoc.getString("name") ?: firstDoc.id,
                        code = firstDoc.getString("code") ?: "LZ-HSE",
                        ownerUid = firstDoc.getString("ownerUid") ?: "",
                        isOpen = firstDoc.getBoolean("isOpen") ?: true
                    )
                    selectActiveProject(p)
                    return@withContext Result.success(p)
                }

                val defaultProject = FirestoreProject(
                    id = "lower-zakum-operations",
                    name = "Lower Zakum Offshore Operations",
                    code = "LZ-HSE",
                    createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()),
                    ownerUid = creatorUid,
                    isOpen = true,
                    memberUids = if (creatorUid.isNotBlank()) listOf(creatorUid) else emptyList(),
                    customCompanies = listOf(
                        creatorCompany.ifBlank { "Lower Zakum Operations" },
                        "HSSE Tech Operations"
                    ),
                    customLocations = listOf(
                        "Wellhead Platform (WHP-01)",
                        "Central Complex (CC)",
                        "Gas Treatment Plant (GTP)",
                        "Drilling Rig Deck",
                        "Living Quarters (LQ)",
                        "Flare Platform & Bridge"
                    ),
                    customObservationCategories = listOf(
                        "Safe Act",
                        "Unsafe Act",
                        "Unsafe Condition",
                        "Near Miss",
                        "LSR Non-Compliance"
                    )
                )

                val projectMap = hashMapOf<String, Any>(
                    "id" to defaultProject.id,
                    "name" to defaultProject.name,
                    "code" to defaultProject.code,
                    "createdAt" to defaultProject.createdAt,
                    "ownerUid" to defaultProject.ownerUid,
                    "isOpen" to defaultProject.isOpen,
                    "memberUids" to defaultProject.memberUids,
                    "customCompanies" to defaultProject.customCompanies,
                    "customLocations" to defaultProject.customLocations,
                    "customObservationCategories" to defaultProject.customObservationCategories
                )

                firestore.collection("projects").document(defaultProject.id).set(projectMap, SetOptions.merge()).await()
                selectActiveProject(defaultProject)
                Log.d(tag, "Initialized default workspace in Firestore: ${defaultProject.name}")
                Result.success(defaultProject)
            } catch (e: Exception) {
                Log.e(tag, "Failed to ensure default workspace: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun refreshData(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Fetch users
                val usersSnap = firestore.collection("users").get().await()
                if (!usersSnap.isEmpty) {
                    val parsed = usersSnap.documents.mapNotNull { doc ->
                        val uid = doc.getString("uid") ?: doc.id
                        val displayName = doc.getString("displayName") ?: (doc.getString("email")?.substringBefore("@") ?: "User")
                        val email = doc.getString("email") ?: ""
                        val company = doc.getString("company") ?: "Unassigned"
                        val position = doc.getString("position") ?: "Not Set"
                        val photoURL = doc.getString("photoURL")
                        val aiEnabled = doc.getBoolean("aiEnabled") ?: false

                        @Suppress("UNCHECKED_CAST")
                        val projectIds = when (val p = doc.get("projectIds")) {
                            is List<*> -> p.mapNotNull { it?.toString() }
                            is String -> listOf(p)
                            else -> emptyList()
                        }

                        FirestoreUser(
                            uid = uid,
                            displayName = displayName,
                            displayNameLowercase = displayName.lowercase(),
                            email = email,
                            company = company,
                            position = position,
                            photoURL = photoURL,
                            aiEnabled = aiEnabled,
                            projectIds = projectIds,
                            searchKeywords = listOf(displayName.lowercase(), email.lowercase())
                        )
                    }
                    if (parsed.isNotEmpty()) {
                        _users.value = parsed
                    }
                }

                // Fetch projects
                val projSnap = firestore.collection("projects").get().await()
                if (!projSnap.isEmpty) {
                    val parsedProjects = projSnap.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val name = doc.getString("name") ?: "Project $id"
                        val createdAt = doc.getString("createdAt") ?: ""
                        val ownerUid = doc.getString("ownerUid") ?: ""
                        val isOpen = doc.getBoolean("isOpen") ?: false

                        @Suppress("UNCHECKED_CAST")
                        val memberUids = when (val m = doc.get("memberUids")) {
                            is List<*> -> m.mapNotNull { it?.toString() }
                            else -> emptyList()
                        }

                        @Suppress("UNCHECKED_CAST")
                        val customCompanies = when (val c = doc.get("customCompanies")) {
                            is List<*> -> c.mapNotNull { it?.toString() }
                            else -> emptyList()
                        }

                        @Suppress("UNCHECKED_CAST")
                        val customLocations = when (val l = doc.get("customLocations")) {
                            is List<*> -> l.mapNotNull { it?.toString() }
                            else -> emptyList()
                        }

                        @Suppress("UNCHECKED_CAST")
                        val customObservationCategories = when (val o = doc.get("customObservationCategories")) {
                            is List<*> -> o.mapNotNull { it?.toString() }
                            else -> emptyList()
                        }

                        @Suppress("UNCHECKED_CAST")
                        val roles = (doc.get("roles") as? Map<String, Any>) ?: emptyMap()

                        FirestoreProject(
                            id = id,
                            name = name,
                            createdAt = createdAt,
                            ownerUid = ownerUid,
                            isOpen = isOpen,
                            memberUids = memberUids,
                            customCompanies = customCompanies,
                            customLocations = customLocations,
                            customObservationCategories = customObservationCategories,
                            roles = roles
                        )
                    }
                    if (parsedProjects.isNotEmpty()) {
                        _projects.value = parsedProjects
                    }
                }

                _isLoading.value = false
                Result.success(Unit)
            } catch (e: Exception) {
                _isLoading.value = false
                Log.e(tag, "Firestore refresh error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    fun fetchAllNow() {
        CoroutineScope(Dispatchers.IO).launch {
            refreshData()
        }
    }

    /**
     * Get accessible projects for a specific user based on Firestore structure:
     * - project.isOpen == true OR
     * - project.memberUids contains user.uid OR
     * - project.ownerUid == user.uid OR
     * - user.projectIds contains project.id
     */
    fun getAccessibleProjectsForUser(userUid: String, userEmail: String): List<FirestoreProject> {
        val allProjects = _projects.value
        val allUsers = _users.value
        val userObj = allUsers.find { it.uid == userUid || (userEmail.isNotBlank() && it.email.equals(userEmail, ignoreCase = true)) }

        val userProjectIds = userObj?.projectIds ?: emptyList()

        val matching = allProjects.filter { proj ->
            proj.hasUserAccess(userUid, userProjectIds)
        }

        // If no restricted projects matched, return all available projects or open ones
        return if (matching.isNotEmpty()) matching else allProjects
    }

    /**
     * Set active project, save preference, and scope findings listener to active workspace only.
     */
    fun selectActiveProject(project: FirestoreProject) {
        val changed = _activeProject.value?.id != project.id
        _activeProject.value = project
        prefs.edit()
            .putString("active_project_id", project.id)
            .putString("active_project_name", project.name)
            .apply()
        if (changed || findingsListener == null) {
            startScopedFindingsListener(project.id)
        }
    }

    /**
     * Workspace-scoped realtime listener for findings in the active project (bounded to 100 latest updates).
     */
    fun startScopedFindingsListener(projectId: String) {
        if (projectId.isBlank()) return
        try {
            findingsListener?.remove()
            findingsListener = firestore.collection("projects")
                .document(projectId)
                .collection("findings")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(tag, "Scoped findings listen note for $projectId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val docs = snapshot.documents.mapNotNull { parseFindingDoc(it, projectId) }
                        _remoteFindings.value = docs
                        val now = System.currentTimeMillis()
                        _lastSyncTimestamp.value = now
                        prefs.edit().putLong("last_findings_sync_$projectId", now).apply()
                    }
                }
        } catch (e: Exception) {
            Log.w(tag, "Failed starting scoped findings listener: ${e.message}")
        }
    }

    /**
     * Incremental delta sync for the active project (fetches only docs updated since lastSync).
     */
    suspend fun syncDeltaForActiveProject(): Result<Int> = withContext(Dispatchers.IO) {
        val projId = _activeProject.value?.id ?: return@withContext Result.success(0)
        try {
            val lastSync = prefs.getLong("last_findings_sync_$projId", 0L)
            val baseRef = firestore.collection("projects").document(projId).collection("findings")
            val snap = if (lastSync > 0L) {
                baseRef.whereGreaterThan("updatedAt", lastSync).limit(100).get().await()
            } else {
                baseRef.orderBy("updatedAt", Query.Direction.DESCENDING).limit(100).get().await()
            }
            val deltaDocs = snap.documents.mapNotNull { parseFindingDoc(it, projId) }
            if (deltaDocs.isNotEmpty()) {
                val merged = (_remoteFindings.value.associateBy { it.id } + deltaDocs.associateBy { it.id })
                    .values
                    .sortedByDescending { it.updatedAt }
                _remoteFindings.value = merged
            }
            val now = System.currentTimeMillis()
            _lastSyncTimestamp.value = now
            prefs.edit().putLong("last_findings_sync_$projId", now).apply()
            Result.success(deltaDocs.size)
        } catch (e: Exception) {
            Log.w(tag, "Delta sync warning: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseFindingDoc(doc: DocumentSnapshot, projectId: String): FirestoreFindingDocument? {
        return try {
            FirestoreFindingDocument(
                id = doc.getString("id") ?: doc.id,
                projectId = doc.getString("projectId") ?: projectId,
                checklistCategory = doc.getString("checklistCategory") ?: "",
                questionText = doc.getString("questionText") ?: "",
                description = doc.getString("description") ?: "",
                location = doc.getString("location") ?: "",
                severity = doc.getString("severity") ?: "HIGH",
                status = doc.getString("status") ?: "OPEN",
                creatorUid = doc.getString("creatorUid") ?: "",
                creatorName = doc.getString("creatorName") ?: "",
                creatorEmail = doc.getString("creatorEmail") ?: "",
                picName = doc.getString("picName") ?: "",
                picEmail = doc.getString("picEmail") ?: "",
                picRole = doc.getString("picRole") ?: "",
                actionTaken = doc.getString("actionTaken") ?: "",
                picRating = (doc.getLong("picRating") ?: 0L).toInt(),
                verificationRemarks = doc.getString("verificationRemarks") ?: "",
                createdAt = doc.getLong("createdAt") ?: 0L,
                updatedAt = doc.getLong("updatedAt") ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sync or create user profile in Firestore collection "users"
     */
    suspend fun syncUserToFirestore(
        uid: String,
        displayName: String,
        email: String,
        company: String,
        position: String,
        assignedProjectId: String? = null
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val existingDoc = firestore.collection("users").document(uid).get().await()
                val currentProjectIds = if (existingDoc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    (existingDoc.get("projectIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                } else emptyList()

                val updatedProjectIds = if (assignedProjectId != null && !currentProjectIds.contains(assignedProjectId)) {
                    currentProjectIds + assignedProjectId
                } else currentProjectIds

                val userMap = hashMapOf<String, Any>(
                    "uid" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "company" to company,
                    "position" to position,
                    "aiEnabled" to (existingDoc.getBoolean("aiEnabled") ?: false),
                    "projectIds" to updatedProjectIds
                )

                firestore.collection("users").document(uid).set(userMap, SetOptions.merge()).await()
                Log.d(tag, "Successfully synced user $uid to Firestore")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "Failed to sync user to Firestore: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get only users belonging to the specified Project Circle.
     * Enforces Circle / Tenant isolation.
     */
    fun getUsersInProjectCircle(projectId: String): List<FirestoreUser> {
        val proj = _projects.value.find { it.id == projectId } ?: _activeProject.value
        val allUsers = _users.value
        if (proj == null) return allUsers

        return allUsers.filter { u ->
            proj.isOpen ||
            proj.memberUids.contains(u.uid) ||
            proj.ownerUid == u.uid ||
            u.projectIds.contains(proj.id)
        }
    }

    /**
     * Search users by name within active project circle.
     * Cost-effective prefix matching with local & remote fallback.
     */
    suspend fun searchUsersInCircle(
        projectId: String,
        keyword: String,
        maxResults: Int = 10
    ): List<FirestoreUser> {
        val clean = keyword.trim().lowercase()
        if (clean.isBlank()) return getUsersInProjectCircle(projectId).take(maxResults)

        return withContext(Dispatchers.IO) {
            try {
                val circleUsers = getUsersInProjectCircle(projectId)
                val filtered = circleUsers.filter { u ->
                    u.displayName.lowercase().contains(clean) ||
                    u.email.lowercase().contains(clean) ||
                    u.position.lowercase().contains(clean) ||
                    u.company.lowercase().contains(clean)
                }.take(maxResults)

                if (filtered.isNotEmpty()) return@withContext filtered

                // Firestore prefix search query: whereGreaterThanOrEqualTo + whereLessThanOrEqualTo
                val snap = firestore.collection("users")
                    .whereGreaterThanOrEqualTo("displayNameLowercase", clean)
                    .whereLessThanOrEqualTo("displayNameLowercase", clean + "\uf8ff")
                    .limit(maxResults.toLong())
                    .get()
                    .await()

                if (!snap.isEmpty) {
                    snap.documents.mapNotNull { doc ->
                        val uid = doc.getString("uid") ?: doc.id
                        val displayName = doc.getString("displayName") ?: ""
                        val email = doc.getString("email") ?: ""
                        val company = doc.getString("company") ?: "Unassigned"
                        val position = doc.getString("position") ?: "Not Set"
                        @Suppress("UNCHECKED_CAST")
                        val pIds = (doc.get("projectIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        FirestoreUser(uid, displayName, clean, email, company, position, null, false, pIds)
                    }
                } else {
                    filtered
                }
            } catch (e: Exception) {
                Log.w(tag, "Search user error: ${e.message}")
                getUsersInProjectCircle(projectId).filter {
                    it.displayName.contains(clean, ignoreCase = true) || it.email.contains(clean, ignoreCase = true)
                }.take(maxResults)
            }
        }
    }

    /**
     * Map Firestore circle users to TeamRosterMember for PIC assignments
     */
    fun getTeamRosterForCircle(projectId: String): List<TeamRosterMember> {
        val circleUsers = getUsersInProjectCircle(projectId)
        if (circleUsers.isNotEmpty()) {
            return circleUsers.map { u ->
                TeamRosterMember(
                    name = u.displayName.ifBlank { u.email.substringBefore("@") },
                    email = u.email,
                    role = u.getEffectiveRole(),
                    department = u.getEffectiveCompany()
                )
            }
        }
        return getTeamRosterFromUsers()
    }

    /**
     * Save Denormalized Finding to Firestore (/projects/{projectId}/findings/{findingId})
     * Eliminates N+1 Reads across all clients.
     */
    suspend fun saveFindingToFirestore(finding: FirestoreFindingDocument): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val projId = finding.projectId.ifBlank { _activeProject.value?.id ?: "default_proj" }
                val docId = finding.id.ifBlank { firestore.collection("projects").document(projId).collection("findings").document().id }
                val finalDoc = finding.copy(id = docId, projectId = projId, updatedAt = System.currentTimeMillis())

                firestore.collection("projects")
                    .document(projId)
                    .collection("findings")
                    .document(docId)
                    .set(finalDoc, SetOptions.merge())
                    .await()

                Log.d(tag, "Denormalized finding $docId saved in Firestore project $projId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "Failed to save finding to Firestore: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Record Immutable Audit Trail in Firestore Subcollection
     * (/projects/{projectId}/findings/{findingId}/action_logs/{logId})
     */
    suspend fun recordAuditActionLog(log: FirestoreActionLog): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val projId = log.projectId.ifBlank { _activeProject.value?.id ?: "default_proj" }
                val findingId = log.findingId.ifBlank { "finding_${System.currentTimeMillis()}" }
                val logId = log.id.ifBlank { "log_${System.currentTimeMillis()}" }

                firestore.collection("projects")
                    .document(projId)
                    .collection("findings")
                    .document(findingId)
                    .collection("action_logs")
                    .document(logId)
                    .set(log.copy(id = logId, projectId = projId, findingId = findingId), SetOptions.merge())
                    .await()

                Log.d(tag, "Audit action log recorded in Firestore for finding $findingId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(tag, "Audit log write warning: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Map Firestore users to TeamRosterMember for PIC assignments
     */
    fun getTeamRosterFromUsers(): List<TeamRosterMember> {
        val currentUsers = _users.value
        if (currentUsers.isEmpty()) return emptyList()

        return currentUsers.map { u ->
            TeamRosterMember(
                name = u.displayName.ifBlank { u.email.substringBefore("@") },
                email = u.email,
                role = u.getEffectiveRole(),
                department = u.getEffectiveCompany()
            )
        }
    }

    fun syncFcmToken(uid: String) {
        if (uid.isBlank()) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    firestore.collection("users")
                        .document(uid)
                        .set(
                            mapOf("fcmToken" to token, "updatedAt" to System.currentTimeMillis()),
                            SetOptions.merge()
                        )
                        .addOnFailureListener { e ->
                            Log.w(tag, "FCM token save note: ${e.message}")
                        }
                }
            }
        }
    }

    /**
     * Scoped realtime listener for cross-user notifications, reminders, and finding messages.
     * Uses single-field index on `timestamp` (works out-of-the-box) and filters client-side for recipient.
     */
    fun startScopedNotificationsListener(
        projectId: String,
        userUid: String,
        userEmail: String
    ) {
        if (projectId.isBlank()) return
        try {
            notificationsListener?.remove()
            notificationsListener = firestore.collection("projects")
                .document(projectId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(60)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(tag, "Notifications listen note for $projectId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val parsed = snapshot.documents.mapNotNull { doc ->
                            runCatching {
                                FirestoreNotification(
                                    id = doc.getString("id") ?: doc.id,
                                    projectId = doc.getString("projectId") ?: projectId,
                                    projectName = doc.getString("projectName") ?: "",
                                    findingId = doc.getString("findingId") ?: "",
                                    recipientUid = doc.getString("recipientUid") ?: "",
                                    recipientEmail = doc.getString("recipientEmail") ?: "",
                                    senderUid = doc.getString("senderUid") ?: "",
                                    senderName = doc.getString("senderName") ?: "",
                                    senderEmail = doc.getString("senderEmail") ?: "",
                                    title = doc.getString("title") ?: "",
                                    message = doc.getString("message") ?: "",
                                    type = doc.getString("type") ?: "ASSIGNMENT",
                                    isCritical = doc.getBoolean("isCritical") ?: false,
                                    isBroadcast = doc.getBoolean("isBroadcast") ?: false,
                                    isRead = doc.getBoolean("isRead") ?: false,
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            }.getOrNull()
                        }.filter { n ->
                            val matchesUid = userUid.isNotBlank() && n.recipientUid == userUid
                            val matchesEmail = userEmail.isNotBlank() && n.recipientEmail.equals(userEmail, ignoreCase = true)
                            val isPublicOrCritical = n.isBroadcast || (n.recipientUid.isBlank() && n.recipientEmail.isBlank())
                            matchesUid || matchesEmail || isPublicOrCritical
                        }
                        _remoteNotifications.value = parsed
                    }
                }
        } catch (e: Exception) {
            Log.w(tag, "Failed starting notifications listener: ${e.message}")
        }
    }

    suspend fun pushCrossUserNotification(notification: FirestoreNotification): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val projId = notification.projectId.ifBlank { _activeProject.value?.id ?: "lower-zakum-operations" }
                val notifId = notification.id.ifBlank { "notif_${System.currentTimeMillis()}_${(100..999).random()}" }
                val finalDoc = notification.copy(
                    id = notifId,
                    projectId = projId,
                    projectName = notification.projectName.ifBlank { _activeProject.value?.name ?: "" }
                )
                firestore.collection("projects")
                    .document(projId)
                    .collection("notifications")
                    .document(notifId)
                    .set(finalDoc, SetOptions.merge())
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(tag, "Cross-user notification push note: ${e.message}")
                Result.failure(e)
            }
        }
    }

    fun observeFindingActionLogs(projectId: String, findingDocId: String): Flow<List<FirestoreActionLog>> = callbackFlow {
        val projId = projectId.ifBlank { _activeProject.value?.id ?: "lower-zakum-operations" }
        if (findingDocId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("projects")
            .document(projId)
            .collection("findings")
            .document(findingDocId)
            .collection("action_logs")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val logs = snapshot.documents.mapNotNull { doc ->
                        runCatching {
                            FirestoreActionLog(
                                id = doc.getString("id") ?: doc.id,
                                findingId = doc.getString("findingId") ?: findingDocId,
                                projectId = doc.getString("projectId") ?: projId,
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                actorUid = doc.getString("actorUid") ?: "",
                                actorName = doc.getString("actorName") ?: "",
                                actorRole = doc.getString("actorRole") ?: "",
                                actionType = doc.getString("actionType") ?: "COMMENT",
                                oldStatus = doc.getString("oldStatus") ?: "",
                                newStatus = doc.getString("newStatus") ?: "",
                                remarks = doc.getString("remarks") ?: "",
                                rating = (doc.getLong("rating") ?: 0L).toInt()
                            )
                        }.getOrNull()
                    }
                    trySend(logs)
                }
            }
        awaitClose { registration.remove() }
    }

    fun cleanup() {
        usersListener?.remove()
        projectsListener?.remove()
        findingsListener?.remove()
        notificationsListener?.remove()
    }
}
