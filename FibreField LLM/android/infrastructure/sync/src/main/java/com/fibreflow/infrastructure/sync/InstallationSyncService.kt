package com.fibreflow.infrastructure.sync

import com.fibreflow.core.common.result.Result
import com.fibreflow.core.network.api.InstallationAPI
import com.fibreflow.domain.drops.entities.Drop
import com.fibreflow.feature.installation.workflow.InstallationSession
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for syncing installation completion data to the server
 * Handles offline queue management and retry logic for installation syncs
 */
@Singleton
class InstallationSyncService @Inject constructor(
    private val installationApi: InstallationAPI,
    private val offlineQueue: OfflineQueue
) {

    companion object {
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 5000L // 5 seconds
    }

    /**
     * Sync installation completion data
     */
    suspend fun syncInstallationCompletion(
        session: InstallationSession,
        drop: Drop,
        finalNotes: String?
    ): Result<Unit> {
        return try {
            Timber.d("Syncing installation completion for drop: ${drop.dropNumber}")

            val completionData = InstallationCompletionData(
                sessionId = session.sessionId,
                dropNumber = drop.dropNumber,
                technicianId = session.technicianId,
                completionTime = System.currentTimeMillis(),
                durationMinutes = session.durationMinutes ?: 0,
                completedSteps = session.completedSteps.toList(),
                skippedSteps = session.skippedSteps,
                finalNotes = finalNotes,
                syncAttempt = 1
            )

            // Try to sync immediately
            val syncResult = performSync(completionData)

            if (syncResult is Result.Success) {
                Timber.d("Installation completion synced successfully for drop: ${drop.dropNumber}")
                Result.Success(Unit)
            } else {
                // Add to offline queue for retry
                Timber.w(syncResult.exception, "Failed to sync completion, queuing for retry")
                offlineQueue.addToQueue(completionData)
                Result.Error(syncResult.exception)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error syncing installation completion")
            Result.Error(e)
        }
    }

    /**
     * Sync installation failure data
     */
    suspend fun syncInstallationFailure(
        session: InstallationSession,
        drop: Drop,
        failureReason: String,
        notes: String?
    ): Result<Unit> {
        return try {
            Timber.d("Syncing installation failure for drop: ${drop.dropNumber}")

            val failureData = InstallationFailureData(
                sessionId = session.sessionId,
                dropNumber = drop.dropNumber,
                technicianId = session.technicianId,
                failureTime = System.currentTimeMillis(),
                failureReason = failureReason,
                notes = notes,
                completedSteps = session.completedSteps.toList(),
                syncAttempt = 1
            )

            val syncResult = performFailureSync(failureData)

            if (syncResult is Result.Success) {
                Timber.d("Installation failure synced successfully for drop: ${drop.dropNumber}")
                Result.Success(Unit)
            } else {
                Timber.w(syncResult.exception, "Failed to sync failure, queuing for retry")
                offlineQueue.addToQueue(failureData)
                Result.Error(syncResult.exception)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error syncing installation failure")
            Result.Error(e)
        }
    }

    /**
     * Retry syncing failed operations
     */
    suspend fun retrySync(session: InstallationSession): Result<Unit> {
        return try {
            val queuedItems = offlineQueue.getQueuedItems(session.sessionId)

            if (queuedItems.isEmpty()) {
                return Result.Success(Unit) // Nothing to retry
            }

            var successCount = 0
            var failureCount = 0

            for (item in queuedItems) {
                val retryResult = when (item) {
                    is InstallationCompletionData -> performSync(item.copy(syncAttempt = item.syncAttempt + 1))
                    is InstallationFailureData -> performFailureSync(item.copy(syncAttempt = item.syncAttempt + 1))
                    else -> continue
                }

                if (retryResult is Result.Success) {
                    offlineQueue.removeFromQueue(item)
                    successCount++
                } else {
                    failureCount++
                    // Update retry count
                    offlineQueue.updateRetryCount(item, item.syncAttempt + 1)
                }
            }

            Timber.d("Retry sync completed: $successCount successful, $failureCount failed")

            if (failureCount == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("$failureCount sync operations still failed after retry"))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error during sync retry")
            Result.Error(e)
        }
    }

    /**
     * Perform the actual sync operation
     */
    private suspend fun performSync(data: InstallationCompletionData): Result<Unit> {
        return try {
            // In a real implementation, this would call the actual API
            // For now, simulate the API call
            simulateApiCall()

            // If successful, mark as synced
            Timber.d("Simulated sync successful for completion: ${data.sessionId}")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Sync failed for completion: ${data.sessionId}")
            Result.Error(e)
        }
    }

    /**
     * Perform failure sync operation
     */
    private suspend fun performFailureSync(data: InstallationFailureData): Result<Unit> {
        return try {
            // Simulate API call for failure reporting
            simulateApiCall()

            Timber.d("Simulated failure sync successful for: ${data.sessionId}")
            Result.Success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failure sync failed for: ${data.sessionId}")
            Result.Error(e)
        }
    }

    /**
     * Simulate API call delay
     */
    private suspend fun simulateApiCall() {
        // Simulate network delay
        kotlinx.coroutines.delay(1000)
    }

    /**
     * Get sync status for a session
     */
    suspend fun getSyncStatus(sessionId: String): Result<SyncStatus> {
        return try {
            val queuedItems = offlineQueue.getQueuedItems(sessionId)
            val hasPendingSync = queuedItems.isNotEmpty()

            val status = if (hasPendingSync) {
                SyncStatus.PENDING
            } else {
                SyncStatus.SYNCED
            }

            Result.Success(status)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Force sync all pending operations
     */
    suspend fun forceSyncAllPending(): Result<SyncSummary> {
        return try {
            val allQueuedItems = offlineQueue.getAllQueuedItems()
            var successCount = 0
            var failureCount = 0

            for (item in allQueuedItems) {
                val syncResult = when (item) {
                    is InstallationCompletionData -> performSync(item)
                    is InstallationFailureData -> performFailureSync(item)
                    else -> continue
                }

                if (syncResult is Result.Success) {
                    offlineQueue.removeFromQueue(item)
                    successCount++
                } else {
                    failureCount++
                }
            }

            val summary = SyncSummary(
                totalProcessed = allQueuedItems.size,
                successful = successCount,
                failed = failureCount,
                timestamp = System.currentTimeMillis()
            )

            Timber.d("Force sync completed: ${summary.successful} successful, ${summary.failed} failed")
            Result.Success(summary)

        } catch (e: Exception) {
            Timber.e(e, "Error during force sync")
            Result.Error(e)
        }
    }

    /**
     * Clean up old sync data
     */
    suspend fun cleanupOldSyncData(olderThanDays: Int = 30): Result<Int> {
        return try {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(olderThanDays.toLong())
            val removedCount = offlineQueue.cleanupOldItems(cutoffTime)

            Timber.d("Cleaned up $removedCount old sync items")
            Result.Success(removedCount)

        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old sync data")
            Result.Error(e)
        }
    }
}

/**
 * Data class for installation completion sync
 */
data class InstallationCompletionData(
    val sessionId: String,
    val dropNumber: String,
    val technicianId: String,
    val completionTime: Long,
    val durationMinutes: Long,
    val completedSteps: List<Int>,
    val skippedSteps: Map<Int, String>,
    val finalNotes: String?,
    val syncAttempt: Int = 1
)

/**
 * Data class for installation failure sync
 */
data class InstallationFailureData(
    val sessionId: String,
    val dropNumber: String,
    val technicianId: String,
    val failureTime: Long,
    val failureReason: String,
    val notes: String?,
    val completedSteps: List<Int>,
    val syncAttempt: Int = 1
)

/**
 * Sync status enum
 */
enum class SyncStatus {
    SYNCED,
    PENDING,
    FAILED
}

/**
 * Summary of sync operations
 */
data class SyncSummary(
    val totalProcessed: Int,
    val successful: Int,
    val failed: Int,
    val timestamp: Long
)