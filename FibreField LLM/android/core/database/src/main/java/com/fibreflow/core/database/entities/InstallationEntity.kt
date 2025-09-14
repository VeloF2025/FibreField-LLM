package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing an installation workflow
 * Tracks the complete installation process for a drop
 */
@Entity(
    tableName = "installations",
    indices = [
        Index(value = ["drop_number"]),
        Index(value = ["technician_id"]),
        Index(value = ["status"]),
        Index(value = ["created_at"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = DropEntity::class,
            parentColumns = ["drop_number"],
            childColumns = ["drop_number"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TechnicianEntity::class,
            parentColumns = ["technician_id"],
            childColumns = ["technician_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class InstallationEntity(
    @PrimaryKey
    @ColumnInfo(name = "installation_id")
    val installationId: Long,

    @ColumnInfo(name = "drop_number")
    val dropNumber: String,

    @ColumnInfo(name = "technician_id")
    val technicianId: String?,

    @ColumnInfo(name = "status")
    val status: InstallationStatus,

    @ColumnInfo(name = "start_time")
    val startTime: Date,

    @ColumnInfo(name = "end_time")
    val endTime: Date? = null,

    @ColumnInfo(name = "ont_serial")
    val ontSerial: String? = null,

    @ColumnInfo(name = "speed_test_results")
    val speedTestResults: String? = null, // JSON

    @ColumnInfo(name = "photos")
    val photos: List<Long> = emptyList(), // Photo IDs

    @ColumnInfo(name = "completed_steps")
    val completedSteps: String? = null, // JSON array of completed step IDs

    @ColumnInfo(name = "current_step")
    val currentStep: Int = 1,

    @ColumnInfo(name = "total_steps")
    val totalSteps: Int = 9,

    @ColumnInfo(name = "validation_errors")
    val validationErrors: String? = null, // JSON array of validation errors

    @ColumnInfo(name = "ai_guidance_used")
    val aiGuidanceUsed: Boolean = false,

    @ColumnInfo(name = "manual_override_used")
    val manualOverrideUsed: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date
) {

    /**
     * Calculate completion percentage
     */
    val completionPercentage: Float
        get() = if (totalSteps > 0) {
            (completedStepsCount.toFloat() / totalSteps) * 100f
        } else 0f

    /**
     * Get number of completed steps
     */
    val completedStepsCount: Int
        get() = completedSteps?.split(",")?.size ?: 0

    /**
     * Check if installation is complete
     */
    val isComplete: Boolean
        get() = status == InstallationStatus.COMPLETED

    /**
     * Check if installation is in progress
     */
    val isInProgress: Boolean
        get() = status == InstallationStatus.IN_PROGRESS

    /**
     * Check if installation has validation errors
     */
    val hasValidationErrors: Boolean
        get() = !validationErrors.isNullOrBlank()

    /**
     * Get duration in minutes
     */
    val durationMinutes: Long?
        get() = if (endTime != null) {
            (endTime.time - startTime.time) / (1000 * 60)
        } else null
}

/**
 * Installation status enum
 */
enum class InstallationStatus {
    NOT_STARTED,
    IN_PROGRESS,
    PENDING_VALIDATION,
    COMPLETED,
    FAILED,
    CANCELLED
}