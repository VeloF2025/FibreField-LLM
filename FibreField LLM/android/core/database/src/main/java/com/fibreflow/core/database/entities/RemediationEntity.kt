package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing remediation tasks for failed installations
 */
@Entity(
    tableName = "remediations",
    indices = [
        Index(value = ["installation_id"]),
        Index(value = ["status"]),
        Index(value = ["created_at"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = InstallationEntity::class,
            parentColumns = ["installation_id"],
            childColumns = ["installation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RemediationEntity(
    @PrimaryKey
    @ColumnInfo(name = "remediation_id")
    val remediationId: Long,

    @ColumnInfo(name = "installation_id")
    val installationId: Long,

    @ColumnInfo(name = "issue_type")
    val issueType: RemediationType,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "severity")
    val severity: RemediationSeverity,

    @ColumnInfo(name = "status")
    val status: RemediationStatus = RemediationStatus.PENDING,

    @ColumnInfo(name = "assigned_to")
    val assignedTo: String? = null,

    @ColumnInfo(name = "resolution_notes")
    val resolutionNotes: String? = null,

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Date? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date
)

/**
 * Remediation type enum
 */
enum class RemediationType {
    HARDWARE_ISSUE,
    CONFIGURATION_ERROR,
    NETWORK_PROBLEM,
    PHOTO_VALIDATION_FAILED,
    GPS_ACCURACY_ISSUE,
    MANUAL_OVERRIDE_NEEDED,
    OTHER
}

/**
 * Remediation severity enum
 */
enum class RemediationSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Remediation status enum
 */
enum class RemediationStatus {
    PENDING,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED
}