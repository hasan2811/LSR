package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreManager(private val context: Context) {

    private val tag = "FirestoreManager"
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _users = MutableStateFlow<List<FirestoreUser>>(emptyList())
    val users: StateFlow<List<FirestoreUser>> = _users.asStateFlow()

    private val _projects = MutableStateFlow<List<FirestoreProject>>(emptyList())
    val projects: StateFlow<List<FirestoreProject>> = _projects.asStateFlow()

    private val _activeProject = MutableStateFlow<FirestoreProject?>(null)
    val activeProject: StateFlow<FirestoreProject?> = _activeProject.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var usersListener: ListenerRegistration? = null
    private var projectsListener: ListenerRegistration? = null

    private val prefs = context.getSharedPreferences("lsr_firestore_prefs", Context.MODE_PRIVATE)

    init {
        // Load fallback/initial data from cache
        loadCachedData()
        startRealtimeListeners()
    }

    private fun loadCachedData() {
        val cachedActiveProjectId = prefs.getString("active_project_id", "") ?: ""
        // Provide rich default projects & users matching Firestore structure
        val defaultUsers = listOf(
            FirestoreUser(
                uid = "0GiEAk5NPbVuCiZAfvwQVhwucTN2",
                displayName = "Dika",
                email = "tb.dikakurnia@gmail.com",
                company = "Unassigned",
                position = "Lead HSE Inspector",
                photoURL = null,
                aiEnabled = false,
                projectIds = listOf("3n97CeSEhhQhFKQiuUvW")
            ),
            FirestoreUser(
                uid = "8xnYYEXlXZfvJK0w3BKMtWGjiit2",
                displayName = "Rully Beriandi",
                email = "beriandirully@gmail.com",
                company = "PT Pertamina Hulu Energi",
                position = "HSSE Operations Lead",
                photoURL = null,
                aiEnabled = true,
                projectIds = listOf("3n97CeSEhhQhFKQiuUvW", "0eyVSkUyrGZVeo141IJL")
            ),
            FirestoreUser(
                uid = "rLOEaSDR7JVIA7Air36KqMiLTTo2",
                displayName = "Ahmad Fauzi",
                email = "ahmad.fauzi@company.com",
                company = "PT Chevron Pacific Indonesia",
                position = "Rig Mechanical Supervisor",
                photoURL = null,
                aiEnabled = false,
                projectIds = listOf("3n97CeSEhhQhFKQiuUvW")
            ),
            FirestoreUser(
                uid = "6qiGyW7U3VfyRyy5XCayLpAuaTK2",
                displayName = "Budi Santoso",
                email = "budi.santoso@company.com",
                company = "PT Schlumberger Geophysics",
                position = "Lead Electrical Engineer",
                photoURL = null,
                aiEnabled = false,
                projectIds = listOf("3n97CeSEhhQhFKQiuUvW")
            ),
            FirestoreUser(
                uid = "4GQ2zwAB3jPx2DjONIFTagaOrml2",
                displayName = "Siti Rahma",
                email = "siti.rahma@company.com",
                company = "PT Medco E&P Indonesia",
                position = "Scaffolding Inspector & Lead",
                photoURL = null,
                aiEnabled = false,
                projectIds = listOf("3n97CeSEhhQhFKQiuUvW")
            ),
            FirestoreUser(
                uid = "0CPU9BOTCWQr50vEVJ1twcQ3WBg1",
                displayName = "Doni Prasetyo",
                email = "doni.prasetyo@company.com",
                company = "PT Halliburton Indonesia",
                position = "Production Field Supervisor",
                photoURL = null,
                aiEnabled = false,
                projectIds = listOf("1gFXLn4g9HOk39KvyuLn")
            )
        )

        val defaultProjects = listOf(
            FirestoreProject(
                id = "3n97CeSEhhQhFKQiuUvW",
                name = "HSE Observation",
                createdAt = "2025-08-07T02:21:35.691Z",
                ownerUid = "8xnYYEXlXZfvJK0w3BKMtWGjiit2",
                isOpen = false,
                memberUids = listOf(
                    "8xnYYEXlXZfvJK0w3BKMtWGjiit2",
                    "rLOEaSDR7JVIA7Air36KqMiLTTo2",
                    "6qiGyW7U3VfyRyy5XCayLpAuaTK2",
                    "4GQ2zwAB3jPx2DjONIFTagaOrml2",
                    "0GiEAk5NPbVuCiZAfvwQVhwucTN2"
                ),
                customCompanies = listOf(
                    "PT Pertamina Hulu Energi",
                    "PT Chevron Pacific Indonesia",
                    "PT Medco E&P Indonesia",
                    "PT Schlumberger Geophysics",
                    "PT Halliburton Indonesia",
                    "HSSE Tech Operations"
                ),
                customLocations = listOf(
                    "Rig Deck #01 - Offshore",
                    "Processing Facility Block A",
                    "Wellhead Area WHP-3",
                    "Gas Compressor Station",
                    "Chemical Storage Yard",
                    "Confined Space Tank TK-204",
                    "Electrical Switchgear Room"
                ),
                customObservationCategories = listOf("Safe Act", "Unsafe Act", "Unsafe Condition", "Near Miss")
            ),
            FirestoreProject(
                id = "0eyVSkUyrGZVeo141IJL",
                name = "Offshore Drilling Campaign 2026",
                createdAt = "2026-01-15T08:00:00.000Z",
                ownerUid = "8xnYYEXlXZfvJK0w3BKMtWGjiit2",
                isOpen = false,
                memberUids = listOf("8xnYYEXlXZfvJK0w3BKMtWGjiit2", "0GiEAk5NPbVuCiZAfvwQVhwucTN2"),
                customCompanies = listOf("PT Pertamina Hulu Rokan", "PT Baker Hughes"),
                customLocations = listOf("Jack-Up Rig Emerald-1", "Mud Pit Area", "Helideck Platform"),
                customObservationCategories = listOf("LSR Non-Compliance", "Good Practice")
            ),
            FirestoreProject(
                id = "1gFXLn4g9HOk39KvyuLn",
                name = "Refinery Maintenance & Turnaround",
                createdAt = "2026-02-01T10:30:00.000Z",
                ownerUid = "0CPU9BOTCWQr50vEVJ1twcQ3WBg1",
                isOpen = true,
                memberUids = listOf("0CPU9BOTCWQr50vEVJ1twcQ3WBg1"),
                customCompanies = listOf("PT Kilang Pertamina Internasional", "PT Tripatra"),
                customLocations = listOf("Crude Distillation Unit (CDU)", "Boiler House", "Flare Stack Zone"),
                customObservationCategories = listOf("Hot Work", "Working at Height", "Isolation of Energy")
            )
        )

        _users.value = defaultUsers
        _projects.value = defaultProjects

        val selected = defaultProjects.find { it.id == cachedActiveProjectId } ?: defaultProjects.firstOrNull()
        _activeProject.value = selected
    }

    fun startRealtimeListeners() {
        try {
            // Listen to `users` collection
            usersListener?.remove()
            usersListener = firestore.collection("users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(tag, "Listen failed for users collection: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
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
                        if (parsedUsers.isNotEmpty()) {
                            _users.value = parsedUsers
                            Log.d(tag, "Loaded ${parsedUsers.size} users from Firestore collection 'users'")
                        }
                    }
                }

            // Listen to `projects` collection
            projectsListener?.remove()
            projectsListener = firestore.collection("projects")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(tag, "Listen failed for projects collection: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
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
                            } catch (e: Exception) {
                                Log.e(tag, "Error parsing project doc ${doc.id}", e)
                                null
                            }
                        }
                        if (parsedProjects.isNotEmpty()) {
                            _projects.value = parsedProjects
                            Log.d(tag, "Loaded ${parsedProjects.size} projects from Firestore collection 'projects'")

                            // If active project is not set or not in list, pick appropriate
                            val current = _activeProject.value
                            if (current == null || parsedProjects.none { it.id == current.id }) {
                                _activeProject.value = parsedProjects.firstOrNull()
                            } else {
                                // Refresh current project data with new snapshot
                                parsedProjects.find { it.id == current.id }?.let {
                                    _activeProject.value = it
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(tag, "Error starting Firestore listeners", e)
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
     * Set active project and save preference
     */
    fun selectActiveProject(project: FirestoreProject) {
        _activeProject.value = project
        prefs.edit().putString("active_project_id", project.id).apply()
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

    fun cleanup() {
        usersListener?.remove()
        projectsListener?.remove()
    }
}
