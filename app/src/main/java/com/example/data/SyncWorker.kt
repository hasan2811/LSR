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
        val scriptUrl = prefs.getString("script_url", "") ?: ""
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
                    db.collection("inspections")
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

            if (!synced && isGasEnabled && scriptUrl.startsWith("http")) {
                try {
                    val payload = JSONObject().apply {
                        put("action", "submit_inspection")
                        put("inspectionId", record.id)
                        put("inspectorName", record.inspectorName)
                        put("area", record.area)
                        put("timestamp", record.timestamp)
                        put("score", record.score)
                    }
                    val req = Request.Builder()
                        .url(scriptUrl)
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) synced = true
                    }
                } catch (e: Exception) {
                    Log.w("SyncWorker", "GAS fallback failed for #${record.id}: ${e.message}")
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

object SyncScheduler {
    private const val SYNC_WORK_NAME = "lsr_outbox_sync"
    private const val DRAFT_REMINDER_WORK_NAME = "lsr_draft_reminder"

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
}
