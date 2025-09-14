package com.fibreflow.core.network.models.response

/**
 * Response model for activation data
 */
data class ActivationResponse(
    val activationId: String,
    val dropId: String,
    val dropNumber: String,
    val technicianId: String,
    val technicianName: String? = null,
    val status: String,
    val serviceType: String,
    val priority: String = "NORMAL",
    val scheduledDate: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val estimatedDuration: Long? = null, // minutes
    val actualDuration: Long? = null, // minutes
    val progress: Float = 0.0f, // 0.0 to 1.0
    val notes: String? = null,
    val testResults: Map<String, Boolean>? = null,
    val issuesCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "PENDING",
    val lastSyncAttempt: Long? = null,
    val syncError: String? = null,
    val location: ActivationLocation? = null,
    val equipmentUsed: List<String>? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Activation location information
 */
data class ActivationLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val address: String? = null
)