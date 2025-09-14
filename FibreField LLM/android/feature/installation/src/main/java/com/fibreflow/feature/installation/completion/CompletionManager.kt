package com.fibreflow.feature.installation.completion

import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.drops.entities.Drop
import com.fibreflow.domain.drops.entities.DropStatus
import com.fibreflow.feature.installation.workflow.InstallationSession
import com.fibreflow.feature.installation.workflow.InstallationSessionManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages installation completion process
 * Handles final validations, status updates, and completion workflows
 */
@Singleton
class CompletionManager @Inject constructor(
    private val sessionManager: InstallationSessionManager,
    private val syncService: InstallationSyncService
) {

    /**
     * Complete installation for a drop
     * Performs final validations and updates all related systems
     */
    suspend fun completeInstallation(
        session: InstallationSession,
        drop: Drop,
        finalNotes: String? = null
    ): Result<CompletionResult> {
        return try {
            Timber.d("Starting installation completion for drop: ${drop.dropNumber}")

            // Validate completion requirements
            val validationResult = validateCompletionRequirements(session, drop)
            if (validationResult is Result.Error) {
                return validationResult
            }

            // Mark session as completed
            val sessionCompleteResult = sessionManager.completeSession(session.sessionId)
            if (sessionCompleteResult is Result.Error) {
                return Result.Error(sessionCompleteResult.exception)
            }

            // Update drop status to completed
            val dropUpdateResult = updateDropStatus(drop, DropStatus.COMPLETED)
            if (dropUpdateResult is Result.Error) {
                return Result.Error(dropUpdateResult.exception)
            }

            // Sync completion data
            val syncResult = syncService.syncInstallationCompletion(
                session,
                drop,
                finalNotes
            )
            if (syncResult is Result.Error) {
                Timber.w(syncResult.exception, "Failed to sync completion, but installation marked complete locally")
                // Don't fail the completion if sync fails - can retry later
            }

            // Generate completion summary
            val completionResult = CompletionResult(
                sessionId = session.sessionId,
                dropNumber = drop.dropNumber,
                completionTime = System.currentTimeMillis(),
                durationMinutes = session.durationMinutes ?: 0,
                finalNotes = finalNotes,
                syncSuccessful = syncResult is Result.Success,
                validationPassed = true
            )

            Timber.d("Installation completion successful for drop: ${drop.dropNumber}")
            Result.Success(completionResult)

        } catch (e: Exception) {
            Timber.e(e, "Error completing installation for drop: ${drop.dropNumber}")
            Result.Error(e)
        }
    }

    /**
     * Validate that all completion requirements are met
     */
    private suspend fun validateCompletionRequirements(
        session: InstallationSession,
        drop: Drop
    ): Result<Unit> {
        val errors = mutableListOf<String>()

        // Check if session is in valid state for completion
        if (!session.isActive) {
            errors.add("Session is not active")
        }

        // Check if all required steps are completed
        val totalSteps = 9 // Assuming 9-step workflow
        val requiredStepsCompleted = session.completedSteps.size >= totalSteps - 1 // Allow some flexibility

        if (!requiredStepsCompleted) {
            errors.add("Not all required installation steps completed")
        }

        // Check for critical skipped steps
        val criticalSkippedSteps = session.skippedSteps.filter { (stepId, _) ->
            isCriticalStep(stepId)
        }

        if (criticalSkippedSteps.isNotEmpty()) {
            errors.add("Critical steps were skipped: ${criticalSkippedSteps.keys.joinToString()}")
        }

        // Validate drop status allows completion
        if (drop.status != DropStatus.IN_PROGRESS && drop.status != DropStatus.PENDING_VALIDATION) {
            errors.add("Drop status does not allow completion")
        }

        return if (errors.isEmpty()) {
            Result.Success(Unit)
        } else {
            Result.Error(Exception("Completion validation failed: ${errors.joinToString(", ")}"))
        }
    }

    /**
     * Update drop status to completed
     */
    private suspend fun updateDropStatus(drop: Drop, newStatus: DropStatus): Result<Unit> {
        return try {
            // In a real implementation, this would update the drop through the domain service
            // For now, we'll simulate the update
            Timber.d("Updating drop ${drop.dropNumber} status to $newStatus")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Check if a step is critical for completion
     */
    private fun isCriticalStep(stepId: Int): Boolean {
        // Define critical steps that cannot be skipped
        val criticalSteps = setOf(1, 2, 3, 9) // First, second, third, and final steps
        return stepId in criticalSteps
    }

    /**
     * Handle installation failure
     */
    suspend fun failInstallation(
        session: InstallationSession,
        drop: Drop,
        failureReason: String,
        notes: String? = null
    ): Result<FailureResult> {
        return try {
            Timber.d("Handling installation failure for drop: ${drop.dropNumber}, reason: $failureReason")

            // Mark session as failed
            session.fail()

            // Update drop status
            val dropUpdateResult = updateDropStatus(drop, DropStatus.FAILED)
            if (dropUpdateResult is Result.Error) {
                Timber.w(dropUpdateResult.exception, "Failed to update drop status")
            }

            // Sync failure data
            val syncResult = syncService.syncInstallationFailure(
                session,
                drop,
                failureReason,
                notes
            )

            val failureResult = FailureResult(
                sessionId = session.sessionId,
                dropNumber = drop.dropNumber,
                failureTime = System.currentTimeMillis(),
                failureReason = failureReason,
                notes = notes,
                syncSuccessful = syncResult is Result.Success
            )

            Timber.d("Installation failure handled for drop: ${drop.dropNumber}")
            Result.Success(failureResult)

        } catch (e: Exception) {
            Timber.e(e, "Error handling installation failure")
            Result.Error(e)
        }
    }

    /**
     * Get completion statistics
     */
    suspend fun getCompletionStatistics(): Result<CompletionStatistics> {
        return try {
            // In a real implementation, this would query the database
            // For now, return mock statistics
            val stats = CompletionStatistics(
                totalCompletions = 0,
                averageCompletionTime = 0,
                successRate = 0.0,
                commonFailureReasons = emptyList()
            )
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Retry failed sync operations
     */
    suspend fun retryFailedSync(sessionId: String): Result<Unit> {
        return try {
            val sessionResult = sessionManager.getSession(sessionId)
            if (sessionResult is Result.Error) {
                return sessionResult
            }

            val session = (sessionResult as Result.Success).data

            // Attempt to sync completion data again
            val syncResult = syncService.retrySync(session)
            if (syncResult is Result.Error) {
                return syncResult
            }

            Timber.d("Successfully retried sync for session: $sessionId")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Error retrying sync for session: $sessionId")
            Result.Error(e)
        }
    }
}

/**
 * Result of installation completion
 */
data class CompletionResult(
    val sessionId: String,
    val dropNumber: String,
    val completionTime: Long,
    val durationMinutes: Long,
    val finalNotes: String?,
    val syncSuccessful: Boolean,
    val validationPassed: Boolean
) {
    val completionMessage: String
        get() = "Installation completed successfully${if (!syncSuccessful) " (sync pending)" else ""}"
}

/**
 * Result of installation failure
 */
data class FailureResult(
    val sessionId: String,
    val dropNumber: String,
    val failureTime: Long,
    val failureReason: String,
    val notes: String?,
    val syncSuccessful: Boolean
)

/**
 * Completion statistics
 */
data class CompletionStatistics(
    val totalCompletions: Int,
    val averageCompletionTime: Long, // minutes
    val successRate: Double, // 0.0 to 1.0
    val commonFailureReasons: List<String>
)