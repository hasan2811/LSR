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

    // Inspection Records
    @Query("SELECT * FROM inspection_records ORDER BY timestamp DESC")
    fun getInspectionRecordsFlow(): Flow<List<InspectionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspectionRecord(record: InspectionRecord): Long

    // Findings
    @Query("SELECT * FROM finding_items ORDER BY timestamp DESC")
    fun getFindingsFlow(): Flow<List<FindingItem>>

    @Query("SELECT * FROM finding_items WHERE id = :id LIMIT 1")
    suspend fun getFindingById(id: Int): FindingItem?

    @Query("SELECT * FROM finding_items WHERE status = :status ORDER BY timestamp DESC")
    fun getFindingsByStatusFlow(status: String): Flow<List<FindingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<FindingItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: FindingItem): Long

    @Update
    suspend fun updateFindingEntity(finding: FindingItem)

    @Query("UPDATE finding_items SET status = :status, correctiveAction = :correctiveAction WHERE id = :id")
    suspend fun updateFinding(id: Int, status: String, correctiveAction: String)

    @Query("""
        UPDATE finding_items 
        SET assignedPicName = :picName, assignedPicEmail = :picEmail, assignedPicRole = :picRole, assignedTimestamp = :timestamp, status = 'OPEN' 
        WHERE id = :id
    """)
    suspend fun assignFindingPic(id: Int, picName: String, picEmail: String, picRole: String, timestamp: Long)

    @Query("""
        UPDATE finding_items 
        SET correctiveAction = :action, actionEvidenceNotes = :evidence, actionSubmittedBy = :submittedBy, actionSubmittedTimestamp = :timestamp, status = 'ACTION_SUBMITTED' 
        WHERE id = :id
    """)
    suspend fun submitPicAction(id: Int, action: String, evidence: String, submittedBy: String, timestamp: Long)

    @Query("""
        UPDATE finding_items 
        SET status = 'CLOSED', verifiedBy = :verifiedBy, verifiedTimestamp = :verifiedTime, satisfactionRating = :rating, inspectorComment = :comment 
        WHERE id = :id
    """)
    suspend fun verifyAndCloseFinding(id: Int, verifiedBy: String, verifiedTime: Long, rating: Int, comment: String)

    @Query("""
        UPDATE finding_items 
        SET status = 'REOPENED', reopenReason = :reopenReason 
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

    // App Notifications
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification): Long

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getNotificationsFlow(): Flow<List<AppNotification>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadNotificationsCountFlow(): Flow<Int>

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: Int)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM app_notifications")
    suspend fun clearNotifications()
}

