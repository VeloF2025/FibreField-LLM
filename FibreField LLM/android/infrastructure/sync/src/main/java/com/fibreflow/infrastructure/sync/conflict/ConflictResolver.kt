package com.fibreflow.infrastructure.sync.conflict

import android.util.Log
import com.fibreflow.core.common.result.Result
import com.fibreflow.core.database.entities.InstallationEntity
import com.fibreflow.core.database.entities.PhotoEntity
import com.fibreflow.core.database.entities.DropEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conflict Resolution Manager for Data Synchronization
 * Handles conflicts between local and remote data during sync operations
 */
@Singleton
class ConflictResolver @Inject constructor() {

    companion object {
        private const val TAG = "ConflictResolver"
        private const val CONFLICT_THRESHOLD_MS = 5000L // 5 seconds
    }

    /**
     * Resolve installation conflict
     */
    suspend fun resolveInstallationConflict(
        local: InstallationEntity,
        remote: InstallationEntity
    ): Result<ConflictResolution<InstallationEntity>> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Resolving installation conflict for ID: ${local.id}")

            val resolution = when {
                // Local is more recent
                local.updatedAt > remote.updatedAt -> {
                    ConflictResolution(
                        resolvedEntity = local,
                        strategy = ConflictStrategy.USE_LOCAL,
                        reason = "Local data is more recent"
                    )
                }
                // Remote is more recent
                remote.updatedAt > local.updatedAt -> {
                    ConflictResolution(
                        resolvedEntity = remote,
                        strategy = ConflictStrategy.USE_REMOTE,
                        reason = "Remote data is more recent"
                    )
                }
                // Same timestamp, check status progression
                else -> resolveByStatusProgression(local, remote)
            }

            Log.d(TAG, "Installation conflict resolved using ${resolution.strategy}")
            Result.Success(resolution)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve installation conflict", e)
            Result.Error(e)
        }
    }

    /**
     * Resolve photo conflict
     */
    suspend fun resolvePhotoConflict(
        local: PhotoEntity,
        remote: PhotoEntity
    ): Result<ConflictResolution<PhotoEntity>> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Resolving photo conflict for ID: ${local.id}")

            val resolution = when {
                // Local validation is more recent
                local.validatedAt ?: 0 > remote.validatedAt ?: 0 -> {
                    ConflictResolution(
                        resolvedEntity = local,
                        strategy = ConflictStrategy.USE_LOCAL,
                        reason = "Local validation is more recent"
                    )
                }
                // Remote validation is more recent
                remote.validatedAt ?: 0 > local.validatedAt ?: 0 -> {
                    ConflictResolution(
                        resolvedEntity = remote,
                        strategy = ConflictStrategy.USE_REMOTE,
                        reason = "Remote validation is more recent"
                    )
                }
                // Both validated, prefer higher confidence
                local.validationConfidence ?: 0f > remote.validationConfidence ?: 0f -> {
                    ConflictResolution(
                        resolvedEntity = local,
                        strategy = ConflictStrategy.USE_LOCAL,
                        reason = "Local validation has higher confidence"
                    )
                }
                else -> {
                    ConflictResolution(
                        resolvedEntity = remote,
                        strategy = ConflictStrategy.USE_REMOTE,
                        reason = "Remote validation preferred"
                    )
                }
            }

            Log.d(TAG, "Photo conflict resolved using ${resolution.strategy}")
            Result.Success(resolution)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve photo conflict", e)
            Result.Error(e)
        }
    }

    /**
     * Resolve drop conflict
     */
    suspend fun resolveDropConflict(
        local: DropEntity,
        remote: DropEntity
    ): Result<ConflictResolution<DropEntity>> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Resolving drop conflict for ID: ${local.id}")

            val resolution = when {
                // Status progression logic
                isStatusProgression(local.status, remote.status) -> {
                    ConflictResolution(
                        resolvedEntity = local,
                        strategy = ConflictStrategy.USE_LOCAL,
                        reason = "Local status represents progression"
                    )
                }
                isStatusProgression(remote.status, local.status) -> {
                    ConflictResolution(
                        resolvedEntity = remote,
                        strategy = ConflictStrategy.USE_REMOTE,
                        reason = "Remote status represents progression"
                    )
                }
                // Timestamp-based resolution
                local.updatedAt > remote.updatedAt -> {
                    ConflictResolution(
                        resolvedEntity = local,
                        strategy = ConflictStrategy.USE_LOCAL,
                        reason = "Local data is more recent"
                    )
                }
                else -> {
                    ConflictResolution(
                        resolvedEntity = remote,
                        strategy = ConflictStrategy.USE_REMOTE,
                        reason = "Remote data preferred"
                    )
                }
            }

            Log.d(TAG, "Drop conflict resolved using ${resolution.strategy}")
            Result.Success(resolution)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve drop conflict", e)
            Result.Error(e)
        }
    }

    /**
     * Merge conflicting data when appropriate
     */
    suspend fun <T> attemptMerge(
        local: T,
        remote: T,
        mergeStrategy: MergeStrategy
    ): Result<ConflictResolution<T>> = withContext(Dispatchers.Default) {
        try {
            when (mergeStrategy) {
                MergeStrategy.KEEP_LOCAL_CHANGES -> Result.Success(
                    ConflictResolution(
                        resolvedEntity = local,
                        strategy = ConflictStrategy.MERGE_LOCAL_PRIORITY,
                        reason = "Merge with local priority"
                    )
                )
                MergeStrategy.KEEP_REMOTE_CHANGES -> Result.Success(
                    ConflictResolution(
                        resolvedEntity = remote,
                        strategy = ConflictStrategy.MERGE_REMOTE_PRIORITY,
                        reason = "Merge with remote priority"
                    )
                )
                MergeStrategy.MANUAL_RESOLUTION -> Result.Success(
                    ConflictResolution(
                        resolvedEntity = local, // Default to local, requires manual review
                        strategy = ConflictStrategy.REQUIRES_MANUAL_REVIEW,
                        reason = "Requires manual conflict resolution"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge conflicting data", e)
            Result.Error(e)
        }
    }

    /**
     * Resolve sync change conflict
     */
    suspend fun resolveChangeConflict(
        conflict: com.fibreflow.infrastructure.sync.SyncConflict
    ): Result<com.fibreflow.infrastructure.sync.SyncChange> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Resolving sync change conflict for entity: ${conflict.entityType} ${conflict.entityId}")
            
            // For sync changes, we typically use timestamp-based resolution
            val localChange = conflict.localData as com.fibreflow.infrastructure.sync.SyncChange
            val remoteChange = conflict.remoteData as com.fibreflow.infrastructure.sync.SyncChange
            
            val resolvedChange = if (localChange.timestamp > remoteChange.timestamp) {
                localChange
            } else {
                remoteChange
            }
            
            Log.d(TAG, "Sync change conflict resolved using timestamp comparison")
            Result.Success(resolvedChange)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve sync change conflict", e)
            Result.Error(e)
        }
    }

    /**
     * Get conflict resolution statistics
     */
    fun getConflictStatistics(): ConflictStatistics {
        // In a real implementation, would track actual conflict resolution metrics
        return ConflictStatistics(
            totalConflicts = 25,
            resolvedAutomatically = 20,
            requiredManualReview = 5,
            resolutionRate = 0.8f,
            averageResolutionTimeMs = 1500L
        )
    }

    // Private helper methods

    private fun resolveByStatusProgression(
        local: InstallationEntity,
        remote: InstallationEntity
    ): ConflictResolution<InstallationEntity> {
        val statusHierarchy = mapOf(
            "PENDING" to 1,
            "IN_PROGRESS" to 2,
            "COMPLETED" to 3,
            "CANCELLED" to 4
        )

        val localPriority = statusHierarchy[local.status] ?: 0
        val remotePriority = statusHierarchy[remote.status] ?: 0

        return if (localPriority > remotePriority) {
            ConflictResolution(
                resolvedEntity = local,
                strategy = ConflictStrategy.USE_LOCAL,
                reason = "Local status represents further progression"
            )
        } else {
            ConflictResolution(
                resolvedEntity = remote,
                strategy = ConflictStrategy.USE_REMOTE,
                reason = "Remote status represents further progression"
            )
        }
    }

    private fun isStatusProgression(fromStatus: String, toStatus: String): Boolean {
        val progressionMap = mapOf(
            "AVAILABLE" to listOf("PENDING", "IN_PROGRESS", "COMPLETED"),
            "PENDING" to listOf("IN_PROGRESS", "COMPLETED"),
            "IN_PROGRESS" to listOf("COMPLETED")
        )

        return progressionMap[fromStatus]?.contains(toStatus) == true
    }
}

/**
 * Conflict resolution result
 */
data class ConflictResolution<T>(
    val resolvedEntity: T,
    val strategy: ConflictStrategy,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Conflict resolution strategies
 */
enum class ConflictStrategy {
    USE_LOCAL,
    USE_REMOTE,
    MERGE_LOCAL_PRIORITY,
    MERGE_REMOTE_PRIORITY,
    REQUIRES_MANUAL_REVIEW
}

/**
 * Merge strategies for conflict resolution
 */
enum class MergeStrategy {
    KEEP_LOCAL_CHANGES,
    KEEP_REMOTE_CHANGES,
    MANUAL_RESOLUTION
}

/**
 * Conflict resolution statistics
 */
data class ConflictStatistics(
    val totalConflicts: Int,
    val resolvedAutomatically: Int,
    val requiredManualReview: Int,
    val resolutionRate: Float,
    val averageResolutionTimeMs: Long
)