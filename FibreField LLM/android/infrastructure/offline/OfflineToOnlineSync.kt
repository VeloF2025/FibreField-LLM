package com.fibreflow.infrastructure.offline

import com.fibreflow.core.common.result.Result
import com.fibreflow.infrastructure.sync.SyncManager
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline to Online Synchronization Service
 * Handles synchronization of offline operations when connectivity is restored
 */
@Singleton
class OfflineToOnlineSync @Inject constructor(
    private val syncManager: SyncManager
) {

    private val pendingOperations = ConcurrentLinkedQueue<OfflineOperationData>()
    private val failedOperations = ConcurrentLinkedQueue<OfflineOperationData>()

    /**
     * Queue an operation for execution when online
     */
    fun queueOperation(operation: OfflineOperationData): Result<Unit> {
        return try {
            pendingOperations.add(operation)
            Timber.d("Queued operation for online sync: ${operation.type}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error queuing operation")
            Result.Error(e)
        }
    }

    /**
     * Process all pending operations
     */
    suspend fun processPendingOperations(): Result<SyncResult> {
        return try {
            if (pendingOperations.isEmpty()) {
                Timber.d("No pending operations to process")
                return Result.Success(SyncResult(0, 0, 0))
            }

            Timber.i("Processing ${pendingOperations.size} pending operations")

            var processed = 0
            var failed = 0
            val startTime = System.currentTimeMillis()

            // Process operations in batches to avoid overwhelming the system
            val batchSize = 10
            val operationsToProcess = mutableListOf<OfflineOperationData>()

            while (pendingOperations.isNotEmpty() && operationsToProcess.size < batchSize) {
                pendingOperations.poll()?.let { operationsToProcess.add(it) }
            }

            for (operation in operationsToProcess) {
                val result = executeOperation(operation)

                if (result is Result.Success) {
                    processed++
                    Timber.d("Successfully processed operation: ${operation.type}")
                } else {
                    failed++
                    failedOperations.add(operation)
                    Timber.w("Failed to process operation: ${operation.type}, ${result.exception?.message}")
                }

                // Small delay between operations to prevent overwhelming
                delay(100)
            }

            val syncTime = System.currentTimeMillis() - startTime

            Timber.i("Processed $processed operations, $failed failed, in ${syncTime}ms")

            Result.Success(SyncResult(processed, failed, syncTime))

        } catch (e: Exception) {
            Timber.e(e, "Error processing pending operations")
            Result.Error(e)
        }
    }

    /**
     * Retry failed operations
     */
    suspend fun retryFailedOperations(): Result<SyncResult> {
        return try {
            if (failedOperations.isEmpty()) {
                Timber.d("No failed operations to retry")
                return Result.Success(SyncResult(0, 0, 0))
            }

            Timber.i("Retrying ${failedOperations.size} failed operations")

            val operationsToRetry = mutableListOf<OfflineOperationData>()
            while (failedOperations.isNotEmpty()) {
                failedOperations.poll()?.let { operationsToRetry.add(it) }
            }

            var processed = 0
            var failed = 0
            val startTime = System.currentTimeMillis()

            for (operation in operationsToRetry) {
                val result = executeOperation(operation)

                if (result is Result.Success) {
                    processed++
                } else {
                    failed++
                    failedOperations.add(operation) // Re-queue for another retry
                }
            }

            val syncTime = System.currentTimeMillis() - startTime

            Timber.i("Retried operations: $processed successful, $failed still failed")

            Result.Success(SyncResult(processed, failed, syncTime))

        } catch (e: Exception) {
            Timber.e(e, "Error retrying failed operations")
            Result.Error(e)
        }
    }

    /**
     * Get pending operations count
     */
    fun getPendingOperationsCount(): Int {
        return pendingOperations.size
    }

    /**
     * Get failed operations count
     */
    fun getFailedOperationsCount(): Int {
        return failedOperations.size
    }

    /**
     * Clear all pending operations
     */
    fun clearPendingOperations(): Result<Unit> {
        return try {
            val cleared = pendingOperations.size
            pendingOperations.clear()
            Timber.i("Cleared $cleared pending operations")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error clearing pending operations")
            Result.Error(e)
        }
    }

    /**
     * Clear failed operations
     */
    fun clearFailedOperations(): Result<Unit> {
        return try {
            val cleared = failedOperations.size
            failedOperations.clear()
            Timber.i("Cleared $cleared failed operations")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error clearing failed operations")
            Result.Error(e)
        }
    }

    /**
     * Get operations statistics
     */
    fun getOperationsStatistics(): OperationsStatistics {
        return OperationsStatistics(
            pendingCount = pendingOperations.size,
            failedCount = failedOperations.size,
            oldestPendingOperation = pendingOperations.peek()?.timestamp,
            newestPendingOperation = pendingOperations.maxByOrNull { it.timestamp }?.timestamp
        )
    }

    /**
     * Check if there are critical operations pending
     */
    fun hasCriticalOperationsPending(): Boolean {
        return pendingOperations.any { operation ->
            operation.priority >= 5 || operation.type in listOf("EMERGENCY_SYNC", "CRITICAL_UPDATE")
        }
    }

    /**
     * Process operations by priority
     */
    suspend fun processOperationsByPriority(): Result<PrioritySyncResult> {
        return try {
            val highPriority = pendingOperations.filter { it.priority >= 3 }.toMutableList()
            val normalPriority = pendingOperations.filter { it.priority < 3 }.toMutableList()

            Timber.d("Processing ${highPriority.size} high priority and ${normalPriority.size} normal priority operations")

            var highProcessed = 0
            var highFailed = 0
            var normalProcessed = 0
            var normalFailed = 0

            // Process high priority first
            for (operation in highPriority) {
                val result = executeOperation(operation)
                if (result is Result.Success) {
                    highProcessed++
                    pendingOperations.remove(operation)
                } else {
                    highFailed++
                }
            }

            // Then process normal priority
            for (operation in normalPriority) {
                val result = executeOperation(operation)
                if (result is Result.Success) {
                    normalProcessed++
                    pendingOperations.remove(operation)
                } else {
                    normalFailed++
                }
            }

            Result.Success(
                PrioritySyncResult(
                    highPriorityProcessed = highProcessed,
                    highPriorityFailed = highFailed,
                    normalPriorityProcessed = normalProcessed,
                    normalPriorityFailed = normalFailed
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Error processing operations by priority")
            Result.Error(e)
        }
    }

    // Private methods

    private suspend fun executeOperation(operation: OfflineOperationData): Result<Unit> {
        return try {
            when (operation.type) {
                "SYNC_DATA" -> {
                    syncManager.performImmediateSync()
                }
                "UPLOAD_PHOTO" -> {
                    // Would delegate to photo upload service
                    delay(200) // Simulate upload time
                    Result.Success(Unit)
                }
                "UPDATE_INSTALLATION" -> {
                    // Would delegate to installation service
                    delay(100)
                    Result.Success(Unit)
                }
                "ACTIVATION_REQUEST" -> {
                    // Would delegate to activation service
                    delay(150)
                    Result.Success(Unit)
                }
                else -> {
                    Timber.w("Unknown operation type: ${operation.type}")
                    Result.Success(Unit) // Treat unknown operations as successful
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error executing operation: ${operation.type}")
            Result.Error(e)
        }
    }
}

/**
 * Operations statistics
 */
data class OperationsStatistics(
    val pendingCount: Int,
    val failedCount: Int,
    val oldestPendingOperation: Long?,
    val newestPendingOperation: Long?
)

/**
 * Priority-based sync result
 */
data class PrioritySyncResult(
    val highPriorityProcessed: Int,
    val highPriorityFailed: Int,
    val normalPriorityProcessed: Int,
    val normalPriorityFailed: Int
) {
    val totalProcessed: Int = highPriorityProcessed + normalPriorityProcessed
    val totalFailed: Int = highPriorityFailed + normalPriorityFailed
    val successRate: Double = if (totalProcessed + totalFailed > 0) {
        totalProcessed.toDouble() / (totalProcessed + totalFailed)
    } else 0.0
}