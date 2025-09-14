package com.fibreflow.domain.drops.repositories

import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.drops.entities.Drop
import com.fibreflow.domain.drops.entities.DropPriority
import com.fibreflow.domain.drops.entities.DropStatus
import com.fibreflow.domain.drops.entities.Location
import com.fibreflow.domain.drops.entities.ProximityResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for drop management operations
 */
interface DropRepository {

    /**
     * Get all available drops for assignment
     */
    suspend fun getAvailableDrops(): Result<List<Drop>>

    /**
     * Get drop by drop number
     */
    suspend fun getDropByNumber(dropNumber: String): Result<Drop>

    /**
     * Get drops assigned to current technician
     */
    suspend fun getAssignedDrops(technicianId: String): Result<List<Drop>>

    /**
     * Assign drop to technician
     */
    suspend fun assignDrop(dropNumber: String, technicianId: String): Result<Unit>

    /**
     * Unassign drop from technician
     */
    suspend fun unassignDrop(dropNumber: String): Result<Unit>

    /**
     * Update drop status
     */
    suspend fun updateDropStatus(dropNumber: String, status: DropStatus): Result<Unit>

    /**
     * Validate proximity to drop location
     */
    suspend fun validateProximity(dropNumber: String, userLocation: Location): Result<ProximityResult>

    /**
     * Get drop location
     */
    suspend fun getDropLocation(dropNumber: String): Result<Location>

    /**
     * Search drops by various criteria
     */
    suspend fun searchDrops(
        query: String? = null,
        status: DropStatus? = null,
        priority: DropPriority? = null,
        assignedTo: String? = null
    ): Result<List<Drop>>

    /**
     * Get drops within radius of location
     */
    suspend fun getDropsInRadius(
        center: Location,
        radiusMeters: Double
    ): Result<List<Drop>>

    /**
     * Sync drops from remote server
     */
    suspend fun syncDrops(): Result<Unit>

    /**
     * Get drop statistics
     */
    suspend fun getDropStatistics(): Result<DropStatistics>

    /**
     * Observe drop changes (for real-time updates)
     */
    fun observeDropChanges(dropNumber: String): Flow<Drop>

    /**
     * Observe available drops changes
     */
    fun observeAvailableDrops(): Flow<List<Drop>>
}

/**
 * Drop statistics data class
 */
data class DropStatistics(
    val totalDrops: Int,
    val availableDrops: Int,
    val assignedDrops: Int,
    val completedDrops: Int,
    val failedDrops: Int
)