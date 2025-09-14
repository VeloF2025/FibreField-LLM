// 🟢 WORKING: Drop entity with comprehensive location and status tracking
package com.fibreflow.core.database.entities

import androidx.room.*
import com.fibreflow.core.database.converters.DateConverters
import com.fibreflow.core.database.converters.LocationConverters
import java.util.*

/**
 * Entity representing a fiber drop installation point
 * Contains location data, customer information, and installation status
 */
@Entity(
    tableName = "drops",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["project_id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TechnicianEntity::class,
            parentColumns = ["technician_id"],
            childColumns = ["assigned_technician_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["status"]),
        Index(value = ["assigned_technician_id"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["sync_status"]),
        Index(value = ["project_id", "status"]),
        Index(value = ["installation_date"]),
        Index(value = ["priority"]),
        Index(value = ["activation_status"])
    ]
)
@TypeConverters(DateConverters::class, LocationConverters::class)
data class DropEntity(
    @PrimaryKey
    @ColumnInfo(name = "drop_number")
    val dropNumber: String,
    
    @ColumnInfo(name = "project_id")
    val projectId: Int,
    
    // Location data
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    
    @ColumnInfo(name = "altitude")
    val altitude: Double? = null,
    
    @ColumnInfo(name = "accuracy")
    val accuracy: Float? = null,
    
    @ColumnInfo(name = "address")
    val address: String,
    
    // Status and assignment
    @ColumnInfo(name = "status")
    val status: DropStatus,
    
    @ColumnInfo(name = "assigned_technician_id")
    val assignedTechnicianId: String? = null,
    
    // Customer information
    @ColumnInfo(name = "customer_name")
    val customerName: String? = null,
    
    @ColumnInfo(name = "customer_phone")
    val customerPhone: String? = null,
    
    @ColumnInfo(name = "customer_email")
    val customerEmail: String? = null,
    
    // Installation scheduling
    @ColumnInfo(name = "installation_date")
    val installationDate: Date? = null,
    
    // Activation tracking
    @ColumnInfo(name = "activation_status")
    val activationStatus: ActivationStatus = ActivationStatus.PENDING,
    
    @ColumnInfo(name = "activation_date")
    val activationDate: Date? = null,
    
    // Work management
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    
    /**
     * Priority level: 0 = normal, higher numbers = higher priority
     */
    @ColumnInfo(name = "priority")
    val priority: Int = 0,
    
    // Audit fields
    @ColumnInfo(name = "created_at")
    val createdAt: Date,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date,
    
    // Synchronization tracking
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    
    @ColumnInfo(name = "last_sync_attempt")
    val lastSyncAttempt: Date? = null,
    
    @ColumnInfo(name = "sync_error")
    val syncError: String? = null
) {
    
    /**
     * Check if drop is available for assignment
     */
    val isAvailable: Boolean
        get() = status == DropStatus.AVAILABLE && assignedTechnicianId == null
    
    /**
     * Check if drop is assigned to a technician
     */
    val isAssigned: Boolean
        get() = status == DropStatus.ASSIGNED && assignedTechnicianId != null
    
    /**
     * Check if installation is in progress
     */
    val isInProgress: Boolean
        get() = status == DropStatus.IN_PROGRESS
    
    /**
     * Check if installation is completed
     */
    val isCompleted: Boolean
        get() = status == DropStatus.COMPLETED
    
    /**
     * Check if drop requires remediation
     */
    val requiresRemediation: Boolean
        get() = status == DropStatus.REMEDIATION_REQUIRED
    
    /**
     * Check if drop is overdue (installation date passed)
     */
    val isOverdue: Boolean
        get() = installationDate?.let { scheduled ->
            scheduled.before(Date()) && !isCompleted
        } ?: false
    
    /**
     * Check if drop is high priority
     */
    val isHighPriority: Boolean
        get() = priority > 0
    
    /**
     * Get display text for status
     */
    val statusDisplayText: String
        get() = when (status) {
            DropStatus.AVAILABLE -> "Available"
            DropStatus.ASSIGNED -> "Assigned"
            DropStatus.IN_PROGRESS -> "In Progress"
            DropStatus.PENDING_ACTIVATION -> "Pending Activation"
            DropStatus.COMPLETED -> "Completed"
            DropStatus.FAILED -> "Failed"
            DropStatus.REMEDIATION_REQUIRED -> "Needs Remediation"
        }
    
    /**
     * Get display text for activation status
     */
    val activationStatusDisplayText: String
        get() = when (activationStatus) {
            ActivationStatus.PENDING -> "Pending"
            ActivationStatus.SCHEDULED -> "Scheduled"
            ActivationStatus.ACTIVE -> "Active"
            ActivationStatus.FAILED -> "Failed"
        }
}

/**
 * Drop status enumeration
 */
enum class DropStatus {
    AVAILABLE,              // Ready for assignment
    ASSIGNED,              // Assigned to technician
    IN_PROGRESS,           // Installation in progress
    PENDING_ACTIVATION,    // Installation complete, waiting for activation
    COMPLETED,             // Fully completed and activated
    FAILED,                // Installation failed
    REMEDIATION_REQUIRED   // Requires remediation work
}

/**
 * Activation status enumeration
 */
enum class ActivationStatus {
    PENDING,    // Not yet scheduled for activation
    SCHEDULED,  // Activation scheduled
    ACTIVE,     // Successfully activated
    FAILED      // Activation failed
}

/**
 * Synchronization status enumeration
 */
enum class SyncStatus {
    PENDING,      // Waiting to be synced
    IN_PROGRESS,  // Currently syncing
    SYNCED,       // Successfully synced
    FAILED,       // Sync failed
    CONFLICT      // Sync conflict detected
}