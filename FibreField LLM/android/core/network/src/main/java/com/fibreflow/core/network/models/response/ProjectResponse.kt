package com.fibreflow.core.network.models.response

/**
 * Response model for project data
 */
data class ProjectResponse(
    val projectId: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val priority: String = "NORMAL",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val totalDrops: Int = 0,
    val completedDrops: Int = 0,
    val assignedDrops: Int = 0,
    val location: ProjectLocation? = null,
    val boundaries: List<BoundaryPoint>? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Response model for drop data
 */
data class DropResponse(
    val dropId: String,
    val dropNumber: String,
    val projectId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val address: String,
    val status: String,
    val priority: Int = 0,
    val assignedTo: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerEmail: String? = null,
    val installationDate: Long? = null,
    val activationStatus: String = "PENDING",
    val activationDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncAttempt: Long? = null,
    val syncStatus: String = "PENDING",
    val syncError: String? = null
)

/**
 * Project location information
 */
data class ProjectLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postalCode: String? = null
)

/**
 * Boundary point for project area
 */
data class BoundaryPoint(
    val latitude: Double,
    val longitude: Double,
    val sequence: Int
)