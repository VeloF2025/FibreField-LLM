package com.fibreflow.infrastructure.sync

import com.fibreflow.infrastructure.sync.conflict.ConflictResolver as InternalConflictResolver
import com.fibreflow.core.common.result.Result
import com.fibreflow.core.database.entities.InstallationEntity
import com.fibreflow.core.database.entities.PhotoEntity
import com.fibreflow.core.database.entities.DropEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public Conflict Resolution Manager for Data Synchronization
 * Delegates to internal conflict resolver implementation
 */
@Singleton
class ConflictResolver @Inject constructor(
    private val internalResolver: InternalConflictResolver
) {

    /**
     * Resolve installation conflict
     */
    suspend fun resolveInstallationConflict(
        local: InstallationEntity,
        remote: InstallationEntity
    ): Result<com.fibreflow.infrastructure.sync.conflict.ConflictResolution<InstallationEntity>> {
        return internalResolver.resolveInstallationConflict(local, remote)
    }

    /**
     * Resolve photo conflict
     */
    suspend fun resolvePhotoConflict(
        local: PhotoEntity,
        remote: PhotoEntity
    ): Result<com.fibreflow.infrastructure.sync.conflict.ConflictResolution<PhotoEntity>> {
        return internalResolver.resolvePhotoConflict(local, remote)
    }

    /**
     * Resolve drop conflict
     */
    suspend fun resolveDropConflict(
        local: DropEntity,
        remote: DropEntity
    ): Result<com.fibreflow.infrastructure.sync.conflict.ConflictResolution<DropEntity>> {
        return internalResolver.resolveDropConflict(local, remote)
    }

    /**
     * Get conflict resolution statistics
     */
    fun getConflictStatistics(): com.fibreflow.infrastructure.sync.conflict.ConflictStatistics {
        return internalResolver.getConflictStatistics()
    }
}