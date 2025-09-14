package com.fibreflow.domain.drops.validation

import com.fibreflow.core.common.result.Result
import com.fibreflow.core.location.ProximityDetector
import com.fibreflow.domain.drops.entities.Drop
import com.fibreflow.domain.drops.entities.DropStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validator for drop-related business rules and constraints
 * Handles validation of drop assignments, proximity checks, and status transitions
 */
@Singleton
class DropValidator @Inject constructor(
    private val proximityDetector: ProximityDetector
) {

    companion object {
        // Validation constants
        const val MIN_DROP_NUMBER_LENGTH = 3
        const val MAX_DROP_NUMBER_LENGTH = 20
        const val MAX_VALIDATION_DISTANCE_METERS = 100f
        const val MIN_VALIDATION_DISTANCE_METERS = 10f
    }

    /**
     * Validate drop data integrity
     * @param drop The drop to validate
     * @return Result with validation result
     */
    fun validateDropData(drop: Drop): Result<ValidationResult> {
        val errors = mutableListOf<String>()

        // Validate drop number
        if (drop.dropNumber.isBlank()) {
            errors.add("Drop number cannot be empty")
        } else if (drop.dropNumber.length < MIN_DROP_NUMBER_LENGTH ||
                   drop.dropNumber.length > MAX_DROP_NUMBER_LENGTH) {
            errors.add("Drop number must be between $MIN_DROP_NUMBER_LENGTH and $MAX_DROP_NUMBER_LENGTH characters")
        }

        // Validate coordinates
        if (!isValidLatitude(drop.latitude)) {
            errors.add("Invalid latitude: ${drop.latitude}")
        }

        if (!isValidLongitude(drop.longitude)) {
            errors.add("Invalid longitude: ${drop.longitude}")
        }

        // Validate address
        if (drop.address.isNullOrBlank()) {
            errors.add("Drop address cannot be empty")
        }

        // Validate project ID
        if (drop.projectId == null || drop.projectId <= 0) {
            errors.add("Valid project ID is required")
        }

        val result = ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = emptyList()
        )

        return if (result.isValid) Result.Success(result) else Result.Error(Exception("Validation failed: ${errors.joinToString(", ")}"))
    }

    /**
     * Validate proximity to drop location for installation
     * @param drop The drop to validate proximity for
     * @param strictMode If true, uses stricter proximity requirements
     * @return Result with proximity validation
     */
    suspend fun validateInstallationProximity(
        drop: Drop,
        strictMode: Boolean = false
    ): Result<ProximityValidationResult> {
        return try {
            val radius = if (strictMode) ProximityDetector.STRICT_PROXIMITY_RADIUS
                        else ProximityDetector.DEFAULT_PROXIMITY_RADIUS

            val proximityResult = proximityDetector.validateProximity(drop, radius)

            if (proximityResult is Result.Error) {
                return Result.Error(proximityResult.exception)
            }

            val proximity = (proximityResult as Result.Success).data

            val validationResult = ProximityValidationResult(
                isValid = proximity.isWithinProximity,
                proximityResult = proximity,
                validationRadius = radius,
                strictMode = strictMode
            )

            Result.Success(validationResult)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Validate drop assignment rules
     * @param drop The drop being assigned
     * @param technicianId The technician ID
     * @return Result with assignment validation
     */
    fun validateDropAssignment(drop: Drop, technicianId: String): Result<AssignmentValidationResult> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check if drop is available
        if (!drop.isAvailable) {
            errors.add("Drop is not available for assignment")
        }

        // Check if technician ID is valid
        if (technicianId.isBlank()) {
            errors.add("Valid technician ID is required")
        }

        // Check if drop is already assigned to this technician
        if (drop.assignedTo == technicianId) {
            warnings.add("Drop is already assigned to this technician")
        }

        // Check priority constraints
        if (drop.isHighPriority) {
            warnings.add("High priority drop requires immediate attention")
        }

        val result = AssignmentValidationResult(
            canAssign = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            drop = drop,
            technicianId = technicianId
        )

        return if (result.canAssign) Result.Success(result) else Result.Error(Exception("Assignment validation failed: ${errors.joinToString(", ")}"))
    }

    /**
     * Validate status transition
     * @param drop The drop
     * @param newStatus The new status to transition to
     * @return Result with transition validation
     */
    fun validateStatusTransition(drop: Drop, newStatus: DropStatus): Result<StatusTransitionResult> {
        val errors = mutableListOf<String>()

        when (drop.status) {
            DropStatus.AVAILABLE -> {
                if (newStatus !in listOf(DropStatus.ASSIGNED, DropStatus.CANCELLED)) {
                    errors.add("Available drop can only be assigned or cancelled")
                }
            }
            DropStatus.ASSIGNED -> {
                if (newStatus !in listOf(DropStatus.IN_PROGRESS, DropStatus.AVAILABLE, DropStatus.CANCELLED)) {
                    errors.add("Assigned drop can only move to in-progress, available, or cancelled")
                }
            }
            DropStatus.IN_PROGRESS -> {
                if (newStatus !in listOf(DropStatus.PENDING_VALIDATION, DropStatus.FAILED, DropStatus.ASSIGNED)) {
                    errors.add("In-progress drop can only move to pending validation, failed, or back to assigned")
                }
            }
            DropStatus.PENDING_VALIDATION -> {
                if (newStatus !in listOf(DropStatus.COMPLETED, DropStatus.FAILED, DropStatus.IN_PROGRESS)) {
                    errors.add("Pending validation drop can only be completed, failed, or returned to in-progress")
                }
            }
            DropStatus.COMPLETED -> {
                errors.add("Completed drop cannot change status")
            }
            DropStatus.FAILED -> {
                if (newStatus !in listOf(DropStatus.ASSIGNED, DropStatus.CANCELLED)) {
                    errors.add("Failed drop can only be reassigned or cancelled")
                }
            }
            DropStatus.CANCELLED -> {
                errors.add("Cancelled drop cannot change status")
            }
        }

        val result = StatusTransitionResult(
            canTransition = errors.isEmpty(),
            errors = errors,
            currentStatus = drop.status,
            newStatus = newStatus,
            drop = drop
        )

        return if (result.canTransition) Result.Success(result) else Result.Error(Exception("Status transition validation failed: ${errors.joinToString(", ")}"))
    }

    /**
     * Validate multiple drops for bulk operations
     * @param drops List of drops to validate
     * @return Result with bulk validation results
     */
    fun validateBulkDrops(drops: List<Drop>): Result<BulkValidationResult> {
        val results = drops.map { drop ->
            val dataValidation = validateDropData(drop)
            drop.dropNumber to (if (dataValidation is Result.Success) dataValidation.data else null)
        }.toMap()

        val validDrops = results.filterValues { it?.isValid == true }.keys
        val invalidDrops = results.filterValues { it?.isValid == false }

        val result = BulkValidationResult(
            totalDrops = drops.size,
            validDrops = validDrops.toList(),
            invalidDrops = invalidDrops.map { (dropNumber, validationResult) ->
                dropNumber to (validationResult?.errors ?: emptyList())
            }.toMap(),
            validationResults = results
        )

        return Result.Success(result)
    }

    /**
     * Check if latitude is valid
     */
    private fun isValidLatitude(latitude: Double): Boolean {
        return latitude in -90.0..90.0
    }

    /**
     * Check if longitude is valid
     */
    private fun isValidLongitude(longitude: Double): Boolean {
        return longitude in -180.0..180.0
    }
}

/**
 * Result of data validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

/**
 * Result of proximity validation
 */
data class ProximityValidationResult(
    val isValid: Boolean,
    val proximityResult: com.fibreflow.core.location.ProximityResult,
    val validationRadius: Float,
    val strictMode: Boolean
) {
    val validationMessage: String
        get() = if (isValid) {
            "Within ${validationRadius}m proximity requirement"
        } else {
            "Outside ${validationRadius}m proximity requirement (${proximityResult.distanceText})"
        }
}

/**
 * Result of assignment validation
 */
data class AssignmentValidationResult(
    val canAssign: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val drop: Drop,
    val technicianId: String
)

/**
 * Result of status transition validation
 */
data class StatusTransitionResult(
    val canTransition: Boolean,
    val errors: List<String>,
    val currentStatus: DropStatus,
    val newStatus: DropStatus,
    val drop: Drop
)

/**
 * Result of bulk validation
 */
data class BulkValidationResult(
    val totalDrops: Int,
    val validDrops: List<String>,
    val invalidDrops: Map<String, List<String>>,
    val validationResults: Map<String, ValidationResult?>
) {
    val validCount: Int = validDrops.size
    val invalidCount: Int = invalidDrops.size
    val successRate: Double = if (totalDrops > 0) validCount.toDouble() / totalDrops else 0.0
}