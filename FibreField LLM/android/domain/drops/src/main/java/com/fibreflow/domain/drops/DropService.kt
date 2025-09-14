package com.fibreflow.domain.drops

import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.drops.entities.Drop
import com.fibreflow.domain.drops.entities.DropPriority
import com.fibreflow.domain.drops.entities.DropStatistics
import com.fibreflow.domain.drops.entities.DropStatus
import com.fibreflow.domain.drops.repositories.DropRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service class for drop management operations
 * Provides high-level business logic for drop operations
 */
@Singleton
class DropService @Inject constructor(
    private val dropRepository: DropRepository
) {

    /**
     * Get all available drops for assignment
     */
    suspend fun getAvailableDrops(): Result<List<Drop>> {
        return dropRepository.getAvailableDrops()
    }

    /**
     * Get drop details by drop number
     */
    suspend fun getDropDetails(dropNumber: String): Result<Drop> {
        return dropRepository.getDropByNumber(dropNumber)
    }

    /**
     * Assign drop to technician
     */
    suspend fun assignDropToTechnician(dropNumber: String, technicianId: String): Result<Unit> {
        return try {
            // First check if drop is available
            val dropResult = dropRepository.getDropByNumber(dropNumber)
            if (dropResult is Result.Error) {
                return dropResult
            }

            val drop = (dropResult as Result.Success).data
            if (!drop.isAvailable) {
                return Result.Error(Exception("Drop is not available for assignment"))
            }

            // Assign the drop
            val assignResult = dropRepository.assignDrop(dropNumber, technicianId)
            if (assignResult is Result.Success) {
                // Update status to ASSIGNED
                dropRepository.updateDropStatus(dropNumber, DropStatus.ASSIGNED)
            } else {
                assignResult
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Start installation for assigned drop
     */
    suspend fun startInstallation(dropNumber: String, technicianId: String): Result<Unit> {
        return try {
            // Verify technician is assigned to this drop
            val assignedDrops = dropRepository.getAssignedDrops(technicianId)
            if (assignedDrops is Result.Error) {
                return assignedDrops
            }

            val isAssigned = (assignedDrops as Result.Success).data
                .any { it.dropNumber == dropNumber && it.status == DropStatus.ASSIGNED }

            if (!isAssigned) {
                return Result.Error(Exception("Technician is not assigned to this drop"))
            }

            // Update status to IN_PROGRESS
            dropRepository.updateDropStatus(dropNumber, DropStatus.IN_PROGRESS)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Complete drop installation
     */
    suspend fun completeInstallation(dropNumber: String): Result<Unit> {
        return dropRepository.updateDropStatus(dropNumber, DropStatus.COMPLETED)
    }

    /**
     * Mark drop as failed
     */
    suspend fun failInstallation(dropNumber: String): Result<Unit> {
        return dropRepository.updateDropStatus(dropNumber, DropStatus.FAILED)
    }

    /**
     * Get drops assigned to technician
     */
    suspend fun getTechnicianDrops(technicianId: String): Result<List<Drop>> {
        return dropRepository.getAssignedDrops(technicianId)
    }

    /**
     * Search drops with filters
     */
    suspend fun searchDrops(
        query: String? = null,
        status: DropStatus? = null,
        priority: DropPriority? = null
    ): Result<List<Drop>> {
        return dropRepository.searchDrops(query, status, priority, null)
    }

    /**
     * Get drop statistics
     */
    suspend fun getDropStatistics(): Result<DropStatistics> {
        return dropRepository.getDropStatistics()
    }

    /**
     * Observe drop changes for real-time updates
     */
    fun observeDrop(dropNumber: String): Flow<Drop> {
        return dropRepository.observeDropChanges(dropNumber)
    }

    /**
     * Observe available drops for real-time updates
     */
    fun observeAvailableDrops(): Flow<List<Drop>> {
        return dropRepository.observeAvailableDrops()
    }
}