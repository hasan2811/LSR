package com.example.data

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
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

            // Sync to Firestore
            try {
                val db = FirebaseFirestore.getInstance()
                val updateMap = mapOf(
                    "assignedPicName" to picName,
                    "assignedPicEmail" to picEmail,
                    "assignedPicRole" to picRole,
                    "assignedTimestamp" to now,
                    "status" to "OPEN",
                    "updatedAt" to now
                )
                db.collection("findings").document("finding_${existing.inspectionRecordId}_${existing.ruleId}")
                    .set(updateMap, SetOptions.merge())

                // Write log to subcollection
                db.collection("findings").document("finding_${existing.inspectionRecordId}_${existing.ruleId}")
                    .collection("action_logs").add(
                        mapOf(
                            "actionType" to "ASSIGNED_TO_PIC",
                            "actorName" to actorName,
                            "actorEmail" to actorEmail,
                            "actorRole" to actorRole,
                            "notes" to "Ditugaskan kepada PIC: $picName ($picRole)",
                            "timestamp" to now
                        )
                    )
            } catch (e: Exception) {
                Log.d("LsrRepository", "Firestore assign update note: ${e.message}")
            }
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

            // Sync to Firestore
            try {
                val db = FirebaseFirestore.getInstance()
                val updateMap = mapOf(
                    "correctiveAction" to correctiveAction,
                    "actionEvidenceNotes" to evidenceNotes,
                    "actionSubmittedBy" to submittedBy,
                    "actionSubmittedTimestamp" to now,
                    "status" to "ACTION_SUBMITTED",
                    "updatedAt" to now
                )
                db.collection("findings").document("finding_${existing.inspectionRecordId}_${existing.ruleId}")
                    .set(updateMap, SetOptions.merge())

                db.collection("findings").document("finding_${existing.inspectionRecordId}_${existing.ruleId}")
                    .collection("action_logs").add(
                        mapOf(
                            "actionType" to "ACTION_SUBMITTED",
                            "actorName" to submittedBy,
                            "actorEmail" to submittedByEmail,
                            "actorRole" to actorRole,
                            "notes" to "Tindakan: $correctiveAction. Bukti: $evidenceNotes",
                            "timestamp" to now
                        )
                    )
            } catch (e: Exception) {
                Log.d("LsrRepository", "Firestore action update note: ${e.message}")
            }
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

            // Sync to Firestore
            try {
                val db = FirebaseFirestore.getInstance()
                val updateMap = mapOf(
                    "status" to "CLOSED",
                    "verifiedBy" to verifiedBy,
                    "verifiedTimestamp" to now,
                    "satisfactionRating" to rating,
                    "inspectorComment" to comment,
                    "closedAt" to now,
                    "updatedAt" to now
                )
                db.collection("findings").document("finding_${existing.inspectionRecordId}_${existing.ruleId}")
                    .set(updateMap, SetOptions.merge())

                db.collection("findings").document("finding_${existing.inspectionRecordId}_${existing.ruleId}")
                    .collection("action_logs").add(
                        mapOf(
                            "actionType" to "VERIFIED_AND_CLOSED",
                            "actorName" to verifiedBy,
                            "actorEmail" to verifiedByEmail,
                            "actorRole" to actorRole,
                            "notes" to "Diverifikasi & Ditutup. Rating: $rating/5 Bintang. Ulasan: $comment",
                            "rating" to rating,
                            "timestamp" to now
                        )
                    )
            } catch (e: Exception) {
                Log.d("LsrRepository", "Firestore close update note: ${e.message}")
            }
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

            // Sync to Firestore
            try {
                val db = FirebaseFirestore.getInstance()
                val updateMap = mapOf(
                    "status" to "REOPENED",
                    "reopenReason" to reopenReason,
                    "updatedAt" to now
                )
                db.collection("findings").document("finding_${existing.inspectionRecordId}_${existing.ruleId}")
                    .set(updateMap, SetOptions.merge())

                db.collection("findings").document("finding_${existing.inspectionRecordId}_${existing.ruleId}")
                    .collection("action_logs").add(
                        mapOf(
                            "actionType" to "REOPENED_BY_INSPECTOR",
                            "actorName" to actorName,
                            "actorEmail" to actorEmail,
                            "actorRole" to actorRole,
                            "notes" to "Temuan ditolak & dibuka kembali. Alasan: $reopenReason",
                            "timestamp" to now
                        )
                    )
            } catch (e: Exception) {
                Log.d("LsrRepository", "Firestore reopen update note: ${e.message}")
            }
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
                val yesCount = answeredItems.count { it.answer == "YES" || it.answer == "NA" }
                val noCount = answeredItems.count { it.answer == "NO" }
                val naCount = answeredItems.count { it.answer == "NA" }
                val totalCount = answeredItems.size
                val score = if (totalCount > 0) {
                    (yesCount * 100) / totalCount
                } else {
                    100
                }

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
                    fObj.put("ruleId", finding.ruleId)
                    fObj.put("category", finding.category)
                    fObj.put("itemText", finding.itemText)
                    fObj.put("standardRef", finding.standardRef)
                    fObj.put("isCritical", finding.isCritical)
                    fObj.put("inspectorNotes", finding.notes)
                    fObj.put("status", finding.status)
                    fObj.put("area", finding.area)
                    fObj.put("inspectorName", finding.inspectorName)
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
                    // 2A. Native Cloud Firestore sync (Enterprise Level)
                    try {
                        val db = FirebaseFirestore.getInstance()
                        val firestoreDoc = hashMapOf<String, Any>(
                            "inspectionId" to recordId,
                            "inspectorName" to inspectorName,
                            "userUid" to userUid,
                            "userEmail" to userEmail,
                            "area" to area,
                            "timestamp" to now,
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

                        val task = db.collection("inspections").document("inspection_$recordId").set(firestoreDoc, SetOptions.merge())
                        Tasks.await(task, 10, TimeUnit.SECONDS)
                        firebaseSuccess = true
                        Log.d("LsrRepository", "Successfully written to Cloud Firestore /inspections/inspection_$recordId")

                        // Also batch write findings to dedicated /findings collection
                        newFindings.forEach { finding ->
                            val findingDoc = hashMapOf<String, Any>(
                                "inspectionId" to recordId,
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
                            val fTask = db.collection("findings").document("finding_${recordId}_${finding.ruleId}").set(findingDoc, SetOptions.merge())
                            try { Tasks.await(fTask, 5, TimeUnit.SECONDS) } catch (e: Exception) { /* continue */ }
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

    suspend fun testGasConnection(url: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    return@withContext Result.failure(Exception("URL tidak valid"))
                }
                val payload = JSONObject().apply {
                    put("action", "ping")
                    put("timestamp", System.currentTimeMillis())
                }
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(mediaType))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success("Koneksi Google Apps Script berhasil (Status ${response.code})")
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
