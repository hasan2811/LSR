package com.example.data

data class FirestoreUser(
    val uid: String = "",
    val displayName: String = "",
    val displayNameLowercase: String = "",
    val email: String = "",
    val company: String = "Unassigned",
    val position: String = "Not Set",
    val photoURL: String? = null,
    val aiEnabled: Boolean = false,
    val projectIds: List<String> = emptyList(),
    val searchKeywords: List<String> = emptyList()
) {
    fun getEffectiveRole(): String {
        return if (position.isNotBlank() && position != "Not Set") position else "HSE Officer"
    }

    fun getEffectiveCompany(): String {
        return if (company.isNotBlank() && company != "Unassigned") company else "HSSE Operations"
    }
}

data class FirestoreProject(
    val id: String = "",
    val name: String = "HSE Observation",
    val code: String = "HSE-PROJ",
    val createdAt: String = "",
    val ownerUid: String = "",
    val isOpen: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val customCompanies: List<String> = emptyList(),
    val customLocations: List<String> = emptyList(),
    val customObservationCategories: List<String> = emptyList(),
    val roles: Map<String, Any> = emptyMap(),
    val totalFindings: Int = 0,
    val openFindings: Int = 0,
    val closedFindings: Int = 0
) {
    fun hasUserAccess(userUid: String, userProjectIds: List<String> = emptyList()): Boolean {
        if (isOpen) return true
        if (userUid.isNotBlank() && (ownerUid == userUid || memberUids.contains(userUid))) return true
        if (id.isNotBlank() && userProjectIds.contains(id)) return true
        return false
    }
}

/**
 * Denormalized Finding Document for Firestore (/projects/{projectId}/findings/{findingId})
 * Eliminates N+1 reads by snapshotting creator and PIC profiles directly.
 */
data class FirestoreFindingDocument(
    val id: String = "",
    val projectId: String = "",
    val checklistCategory: String = "",
    val questionText: String = "",
    val description: String = "",
    val location: String = "",
    val severity: String = "HIGH", // LOW, MEDIUM, HIGH, CRITICAL
    val status: String = "OPEN", // OPEN, IN_REVIEW, CLOSED, REJECTED
    
    // Denormalized Creator Snapshot
    val creatorUid: String = "",
    val creatorName: String = "",
    val creatorEmail: String = "",
    val creatorRole: String = "",
    val creatorCompany: String = "",

    // Denormalized PIC Snapshot
    val picUid: String = "",
    val picName: String = "",
    val picEmail: String = "",
    val picRole: String = "",
    val picCompany: String = "",
    val picPhotoURL: String? = null,

    // Evidence Photos
    val evidencePhotos: List<String> = emptyList(),
    val targetCompletionTimestamp: Long = 0L,

    // PIC Corrective Action
    val actionTaken: String = "",
    val proofPhotos: List<String> = emptyList(),
    val actionSubmittedTimestamp: Long = 0L,
    val actionSubmittedByUid: String = "",

    // Auditor Verification & Rating
    val verifiedTimestamp: Long = 0L,
    val verifiedByUid: String = "",
    val verificationRemarks: String = "",
    val picRating: Int = 0, // 1 - 5 stars

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class FirestoreActionLog(
    val id: String = "",
    val findingId: String = "",
    val projectId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val actionType: String = "", // CREATED, ASSIGNED, ACTION_SUBMITTED, REJECTED, VERIFIED_CLOSED
    val oldStatus: String = "",
    val newStatus: String = "",
    val remarks: String = "",
    val rating: Int = 0
)

data class FirestoreNotification(
    val id: String = "",
    val projectId: String = "",
    val projectName: String = "",
    val findingId: String = "",
    val recipientUid: String = "",
    val recipientEmail: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderEmail: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "ASSIGNMENT", // ASSIGNMENT, ACTION_SUBMITTED, CLOSED, REOPENED, REMINDER, CHAT_MESSAGE, CRITICAL
    val isCritical: Boolean = false,
    val isBroadcast: Boolean = false,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class PaginatedData<T>(
    val items: List<T> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val lastDocId: String? = null
)

enum class DashboardPerspective(val titleEn: String, val titleId: String) {
    ALL("Overview", "Ringkasan Umum"),
    PIC_TASKS("My PIC Assignments", "Tugas PIC Saya"),
    INSPECTOR_VERIFY("Auditor Verification", "Verifikasi Auditor"),
    HIGH_RISK("Critical Risks", "Risiko Kritis")
}

