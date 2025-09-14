package com.fibreflow.infrastructure.sync

import com.fibreflow.core.common.result.Result
import com.fibreflow.core.database.FibreFieldDatabase
import com.fibreflow.core.database.entities.DropEntity
import com.fibreflow.core.database.entities.InstallationEntity
import com.fibreflow.core.database.entities.PhotoEntity
import kotlinx.coroutines.*
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data Consistency Manager for FibreField
 *
 * Ensures data integrity and consistency across the application.
 * Performs validation, checksum verification, and data repair operations.
 *
 * Features:
 * - Data integrity validation
 * - Checksum verification
 * - Consistency checking
 * - Data repair and recovery
 * - Corruption detection
 * - Referential integrity validation
 */
@Singleton
class DataConsistencyManager @Inject constructor(
    private val database: FibreFieldDatabase
) {

    companion object {
        private const val TAG = "DataConsistencyManager"
        private const val CHECKSUM_ALGORITHM = "SHA-256"
        private const val VALIDATION_BATCH_SIZE = 100
    }

    private val consistencyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Perform comprehensive data consistency check
     */
    suspend fun performConsistencyCheck(): Result<ConsistencyReport> {
        return try {
            Timber.i("$TAG: Starting comprehensive data consistency check")

            val report = ConsistencyReport()

            // Check referential integrity
            report.referentialIntegrityIssues.addAll(checkReferentialIntegrity())

            // Check data integrity
            report.dataIntegrityIssues.addAll(checkDataIntegrity())

            // Check for orphaned records
            report.orphanedRecords.addAll(checkOrphanedRecords())

            // Check for duplicate records
            report.duplicateRecords.addAll(checkDuplicateRecords())

            // Validate checksums
            report.checksumFailures.addAll(validateChecksums())

            report.isConsistent = report.isClean()
            report.checkTimestamp = System.currentTimeMillis()

            Timber.i("$TAG: Consistency check completed. Consistent: ${report.isConsistent}")
            Result.Success(report)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Consistency check failed")
            Result.Error(e)
        }
    }

    /**
     * Validate data integrity for a specific entity
     */
    suspend fun validateEntityIntegrity(
        entityType: String,
        entityId: String
    ): Result<EntityValidationResult> {
        return try {
            val result = when (entityType.lowercase()) {
                "drop" -> validateDropIntegrity(entityId)
                "installation" -> validateInstallationIntegrity(entityId)
                "photo" -> validatePhotoIntegrity(entityId)
                else -> throw IllegalArgumentException("Unknown entity type: $entityType")
            }

            Result.Success(result)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Entity integrity validation failed for $entityType:$entityId")
            Result.Error(e)
        }
    }

    /**
     * Repair data inconsistencies
     */
    suspend fun repairInconsistencies(report: ConsistencyReport): Result<RepairResult> {
        return try {
            Timber.i("$TAG: Starting data repair process")

            val repairResult = RepairResult()

            // Repair referential integrity issues
            repairResult.repairedReferentialIssues.addAll(
                repairReferentialIntegrity(report.referentialIntegrityIssues)
            )

            // Repair orphaned records
            repairResult.removedOrphanedRecords.addAll(
                removeOrphanedRecords(report.orphanedRecords)
            )

            // Repair duplicate records
            repairResult.mergedDuplicates.addAll(
                mergeDuplicateRecords(report.duplicateRecords)
            )

            // Recalculate checksums
            repairResult.updatedChecksums.addAll(
                recalculateChecksums(report.checksumFailures)
            )

            repairResult.success = true
            repairResult.repairTimestamp = System.currentTimeMillis()

            Timber.i("$TAG: Data repair completed successfully")
            Result.Success(repairResult)

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Data repair failed")
            Result.Error(e)
        }
    }

    /**
     * Generate data checksum for integrity verification
     */
    fun generateChecksum(data: String): String {
        return try {
            val digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM)
            val hash = digest.digest(data.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to generate checksum")
            ""
        }
    }

    /**
     * Validate existing checksum
     */
    fun validateChecksum(data: String, expectedChecksum: String): Boolean {
        val actualChecksum = generateChecksum(data)
        return actualChecksum == expectedChecksum
    }

    // Private consistency checking methods

    private suspend fun checkReferentialIntegrity(): List<ReferentialIntegrityIssue> {
        return withContext(Dispatchers.IO) {
            val issues = mutableListOf<ReferentialIntegrityIssue>()

            // Check installations referencing non-existent drops
            val orphanedInstallations = database.installationDao().getOrphanedInstallations()
            for (installation in orphanedInstallations) {
                issues.add(
                    ReferentialIntegrityIssue(
                        table = "installations",
                        column = "drop_number",
                        value = installation.dropNumber,
                        issue = "References non-existent drop",
                        severity = IssueSeverity.HIGH
                    )
                )
            }

            // Check photos referencing non-existent installations
            val orphanedPhotos = database.photoDao().getOrphanedPhotos()
            for (photo in orphanedPhotos) {
                issues.add(
                    ReferentialIntegrityIssue(
                        table = "installation_photos",
                        column = "installation_id",
                        value = photo.installationId.toString(),
                        issue = "References non-existent installation",
                        severity = IssueSeverity.HIGH
                    )
                )
            }

            Timber.d("$TAG: Found ${issues.size} referential integrity issues")
            issues
        }
    }

    private suspend fun checkDataIntegrity(): List<DataIntegrityIssue> {
        return withContext(Dispatchers.IO) {
            val issues = mutableListOf<DataIntegrityIssue>()

            // Check for invalid drop statuses
            val invalidStatusDrops = database.dropDao().getDropsWithInvalidStatus()
            for (drop in invalidStatusDrops) {
                issues.add(
                    DataIntegrityIssue(
                        table = "drops",
                        recordId = drop.dropNumber,
                        field = "status",
                        value = drop.status,
                        issue = "Invalid drop status",
                        severity = IssueSeverity.MEDIUM
                    )
                )
            }

            // Check for future timestamps
            val futureTimestampRecords = database.installationDao().getRecordsWithFutureTimestamps()
            for (record in futureTimestampRecords) {
                issues.add(
                    DataIntegrityIssue(
                        table = "installations",
                        recordId = record.id.toString(),
                        field = "timestamp",
                        value = record.startedAt.toString(),
                        issue = "Future timestamp detected",
                        severity = IssueSeverity.LOW
                    )
                )
            }

            Timber.d("$TAG: Found ${issues.size} data integrity issues")
            issues
        }
    }

    private suspend fun checkOrphanedRecords(): List<OrphanedRecord> {
        return withContext(Dispatchers.IO) {
            val orphaned = mutableListOf<OrphanedRecord>()

            // Find orphaned sync queue entries
            val orphanedSyncEntries = database.syncQueueDao().getOrphanedSyncEntries()
            for (entry in orphanedSyncEntries) {
                orphaned.add(
                    OrphanedRecord(
                        table = "offline_queue",
                        recordId = entry.id.toString(),
                        reason = "References non-existent installation"
                    )
                )
            }

            Timber.d("$TAG: Found ${orphaned.size} orphaned records")
            orphaned
        }
    }

    private suspend fun checkDuplicateRecords(): List<DuplicateRecord> {
        return withContext(Dispatchers.IO) {
            val duplicates = mutableListOf<DuplicateRecord>()

            // Check for duplicate drop numbers
            val duplicateDrops = database.dropDao().getDuplicateDrops()
            for (duplicate in duplicateDrops) {
                duplicates.add(
                    DuplicateRecord(
                        table = "drops",
                        field = "drop_number",
                        value = duplicate.dropNumber,
                        count = duplicate.count,
                        recordIds = duplicate.ids
                    )
                )
            }

            Timber.d("$TAG: Found ${duplicates.size} duplicate record groups")
            duplicates
        }
    }

    private suspend fun validateChecksums(): List<ChecksumFailure> {
        return withContext(Dispatchers.IO) {
            val failures = mutableListOf<ChecksumFailure>()

            // Validate photo checksums (if stored)
            val photosWithoutChecksum = database.photoDao().getPhotosWithoutChecksum()
            for (photo in photosWithoutChecksum) {
                failures.add(
                    ChecksumFailure(
                        table = "installation_photos",
                        recordId = photo.id.toString(),
                        expectedChecksum = "N/A",
                        actualChecksum = "Missing",
                        reason = "Checksum not calculated"
                    )
                )
            }

            Timber.d("$TAG: Found ${failures.size} checksum failures")
            failures
        }
    }

    // Entity-specific validation methods

    private suspend fun validateDropIntegrity(dropNumber: String): EntityValidationResult {
        return withContext(Dispatchers.IO) {
            val drop = database.dropDao().getDropByNumber(dropNumber)

            if (drop == null) {
                return@withContext EntityValidationResult(
                    entityId = dropNumber,
                    isValid = false,
                    issues = listOf("Drop does not exist")
                )
            }

            val issues = mutableListOf<String>()

            // Validate coordinates
            if (drop.latitude < -90 || drop.latitude > 90) {
                issues.add("Invalid latitude: ${drop.latitude}")
            }
            if (drop.longitude < -180 || drop.longitude > 180) {
                issues.add("Invalid longitude: ${drop.longitude}")
            }

            // Validate status transitions
            if (!isValidDropStatus(drop.status)) {
                issues.add("Invalid drop status: ${drop.status}")
            }

            EntityValidationResult(
                entityId = dropNumber,
                isValid = issues.isEmpty(),
                issues = issues
            )
        }
    }

    private suspend fun validateInstallationIntegrity(installationId: String): EntityValidationResult {
        return withContext(Dispatchers.IO) {
            val installation = database.installationDao().getInstallationById(installationId.toLong())

            if (installation == null) {
                return@withContext EntityValidationResult(
                    entityId = installationId,
                    isValid = false,
                    issues = listOf("Installation does not exist")
                )
            }

            val issues = mutableListOf<String>()

            // Validate timestamps
            if (installation.completedAt != null &&
                installation.completedAt!! < installation.startedAt) {
                issues.add("Completion time is before start time")
            }

            // Validate drop reference
            val dropExists = database.dropDao().dropExists(installation.dropNumber)
            if (!dropExists) {
                issues.add("References non-existent drop: ${installation.dropNumber}")
            }

            EntityValidationResult(
                entityId = installationId,
                isValid = issues.isEmpty(),
                issues = issues
            )
        }
    }

    private suspend fun validatePhotoIntegrity(photoId: String): EntityValidationResult {
        return withContext(Dispatchers.IO) {
            val photo = database.photoDao().getPhotoById(photoId.toLong())

            if (photo == null) {
                return@withContext EntityValidationResult(
                    entityId = photoId,
                    isValid = false,
                    issues = listOf("Photo does not exist")
                )
            }

            val issues = mutableListOf<String>()

            // Validate file exists
            val file = java.io.File(photo.filePath)
            if (!file.exists()) {
                issues.add("Photo file does not exist: ${photo.filePath}")
            }

            // Validate installation reference
            val installationExists = database.installationDao().installationExists(photo.installationId)
            if (!installationExists) {
                issues.add("References non-existent installation: ${photo.installationId}")
            }

            EntityValidationResult(
                entityId = photoId,
                isValid = issues.isEmpty(),
                issues = issues
            )
        }
    }

    // Repair methods

    private suspend fun repairReferentialIntegrity(issues: List<ReferentialIntegrityIssue>): List<String> {
        val repaired = mutableListOf<String>()

        for (issue in issues) {
            when (issue.table) {
                "installations" -> {
                    // Remove orphaned installations
                    database.installationDao().deleteInstallationByDropNumber(issue.value)
                    repaired.add("Removed orphaned installation for drop ${issue.value}")
                }
                "installation_photos" -> {
                    // Remove orphaned photos
                    database.photoDao().deletePhotoByInstallationId(issue.value.toLong())
                    repaired.add("Removed orphaned photos for installation ${issue.value}")
                }
            }
        }

        return repaired
    }

    private suspend fun removeOrphanedRecords(orphaned: List<OrphanedRecord>): List<String> {
        val removed = mutableListOf<String>()

        for (record in orphaned) {
            when (record.table) {
                "offline_queue" -> {
                    database.syncQueueDao().deleteSyncEntry(record.recordId.toLong())
                    removed.add("Removed orphaned sync entry ${record.recordId}")
                }
            }
        }

        return removed
    }

    private suspend fun mergeDuplicateRecords(duplicates: List<DuplicateRecord>): List<String> {
        val merged = mutableListOf<String>()

        for (duplicate in duplicates) {
            // Keep the most recent record and remove others
            val idsToRemove = duplicate.recordIds.drop(1) // Keep first, remove rest
            for (id in idsToRemove) {
                when (duplicate.table) {
                    "drops" -> {
                        database.dropDao().deleteDropByNumber(id)
                        merged.add("Removed duplicate drop $id")
                    }
                }
            }
        }

        return merged
    }

    private suspend fun recalculateChecksums(failures: List<ChecksumFailure>): List<String> {
        val updated = mutableListOf<String>()

        for (failure in failures) {
            when (failure.table) {
                "installation_photos" -> {
                    // Recalculate photo checksum
                    val photo = database.photoDao().getPhotoById(failure.recordId.toLong())
                    if (photo != null) {
                        val file = java.io.File(photo.filePath)
                        if (file.exists()) {
                            val checksum = generateChecksum(file.readText())
                            database.photoDao().updatePhotoChecksum(photo.id, checksum)
                            updated.add("Updated checksum for photo ${photo.id}")
                        }
                    }
                }
            }
        }

        return updated
    }

    // Helper methods

    private fun isValidDropStatus(status: String): Boolean {
        val validStatuses = listOf(
            "AVAILABLE", "IN_PROGRESS", "PENDING_ACTIVATION",
            "ACTIVATED", "FAILED", "REMEDIATION_REQUIRED", "BLOCKED"
        )
        return status in validStatuses
    }

    /**
     * Shutdown the consistency manager
     */
    fun shutdown() {
        consistencyScope.cancel()
        Timber.i("$TAG: Data consistency manager shutdown")
    }
}

/**
 * Data structures for consistency checking and repair
 */

data class ConsistencyReport(
    val checkTimestamp: Long = 0,
    var isConsistent: Boolean = true,
    val referentialIntegrityIssues: MutableList<ReferentialIntegrityIssue> = mutableListOf(),
    val dataIntegrityIssues: MutableList<DataIntegrityIssue> = mutableListOf(),
    val orphanedRecords: MutableList<OrphanedRecord> = mutableListOf(),
    val duplicateRecords: MutableList<DuplicateRecord> = mutableListOf(),
    val checksumFailures: MutableList<ChecksumFailure> = mutableListOf()
) {
    fun isClean(): Boolean {
        return referentialIntegrityIssues.isEmpty() &&
               dataIntegrityIssues.isEmpty() &&
               orphanedRecords.isEmpty() &&
               duplicateRecords.isEmpty() &&
               checksumFailures.isEmpty()
    }

    fun getTotalIssues(): Int {
        return referentialIntegrityIssues.size +
               dataIntegrityIssues.size +
               orphanedRecords.size +
               duplicateRecords.size +
               checksumFailures.size
    }
}

data class EntityValidationResult(
    val entityId: String,
    val isValid: Boolean,
    val issues: List<String> = emptyList()
)

data class ReferentialIntegrityIssue(
    val table: String,
    val column: String,
    val value: String,
    val issue: String,
    val severity: IssueSeverity
)

data class DataIntegrityIssue(
    val table: String,
    val recordId: String,
    val field: String,
    val value: String,
    val issue: String,
    val severity: IssueSeverity
)

data class OrphanedRecord(
    val table: String,
    val recordId: String,
    val reason: String
)

data class DuplicateRecord(
    val table: String,
    val field: String,
    val value: String,
    val count: Int,
    val recordIds: List<String>
)

data class ChecksumFailure(
    val table: String,
    val recordId: String,
    val expectedChecksum: String,
    val actualChecksum: String,
    val reason: String
)

data class RepairResult(
    var success: Boolean = false,
    val repairTimestamp: Long = 0,
    val repairedReferentialIssues: MutableList<String> = mutableListOf(),
    val removedOrphanedRecords: MutableList<String> = mutableListOf(),
    val mergedDuplicates: MutableList<String> = mutableListOf(),
    val updatedChecksums: MutableList<String> = mutableListOf()
)

enum class IssueSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}