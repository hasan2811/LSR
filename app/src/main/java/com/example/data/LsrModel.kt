package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

data class LsrItem(
    val id: String,
    val category: String,
    val item_text: String,
    val item_text_en: String = "",
    val category_en: String = "",
    val standard_ref: String = "IOGP LSR, OSHA",
    val is_critical: Boolean = false
) {
    fun getLocalizedCategory(language: AppLanguage): String {
        return if (language == AppLanguage.EN && category_en.isNotBlank()) category_en else category
    }

    fun getLocalizedText(language: AppLanguage): String {
        return if (language == AppLanguage.EN && item_text_en.isNotBlank()) item_text_en else item_text
    }
}

enum class AnswerType {
    YES, NO, NA, UNANSWERED
}

@Entity(tableName = "active_answers")
data class ActiveAnswer(
    @PrimaryKey val questionId: String,
    val answer: String, // YES, NO, NA, UNANSWERED
    val notes: String = "",
    val assignedPicName: String = "",
    val assignedPicEmail: String = "",
    val assignedPicRole: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "inspection_records")
data class InspectionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inspectorName: String,
    val area: String,
    val timestamp: Long = System.currentTimeMillis(),
    val answersJson: String, // JSON mapping of questionId -> AnswerData
    val score: Int, // Percentage of compliance
    val isSynced: Boolean = false
)

data class AnswerData(
    val id: String,
    val answer: AnswerType,
    val notes: String,
    val assignedPicName: String = "",
    val assignedPicEmail: String = "",
    val assignedPicRole: String = ""
)

@Entity(tableName = "finding_items")
data class FindingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inspectionRecordId: Int = 0,
    val ruleId: String,
    val category: String,
    val itemText: String,
    val standardRef: String,
    val isCritical: Boolean,
    val inspectorName: String,
    val inspectorEmail: String = "",
    val inspectorUid: String = "",
    val area: String,
    val notes: String = "",
    // PIC Assignment Details
    val assignedPicName: String = "",
    val assignedPicEmail: String = "",
    val assignedPicRole: String = "",
    val assignedTimestamp: Long = 0L,
    // Corrective Action from PIC
    val correctiveAction: String = "",
    val actionEvidenceNotes: String = "",
    val actionSubmittedBy: String = "",
    val actionSubmittedTimestamp: Long = 0L,
    // Lifecycle Status: "OPEN", "IN_PROGRESS", "ACTION_SUBMITTED", "CLOSED", "REOPENED"
    val status: String = "OPEN",
    // Inspector Rejection / Reopen Feedback
    val reopenReason: String = "",
    // Inspector Verification & Satisfaction Rating
    val verifiedBy: String = "",
    val verifiedTimestamp: Long = 0L,
    val satisfactionRating: Int = 0, // 1 to 5 stars
    val inspectorComment: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "action_history_logs")
data class ActionHistoryLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val findingId: Int,
    val ruleId: String,
    val actorName: String,
    val actorEmail: String = "",
    val actorRole: String = "",
    val actionType: String, // "CREATED_NON_COMPLIANT", "ASSIGNED_TO_PIC", "ACTION_SUBMITTED", "REOPENED_BY_INSPECTOR", "VERIFIED_AND_CLOSED", "STATUS_UPDATED"
    val oldStatus: String = "",
    val newStatus: String = "",
    val notes: String = "",
    val rating: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipientEmail: String = "",
    val recipientName: String = "",
    val title: String,
    val message: String,
    val type: String, // "ASSIGNMENT", "ACTION_SUBMITTED", "REOPENED", "CLOSED"
    val findingId: Int = 0,
    val ruleId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isSynced: Boolean = false
)

data class TeamRosterMember(
    val name: String,
    val email: String,
    val role: String,
    val department: String
)

class Converters {
    private val moshi = Moshi.Builder().build()
    private val answerMapType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    private val answerMapAdapter = moshi.adapter<Map<String, String>>(answerMapType)

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return value?.let { answerMapAdapter.toJson(it) }
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return value?.let { answerMapAdapter.fromJson(it) }
    }
}

