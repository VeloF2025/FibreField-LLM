package com.fibreflow.domain.drops

import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.drops.entities.DropStatus
import com.fibreflow.domain.drops.repositories.DropRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class for drop status lifecycle management
 * Ensures valid status transitions and business rules
 */
@Singleton
class DropStatusManager @Inject constructor(
    private val dropRepository: DropRepository
) {

    /**
     * Valid status transitions map
     * Key: current status, Value: list of allowed next statuses
     */
    private val validTransitions = mapOf(
        DropStatus.AVAILABLE to listOf(DropStatus.ASSIGNED, DropStatus.CANCELLED),
        DropStatus.ASSIGNED to listOf(DropStatus.IN_PROGRESS, DropStatus.AVAILABLE, DropStatus.CANCELLED),
        DropStatus.IN_PROGRESS to listOf(
            DropStatus.PENDING_VALIDATION,
            DropStatus.ASSIGNED,
            DropStatus.FAILED,
            DropStatus.CANCELLED
        ),
        DropStatus.PENDING_VALIDATION to listOf(
            DropStatus.COMPLETED,
            DropStatus.IN_PROGRESS,
            DropStatus.FAILED,
            DropStatus.CANCELLED
        ),
        DropStatus.COMPLETED to emptyList(), // Terminal state
        DropStatus.FAILED to listOf(DropStatus.ASSIGNED), // Can be reassigned
        DropStatus.CANCELLED to listOf(DropStatus.AVAILABLE) // Can be made available again
    )

    /**
     * Update drop status with validation
     */
    suspend fun updateStatus(dropNumber: String, newStatus: DropStatus): Result<Unit> {
        return try {
            // Get current drop status
            val currentDropResult = dropRepository.getDropByNumber(dropNumber)
            if (currentDropResult is Result.Error) {
                return currentDropResult
            }

            val currentDrop = (currentDropResult as Result.Success).data
            val currentStatus = currentDrop.status

            // Validate status transition
            val validationResult = validateStatusTransition(currentStatus, newStatus)
            if (validationResult is Result.Error) {
                return validationResult
            }

            // Log status change
            Timber.d("Drop $dropNumber status change: $currentStatus -> $newStatus")

            // Update status
            val updateResult = dropRepository.updateDropStatus(dropNumber, newStatus)
            if (updateResult is Result.Success) {
                // Perform additional actions based on status change
                handleStatusChange(dropNumber, currentStatus, newStatus)
            }

            updateResult
        } catch (e: Exception) {
            Timber.e(e, "Error updating drop status")
            Result.Error(e)
        }
    }

    /**
     * Validate if status transition is allowed
     */
    private fun validateStatusTransition(
        currentStatus: DropStatus,
        newStatus: DropStatus
    ): Result<Unit> {
        val allowedTransitions = validTransitions[currentStatus] ?: emptyList()

        return if (newStatus in allowedTransitions) {
            Result.Success(Unit)
        } else {
            val error = Exception(
                "Invalid status transition: $currentStatus -> $newStatus. " +
                "Allowed transitions: ${allowedTransitions.joinToString()}"
            )
            Result.Error(error)
        }
    }

    /**
     * Handle additional logic for status changes
     */
    private suspend fun handleStatusChange(
        dropNumber: String,
        oldStatus: DropStatus,
        newStatus: DropStatus
    ) {
        when (newStatus) {
            DropStatus.ASSIGNED -> {
                // Clear any previous assignment if reassigning
                if (oldStatus == DropStatus.FAILED) {
                    Timber.d("Reassigning previously failed drop: $dropNumber")
                }
            }
            DropStatus.IN_PROGRESS -> {
                // Log installation start
                Timber.d("Installation started for drop: $dropNumber")
            }
            DropStatus.COMPLETED -> {
                // Log successful completion
                Timber.d("Installation completed for drop: $dropNumber")
                // Could trigger notifications, analytics, etc.
            }
            DropStatus.FAILED -> {
                // Log failure and potentially create remediation task
                Timber.w("Installation failed for drop: $dropNumber")
            }
            DropStatus.CANCELLED -> {
                // Log cancellation
                Timber.d("Installation cancelled for drop: $dropNumber")
            }
            else -> {
                // No special handling needed
            }
        }
    }

    /**
     * Check if status transition is valid without performing it
     */
    fun canTransition(currentStatus: DropStatus, newStatus: DropStatus): Boolean {
        val allowedTransitions = validTransitions[currentStatus] ?: emptyList()
        return newStatus in allowedTransitions
    }

    /**
     * Get all possible next statuses for current status
     */
    fun getPossibleTransitions(currentStatus: DropStatus): List<DropStatus> {
        return validTransitions[currentStatus] ?: emptyList()
    }

    /**
     * Check if status is terminal (no further transitions allowed)
     */
    fun isTerminalStatus(status: DropStatus): Boolean {
        return validTransitions[status]?.isEmpty() == true
    }
}