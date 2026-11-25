package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LsrDao {
    // Active draft answers
    @Query("SELECT * FROM active_answers")
    fun getActiveAnswersFlow(): Flow<List<ActiveAnswer>>

    @Query("SELECT * FROM active_answers")
    suspend fun getActiveAnswers(): List<ActiveAnswer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveAnswer(answer: ActiveAnswer)

    @Query("DELETE FROM active_answers WHERE questionId = :questionId")
    suspend fun deleteActiveAnswer(questionId: String)

    @Query("DELETE FROM active_answers")
    suspend fun clearActiveAnswers()

    // Inspection Records (Full + Lightweight Windowed Projection)
    @Query("SELECT * FROM inspection_records ORDER BY timestamp DESC")
    fun getInspectionRecordsFlow(): Flow<List<InspectionRecord>>

    @Query("SELECT id, inspectorName, area, timestamp, score, isSynced, projectId, company, lsrCategories FROM inspection_records ORDER BY timestamp DESC LIMIT :limit")
    fun getInspectionSummariesFlow(limit: Int): Flow<List<InspectionSummary>>

    @Query("SELECT COUNT(*) FROM inspection_records")
    fun getTotalInspectionCountFlow(): Flow<Int>

    @Query("SELECT * FROM inspection_records WHERE id = :id LIMIT 1")
    suspend fun getInspectionRecordById(id: Int): InspectionRecord?

    @Query("SELECT COUNT(*) AS totalAudits, COALESCE(CAST(AVG(score) AS INTEGER), 100) AS avgScore FROM inspection_records")
    fun getDashboardMetricsFlow(): Flow<DashboardMetrics>

    @Query("SELECT COUNT(*) FROM inspection_records WHERE isSynced = 0")
    fun getPendingSyncRecordsCountFlow(): Flow<Int>

    @Query("SELECT * FROM inspection_records WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedRecords(): List<InspectionRecord>

    @Query("UPDATE inspection_records SET isSynced = 1 WHERE id = :id")
    suspend fun markRecordSynced(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspectionRecord(record: InspectionRecord): Long

    // Findings
    @Query("SELECT * FROM finding_items ORDER BY timestamp DESC")
    fun getFindingsFlow(): Flow<List<FindingItem>>

    @Query("SELECT * FROM finding_items WHERE id = :id LIMIT 1")
    suspend fun getFindingById(id: Int): FindingItem?

    @Query("SELECT * FROM finding_items WHERE status = :status ORDER BY timestamp DESC")
    fun getFindingsByStatusFlow(status: String): Flow<List<FindingItem>>

    @Query("SELECT COUNT(*) FROM finding_items WHERE isSynced = 0")
    fun getPendingSyncFindingsCountFlow(): Flow<Int>

    @Query("SELECT * FROM finding_items WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedFindings(): List<FindingItem>

    @Query("UPDATE finding_items SET isSynced = 1 WHERE id = :id")
    suspend fun markFindingSynced(id: Int)

    @Query("UPDATE finding_items SET evidencePhotoUri = :localUri, evidencePhotoUrl = :remoteUrl, isSynced = 0 WHERE id = :id")
    suspend fun updateFindingEvidencePhoto(id: Int, localUri: String, remoteUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<FindingItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: FindingItem): Long

    @Update
    suspend fun updateFindingEntity(finding: FindingItem)

    @Query("UPDATE finding_items SET status = :status, correctiveAction = :correctiveAction, isSynced = 0 WHERE id = :id")
    suspend fun updateFinding(id: Int, status: String, correctiveAction: String)

    @Query("""
        UPDATE finding_items 
        SET assignedPicName = :picName, assignedPicEmail = :picEmail, assignedPicRole = :picRole, assignedTimestamp = :timestamp, status = 'OPEN', isSynced = 0
        WHERE id = :id
    """)
    suspend fun assignFindingPic(id: Int, picName: String, picEmail: String, picRole: String, timestamp: Long)

    @Query("""
        UPDATE finding_items 
        SET correctiveAction = :action, actionEvidenceNotes = :evidence, actionSubmittedBy = :submittedBy, actionSubmittedTimestamp = :timestamp, status = 'ACTION_SUBMITTED', isSynced = 0
        WHERE id = :id
    """)
    suspend fun submitPicAction(id: Int, action: String, evidence: String, submittedBy: String, timestamp: Long)

    @Query("""
        UPDATE finding_items 
        SET status = 'CLOSED', verifiedBy = :verifiedBy, verifiedTimestamp = :verifiedTime, satisfactionRating = :rating, inspectorComment = :comment, isSynced = 0
        WHERE id = :id
    """)
    suspend fun verifyAndCloseFinding(id: Int, verifiedBy: String, verifiedTime: Long, rating: Int, comment: String)

    @Query("""
        UPDATE finding_items 
        SET status = 'REOPENED', reopenReason = :reopenReason, isSynced = 0
        WHERE id = :id
    """)
    suspend fun reopenFinding(id: Int, reopenReason: String)

    @Query("DELETE FROM finding_items WHERE id = :id")
    suspend fun deleteFinding(id: Int)

    // Action History Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: ActionHistoryLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLogs(logs: List<ActionHistoryLog>)

    @Query("SELECT * FROM action_history_logs WHERE findingId = :findingId ORDER BY timestamp ASC")
    fun getActionLogsFlow(findingId: Int): Flow<List<ActionHistoryLog>>

    @Query("SELECT * FROM action_history_logs ORDER BY timestamp DESC")
    fun getAllActionLogsFlow(): Flow<List<ActionHistoryLog>>

    // App Notifications (Windowed + Retention Cleanup)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification): Long

    @Query("SELECT * FROM app_notifications WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getNotificationByRemoteId(remoteId: String): AppNotification?

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getNotificationsFlow(): Flow<List<AppNotification>>

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC LIMIT :limit")
    fun getNotificationsWindowFlow(limit: Int): Flow<List<AppNotification>>

    @Query("SELECT COUNT(*) FROM app_notifications")
    fun getTotalNotificationsCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadNotificationsCountFlow(): Flow<Int>

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: Int)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM app_notifications WHERE isRead = 1 AND timestamp < :cutoffTimestamp")
    suspend fun purgeOldReadNotifications(cutoffTimestamp: Long)

    @Query("DELETE FROM app_notifications WHERE isRead = 1")
    suspend fun deleteReadNotifications()

    @Query("DELETE FROM app_notifications")
    suspend fun clearNotifications()
}

