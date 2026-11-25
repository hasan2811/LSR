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

    @Query(
        """
        SELECT id, inspectorName, area, timestamp, score, isSynced, projectId, company, lsrCategories
        FROM inspection_records
        WHERE (timestamp < :cursorTs OR (timestamp = :cursorTs AND id < :cursorId))
        ORDER BY timestamp DESC, id DESC
        LIMIT :pageSize
        """
    )
    suspend fun getInspectionSummariesPageByCursor(
        cursorTs: Long,
        cursorId: Int,
        pageSize: Int
    ): List<InspectionSummary>

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

    @Query("SELECT * FROM inspection_records WHERE timestamp = :timestamp AND inspectorName = :inspectorName AND area = :area LIMIT 1")
    suspend fun findInspectionBySignature(timestamp: Long, inspectorName: String, area: String): InspectionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspectionRecord(record: InspectionRecord): Long

    // Findings
    @Query("SELECT * FROM finding_items ORDER BY timestamp DESC")
    fun getFindingsFlow(): Flow<List<FindingItem>>

    @Query("SELECT * FROM finding_items WHERE id = :id LIMIT 1")
    suspend fun getFindingById(id: Int): FindingItem?

    @Query("SELECT * FROM finding_items WHERE inspectionRecordId = :recordId")
    suspend fun getFindingsByInspectionRecordId(recordId: Int): List<FindingItem>

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

    @Query("SELECT * FROM finding_items WHERE timestamp = :timestamp AND ruleId = :ruleId AND area = :area LIMIT 1")
    suspend fun findFindingBySignature(timestamp: Long, ruleId: String, area: String): FindingItem?

    @Query(
        """
        SELECT * FROM finding_items
        WHERE (:projectId = '' OR projectId = '' OR projectId = :projectId)
          AND (
              :statusFilter = 'ALL'
              OR (:statusFilter = 'OPEN' AND UPPER(status) = 'OPEN')
              OR (:statusFilter = 'IN_REVIEW' AND UPPER(status) IN ('ACTION_SUBMITTED', 'IN_REVIEW', 'IN_PROGRESS'))
              OR (:statusFilter = 'CLOSED' AND UPPER(status) = 'CLOSED')
              OR (:statusFilter = 'REOPENED' AND UPPER(status) = 'REOPENED')
              OR (:statusFilter = 'OVERDUE' AND UPPER(status) != 'CLOSED' AND ((isCritical = 1 AND timestamp < :criticalCutoff) OR (isCritical = 0 AND timestamp < :highCutoff)))
          )
          AND (
              :query = ''
              OR itemText LIKE '%' || :query || '%'
              OR category LIKE '%' || :query || '%'
              OR ruleId LIKE '%' || :query || '%'
              OR area LIKE '%' || :query || '%'
              OR company LIKE '%' || :query || '%'
              OR inspectorName LIKE '%' || :query || '%'
              OR assignedPicName LIKE '%' || :query || '%'
          )
          AND (:dateCutoff = 0 OR timestamp >= :dateCutoff)
          AND (:filterUser = '' OR inspectorName LIKE '%' || :filterUser || '%' OR assignedPicName LIKE '%' || :filterUser || '%')
          AND (:filterLsr = '' OR category LIKE '%' || :filterLsr || '%' OR ruleId LIKE '%' || :filterLsr || '%')
          AND (:filterCompany = '' OR company LIKE '%' || :filterCompany || '%')
          AND (:filterLocation = '' OR area LIKE '%' || :filterLocation || '%')
          AND (timestamp < :cursorTs OR (timestamp = :cursorTs AND id < :cursorId))
        ORDER BY timestamp DESC, id DESC
        LIMIT :pageSize
        """
    )
    suspend fun getFindingsPageByCursor(
        projectId: String,
        statusFilter: String,
        query: String,
        dateCutoff: Long,
        filterUser: String,
        filterLsr: String,
        filterCompany: String,
        filterLocation: String,
        criticalCutoff: Long,
        highCutoff: Long,
        cursorTs: Long,
        cursorId: Int,
        pageSize: Int
    ): List<FindingItem>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM finding_items WHERE (:projectId = '' OR projectId = :projectId)")
    suspend fun getMaxFindingUpdatedAt(projectId: String): Long

    @Query("SELECT * FROM finding_items WHERE remoteDocId = :remoteDocId LIMIT 1")
    suspend fun findFindingByRemoteDocId(remoteDocId: String): FindingItem?

    @Query("SELECT evidencePhotoUri FROM finding_items WHERE evidencePhotoUri != '' AND (isSynced = 0 OR UPPER(status) != 'CLOSED')")
    suspend fun getProtectedEvidencePhotoPaths(): List<String>

    @Query("DELETE FROM finding_items WHERE isSynced = 1 AND UPPER(status) = 'CLOSED' AND cachedAt < :cutoffTimestamp")
    suspend fun evictExpiredClosedFindings(cutoffTimestamp: Long): Int

    @Query(
        """
        DELETE FROM finding_items
        WHERE id IN (
            SELECT id FROM finding_items
            WHERE isSynced = 1 AND UPPER(status) = 'CLOSED'
            ORDER BY timestamp DESC
            LIMIT -1 OFFSET :maxRetainedCount
        )
        """
    )
    suspend fun trimSyncedClosedFindingsToMaxCount(maxRetainedCount: Int): Int

    @Query("""
        UPDATE finding_items 
        SET status = :status, correctiveAction = :correctiveAction, assignedPicName = :picName, assignedPicEmail = :picEmail, satisfactionRating = :rating, updatedAt = :updatedAt, cachedAt = :updatedAt, isSynced = 1
        WHERE id = :id
    """)
    suspend fun updateFindingStatusFromRemote(
        id: Int,
        status: String,
        correctiveAction: String,
        picName: String,
        picEmail: String,
        rating: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<FindingItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: FindingItem): Long

    @Update
    suspend fun updateFindingEntity(finding: FindingItem)

    @Query("UPDATE finding_items SET status = :status, correctiveAction = :correctiveAction, updatedAt = :updatedAt, isSynced = 0 WHERE id = :id")
    suspend fun updateFinding(id: Int, status: String, correctiveAction: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE finding_items 
        SET assignedPicName = :picName, assignedPicEmail = :picEmail, assignedPicRole = :picRole, assignedTimestamp = :timestamp, updatedAt = :timestamp, status = 'OPEN', isSynced = 0
        WHERE id = :id
    """)
    suspend fun assignFindingPic(id: Int, picName: String, picEmail: String, picRole: String, timestamp: Long)

    @Query("""
        UPDATE finding_items 
        SET correctiveAction = :action, actionEvidenceNotes = :evidence, actionSubmittedBy = :submittedBy, actionSubmittedTimestamp = :timestamp, updatedAt = :timestamp, status = 'ACTION_SUBMITTED', isSynced = 0
        WHERE id = :id
    """)
    suspend fun submitPicAction(id: Int, action: String, evidence: String, submittedBy: String, timestamp: Long)

    @Query("""
        UPDATE finding_items 
        SET status = 'CLOSED', verifiedBy = :verifiedBy, verifiedTimestamp = :verifiedTime, updatedAt = :verifiedTime, satisfactionRating = :rating, inspectorComment = :comment, isSynced = 0
        WHERE id = :id
    """)
    suspend fun verifyAndCloseFinding(id: Int, verifiedBy: String, verifiedTime: Long, rating: Int, comment: String)

    @Query("""
        UPDATE finding_items 
        SET status = 'REOPENED', reopenReason = :reopenReason, updatedAt = :updatedAt, isSynced = 0
        WHERE id = :id
    """)
    suspend fun reopenFinding(id: Int, reopenReason: String, updatedAt: Long = System.currentTimeMillis())

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

    @Query(
        """
        SELECT * FROM app_notifications
        WHERE (timestamp < :cursorTs OR (timestamp = :cursorTs AND id < :cursorId))
        ORDER BY timestamp DESC, id DESC
        LIMIT :pageSize
        """
    )
    suspend fun getNotificationsPageByCursor(
        cursorTs: Long,
        cursorId: Int,
        pageSize: Int
    ): List<AppNotification>

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

