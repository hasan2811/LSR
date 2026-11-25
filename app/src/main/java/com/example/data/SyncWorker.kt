package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).lsrDao()
        val prefs = applicationContext.getSharedPreferences("lsr_prefs", Context.MODE_PRIVATE)
        val fsPrefs = applicationContext.getSharedPreferences("lsr_firestore_prefs", Context.MODE_PRIVATE)

        val isGasEnabled = prefs.getBoolean("is_gas_sync_enabled", true)
        val scriptUrl = prefs.getString("script_url", "")?.ifBlank { GoogleAppsScriptSnippet.DEFAULT_WEBHOOK_URL }
            ?: GoogleAppsScriptSnippet.DEFAULT_WEBHOOK_URL
        val isFirebaseEnabled = prefs.getBoolean("is_firebase_sync_enabled", true)
        val firebaseUrl = prefs.getString("firebase_url", "https://lowerzakum-482af-default-rtdb.firebaseio.com") ?: ""
        val activeProjId = fsPrefs.getString("active_project_id", "default_proj")?.ifBlank { "default_proj" } ?: "default_proj"

        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser
        var anyTransientFailure = false

        // 1. Flush unsynced Inspection Records
        val unsyncedRecords = dao.getUnsyncedRecords()
        for (record in unsyncedRecords) {
            var synced = false
            val projId = record.projectId.ifBlank { activeProjId }

            if (isFirebaseEnabled) {
                try {
                    val doc = hashMapOf<String, Any>(
                        "inspectionId" to record.id,
                        "projectId" to projId,
                        "inspectorName" to record.inspectorName,
                        "userUid" to (currentUser?.uid ?: ""),
                        "userEmail" to (currentUser?.email ?: ""),
                        "area" to record.area,
                        "timestamp" to record.timestamp,
                        "updatedAt" to System.currentTimeMillis(),
                        "dateFormatted" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp)),
                        "score" to record.score,
                        "answersJson" to record.answersJson
                    )
                    db.collection("projects")
                        .document(projId)
                        .collection("inspections")
                        .document("inspection_${record.id}")
                        .set(doc, SetOptions.merge())
                        .await()
                    synced = true
                } catch (e: Exception) {
                    Log.w("SyncWorker", "Firestore record sync failed for #${record.id}: ${e.message}")
                    anyTransientFailure = true
                }

                if (!synced && firebaseUrl.isNotBlank()) {
                    try {
                        val idToken = currentUser?.getIdToken(false)?.await()?.token
                        val cleanUrl = firebaseUrl.trimEnd('/')
                        val baseEndpoint = if (cleanUrl.endsWith(".json")) cleanUrl else "$cleanUrl/inspections/inspection_${record.id}.json"
                        val finalUrl = if (!idToken.isNullOrBlank()) "$baseEndpoint?auth=$idToken" else baseEndpoint
                        val payload = JSONObject().apply {
                            put("inspectionId", record.id)
                            put("inspectorName", record.inspectorName)
                            put("area", record.area)
                            put("timestamp", record.timestamp)
                            put("score", record.score)
                        }
                        val req = Request.Builder()
                            .url(finalUrl)
                            .put(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) synced = true
                        }
                    } catch (e: Exception) {
                        Log.w("SyncWorker", "RTDB fallback failed for #${record.id}: ${e.message}")
                    }
                }
            }

            if (isGasEnabled && scriptUrl.startsWith("http")) {
                try {
                    val parsedAnswersArr = JSONArray()
                    val parsedModulesArr = JSONArray()
                    var yesCount = 0
                    var noCount = 0
                    var naCount = 0
                    runCatching {
                        val root = JSONObject(record.answersJson)
                        val mods = root.optJSONArray("applicableModules")
                        if (mods != null) {
                            for (i in 0 until mods.length()) parsedModulesArr.put(mods.optString(i))
                        }
                        val ansObj = root.optJSONObject("answers") ?: JSONObject()
                        val keys = ansObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val item = ansObj.optJSONObject(k) ?: continue
                            val ans = item.optString("answer", "UNANSWERED")
                            when (ans) {
                                "YES" -> yesCount++
                                "NO" -> noCount++
                                "NA" -> naCount++
                            }
                            parsedAnswersArr.put(JSONObject().apply {
                                put("id", k)
                                put("answer", ans)
                                put("notes", item.optString("notes", ""))
                            })
                        }
                    }
                    val linkedFindings = dao.getFindingsByInspectionRecordId(record.id)
                    val findingsArr = JSONArray().apply {
                        linkedFindings.forEach { f ->
                            put(JSONObject().apply {
                                put("findingKey", "${f.timestamp}_${f.ruleId}_${f.area}")
                                put("timestamp", f.timestamp)
                                put("ruleId", f.ruleId)
                                put("category", f.category)
                                put("itemText", f.itemText)
                                put("standardRef", f.standardRef)
                                put("isCritical", f.isCritical)
                                put("picName", f.assignedPicName)
                                put("picEmail", f.assignedPicEmail)
                                put("inspectorNotes", f.notes)
                                put("correctiveAction", f.correctiveAction)
                                put("status", f.status)
                                put("rating", f.satisfactionRating)
                            })
                        }
                    }

                    val payload = JSONObject().apply {
                        put("action", "submit_inspection")
                        put("inspectionId", record.id)
                        put("inspectorName", record.inspectorName)
                        put("company", record.company)
                        put("lsrCategories", record.lsrCategories)
                        put("area", record.area)
                        put("timestamp", record.timestamp)
                        put("score", record.score)
                        put("totalQuestions", parsedAnswersArr.length())
                        put("yesCount", yesCount)
                        put("noCount", noCount)
                        put("naCount", naCount)
                        put("applicableModules", parsedModulesArr)
                        put("answers", parsedAnswersArr)
                        put("findings", findingsArr)
                    }
                    val req = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) synced = true
                    }
                } catch (e: Exception) {
                    Log.w("SyncWorker", "GAS sync failed for #${record.id}: ${e.message}")
                }
            }

            if (synced) {
                dao.markRecordSynced(record.id)
            }
        }

        // 2. Flush unsynced Findings (including evidence photo upload if present)
        val unsyncedFindings = dao.getUnsyncedFindings()
        for (finding in unsyncedFindings) {
            val projId = finding.projectId.ifBlank { activeProjId }
            var photoUrl = finding.evidencePhotoUrl
            if (finding.evidencePhotoUri.isNotBlank() && photoUrl.isBlank()) {
                val uploadRes = EvidencePhotoHelper.uploadEvidenceToFirebaseStorage(
                    localFilePath = finding.evidencePhotoUri,
                    projectId = projId,
                    findingId = finding.id
                )
                if (uploadRes.isSuccess) {
                    photoUrl = uploadRes.getOrDefault("")
                    dao.updateFindingEvidencePhoto(finding.id, finding.evidencePhotoUri, photoUrl)
                }
            }

            if (isFirebaseEnabled) {
                try {
                    val now = System.currentTimeMillis()
                    val doc = hashMapOf<String, Any>(
                        "id" to "finding_${finding.id}",
                        "projectId" to projId,
                        "ruleId" to finding.ruleId,
                        "checklistCategory" to finding.category,
                        "questionText" to finding.itemText,
                        "description" to finding.notes,
                        "location" to finding.area,
                        "severity" to if (finding.isCritical) "CRITICAL" else "HIGH",
                        "status" to finding.status,
                        "creatorName" to finding.inspectorName,
                        "creatorEmail" to finding.inspectorEmail,
                        "creatorUid" to finding.inspectorUid,
                        "picName" to finding.assignedPicName,
                        "picEmail" to finding.assignedPicEmail,
                        "picRole" to finding.assignedPicRole,
                        "actionTaken" to finding.correctiveAction,
                        "actionEvidenceNotes" to finding.actionEvidenceNotes,
                        "picRating" to finding.satisfactionRating,
                        "verificationRemarks" to finding.inspectorComment,
                        "reopenReason" to finding.reopenReason,
                        "proofPhotos" to if (photoUrl.isNotBlank()) listOf(photoUrl) else emptyList<String>(),
                        "createdAt" to finding.timestamp,
                        "updatedAt" to now
                    )
                    db.collection("projects")
                        .document(projId)
                        .collection("findings")
                        .document("finding_${finding.id}")
                        .set(doc, SetOptions.merge())
                        .await()
                    dao.markFindingSynced(finding.id)
                } catch (e: Exception) {
                    Log.w("SyncWorker", "Finding sync failed for #${finding.id}: ${e.message}")
                    anyTransientFailure = true
                }
            }

            if (isGasEnabled && scriptUrl.startsWith("http")) {
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
                    val req = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful && !isFirebaseEnabled) {
                            dao.markFindingSynced(finding.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SyncWorker", "GAS finding sync failed for #${finding.id}: ${e.message}")
                }
            }
        }

        // 3. Update pre-aggregated project summary counters
        if (isFirebaseEnabled && activeProjId.isNotBlank()) {
            try {
                val findingsSnap = db.collection("projects").document(activeProjId).collection("findings").get().await()
                if (!findingsSnap.isEmpty) {
                    val totalF = findingsSnap.size()
                    val closedF = findingsSnap.documents.count { (it.getString("status") ?: "").equals("CLOSED", ignoreCase = true) }
                    val openF = totalF - closedF
                    db.collection("projects").document(activeProjId).set(
                        mapOf(
                            "totalFindings" to totalF,
                            "openFindings" to openF,
                            "closedFindings" to closedF,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            } catch (e: Exception) {
                Log.d("SyncWorker", "Project summary rollup note: ${e.message}")
            }
        }

        return if (anyTransientFailure) Result.retry() else Result.success()
    }
}

class DraftReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("lsr_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notif_draft_reminder", true)) return Result.success()

        val dao = AppDatabase.getDatabase(applicationContext).lsrDao()
        val draftCount = dao.getActiveAnswers().count { it.answer != "UNANSWERED" }
        if (draftCount > 0) {
            val isId = prefs.getString("app_lang", "id") == "id"
            val title = if (isId) "📝 Draft Inspeksi LSR Belum Dikirim" else "📝 Unsubmitted LSR Inspection Draft"
            val body = if (isId) {
                "Anda memiliki $draftCount jawaban tersimpan di draft lokal. Lanjutkan dan kirim laporan inspeksi Anda."
            } else {
                "You have $draftCount saved answers in your local draft. Resume and submit your inspection report."
            }
            NotificationHelper.showNotification(
                context = applicationContext,
                notificationId = 45901,
                title = title,
                message = body,
                isCritical = false
            )
        }
        return Result.success()
    }
}

class CacheEvictionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TTL_DAYS = LsrRepository.CACHE_TTL_DAYS
        private const val NOTIFICATION_TTL_DAYS = 30L
        private const val MAX_CACHED_CLOSED_FINDINGS = 5_000
        private const val MAX_EVIDENCE_CACHE_BYTES = 50L * 1024L * 1024L // 50 MB

        fun isSafeToEvictFinding(
            isSynced: Boolean,
            status: String,
            cachedAt: Long,
            cutoffTimestamp: Long
        ): Boolean {
            return isSynced && status.equals("CLOSED", ignoreCase = true) && cachedAt < cutoffTimestamp
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val dao = AppDatabase.getDatabase(applicationContext).lsrDao()
            val now = System.currentTimeMillis()
            val findingCutoff = now - TTL_DAYS * 86_400_000L
            val notifCutoff = now - NOTIFICATION_TTL_DAYS * 86_400_000L

            // 1. Purge read notifications older than 30 days
            dao.purgeOldReadNotifications(notifCutoff)

            // 2. Evict ONLY synced + CLOSED findings older than TTL (never evict unsynced or active findings!)
            val evictedCount = dao.evictExpiredClosedFindings(findingCutoff)
            val trimmedCount = dao.trimSyncedClosedFindingsToMaxCount(MAX_CACHED_CLOSED_FINDINGS)

            // 3. Trim local evidence photos above 50 MB, skipping protected unsynced/open finding photos
            val protectedPaths = dao.getProtectedEvidencePhotoPaths().toSet()
            trimEvidencePhotoCache(
                cacheDir = java.io.File(applicationContext.filesDir, "evidence"),
                maxBytes = MAX_EVIDENCE_CACHE_BYTES,
                protectedPaths = protectedPaths
            )

            Log.d(
                "CacheEvictionWorker",
                "Cache eviction complete: evictedTtl=$evictedCount, trimmedCap=$trimmedCount"
            )
            Result.success()
        } catch (e: Exception) {
            Log.w("CacheEvictionWorker", "Cache eviction note: ${e.message}")
            Result.retry()
        }
    }

    private fun trimEvidencePhotoCache(
        cacheDir: java.io.File,
        maxBytes: Long,
        protectedPaths: Set<String>
    ) {
        if (!cacheDir.exists() || !cacheDir.isDirectory) return
        val files = cacheDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var totalSize = files.sumOf { it.length() }
        for (file in files) {
            if (totalSize <= maxBytes) break
            if (protectedPaths.contains(file.absolutePath)) continue
            val size = file.length()
            if (file.delete()) {
                totalSize -= size
            }
        }
    }
}

object SyncScheduler {
    private const val SYNC_WORK_NAME = "lsr_outbox_sync"
    private const val DRAFT_REMINDER_WORK_NAME = "lsr_draft_reminder"
    private const val CACHE_EVICTION_WORK_NAME = "lsr_cache_eviction_periodic"

    fun enqueueImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleDraftReminder(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(DRAFT_REMINDER_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DraftReminderWorker>(12, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(
            DRAFT_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleCacheEviction(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<CacheEvictionWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CACHE_EVICTION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
