package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing a photo captured during installation
 * Stores photo metadata, validation results, and AI analysis
 */
@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["installation_id"]),
        Index(value = ["photo_type"]),
        Index(value = ["validation_status"]),
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
data class PhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "installation_id")
    val installationId: Long,

    @ColumnInfo(name = "photo_type")
    val photoType: PhotoType,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,

    @ColumnInfo(name = "width")
    val width: Int,

    @ColumnInfo(name = "height")
    val height: Int,

    @ColumnInfo(name = "validation_status")
    val validationStatus: ValidationStatus = ValidationStatus.PENDING,

    @ColumnInfo(name = "validation_confidence")
    val validationConfidence: Float? = null,

    @ColumnInfo(name = "ai_metadata")
    val aiMetadata: String? = null, // JSON with detection results

    @ColumnInfo(name = "manual_override")
    val manualOverride: Boolean = false,

    @ColumnInfo(name = "override_reason")
    val overrideReason: String? = null,

    @ColumnInfo(name = "override_by")
    val overrideBy: String? = null,

    @ColumnInfo(name = "override_at")
    val overrideAt: Date? = null,

    @ColumnInfo(name = "upload_status")
    val uploadStatus: UploadStatus = UploadStatus.PENDING,

    @ColumnInfo(name = "upload_attempts")
    val uploadAttempts: Int = 0,

    @ColumnInfo(name = "last_upload_attempt")
    val lastUploadAttempt: Date? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date
) {

    /**
     * Check if photo validation passed
     */
    val isValidationPassed: Boolean
        get() = validationStatus == ValidationStatus.PASSED

    /**
     * Check if photo was manually overridden
     */
    val isManuallyOverridden: Boolean
        get() = manualOverride

    /**
     * Check if photo is ready for upload
     */
    val isReadyForUpload: Boolean
        get() = validationStatus == ValidationStatus.PASSED && uploadStatus == UploadStatus.PENDING

    /**
     * Check if photo upload failed
     */
    val isUploadFailed: Boolean
        get() = uploadStatus == UploadStatus.FAILED

    /**
     * Get validation confidence percentage
     */
    val validationConfidencePercentage: Float
        get() = (validationConfidence ?: 0f) * 100f
}

/**
 * Photo type enum for installation workflow
 */
enum class PhotoType {
    ONT_POWER_LIGHT,
    ONT_LOS_LIGHT,
    ONT_PON_LIGHT,
    ONT_LAN_LIGHT,
    POWER_METER_READING,
    FIBER_CONNECTION,
    SPLICE_CLOSURE,
    CABLE_ROUTING,
    FINAL_INSTALLATION,
    TROUBLESHOOTING
}

/**
 * Validation status enum
 */
enum class ValidationStatus {
    PENDING,
    PROCESSING,
    PASSED,
    FAILED,
    REQUIRES_REVIEW
}

/**
 * Upload status enum
 */
enum class UploadStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED
}