package com.example.data

import android.util.Log
import android.util.LruCache
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class LsrRepository(private val lsrDao: LsrDao) {

    val activeAnswersFlow: Flow<List<ActiveAnswer>> = lsrDao.getActiveAnswersFlow()
    val inspectionRecordsFlow: Flow<List<InspectionRecord>> = lsrDao.getInspectionRecordsFlow()
    val totalInspectionCountFlow: Flow<Int> = lsrDao.getTotalInspectionCountFlow()
    val dashboardMetricsFlow: Flow<DashboardMetrics> = lsrDao.getDashboardMetricsFlow()
    val findingsFlow: Flow<List<FindingItem>> = lsrDao.getFindingsFlow()
    val notificationsFlow: Flow<List<AppNotification>> = lsrDao.getNotificationsFlow()
    val totalNotificationsCountFlow: Flow<Int> = lsrDao.getTotalNotificationsCountFlow()
    val unreadNotificationsCountFlow: Flow<Int> = lsrDao.getUnreadNotificationsCountFlow()
    val allActionLogsFlow: Flow<List<ActionHistoryLog>> = lsrDao.getAllActionLogsFlow()

    val pendingSyncCountFlow: Flow<Int> = combine(
        lsrDao.getPendingSyncRecordsCountFlow(),
        lsrDao.getPendingSyncFindingsCountFlow()
    ) { recCount, findCount -> recCount + findCount }

    fun getInspectionSummariesFlow(limit: Int): Flow<List<InspectionSummary>> =
        lsrDao.getInspectionSummariesFlow(limit)

    fun getNotificationsWindowFlow(limit: Int): Flow<List<AppNotification>> =
        lsrDao.getNotificationsWindowFlow(limit)

    suspend fun getInspectionRecordById(id: Int): InspectionRecord? =
        withContext(Dispatchers.IO) { lsrDao.getInspectionRecordById(id) }

    fun getActionLogsFlow(findingId: Int): Flow<List<ActionHistoryLog>> = lsrDao.getActionLogsFlow(findingId)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 25-second optimistic mutation lock so background polling never overwrites fresh local writes
    private val pendingLocalMutations = ConcurrentHashMap<String, Long>()

    // L1 bounded in-memory detail cache + Firestore startAfter() snapshot cursors
    private val l1FindingDetailCache = LruCache<Int, FindingItem>(L1_DETAIL_CAPACITY)
    private val remoteSnapshotCursors = ConcurrentHashMap<String, DocumentSnapshot>()
    private val lastDeltaSyncAt = ConcurrentHashMap<String, Long>()
    private val deltaSyncMutex = Mutex()

    data class PagedFindingsResult(
        val items: List<FindingItem>,
        val nextCursor: FindingCursor?,
        val endReached: Boolean,
        val fromCache: Boolean
    )

    fun resetRemoteFindingCursor(filterKey: String? = null) {
        if (filterKey == null) remoteSnapshotCursors.clear()
        else remoteSnapshotCursors.remove(filterKey)
    }

    fun getCachedFindingDetail(id: Int): FindingItem? = l1FindingDetailCache.get(id)

    suspend fun loadInspectionSummariesPageByCursor(
        cursor: KeysetCursor?,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<InspectionSummary> = withContext(Dispatchers.IO) {
        lsrDao.getInspectionSummariesPageByCursor(
            cursorTs = cursor?.lastTimestamp ?: Long.MAX_VALUE,
            cursorId = cursor?.lastId ?: Int.MAX_VALUE,
            pageSize = pageSize
        )
    }

    suspend fun loadNotificationsPageByCursor(
        cursor: KeysetCursor?,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<AppNotification> = withContext(Dispatchers.IO) {
        lsrDao.getNotificationsPageByCursor(
            cursorTs = cursor?.lastTimestamp ?: Long.MAX_VALUE,
            cursorId = cursor?.lastId ?: Int.MAX_VALUE,
            pageSize = pageSize
        )
    }

    /**
     * Multi-layer smart cache page loader for Findings:
     * 1) Reads Room L2 via Keyset Cursor (O(log N), < 100ms)
     * 2) Falls back to Firestore cursor startAfter() when L2 page is exhausted
     */
    suspend fun loadFindingsPage(
        projectId: String,
        statusFilter: String,
        query: String,
        criteria: ActivityFilterCriteria,
        cursor: FindingCursor?,
        firestoreManager: FirestoreManager?,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<PagedFindingsResult> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val dateCutoff = when (criteria.dateRange) {
                DateRangeFilter.ALL -> 0L
                DateRangeFilter.TODAY -> Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                DateRangeFilter.LAST_7_DAYS -> now - 7L * 86_400_000L
                DateRangeFilter.LAST_30_DAYS -> now - 30L * 86_400_000L
            }
            val criticalCutoff = now - 24L * 3_600_000L
            val highCutoff = now - 72L * 3_600_000L
            val cursorTs = cursor?.lastTimestamp ?: Long.MAX_VALUE
            val cursorId = cursor?.lastId ?: Int.MAX_VALUE
            val cleanQuery = query.trim()
            val filterKey = "$projectId|$statusFilter|$cleanQuery|${criteria.hashCode()}"

            val localPage = lsrDao.getFindingsPageByCursor(
                projectId = projectId,
                statusFilter = statusFilter,
                query = cleanQuery,
                dateCutoff = dateCutoff,
                filterUser = criteria.userName.trim(),
                filterLsr = criteria.lsrType.trim(),
                filterCompany = criteria.company.trim(),
                filterLocation = criteria.location.trim(),
                criticalCutoff = criticalCutoff,
                highCutoff = highCutoff,
                cursorTs = cursorTs,
                cursorId = cursorId,
                pageSize = pageSize
            )
            localPage.forEach { l1FindingDetailCache.put(it.id, it) }

            if (localPage.isNotEmpty()) {
                val last = localPage.last()
                return@withContext Result.success(
                    PagedFindingsResult(
                        items = localPage,
                        nextCursor = FindingCursor(last.timestamp, last.id, last.remoteDocId),
                        endReached = localPage.size < pageSize,
                        fromCache = true
                    )
                )
            }

            // Fallback to Firestore cursor pagination when L2 is exhausted & no complex local-only filters active
            val canQueryRemote = firestoreManager != null &&
                projectId.isNotBlank() &&
                cleanQuery.isBlank() &&
                criteria.activeCount == 0 &&
                statusFilter != "OVERDUE"

            if (!canQueryRemote) {
                return@withContext Result.success(
                    PagedFindingsResult(emptyList(), cursor, endReached = true, fromCache = true)
                )
            }

            val lastSnap = if (cursor == null) null else remoteSnapshotCursors[filterKey]
            val remotePageRes = firestoreManager.fetchFindingsPageByCursor(
                projectId = projectId,
                statusFilter = statusFilter,
                pageSize = pageSize,
                lastVisibleSnapshot = lastSnap
            )
            if (remotePageRes.isFailure) {
                return@withContext Result.failure(
                    remotePageRes.exceptionOrNull() ?: Exception("Remote page fetch failed")
                )
            }

            val remotePage = remotePageRes.getOrThrow()
            if (remotePage.items.isNotEmpty()) {
                ingestRemoteFindingDocs(projectId, remotePage.items)
            }
            val nextSnap = remotePage.lastVisibleSnapshot
            if (nextSnap != null) {
                remoteSnapshotCursors[filterKey] = nextSnap
            } else {
                remoteSnapshotCursors.remove(filterKey)
            }

            val persistedPage = lsrDao.getFindingsPageByCursor(
                projectId = projectId,
                statusFilter = statusFilter,
                query = cleanQuery,
                dateCutoff = dateCutoff,
                filterUser = criteria.userName.trim(),
                filterLsr = criteria.lsrType.trim(),
                filterCompany = criteria.company.trim(),
                filterLocation = criteria.location.trim(),
                criticalCutoff = criticalCutoff,
                highCutoff = highCutoff,
                cursorTs = cursorTs,
                cursorId = cursorId,
                pageSize = pageSize
            )
            persistedPage.forEach { l1FindingDetailCache.put(it.id, it) }
            val lastPersisted = persistedPage.lastOrNull()

            Result.success(
                PagedFindingsResult(
                    items = persistedPage,
                    nextCursor = lastPersisted?.let { FindingCursor(it.timestamp, it.id, it.remoteDocId) },
                    endReached = !remotePage.hasMore || persistedPage.size < pageSize,
                    fromCache = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Stale-While-Revalidate Delta Sync for Findings:
     * Fetches only documents modified since MAX(updatedAt) with 30s throttle.
     */
    suspend fun revalidateFindingsDeltaIfStale(
        projectId: String,
        firestoreManager: FirestoreManager,
        force: Boolean = false
    ): Result<Int> = deltaSyncMutex.withLock {
        if (projectId.isBlank()) return@withLock Result.success(0)
        val now = System.currentTimeMillis()
        val lastRun = lastDeltaSyncAt[projectId] ?: 0L
        if (!force && (now - lastRun) < DELTA_SYNC_THROTTLE_MS) {
            return@withLock Result.success(0)
        }
        val since = lsrDao.getMaxFindingUpdatedAt(projectId)
        val deltaRes = firestoreManager.fetchFindingsDeltaSince(projectId, since)
        deltaRes.map { docs ->
            lastDeltaSyncAt[projectId] = System.currentTimeMillis()
            if (docs.isNotEmpty()) ingestRemoteFindingDocs(projectId, docs) else 0
        }
    }

    suspend fun ingestRemoteFindingDocs(
        projectId: String,
        docs: List<FirestoreFindingDocument>
    ): Int = withContext(Dispatchers.IO) {
        if (docs.isEmpty()) return@withContext 0
        var merged = 0
        val now = System.currentTimeMillis()
        docs.forEach { doc ->
            val numericLocalId = doc.id.removePrefix("finding_").toIntOrNull()
            val existing = (if (doc.id.isNotBlank()) lsrDao.findFindingByRemoteDocId(doc.id) else null)
                ?: (if (numericLocalId != null) lsrDao.getFindingById(numericLocalId) else null)
                ?: if (doc.createdAt > 0L && doc.ruleId.isNotBlank()) {
                    lsrDao.findFindingBySignature(doc.createdAt, doc.ruleId, doc.location)
                } else null

            val normalizedStatus = when (doc.status.uppercase()) {
                "IN_REVIEW", "IN_PROGRESS" -> "ACTION_SUBMITTED"
                "REJECTED" -> "REOPENED"
                else -> doc.status.uppercase().ifBlank { "OPEN" }
            }

            if (existing == null) {
                val resolvedRuleId = doc.ruleId.ifBlank { doc.id.ifBlank { "LSR-EXT" } }
                val photoUrl = (doc.proofPhotos.firstOrNull() ?: doc.evidencePhotos.firstOrNull()).orEmpty()
                lsrDao.insertFinding(
                    FindingItem(
                        inspectionRecordId = 0,
                        ruleId = resolvedRuleId,
                        category = doc.checklistCategory.ifBlank { "General Safety" },
                        itemText = doc.questionText.ifBlank { doc.description.ifBlank { resolvedRuleId } },
                        standardRef = "IOGP LSR / OSHA",
                        isCritical = doc.severity.equals("CRITICAL", ignoreCase = true),
                        inspectorName = doc.creatorName,
                        inspectorEmail = doc.creatorEmail,
                        inspectorUid = doc.creatorUid,
                        area = doc.location,
                        notes = doc.description,
                        assignedPicName = doc.picName.ifBlank { "Unassigned / HSE Field Team" },
                        assignedPicEmail = doc.picEmail,
                        assignedPicRole = doc.picRole,
                        correctiveAction = doc.actionTaken,
                        actionEvidenceNotes = doc.actionEvidenceNotes,
                        status = normalizedStatus,
                        reopenReason = doc.reopenReason,
                        satisfactionRating = doc.picRating,
                        inspectorComment = doc.verificationRemarks,
                        evidencePhotoUrl = photoUrl,
                        timestamp = doc.createdAt.takeIf { it > 0L } ?: now,
                        updatedAt = doc.updatedAt.takeIf { it > 0L } ?: now,
                        cachedAt = now,
                        isSynced = true,
                        projectId = doc.projectId.ifBlank { projectId },
                        company = doc.creatorCompany,
                        remoteDocId = doc.id
                    )
                )
                merged++
            } else if (existing.isSynced && !isMutationLocked(existing.timestamp, existing.ruleId, existing.area, now)) {
                val remoteUpdated = doc.updatedAt.takeIf { it > 0L } ?: now
                if (remoteUpdated > existing.updatedAt || existing.remoteDocId.isBlank()) {
                    lsrDao.updateFindingStatusFromRemote(
                        id = existing.id,
                        status = normalizedStatus,
                        correctiveAction = doc.actionTaken.ifBlank { existing.correctiveAction },
                        picName = doc.picName.ifBlank { existing.assignedPicName },
                        picEmail = doc.picEmail.ifBlank { existing.assignedPicEmail },
                        rating = if (doc.picRating > 0) doc.picRating else existing.satisfactionRating,
                        updatedAt = maxOf(remoteUpdated, existing.updatedAt)
                    )
                    merged++
                }
            }
        }
        merged
    }

    fun markLocalFindingMutation(
        timestamp: Long,
        ruleId: String,
        area: String,
        now: Long = System.currentTimeMillis()
    ) {
        if (timestamp <= 0L || ruleId.isBlank()) return
        val key = buildFindingKey(timestamp, ruleId, area)
        pendingLocalMutations[key] = now
        pendingLocalMutations.entries.removeIf { !isTimestampWithinMutationLock(it.value, now) }
    }

    fun isMutationLocked(
        timestamp: Long,
        ruleId: String,
        area: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (timestamp <= 0L || ruleId.isBlank()) return false
        val key = buildFindingKey(timestamp, ruleId, area)
        val mutatedAt = pendingLocalMutations[key] ?: return false
        if (!isTimestampWithinMutationLock(mutatedAt, now)) {
            pendingLocalMutations.remove(key)
            return false
        }
        return true
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 25
        const val MAX_L1_WINDOW_ITEMS = 250
        const val L1_DETAIL_CAPACITY = 200
        const val DELTA_SYNC_THROTTLE_MS = 30_000L
        const val CACHE_TTL_DAYS = 7L
        const val MUTATION_LOCK_DURATION_MS = 25_000L

        fun buildFindingKey(timestamp: Long, ruleId: String, area: String): String {
            return "${timestamp}_${ruleId.trim().lowercase()}_${area.trim().lowercase()}"
        }

        fun isTimestampWithinMutationLock(
            mutatedAt: Long,
            now: Long = System.currentTimeMillis(),
            lockDurationMs: Long = MUTATION_LOCK_DURATION_MS
        ): Boolean {
            return mutatedAt > 0L && (now - mutatedAt) in 0L until lockDurationMs
        }

        fun calculateComplianceScore(yesCount: Int, noCount: Int): Int {
            val evaluated = yesCount + noCount
            return if (evaluated > 0) (yesCount * 100) / evaluated else 100
        }

        fun <T> capL1Window(
            existing: List<T>,
            incoming: List<T>,
            keySelector: (T) -> Any,
            maxItems: Int = MAX_L1_WINDOW_ITEMS
        ): List<T> {
            val merged = (existing + incoming).distinctBy(keySelector)
            return if (merged.size > maxItems) merged.takeLast(maxItems) else merged
        }
    }

    suspend fun saveActiveAnswer(
        questionId: String,
        answer: String,
        notes: String,
        picName: String = "",
        picEmail: String = "",
        picRole: String = ""
    ) {
        withContext(Dispatchers.IO) {
            lsrDao.insertActiveAnswer(
                ActiveAnswer(
                    questionId = questionId,
                    answer = answer,
                    notes = notes,
                    assignedPicName = picName,
                    assignedPicEmail = picEmail,
                    assignedPicRole = picRole,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteActiveAnswer(questionId: String) {
        withContext(Dispatchers.IO) {
            lsrDao.deleteActiveAnswer(questionId)
        }
    }

    suspend fun clearActiveAnswers() {
        withContext(Dispatchers.IO) {
            lsrDao.clearActiveAnswers()
        }
    }

    suspend fun getActiveAnswers(): List<ActiveAnswer> {
        return withContext(Dispatchers.IO) {
            lsrDao.getActiveAnswers()
        }
    }

    suspend fun updateFinding(id: Int, status: String, correctiveAction: String) {
        withContext(Dispatchers.IO) {
            val existing = lsrDao.getFindingById(id)
            if (existing != null) {
                markLocalFindingMutation(existing.timestamp, existing.ruleId, existing.area)
            }
            lsrDao.updateFinding(id, status, correctiveAction)
        }
    }

    suspend fun assignFindingPic(
        findingId: Int,
        picName: String,
        picEmail: String,
        picRole: String,
        actorName: String,
        actorEmail: String = "",
        actorRole: String = "Inspector"
    ) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = lsrDao.getFindingById(findingId) ?: return@withContext
            markLocalFindingMutation(existing.timestamp, existing.ruleId, existing.area, now)

            lsrDao.assignFindingPic(findingId, picName, picEmail, picRole, now)

            // Record Action History Log
            val log = ActionHistoryLog(
                findingId = findingId,
                ruleId = existing.ruleId,
                actorName = actorName,
                actorEmail = actorEmail,
                actorRole = actorRole,
                actionType = "ASSIGNED_TO_PIC",
                oldStatus = existing.status,
                newStatus = "OPEN",
                notes = "Ditugaskan kepada PIC: $picName ($picRole)",
                timestamp = now
            )
            lsrDao.insertActionLog(log)

            // Create In-App Notification for PIC
            val notif = AppNotification(
                recipientEmail = picEmail,
                recipientName = picName,
                title = "⚠️ Tugas Temuan Baru: ${existing.ruleId}",
                message = "Anda ditunjuk sebagai PIC untuk temuan ${existing.category} di ${existing.area}. Mohon tindak lanjuti tindakan perbaikan.",
                type = "ASSIGNMENT",
                findingId = findingId,
                ruleId = existing.ruleId,
                timestamp = now
            )
            lsrDao.insertNotification(notif)
        }
    }

    suspend fun submitPicAction(
        findingId: Int,
        correctiveAction: String,
        evidenceNotes: String,
        submittedBy: String,
        submittedByEmail: String = "",
        actorRole: String = "PIC / Field Team"
    ) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = lsrDao.getFindingById(findingId) ?: return@withContext
            markLocalFindingMutation(existing.timestamp, existing.ruleId, existing.area, now)

            lsrDao.submitPicAction(findingId, correctiveAction, evidenceNotes, submittedBy, now)

            // Record Action History Log
            val log = ActionHistoryLog(
                findingId = findingId,
                ruleId = existing.ruleId,
                actorName = submittedBy,
                actorEmail = submittedByEmail,
                actorRole = actorRole,
                actionType = "ACTION_SUBMITTED",
                oldStatus = existing.status,
                newStatus = "ACTION_SUBMITTED",
                notes = "Tindakan: $correctiveAction. Bukti: $evidenceNotes",
                timestamp = now
            )
            lsrDao.insertActionLog(log)

            // Create Notification for Inspector / Lead Auditor
            val notif = AppNotification(
                recipientEmail = existing.inspectorEmail,
                recipientName = existing.inspectorName,
                title = "🛠️ Tindakan Perbaikan Diajukan: ${existing.ruleId}",
                message = "PIC $submittedBy telah mengajukan perbaikan untuk temuan di ${existing.area}. Silakan verifikasi dan beri rating kepuasan.",
                type = "ACTION_SUBMITTED",
                findingId = findingId,
                ruleId = existing.ruleId,
                timestamp = now
            )
            lsrDao.insertNotification(notif)
        }
    }

    suspend fun verifyAndCloseFinding(
        findingId: Int,
        verifiedBy: String,
        verifiedByEmail: String = "",
        rating: Int,
        comment: String,
        actorRole: String = "Lead Auditor / HSE Inspector"
    ) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = lsrDao.getFindingById(findingId) ?: return@withContext
            markLocalFindingMutation(existing.timestamp, existing.ruleId, existing.area, now)

            lsrDao.verifyAndCloseFinding(findingId, verifiedBy, now, rating, comment)

            // Record Action History Log
            val log = ActionHistoryLog(
                findingId = findingId,
                ruleId = existing.ruleId,
                actorName = verifiedBy,
                actorEmail = verifiedByEmail,
                actorRole = actorRole,
                actionType = "VERIFIED_AND_CLOSED",
                oldStatus = existing.status,
                newStatus = "CLOSED",
                notes = "Diverifikasi & Ditutup. Ulasan: $comment",
                rating = rating,
                timestamp = now
            )
            lsrDao.insertActionLog(log)

            // Create Notification for PIC
            val notif = AppNotification(
                recipientEmail = existing.assignedPicEmail,
                recipientName = existing.assignedPicName,
                title = "✅ Temuan Ditutup & Dinilai [Rating: $rating/5 ⭐]",
                message = "Temuan ${existing.ruleId} di ${existing.area} telah diverifikasi dan resmi DITUTUP oleh $verifiedBy. Rating kepuasan: $rating/5 Bintang.",
                type = "CLOSED",
                findingId = findingId,
                ruleId = existing.ruleId,
                timestamp = now
            )
            lsrDao.insertNotification(notif)
        }
    }

    suspend fun reopenFinding(
        findingId: Int,
        reopenReason: String,
        actorName: String,
        actorEmail: String = "",
        actorRole: String = "Lead Auditor / HSE Inspector"
    ) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = lsrDao.getFindingById(findingId) ?: return@withContext
            markLocalFindingMutation(existing.timestamp, existing.ruleId, existing.area, now)

            lsrDao.reopenFinding(findingId, reopenReason)

            // Record Action History Log
            val log = ActionHistoryLog(
                findingId = findingId,
                ruleId = existing.ruleId,
                actorName = actorName,
                actorEmail = actorEmail,
                actorRole = actorRole,
                actionType = "REOPENED_BY_INSPECTOR",
                oldStatus = existing.status,
                newStatus = "REOPENED",
                notes = "Temuan ditolak & dibuka kembali. Alasan: $reopenReason",
                timestamp = now
            )
            lsrDao.insertActionLog(log)

            // Create Notification for PIC
            val notif = AppNotification(
                recipientEmail = existing.assignedPicEmail,
                recipientName = existing.assignedPicName,
                title = "🔄 Temuan Dibuka Kembali (Perlu Perbaikan Ulang)",
                message = "Tindakan perbaikan untuk ${existing.ruleId} di ${existing.area} belum memenuhi standar. Catatan auditor: \"$reopenReason\".",
                type = "REOPENED",
                findingId = findingId,
                ruleId = existing.ruleId,
                timestamp = now
            )
            lsrDao.insertNotification(notif)
        }
    }

    suspend fun markNotificationRead(id: Int) {
        withContext(Dispatchers.IO) {
            lsrDao.markNotificationRead(id)
        }
    }

    suspend fun markAllNotificationsRead() {
        withContext(Dispatchers.IO) {
            lsrDao.markAllNotificationsRead()
        }
    }

    suspend fun deleteReadNotifications() {
        withContext(Dispatchers.IO) {
            lsrDao.deleteReadNotifications()
        }
    }

    suspend fun purgeOldReadNotifications(days: Int = 30) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (days.toLong() * 86_400_000L)
            lsrDao.purgeOldReadNotifications(cutoff)
        }
    }

    suspend fun attachFindingEvidencePhoto(findingId: Int, localPath: String, remoteUrl: String = "") {
        withContext(Dispatchers.IO) {
            lsrDao.updateFindingEvidencePhoto(findingId, localPath, remoteUrl)
        }
    }

    suspend fun sendFindingCommentOrReminder(
        findingId: Int,
        actorUid: String,
        actorName: String,
        actorEmail: String,
        actorRole: String,
        message: String,
        isReminder: Boolean,
        projectId: String
    ): Pair<FirestoreActionLog, FirestoreNotification>? = withContext(Dispatchers.IO) {
        val existing = lsrDao.getFindingById(findingId) ?: return@withContext null
        val now = System.currentTimeMillis()
        val actionType = if (isReminder) "REMINDER_PING" else "COMMENT"
        val cleanMsg = message.trim().ifBlank {
            if (isReminder) "Mohon segera tindak lanjuti temuan ${existing.ruleId} di ${existing.area}."
            else ""
        }
        if (cleanMsg.isBlank()) return@withContext null

        // Persist locally immediately
        lsrDao.insertActionLog(
            ActionHistoryLog(
                findingId = findingId,
                ruleId = existing.ruleId,
                actorName = actorName,
                actorEmail = actorEmail,
                actorRole = actorRole,
                actionType = actionType,
                oldStatus = existing.status,
                newStatus = existing.status,
                notes = cleanMsg,
                timestamp = now
            )
        )

        // Determine recipient (if sender is PIC -> notify Inspector, otherwise notify PIC)
        val senderIsPic = actorEmail.isNotBlank() && actorEmail.equals(existing.assignedPicEmail, ignoreCase = true)
        val recipientEmail = if (senderIsPic) existing.inspectorEmail else existing.assignedPicEmail
        val recipientName = if (senderIsPic) existing.inspectorName else existing.assignedPicName

        val notifTitle = if (isReminder) {
            "🔔 Pengingat Temuan: ${existing.ruleId}"
        } else {
            "💬 Pesan Baru ($actorName) • ${existing.ruleId}"
        }
        val notifType = if (isReminder) "REMINDER" else "CHAT_MESSAGE"

        // Save local echo so sender also sees activity if needed
        lsrDao.insertNotification(
            AppNotification(
                recipientEmail = recipientEmail,
                recipientName = recipientName,
                title = notifTitle,
                message = cleanMsg,
                type = notifType,
                findingId = findingId,
                ruleId = existing.ruleId,
                timestamp = now,
                isRead = true,
                isSynced = true,
                senderName = actorName
            )
        )

        val logDoc = FirestoreActionLog(
            id = "log_${now}_${(100..999).random()}",
            findingId = "finding_$findingId",
            projectId = projectId,
            timestamp = now,
            actorUid = actorUid,
            actorName = actorName,
            actorRole = actorRole,
            actionType = actionType,
            oldStatus = existing.status,
            newStatus = existing.status,
            remarks = cleanMsg
        )

        val notifDoc = FirestoreNotification(
            id = "notif_${now}_${(100..999).random()}",
            projectId = projectId,
            findingId = findingId.toString(),
            recipientEmail = recipientEmail,
            senderUid = actorUid,
            senderName = actorName,
            senderEmail = actorEmail,
            title = notifTitle,
            message = cleanMsg,
            type = notifType,
            isCritical = isReminder && existing.isCritical,
            isBroadcast = recipientEmail.isBlank(),
            isRead = false,
            timestamp = now
        )
        Pair(logDoc, notifDoc)
    }

    suspend fun ingestRemoteNotifications(
        remoteList: List<FirestoreNotification>,
        currentUserUid: String,
        notifPicAlerts: Boolean,
        notifCriticalAlerts: Boolean
    ): List<FirestoreNotification> = withContext(Dispatchers.IO) {
        val newlyArrived = mutableListOf<FirestoreNotification>()
        remoteList.forEach { remote ->
            if (remote.id.isBlank()) return@forEach
            val alreadyExists = lsrDao.getNotificationByRemoteId(remote.id) != null
            if (!alreadyExists) {
                val isFromSelf = currentUserUid.isNotBlank() && remote.senderUid == currentUserUid
                val fId = remote.findingId.removePrefix("finding_").toIntOrNull() ?: 0
                lsrDao.insertNotification(
                    AppNotification(
                        recipientEmail = remote.recipientEmail,
                        recipientName = remote.senderName,
                        title = remote.title,
                        message = remote.message,
                        type = remote.type,
                        findingId = fId,
                        ruleId = "",
                        timestamp = remote.timestamp,
                        isRead = isFromSelf || remote.isRead,
                        isSynced = true,
                        remoteId = remote.id,
                        senderName = remote.senderName
                    )
                )
                val allowedByPref = if (remote.isCritical) notifCriticalAlerts else notifPicAlerts
                if (!isFromSelf && !remote.isRead && allowedByPref) {
                    newlyArrived.add(remote)
                }
            }
        }
        newlyArrived
    }

    suspend fun clearNotifications() {
        withContext(Dispatchers.IO) {
            lsrDao.clearNotifications()
        }
    }

    suspend fun deleteFinding(id: Int) {
        withContext(Dispatchers.IO) {
            lsrDao.deleteFinding(id)
        }
    }

    /**
     * Submits the completed inspection to:
     * 1. Room Local Database (InspectionRecord & FindingItem for every NO response)
     * 2. Cloud Firestore (Native SDK with offline support & authenticated user)
     * 3. Google Apps Script Web App (if enabled & configured)
     * 4. Firebase Realtime Database (with Auth token if logged in)
     */
    suspend fun submitInspection(
        inspectorName: String,
        area: String,
        activeAnswers: List<ActiveAnswer>,
        checklistItems: List<LsrItem>,
        applicableModules: List<String> = emptyList(),
        scriptUrl: String,
        isGasSyncEnabled: Boolean = true,
        firebaseUrl: String = "https://lowerzakum-482af-default-rtdb.firebaseio.com",
        isFirebaseSyncEnabled: Boolean = true,
        userUid: String = "",
        userEmail: String = "",
        projectId: String = "",
        company: String = "",
        notifPicAlerts: Boolean = true,
        notifCriticalAlerts: Boolean = true
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val answeredItems = activeAnswers.filter { it.answer != "UNANSWERED" }
                val yesCount = answeredItems.count { it.answer == "YES" }
                val noCount = answeredItems.count { it.answer == "NO" }
                val naCount = answeredItems.count { it.answer == "NA" }
                val score = calculateComplianceScore(yesCount, noCount)

                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(now))

                val questionMap = checklistItems.associateBy { it.id }
                val resolvedCategories = applicableModules.ifEmpty {
                    answeredItems.mapNotNull { questionMap[it.questionId]?.category }.distinct()
                }.joinToString(", ")

                // Construct Answers & Metadata JSON mapping for local DB
                val localRootJson = JSONObject()
                val localAnswersJson = JSONObject()
                activeAnswers.forEach {
                    val ansObj = JSONObject()
                    ansObj.put("answer", it.answer)
                    ansObj.put("notes", it.notes)
                    localAnswersJson.put(it.questionId, ansObj)
                }

                val modulesArray = JSONArray()
                applicableModules.forEach { modulesArray.put(it) }
                localRootJson.put("applicableModules", modulesArray)
                localRootJson.put("answers", localAnswersJson)

                // Insert inspection record locally
                val record = InspectionRecord(
                    inspectorName = inspectorName,
                    area = area,
                    timestamp = now,
                    answersJson = localRootJson.toString(),
                    score = score,
                    isSynced = false,
                    projectId = projectId,
                    company = company,
                    lsrCategories = resolvedCategories
                )
                val recordId = lsrDao.insertInspectionRecord(record).toInt()

                // Extract all Non-Compliant ("NO") items as dedicated Findings
                val newFindings = mutableListOf<FindingItem>()
                val initialLogs = mutableListOf<ActionHistoryLog>()

                activeAnswers.filter { it.answer == "NO" }.forEach { ans ->
                    val q = questionMap[ans.questionId]
                    val ruleId = ans.questionId
                    val category = q?.category ?: "General Safety"
                    val itemText = q?.item_text ?: "Non-compliant rule $ruleId"
                    val standardRef = q?.standard_ref ?: "IOGP LSR"
                    val isCritical = q?.is_critical ?: false

                    val hasAssignedPic = ans.assignedPicName.isNotBlank()
                    val picName = if (hasAssignedPic) ans.assignedPicName else "Unassigned / HSE Field Team"
                    val picEmail = ans.assignedPicEmail
                    val picRole = if (hasAssignedPic) ans.assignedPicRole else "PIC"

                    val finding = FindingItem(
                        inspectionRecordId = recordId,
                        ruleId = ruleId,
                        category = category,
                        itemText = itemText,
                        standardRef = standardRef,
                        isCritical = isCritical,
                        inspectorName = inspectorName,
                        inspectorEmail = userEmail,
                        inspectorUid = userUid,
                        area = area,
                        notes = ans.notes,
                        assignedPicName = picName,
                        assignedPicEmail = picEmail,
                        assignedPicRole = picRole,
                        assignedTimestamp = if (hasAssignedPic) now else 0L,
                        correctiveAction = "",
                        status = "OPEN",
                        timestamp = now,
                        isSynced = false,
                        projectId = projectId,
                        company = company
                    )
                    val insertedFindingId = lsrDao.insertFinding(finding).toInt()
                    val persistedFinding = finding.copy(id = insertedFindingId)
                    newFindings.add(persistedFinding)

                    initialLogs.add(
                        ActionHistoryLog(
                            findingId = insertedFindingId,
                            ruleId = ruleId,
                            actorName = inspectorName,
                            actorEmail = userEmail,
                            actorRole = "Inspector / Auditor",
                            actionType = "CREATED_NON_COMPLIANT",
                            oldStatus = "",
                            newStatus = "OPEN",
                            notes = "Temuan ketidakpatuhan dicatat saat audit lapangan. Catatan: ${ans.notes.ifBlank { "Kondisi tidak memenuhi standar LSR." }}",
                            timestamp = now
                        )
                    )
                    if (isCritical && notifCriticalAlerts) {
                        lsrDao.insertNotification(
                            AppNotification(
                                recipientEmail = userEmail,
                                recipientName = inspectorName,
                                title = "🚨 Temuan Kritis LSR: $ruleId",
                                message = "Pelanggaran risiko fatal terdeteksi di $area: $itemText",
                                type = "CRITICAL",
                                findingId = insertedFindingId,
                                ruleId = ruleId,
                                timestamp = now,
                                senderName = inspectorName
                            )
                        )
                    }
                    if (notifPicAlerts && hasAssignedPic && picName != "Unassigned / HSE Field Team") {
                        initialLogs.add(
                            ActionHistoryLog(
                                findingId = insertedFindingId,
                                ruleId = ruleId,
                                actorName = inspectorName,
                                actorEmail = userEmail,
                                actorRole = "Inspector / Auditor",
                                actionType = "ASSIGNED_TO_PIC",
                                oldStatus = "OPEN",
                                newStatus = "OPEN",
                                notes = "Ditugaskan kepada PIC: $picName ($picRole)",
                                timestamp = now
                            )
                        )
                        lsrDao.insertNotification(
                            AppNotification(
                                recipientEmail = picEmail,
                                recipientName = picName,
                                title = "⚠️ Tugas Temuan Baru: $ruleId",
                                message = "Anda ditugaskan sebagai PIC untuk perbaikan di area $area oleh $inspectorName.",
                                type = "ASSIGNMENT",
                                findingId = insertedFindingId,
                                ruleId = ruleId,
                                timestamp = now,
                                senderName = inspectorName
                            )
                        )
                    }
                }

                if (initialLogs.isNotEmpty()) {
                    lsrDao.insertActionLogs(initialLogs)
                }

                // Construct Standard JSON Payload
                val payload = JSONObject()
                payload.put("action", "submit_inspection")
                payload.put("inspectionId", recordId)
                payload.put("inspectorName", inspectorName)
                payload.put("company", company)
                payload.put("lsrCategories", resolvedCategories)
                payload.put("userUid", userUid)
                payload.put("userEmail", userEmail)
                payload.put("area", area)
                payload.put("timestamp", now)
                payload.put("dateFormatted", formattedDate)
                payload.put("score", score)
                payload.put("totalQuestions", checklistItems.size)
                payload.put("answeredQuestions", answeredItems.size)
                payload.put("yesCount", yesCount)
                payload.put("noCount", noCount)
                payload.put("naCount", naCount)
                payload.put("applicableModules", modulesArray)

                val answersArray = JSONArray()
                val firestoreAnswersList = mutableListOf<Map<String, Any>>()

                activeAnswers.forEach {
                    val ansObj = JSONObject()
                    ansObj.put("id", it.questionId)
                    ansObj.put("answer", it.answer)
                    ansObj.put("notes", it.notes)
                    val q = questionMap[it.questionId]
                    if (q != null) {
                        ansObj.put("category", q.category)
                        ansObj.put("item_text", q.item_text)
                        ansObj.put("is_critical", q.is_critical)
                        ansObj.put("standard_ref", q.standard_ref)
                    }
                    answersArray.put(ansObj)

                    firestoreAnswersList.add(
                        mapOf(
                            "id" to it.questionId,
                            "answer" to it.answer,
                            "notes" to it.notes,
                            "category" to (q?.category ?: ""),
                            "item_text" to (q?.item_text ?: ""),
                            "is_critical" to (q?.is_critical ?: false)
                        )
                    )
                }
                payload.put("answers", answersArray)

                // Findings payload array
                val findingsArray = JSONArray()
                val firestoreFindingsList = mutableListOf<Map<String, Any>>()

                newFindings.forEach { finding ->
                    val fObj = JSONObject()
                    fObj.put("findingKey", "${finding.timestamp}_${finding.ruleId}_${finding.area}")
                    fObj.put("ruleId", finding.ruleId)
                    fObj.put("category", finding.category)
                    fObj.put("itemText", finding.itemText)
                    fObj.put("standardRef", finding.standardRef)
                    fObj.put("isCritical", finding.isCritical)
                    fObj.put("inspectorNotes", finding.notes)
                    fObj.put("status", finding.status)
                    fObj.put("area", finding.area)
                    fObj.put("company", finding.company)
                    fObj.put("inspectorName", finding.inspectorName)
                    fObj.put("picName", finding.assignedPicName)
                    fObj.put("picEmail", finding.assignedPicEmail)
                    findingsArray.put(fObj)

                    firestoreFindingsList.add(
                        mapOf(
                            "ruleId" to finding.ruleId,
                            "category" to finding.category,
                            "itemText" to finding.itemText,
                            "standardRef" to finding.standardRef,
                            "isCritical" to finding.isCritical,
                            "inspectorNotes" to finding.notes,
                            "status" to finding.status,
                            "area" to finding.area,
                            "inspectorName" to finding.inspectorName,
                            "timestamp" to finding.timestamp,
                            "userUid" to userUid
                        )
                    )
                }
                payload.put("findings", findingsArray)

                var gasSuccess = false
                var firebaseSuccess = false

                // 1. Google Apps Script Webhook Sync
                if (isGasSyncEnabled && scriptUrl.isNotBlank() && (scriptUrl.startsWith("http://") || scriptUrl.startsWith("https://"))) {
                    try {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = payload.toString().toRequestBody(mediaType)
                        val request = Request.Builder()
                            .url(scriptUrl)
                            .post(requestBody)
                            .build()

                        okHttpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                gasSuccess = true
                                Log.d("LsrRepository", "Successfully synced with Google Apps Script Webhook")
                            } else {
                                Log.e("LsrRepository", "GAS sync returned code ${response.code}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LsrRepository", "Error syncing with GAS Webhook", e)
                    }
                }

                // 2. Cloud Firestore & Firebase Sync
                if (isFirebaseSyncEnabled) {
                    // 2A. Native Cloud Firestore sync (Scoped under /projects/{projectId})
                    try {
                        val db = FirebaseFirestore.getInstance()
                        val resolvedProjId = projectId.ifBlank { "lower-zakum-operations" }
                        val firestoreDoc = hashMapOf<String, Any>(
                            "inspectionId" to recordId,
                            "projectId" to resolvedProjId,
                            "inspectorName" to inspectorName,
                            "company" to company,
                            "lsrCategories" to resolvedCategories,
                            "userUid" to userUid,
                            "userEmail" to userEmail,
                            "area" to area,
                            "timestamp" to now,
                            "updatedAt" to now,
                            "dateFormatted" to formattedDate,
                            "score" to score,
                            "totalQuestions" to checklistItems.size,
                            "answeredQuestions" to answeredItems.size,
                            "yesCount" to yesCount,
                            "noCount" to noCount,
                            "naCount" to naCount,
                            "applicableModules" to applicableModules,
                            "answers" to firestoreAnswersList,
                            "findings" to firestoreFindingsList
                        )

                        val task = db.collection("projects")
                            .document(resolvedProjId)
                            .collection("inspections")
                            .document("inspection_$recordId")
                            .set(firestoreDoc, SetOptions.merge())
                        Tasks.await(task, 10, TimeUnit.SECONDS)
                        firebaseSuccess = true
                        Log.d("LsrRepository", "Successfully written to Cloud Firestore /projects/$resolvedProjId/inspections/inspection_$recordId")

                        // Also batch write findings to scoped /projects/{projId}/findings collection
                        newFindings.forEach { finding ->
                            val findingDoc = hashMapOf<String, Any>(
                                "id" to "finding_${finding.id}",
                                "projectId" to resolvedProjId,
                                "ruleId" to finding.ruleId,
                                "checklistCategory" to finding.category,
                                "questionText" to finding.itemText,
                                "description" to finding.notes,
                                "location" to finding.area,
                                "severity" to if (finding.isCritical) "CRITICAL" else "HIGH",
                                "status" to finding.status,
                                "creatorUid" to userUid,
                                "creatorName" to finding.inspectorName,
                                "creatorEmail" to userEmail,
                                "creatorCompany" to company,
                                "picName" to finding.assignedPicName,
                                "picEmail" to finding.assignedPicEmail,
                                "picRole" to finding.assignedPicRole,
                                "createdAt" to finding.timestamp,
                                "updatedAt" to now
                            )
                            val fTask = db.collection("projects")
                                .document(resolvedProjId)
                                .collection("findings")
                                .document("finding_${finding.id}")
                                .set(findingDoc, SetOptions.merge())
                            try { Tasks.await(fTask, 5, TimeUnit.SECONDS) } catch (_: Exception) { /* continue */ }
                        }
                    } catch (e: Exception) {
                        Log.e("LsrRepository", "Firestore write exception: ${e.message}")
                    }

                    // 2B. Firebase Realtime Database Sync (with Auth Token if available)
                    if (firebaseUrl.isNotBlank()) {
                        try {
                            val cleanBaseUrl = firebaseUrl.trimEnd('/')
                            val currentUser = FirebaseAuth.getInstance().currentUser
                            var idToken: String? = null
                            if (currentUser != null) {
                                try {
                                    val tokenTask = currentUser.getIdToken(false)
                                    idToken = Tasks.await(tokenTask, 5, TimeUnit.SECONDS)?.token
                                } catch (e: Exception) {
                                    Log.w("LsrRepository", "Could not fetch ID token for RTDB: ${e.message}")
                                }
                            }

                            val baseEndpoint = if (cleanBaseUrl.endsWith(".json")) cleanBaseUrl else "$cleanBaseUrl/inspections/inspection_$recordId.json"
                            val finalUrl = if (!idToken.isNullOrBlank()) "$baseEndpoint?auth=$idToken" else baseEndpoint

                            val mediaType = "application/json; charset=utf-8".toMediaType()
                            val requestBody = payload.toString().toRequestBody(mediaType)
                            val request = Request.Builder()
                                .url(finalUrl)
                                .put(requestBody)
                                .build()

                            okHttpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    firebaseSuccess = true
                                    Log.d("LsrRepository", "Successfully synced to Firebase Realtime Database")
                                } else {
                                    Log.w("LsrRepository", "Firebase RTDB sync returned code ${response.code}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LsrRepository", "Error syncing with Firebase RTDB", e)
                        }
                    }
                }

                // Update record sync flag if at least one cloud target succeeded
                val isOverallSynced = gasSuccess || firebaseSuccess
                if (isOverallSynced) {
                    lsrDao.markRecordSynced(recordId)
                }

                // Clear draft answers
                lsrDao.clearActiveAnswers()

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("LsrRepository", "Error during submitInspection", e)
                Result.failure(e)
            }
        }
    }

    data class GasPullResult(
        val inspectionsAdded: Int = 0,
        val findingsMerged: Int = 0,
        val users: List<FirestoreUser> = emptyList(),
        val supportsPull: Boolean = false,
        val serverTime: Long = System.currentTimeMillis(),
        val version: String = ""
    )

    sealed class GasAuthOutcome {
        data class Success(val user: FirestoreUser) : GasAuthOutcome()
        data class WrongPassword(val message: String) : GasAuthOutcome()
        data class UserNotFound(val message: String) : GasAuthOutcome()
        data class ServerOutdated(val version: String, val message: String) : GasAuthOutcome()
        data class EmailExists(val message: String) : GasAuthOutcome()
        data class NetworkOrServerError(val message: String) : GasAuthOutcome()
    }

    suspend fun pullFromGasWebhook(scriptUrl: String): Result<GasPullResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (scriptUrl.isBlank() || (!scriptUrl.startsWith("http://") && !scriptUrl.startsWith("https://"))) {
                    return@withContext Result.failure(Exception("URL GAS tidak valid"))
                }
                val separator = if (scriptUrl.contains("?")) "&" else "?"
                val pullUrl = "${scriptUrl.trim()}${separator}action=sync_all&limit=60&t=${System.currentTimeMillis()}"
                val request = Request.Builder()
                    .url(pullUrl)
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("GAS doGet HTTP ${response.code}"))
                    }
                    if (!bodyStr.trimStart().startsWith("{")) {
                        return@withContext Result.failure(Exception("GAS mengembalikan non-JSON (pastikan akses Web App = Anyone)"))
                    }
                    val json = JSONObject(bodyStr)
                    if (json.optString("status") == "error") {
                        return@withContext Result.failure(Exception(json.optString("message", "GAS error")))
                    }

                    val supportsPull = json.optBoolean("supportsPull", json.has("inspections") || json.has("findings"))
                    var addedInspections = 0
                    var mergedFindings = 0

                    val inspectionsArr = json.optJSONArray("inspections")
                    if (inspectionsArr != null) {
                        for (i in 0 until inspectionsArr.length()) {
                            val obj = inspectionsArr.optJSONObject(i) ?: continue
                            val ts = obj.optLong("timestamp", 0L)
                            val inspector = obj.optString("inspectorName", "").trim()
                            val areaVal = obj.optString("area", "").trim()
                            if (ts <= 0L || inspector.isBlank() || areaVal.isBlank()) continue

                            val existing = lsrDao.findInspectionBySignature(ts, inspector, areaVal)
                            if (existing == null) {
                                val rawAnswers = obj.optString("answersJson", "[]")
                                val wrappedJson = if (rawAnswers.trimStart().startsWith("{")) {
                                    rawAnswers
                                } else {
                                    JSONObject().apply {
                                        put("applicableModules", JSONArray())
                                        put("answers", JSONObject())
                                        put("rawAnswersArray", rawAnswers)
                                    }.toString()
                                }
                                lsrDao.insertInspectionRecord(
                                    InspectionRecord(
                                        inspectorName = inspector,
                                        area = areaVal,
                                        timestamp = ts,
                                        answersJson = wrappedJson,
                                        score = obj.optInt("score", 100),
                                        isSynced = true,
                                        company = obj.optString("company", ""),
                                        lsrCategories = obj.optString("lsrCategories", "")
                                    )
                                )
                                addedInspections++
                            } else if (!existing.isSynced) {
                                lsrDao.markRecordSynced(existing.id)
                            }
                        }
                    }

                    val findingsArr = json.optJSONArray("findings")
                    if (findingsArr != null) {
                        for (i in 0 until findingsArr.length()) {
                            val obj = findingsArr.optJSONObject(i) ?: continue
                            val ts = obj.optLong("timestamp", 0L)
                            val ruleId = obj.optString("ruleId", "").trim()
                            val areaVal = obj.optString("area", "").trim()
                            if (ts <= 0L || ruleId.isBlank()) continue

                            val remoteStatus = obj.optString("status", "OPEN").ifBlank { "OPEN" }
                            val remoteAction = obj.optString("correctiveAction", "").let { if (it == "-") "" else it }
                            val remotePicName = obj.optString("picName", "").let { if (it == "Unassigned") "" else it }
                            val remotePicEmail = obj.optString("picEmail", "")
                            val remoteRating = obj.optInt("rating", 0)

                            val existingFinding = lsrDao.findFindingBySignature(ts, ruleId, areaVal)
                            if (existingFinding == null) {
                                lsrDao.insertFinding(
                                    FindingItem(
                                        inspectionRecordId = 0,
                                        ruleId = ruleId,
                                        category = obj.optString("category", "General Safety"),
                                        itemText = obj.optString("itemText", ruleId),
                                        standardRef = obj.optString("standardRef", "IOGP LSR"),
                                        isCritical = obj.optBoolean("isCritical", false),
                                        inspectorName = obj.optString("inspectorName", ""),
                                        area = areaVal,
                                        notes = obj.optString("notes", "").let { if (it == "-") "" else it },
                                        assignedPicName = remotePicName.ifBlank { "Unassigned / HSE Field Team" },
                                        assignedPicEmail = remotePicEmail,
                                        correctiveAction = remoteAction,
                                        status = remoteStatus,
                                        satisfactionRating = remoteRating,
                                        timestamp = ts,
                                        isSynced = true,
                                        company = obj.optString("company", "")
                                    )
                                )
                                mergedFindings++
                            } else if (!isMutationLocked(ts, ruleId, areaVal) && (existingFinding.status != remoteStatus || existingFinding.correctiveAction != remoteAction)) {
                                lsrDao.updateFindingStatusFromRemote(
                                    id = existingFinding.id,
                                    status = remoteStatus,
                                    correctiveAction = remoteAction.ifBlank { existingFinding.correctiveAction },
                                    picName = remotePicName.ifBlank { existingFinding.assignedPicName },
                                    picEmail = remotePicEmail.ifBlank { existingFinding.assignedPicEmail },
                                    rating = if (remoteRating > 0) remoteRating else existingFinding.satisfactionRating
                                )
                                mergedFindings++
                            }
                        }
                    }

                    val pulledUsers = mutableListOf<FirestoreUser>()
                    val usersArr = json.optJSONArray("users")
                    if (usersArr != null) {
                        for (i in 0 until usersArr.length()) {
                            val uObj = usersArr.optJSONObject(i) ?: continue
                            val emailVal = uObj.optString("email", "").trim().lowercase()
                            if (emailVal.isBlank()) continue
                            val nameVal = uObj.optString("name", "").trim().ifBlank { emailVal.substringBefore("@") }
                            val roleVal = uObj.optString("role", "").trim().ifBlank { "HSE Officer" }
                            val companyVal = uObj.optString("company", "").trim().ifBlank { "HSSE Operations" }
                            val uidVal = uObj.optString("uid", "").trim().ifBlank { "gas_${emailVal.hashCode()}" }
                            pulledUsers.add(
                                FirestoreUser(
                                    uid = uidVal,
                                    displayName = nameVal,
                                    displayNameLowercase = nameVal.lowercase(),
                                    email = emailVal,
                                    company = companyVal,
                                    position = roleVal,
                                    searchKeywords = listOf(nameVal.lowercase(), emailVal)
                                )
                            )
                        }
                    }

                    Result.success(
                        GasPullResult(
                            inspectionsAdded = addedInspections,
                            findingsMerged = mergedFindings,
                            users = pulledUsers,
                            supportsPull = supportsPull,
                            serverTime = json.optLong("serverTime", System.currentTimeMillis()),
                            version = json.optString("version", "")
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun registerUserInGas(
        scriptUrl: String,
        name: String,
        email: String,
        role: String,
        company: String,
        password: String,
        uid: String = ""
    ): GasAuthOutcome {
        if (scriptUrl.isBlank() || (!scriptUrl.startsWith("http://") && !scriptUrl.startsWith("https://"))) {
            return GasAuthOutcome.NetworkOrServerError("URL GAS tidak valid")
        }
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("action", "register_user")
                    put("name", name.trim())
                    put("email", email.trim().lowercase())
                    put("role", role.trim().ifBlank { "Lead HSE Inspector" })
                    put("company", company.trim().ifBlank { "HSSE Tech Operations" })
                    put("password", password)
                    put("uid", uid.trim())
                }
                val req = Request.Builder()
                    .url(scriptUrl.trim())
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful || !raw.trimStart().startsWith("{")) {
                        return@withContext GasAuthOutcome.NetworkOrServerError("GAS HTTP ${resp.code}")
                    }
                    val json = JSONObject(raw)
                    if (json.optString("status") == "success") {
                        val uObj = json.optJSONObject("user")
                        val user = parseGasUserObject(uObj, email, name, role, company, uid)
                        GasAuthOutcome.Success(user)
                    } else {
                        val code = json.optString("code", "")
                        val msg = json.optString("message", "Gagal mendaftar di Google Sheets")
                        if (code == "EMAIL_EXISTS") GasAuthOutcome.EmailExists(msg)
                        else GasAuthOutcome.NetworkOrServerError(msg)
                    }
                }
            } catch (e: Exception) {
                GasAuthOutcome.NetworkOrServerError(e.message ?: "Koneksi GAS gagal")
            }
        }
    }

    suspend fun loginUserViaGas(
        scriptUrl: String,
        email: String,
        password: String
    ): GasAuthOutcome {
        if (scriptUrl.isBlank() || (!scriptUrl.startsWith("http://") && !scriptUrl.startsWith("https://"))) {
            return GasAuthOutcome.NetworkOrServerError("URL GAS tidak valid")
        }
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("action", "login_user")
                    put("email", email.trim().lowercase())
                    put("password", password)
                }
                val req = Request.Builder()
                    .url(scriptUrl.trim())
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful || !raw.trimStart().startsWith("{")) {
                        return@withContext GasAuthOutcome.NetworkOrServerError("GAS HTTP ${resp.code}")
                    }
                    val json = JSONObject(raw)
                    if (json.optString("status") == "success") {
                        val uObj = json.optJSONObject("user")
                        if (uObj == null) {
                            val serverVer = json.optString("version", "3.1")
                            return@withContext GasAuthOutcome.ServerOutdated(
                                version = serverVer,
                                message = "Apps Script masih versi $serverVer (Code.gs v3.2 / sheet 'user' belum di-deploy ulang sebagai Versi baru)"
                            )
                        }
                        val user = parseGasUserObject(uObj, email)
                        GasAuthOutcome.Success(user)
                    } else {
                        val code = json.optString("code", "")
                        val msg = json.optString("message", "Gagal verifikasi di sheet 'user'")
                        when (code) {
                            "INVALID_PASSWORD" -> GasAuthOutcome.WrongPassword(msg)
                            "USER_NOT_FOUND" -> GasAuthOutcome.UserNotFound(msg)
                            else -> GasAuthOutcome.NetworkOrServerError(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                GasAuthOutcome.NetworkOrServerError(e.message ?: "Koneksi GAS gagal")
            }
        }
    }

    suspend fun syncUserToGas(
        scriptUrl: String,
        name: String,
        email: String,
        role: String,
        company: String,
        uid: String = "",
        password: String = ""
    ) {
        if (scriptUrl.isBlank() || (!scriptUrl.startsWith("http://") && !scriptUrl.startsWith("https://"))) return
        if (email.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("action", "sync_user")
                    put("name", name.trim())
                    put("email", email.trim().lowercase())
                    put("role", role.trim())
                    put("company", company.trim())
                    put("uid", uid.trim())
                    if (password.isNotBlank()) put("password", password)
                }
                val req = Request.Builder()
                    .url(scriptUrl.trim())
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                okHttpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.d("LsrRepository", "GAS user sync note: ${e.message}")
            }
        }
    }

    private fun parseGasUserObject(
        uObj: JSONObject?,
        fallbackEmail: String,
        fallbackName: String = "",
        fallbackRole: String = "HSE Officer",
        fallbackCompany: String = "HSSE Operations",
        fallbackUid: String = ""
    ): FirestoreUser {
        val cleanEmail = (uObj?.optString("email", "") ?: fallbackEmail).trim().lowercase()
        val cleanName = (uObj?.optString("name", "") ?: fallbackName).trim()
            .ifBlank { cleanEmail.substringBefore("@") }
        val cleanRole = (uObj?.optString("role", "") ?: fallbackRole).trim()
            .ifBlank { "HSE Officer" }
        val cleanCompany = (uObj?.optString("company", "") ?: fallbackCompany).trim()
            .ifBlank { "HSSE Operations" }
        val cleanUid = (uObj?.optString("uid", "") ?: fallbackUid).trim()
            .ifBlank { "gas_${cleanEmail.hashCode()}" }
        return FirestoreUser(
            uid = cleanUid,
            displayName = cleanName,
            displayNameLowercase = cleanName.lowercase(),
            email = cleanEmail,
            company = cleanCompany,
            position = cleanRole,
            searchKeywords = listOf(cleanName.lowercase(), cleanEmail)
        )
    }

    suspend fun pushFindingUpdateToGas(scriptUrl: String, finding: FindingItem) {
        if (scriptUrl.isBlank() || (!scriptUrl.startsWith("http://") && !scriptUrl.startsWith("https://"))) return
        markLocalFindingMutation(finding.timestamp, finding.ruleId, finding.area)
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("action", "update_finding")
                    put("findingKey", "${finding.timestamp}_${finding.ruleId}_${finding.area}")
                    put("timestamp", finding.timestamp)
                    put("inspectionId", finding.inspectionRecordId)
                    put("inspectorName", finding.inspectorName)
                    put("company", finding.company)
                    put("area", finding.area)
                    put("ruleId", finding.ruleId)
                    put("category", finding.category)
                    put("itemText", finding.itemText)
                    put("standardRef", finding.standardRef)
                    put("isCritical", finding.isCritical)
                    put("picName", finding.assignedPicName)
                    put("picEmail", finding.assignedPicEmail)
                    put("notes", finding.notes)
                    put("correctiveAction", finding.correctiveAction)
                    put("status", finding.status)
                    put("rating", finding.satisfactionRating)
                }
                val request = Request.Builder()
                    .url(scriptUrl)
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                okHttpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.d("LsrRepository", "GAS finding update note: ${e.message}")
            }
        }
    }

    suspend fun testGasConnection(url: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    return@withContext Result.failure(Exception("URL tidak valid"))
                }
                val separator = if (url.contains("?")) "&" else "?"
                val getReq = Request.Builder()
                    .url("${url.trim()}${separator}action=ping&t=${System.currentTimeMillis()}")
                    .get()
                    .build()

                var getOk = false
                var supportsPull = false
                var version = ""
                okHttpClient.newCall(getReq).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && raw.trimStart().startsWith("{")) {
                        val json = JSONObject(raw)
                        getOk = json.optString("status") in listOf("online", "success")
                        supportsPull = json.optBoolean("supportsPull", false)
                        version = json.optString("version", "")
                    }
                }

                val payload = JSONObject().apply {
                    put("action", "ping")
                    put("timestamp", System.currentTimeMillis())
                }
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val postReq = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(mediaType))
                    .build()

                okHttpClient.newCall(postReq).execute().use { response ->
                    val rawPost = response.body?.string().orEmpty()
                    val postJsonOk = response.isSuccessful && rawPost.trimStart().startsWith("{")
                    if (response.isSuccessful && (getOk || postJsonOk)) {
                        val modeLabel = if (supportsPull) {
                            "doGet & doPost v${version.ifBlank { "3.1" }} (Sync 2 Arah Siap)"
                        } else {
                            "doPost aktif • doGet masih versi lama (perlu update Code.gs di Apps Script agar bisa tarik data)"
                        }
                        Result.success("Terhubung ke Google Sheets! $modeLabel")
                    } else if (response.isSuccessful) {
                        Result.failure(Exception("Webhook merespon HTML (bukan JSON). Pastikan di Apps Script 'Who has access = Anyone'."))
                    } else {
                        Result.failure(Exception("Google Apps Script merespon kode: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun testFirebaseConnection(url: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                var authStatus = if (currentUser != null) "User: ${currentUser.email}" else "Guest (Belum Login)"

                // Test 1: Cloud Firestore Native SDK Connection
                var firestoreOk = false
                try {
                    val db = FirebaseFirestore.getInstance()
                    val pingMap = mapOf("ping" to System.currentTimeMillis(), "user" to (currentUser?.email ?: "guest"))
                    val task = db.collection("system").document("ping").set(pingMap)
                    Tasks.await(task, 7, TimeUnit.SECONDS)
                    firestoreOk = true
                } catch (e: Exception) {
                    Log.d("LsrRepository", "Firestore ping note: ${e.message}")
                }

                // Test 2: Realtime Database with Auth Token
                val cleanUrl = url.trimEnd('/')
                var idToken: String? = null
                if (currentUser != null) {
                    try {
                        val tokenTask = currentUser.getIdToken(false)
                        idToken = Tasks.await(tokenTask, 5, TimeUnit.SECONDS)?.token
                    } catch (e: Exception) {
                        Log.w("LsrRepository", "Token fetch note: ${e.message}")
                    }
                }

                val pingUrlBase = if (cleanUrl.endsWith(".json")) cleanUrl else "$cleanUrl/ping.json"
                val pingUrl = if (!idToken.isNullOrBlank()) "$pingUrlBase?auth=$idToken" else pingUrlBase

                val payload = JSONObject().apply {
                    put("ping", System.currentTimeMillis())
                    put("user", currentUser?.email ?: "guest")
                }
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(pingUrl)
                    .put(payload.toString().toRequestBody(mediaType))
                    .build()

                var rtdbCode = 0
                var rtdbOk = false
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        rtdbCode = response.code
                        rtdbOk = response.isSuccessful
                    }
                } catch (e: Exception) {
                    Log.d("LsrRepository", "RTDB call note: ${e.message}")
                }

                if (firestoreOk || rtdbOk) {
                    val msg = buildString {
                        append("✅ Firebase Terhubung! ($authStatus)")
                        if (firestoreOk) append(" • Cloud Firestore OK")
                        if (rtdbOk) append(" • Realtime DB OK")
                    }
                    Result.success(msg)
                } else if (rtdbCode == 401) {
                    if (currentUser == null) {
                        Result.failure(
                            Exception("⚠️ Firebase merespon 401 (Permission Denied). Aturan keamanan Firebase mewajibkan user login. Silakan tekan tombol 'Masuk / Login' di pojok kanan atas Dashboard.")
                        )
                    } else {
                        Result.failure(
                            Exception("⚠️ Firebase merespon 401: Token akun belum memiliki izin tulis di Realtime Database. Pastikan Rules Realtime Database mengizinkan auth != null.")
                        )
                    }
                } else {
                    Result.failure(Exception("Firebase merespon kode: $rtdbCode. Pastikan URL dan koneksi internet aktif."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
