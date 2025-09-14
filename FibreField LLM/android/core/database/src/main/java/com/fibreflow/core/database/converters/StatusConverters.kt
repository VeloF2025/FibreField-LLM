package com.fibreflow.core.database.converters

import androidx.room.TypeConverter
import com.fibreflow.core.database.entities.*

/**
 * Type converters for enum values in Room database
 */
class StatusConverters {

    // Installation Status
    @TypeConverter
    fun fromInstallationStatus(status: InstallationStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toInstallationStatus(value: String?): InstallationStatus? {
        return value?.let { InstallationStatus.valueOf(it) }
    }

    // Photo Type
    @TypeConverter
    fun fromPhotoType(type: PhotoType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toPhotoType(value: String?): PhotoType? {
        return value?.let { PhotoType.valueOf(it) }
    }

    // Validation Status
    @TypeConverter
    fun fromValidationStatus(status: ValidationStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toValidationStatus(value: String?): ValidationStatus? {
        return value?.let { ValidationStatus.valueOf(it) }
    }

    // Upload Status
    @TypeConverter
    fun fromUploadStatus(status: UploadStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toUploadStatus(value: String?): UploadStatus? {
        return value?.let { UploadStatus.valueOf(it) }
    }

    // Remediation Type
    @TypeConverter
    fun fromRemediationType(type: RemediationType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toRemediationType(value: String?): RemediationType? {
        return value?.let { RemediationType.valueOf(it) }
    }

    // Remediation Severity
    @TypeConverter
    fun fromRemediationSeverity(severity: RemediationSeverity?): String? {
        return severity?.name
    }

    @TypeConverter
    fun toRemediationSeverity(value: String?): RemediationSeverity? {
        return value?.let { RemediationSeverity.valueOf(it) }
    }

    // Remediation Status
    @TypeConverter
    fun fromRemediationStatus(status: RemediationStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toRemediationStatus(value: String?): RemediationStatus? {
        return value?.let { RemediationStatus.valueOf(it) }
    }

    // Sync Operation
    @TypeConverter
    fun fromSyncOperation(operation: SyncOperation?): String? {
        return operation?.name
    }

    @TypeConverter
    fun toSyncOperation(value: String?): SyncOperation? {
        return value?.let { SyncOperation.valueOf(it) }
    }

    // Sync Status
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? {
        return value?.let { SyncStatus.valueOf(it) }
    }
}