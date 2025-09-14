package com.fibreflow.domain.drops.entities

/**
 * Domain entity representing a drop (installation point)
 */
data class Drop(
    val dropNumber: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val status: DropStatus = DropStatus.AVAILABLE,
    val priority: DropPriority = DropPriority.NORMAL,
    val estimatedInstallTime: Int? = null, // minutes
    val notes: String? = null,
    val assignedTo: String? = null,
    val projectId: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    /**
     * Check if drop is available for assignment
     */
    val isAvailable: Boolean
        get() = status == DropStatus.AVAILABLE

    /**
     * Check if drop is currently being worked on
     */
    val isInProgress: Boolean
        get() = status == DropStatus.IN_PROGRESS

    /**
     * Check if drop installation is complete
     */
    val isCompleted: Boolean
        get() = status == DropStatus.COMPLETED

    /**
     * Get formatted coordinates
     */
    val coordinates: String
        get() = "%.6f, %.6f".format(latitude, longitude)

    /**
     * Check if drop has high priority
     */
    val isHighPriority: Boolean
        get() = priority == DropPriority.HIGH || priority == DropPriority.CRITICAL
}

/**
 * Drop status enum
 */
enum class DropStatus {
    AVAILABLE,
    ASSIGNED,
    IN_PROGRESS,
    PENDING_VALIDATION,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Drop priority enum
 */
enum class DropPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}