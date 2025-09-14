package com.fibreflow.core.network.models.response

/**
 * Response model for installation data
 */
data class InstallationResponse(
    val installationId: String,
    val dropId: String,
    val dropNumber: String,
    val technicianId: String,
    val technicianName: String? = null,
    val status: String,
    val progress: Float = 0.0f, // 0.0 to 1.0
    val currentStep: String? = null,
    val equipmentType: String,
    val priority: Int = 1,
    val notes: String? = null,
    val scheduledDate: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val estimatedDuration: Long? = null, // minutes
    val actualDuration: Long? = null, // minutes
    val createdAt: Long,
    val updatedAt: Long,
    val validationStatus: String? = null,
    val validationScore: Float? = null,
    val photosCount: Int = 0,
    val issuesCount: Int = 0,
    val syncStatus: String = "PENDING",
    val lastSyncAttempt: Long? = null,
    val syncError: String? = null,
    val location: InstallationLocation? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Installation location information
 */
data class InstallationLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val address: String? = null
)