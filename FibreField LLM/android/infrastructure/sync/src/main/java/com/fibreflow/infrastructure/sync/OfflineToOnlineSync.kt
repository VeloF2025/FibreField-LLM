package com.fibreflow.infrastructure.sync

import android.util.Log
import com.fibreflow.core.common.result.Result
import com.fibreflow.infrastructure.offline.OfflineOperationData
import com.fibreflow.infrastructure.offline.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline to Online Synchronization Manager
 * Handles queuing operations performed while offline and executing them when connection is restored
 */
@Singleton
class OfflineToOnlineSync @Inject constructor(
    private val syncService: SyncService,
    private val syncManager: SyncManager
) {

    companion object {
        private const val TAG = "OfflineToOnlineSync"
        private const val MAX_QUEUE_SIZE = 1000
        private const val BATCH_SIZE = 10
    }

    // Thread-safe queue for pending operations
    private val pendingOperations = ConcurrentLinkedQueue<OfflineOperationData>()

    /**
     * Queue an operation to be executed when online
     */
    suspend fun queueOperation(operation: OfflineOperationData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (pendingOperations.size >= MAX_QUEUE_SIZE) {
                Log.w(TAG, "Operation queue full, rejecting operation: ${operation.type}")
                return@withContext Result.Error(RuntimeException("Operation queue full"))
            }

            pendingOperations.add(operation)
            Log.d(TAG, "Operation queued: ${operation.type} (${operation.id})")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue operation", e)
            Result.Error(e)
        }
    }

    /**
     * Process all pending operations
     */
    suspend fun processPendingOperations(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            var processed = 0
            var failed = 0

            Log.i(TAG, "Processing ${pendingOperations.size} pending operations")

            // Process operations in batches
            val operationsToProcess = mutableListOf<OfflineOperationData>()
            var operation = pendingOperations.poll()

            while (operation != null && operationsToProcess.size < BATCH_SIZE) {
                operationsToProcess.add(operation)
                operation = pendingOperations.poll()
            }

            // Process the batch
            for (op in operationsToProcess) {
                try {
                    val result = executeOperation(op)
                    if (result.isSuccess) {
                        processed++
                        Log.d(TAG, "Operation completed: ${op.type}")
                    } else {
                        failed++
                        Log.w(TAG, "Operation failed: ${op.type}", result.exceptionOrNull())
                        // Re-queue failed operations for retry
                        pendingOperations.add(op)
                    }
                } catch (e: Exception) {
                    failed++
                    Log.e(TAG, "Operation execution error: ${op.type}", e)
                    // Re-queue on error
                    pendingOperations.add(op)
                }
            }

            val syncTime = System.currentTimeMillis() - startTime

            val result = SyncResult(
                operationsProcessed = processed,
                operationsFailed = failed,
                syncTimeMs = syncTime
            )

            Log.i(TAG, "Processed $processed operations, $failed failed in ${syncTime}ms")
            Result.Success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process pending operations", e)
            Result.Error(e)
        }
    }

    /**
     * Get the number of pending operations
     */
    fun getPendingOperationsCount(): Int {
        return pendingOperations.size
    }

    /**
     * Clear all pending operations
     */
    suspend fun clearPendingOperations(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleared = pendingOperations.size
            pendingOperations.clear()
            Log.i(TAG, "Cleared $cleared pending operations")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear pending operations", e)
            Result.Error(e)
        }
    }

    /**
     * Get pending operations for a specific type
     */
    fun getPendingOperationsByType(type: String): List<OfflineOperationData> {
        return pendingOperations.filter { it.type == type }
    }

    /**
     * Check if there are high-priority operations pending
     */
    fun hasHighPriorityOperations(): Boolean {
        return pendingOperations.any { it.priority >= 5 }
    }

    // Private helper methods

    private suspend fun executeOperation(operation: OfflineOperationData): Result<Unit> {
        return try {
            when (operation.type) {
                "SYNC_INSTALLATION" -> {
                    // Extract installation data from operation
                    val installationId = operation.data["installationId"] as? String
                        ?: return Result.Error(RuntimeException("Missing installationId"))

                    // In real implementation, would fetch installation from database
                    // and call syncService.syncInstallation(installation)
                    Result.Success(Unit)
                }

                "SYNC_PHOTO" -> {
                    val photoId = operation.data["photoId"] as? String
                        ?: return Result.Error(RuntimeException("Missing photoId"))

                    // Would fetch photo from database and sync
                    Result.Success(Unit)
                }

                "SYNC_DROP" -> {
                    val dropId = operation.data["dropId"] as? String
                        ?: return Result.Error(RuntimeException("Missing dropId"))

                    // Would fetch drop from database and sync
                    Result.Success(Unit)
                }

                "FULL_SYNC" -> {
                    // Trigger full sync
                    syncManager.performImmediateSync()
                    Result.Success(Unit)
                }

                else -> {
                    Log.w(TAG, "Unknown operation type: ${operation.type}")
                    Result.Error(RuntimeException("Unknown operation type: ${operation.type}"))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error executing operation ${operation.type}", e)
            Result.Error(e)
        }
    }
}