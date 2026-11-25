package com.example.data

import androidx.room.Entity
import androidx.room.Index
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

@Entity(
    tableName = "inspection_records",
    indices = [
        Index(value = ["timestamp", "id"], name = "idx_inspection_cursor"),
        Index(value = ["projectId", "timestamp", "id"], name = "idx_inspection_proj_cursor"),
        Index(value = ["isSynced", "timestamp"], name = "idx_inspection_sync")
    ]
)
data class InspectionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inspectorName: String,
    val area: String,
    val timestamp: Long = System.currentTimeMillis(),
    val answersJson: String, // JSON mapping of questionId -> AnswerData
    val score: Int, // Percentage of compliance
    val isSynced: Boolean = false,
    val projectId: String = "",
    val company: String = "",
    val lsrCategories: String = ""
)

data class InspectionSummary(
    val id: Int,
    val inspectorName: String,
    val area: String,
    val timestamp: Long,
    val score: Int,
    val isSynced: Boolean,
    val projectId: String = "",
    val company: String = "",
    val lsrCategories: String = ""
)

data class DashboardMetrics(
    val totalAudits: Int = 0,
    val avgScore: Int = 100
)

data class AnswerData(
    val id: String,
    val answer: AnswerType,
    val notes: String,
    val assignedPicName: String = "",
    val assignedPicEmail: String = "",
    val assignedPicRole: String = ""
)

@Entity(
    tableName = "finding_items",
    indices = [
        Index(value = ["projectId", "timestamp", "id"], name = "idx_finding_cursor_proj"),
        Index(value = ["status", "timestamp", "id"], name = "idx_finding_status_cursor"),
        Index(value = ["isSynced", "cachedAt"], name = "idx_finding_sync_ttl"),
        Index(value = ["updatedAt"], name = "idx_finding_updated_at"),
        Index(value = ["remoteDocId"], name = "idx_finding_remote_doc_id")
    ]
)
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
    val isSynced: Boolean = false,
    val projectId: String = "",
    val evidencePhotoUri: String = "",
    val evidencePhotoUrl: String = "",
    val company: String = "",
    val remoteDocId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun slaDeadlineTimestamp(): Long = timestamp + (if (isCritical) 24L else 72L) * 3_600_000L

    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean =
        !status.equals("CLOSED", ignoreCase = true) && now > slaDeadlineTimestamp()

    fun slaRemainingHours(now: Long = System.currentTimeMillis()): Long =
        (slaDeadlineTimestamp() - now) / 3_600_000L
}

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

@Entity(
    tableName = "app_notifications",
    indices = [
        Index(value = ["timestamp", "id"], name = "idx_notif_cursor"),
        Index(value = ["isRead", "timestamp"], name = "idx_notif_read_ttl")
    ]
)
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipientEmail: String = "",
    val recipientName: String = "",
    val title: String,
    val message: String,
    val type: String, // "ASSIGNMENT", "ACTION_SUBMITTED", "REOPENED", "CLOSED", "REMINDER", "CHAT_MESSAGE"
    val findingId: Int = 0,
    val ruleId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isSynced: Boolean = false,
    val remoteId: String = "",
    val senderName: String = ""
)

data class FindingCursor(
    val lastTimestamp: Long,
    val lastId: Int,
    val lastRemoteDocId: String = ""
)

data class KeysetCursor(
    val lastTimestamp: Long,
    val lastId: Int
)

enum class DateRangeFilter {
    ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS
}

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

data class ActivityFilterCriteria(
    val dateRange: DateRangeFilter = DateRangeFilter.ALL,
    val userName: String = "",
    val lsrType: String = "",
    val company: String = "",
    val location: String = ""
) {
    val activeCount: Int
        get() = listOfNotNull(
            dateRange.takeIf { it != DateRangeFilter.ALL },
            userName.takeIf { it.isNotBlank() },
            lsrType.takeIf { it.isNotBlank() },
            company.takeIf { it.isNotBlank() },
            location.takeIf { it.isNotBlank() }
        ).size

    fun matches(
        timestamp: Long,
        users: List<String>,
        lsrTypes: String,
        itemCompany: String,
        itemLocation: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val matchesDate = when (dateRange) {
            DateRangeFilter.ALL -> true
            DateRangeFilter.TODAY -> {
                val calNow = java.util.Calendar.getInstance().apply { timeInMillis = now }
                val calItem = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
                calNow.get(java.util.Calendar.YEAR) == calItem.get(java.util.Calendar.YEAR) &&
                    calNow.get(java.util.Calendar.DAY_OF_YEAR) == calItem.get(java.util.Calendar.DAY_OF_YEAR)
            }
            DateRangeFilter.LAST_7_DAYS -> timestamp >= now - 7L * 86_400_000L
            DateRangeFilter.LAST_30_DAYS -> timestamp >= now - 30L * 86_400_000L
        }
        if (!matchesDate) return false

        if (userName.isNotBlank() && users.none { it.equals(userName, ignoreCase = true) || it.contains(userName, ignoreCase = true) }) {
            return false
        }
        if (lsrType.isNotBlank() && !lsrTypes.contains(lsrType, ignoreCase = true)) {
            return false
        }
        if (company.isNotBlank() && !itemCompany.equals(company, ignoreCase = true) && !itemCompany.contains(company, ignoreCase = true)) {
            return false
        }
        if (location.isNotBlank() && !itemLocation.equals(location, ignoreCase = true) && !itemLocation.contains(location, ignoreCase = true)) {
            return false
        }
        return true
    }
}

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

