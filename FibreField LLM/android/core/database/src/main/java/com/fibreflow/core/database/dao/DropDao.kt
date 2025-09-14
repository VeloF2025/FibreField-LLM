package com.fibreflow.core.database.dao

import androidx.room.*
import com.fibreflow.core.database.entities.DropEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Drop operations
 * Handles all database operations related to fibre optic drops
 */
@Dao
interface DropDao {

    /**
     * Insert a new drop
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrop(drop: DropEntity): Long

    /**
     * Insert multiple drops
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrops(drops: List<DropEntity>): List<Long>

    /**
     * Update an existing drop
     */
    @Update
    suspend fun updateDrop(drop: DropEntity)

    /**
     * Update multiple drops
     */
    @Update
    suspend fun updateDrops(drops: List<DropEntity>)

    /**
     * Delete a drop
     */
    @Delete
    suspend fun deleteDrop(drop: DropEntity)

    /**
     * Delete multiple drops
     */
    @Delete
    suspend fun deleteDrops(drops: List<DropEntity>)

    /**
     * Get drop by ID
     */
    @Query("SELECT * FROM drops WHERE id = :dropId")
    suspend fun getDropById(dropId: String): DropEntity?

    /**
     * Get drop by ID as Flow
     */
    @Query("SELECT * FROM drops WHERE id = :dropId")
    fun getDropByIdFlow(dropId: String): Flow<DropEntity?>

    /**
     * Get all drops
     */
    @Query("SELECT * FROM drops ORDER BY createdAt DESC")
    suspend fun getAllDrops(): List<DropEntity>

    /**
     * Get all drops as Flow
     */
    @Query("SELECT * FROM drops ORDER BY createdAt DESC")
    fun getAllDropsFlow(): Flow<List<DropEntity>>

    /**
     * Get drops by status
     */
    @Query("SELECT * FROM drops WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun getDropsByStatus(status: String): List<DropEntity>

    /**
     * Get drops by status as Flow
     */
    @Query("SELECT * FROM drops WHERE status = :status ORDER BY updatedAt DESC")
    fun getDropsByStatusFlow(status: String): Flow<List<DropEntity>>

    /**
     * Get available drops (not assigned or in progress)
     */
    @Query("SELECT * FROM drops WHERE status IN ('AVAILABLE', 'PENDING') ORDER BY priority DESC, createdAt ASC")
    suspend fun getAvailableDrops(): List<DropEntity>

    /**
     * Get available drops as Flow
     */
    @Query("SELECT * FROM drops WHERE status IN ('AVAILABLE', 'PENDING') ORDER BY priority DESC, createdAt ASC")
    fun getAvailableDropsFlow(): Flow<List<DropEntity>>

    /**
     * Get drops within proximity (simplified - would use spatial queries in real implementation)
     */
    @Query("SELECT * FROM drops WHERE status IN ('AVAILABLE', 'PENDING') AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng ORDER BY priority DESC")
    suspend fun getDropsInProximity(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<DropEntity>

    /**
     * Update drop status
     */
    @Query("UPDATE drops SET status = :status, updatedAt = :timestamp WHERE id = :dropId")
    suspend fun updateDropStatus(dropId: String, status: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Assign drop to technician
     */
    @Query("UPDATE drops SET assignedTechnicianId = :technicianId, status = 'IN_PROGRESS', assignedAt = :timestamp, updatedAt = :timestamp WHERE id = :dropId")
    suspend fun assignDropToTechnician(dropId: String, technicianId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Unassign drop from technician
     */
    @Query("UPDATE drops SET assignedTechnicianId = NULL, status = 'AVAILABLE', assignedAt = NULL, updatedAt = :timestamp WHERE id = :dropId")
    suspend fun unassignDrop(dropId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark drop as completed
     */
    @Query("UPDATE drops SET status = 'COMPLETED', completedAt = :timestamp, updatedAt = :timestamp WHERE id = :dropId")
    suspend fun markDropCompleted(dropId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update drop location
     */
    @Query("UPDATE drops SET latitude = :latitude, longitude = :longitude, updatedAt = :timestamp WHERE id = :dropId")
    suspend fun updateDropLocation(dropId: String, latitude: Double, longitude: Double, timestamp: Long = System.currentTimeMillis())

    /**
     * Update drop priority
     */
    @Query("UPDATE drops SET priority = :priority, updatedAt = :timestamp WHERE id = :dropId")
    suspend fun updateDropPriority(dropId: String, priority: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Get drops assigned to technician
     */
    @Query("SELECT * FROM drops WHERE assignedTechnicianId = :technicianId ORDER BY priority DESC, assignedAt ASC")
    suspend fun getDropsAssignedToTechnician(technicianId: String): List<DropEntity>

    /**
     * Get drops assigned to technician as Flow
     */
    @Query("SELECT * FROM drops WHERE assignedTechnicianId = :technicianId ORDER BY priority DESC, assignedAt ASC")
    fun getDropsAssignedToTechnicianFlow(technicianId: String): Flow<List<DropEntity>>

    /**
     * Get drop count by status
     */
    @Query("SELECT COUNT(*) FROM drops WHERE status = :status")
    suspend fun getDropCountByStatus(status: String): Int

    /**
     * Get total drop count
     */
    @Query("SELECT COUNT(*) FROM drops")
    suspend fun getTotalDropCount(): Int

    /**
     * Mark drop for sync
     */
    @Query("UPDATE drops SET needsSync = 1, updatedAt = :timestamp WHERE id = :dropId")
    suspend fun markDropForSync(dropId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark drop as synced
     */
    @Query("UPDATE drops SET needsSync = 0, lastSyncedAt = :timestamp WHERE id = :dropId")
    suspend fun markDropSynced(dropId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Get drops needing sync
     */
    @Query("SELECT * FROM drops WHERE needsSync = 1 ORDER BY updatedAt ASC")
    suspend fun getDropsNeedingSync(): List<DropEntity>

    /**
     * Search drops by address
     */
    @Query("SELECT * FROM drops WHERE address LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun searchDropsByAddress(query: String): List<DropEntity>

    /**
     * Get drops by priority level
     */
    @Query("SELECT * FROM drops WHERE priority >= :minPriority ORDER BY priority DESC, createdAt ASC")
    suspend fun getDropsByMinPriority(minPriority: Int): List<DropEntity>

    /**
     * Get overdue drops (assigned but not completed within expected time)
     */
    @Query("SELECT * FROM drops WHERE status = 'IN_PROGRESS' AND assignedAt < :cutoffTime ORDER BY assignedAt ASC")
    suspend fun getOverdueDrops(cutoffTime: Long): List<DropEntity>

    /**
     * Update drop notes
     */
    @Query("UPDATE drops SET notes = :notes, updatedAt = :timestamp WHERE id = :dropId")
    suspend fun updateDropNotes(dropId: String, notes: String?, timestamp: Long = System.currentTimeMillis())

    /**
     * Get drops created within date range
     */
    @Query("SELECT * FROM drops WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    suspend fun getDropsInDateRange(startDate: Long, endDate: Long): List<DropEntity>

    /**
     * Bulk update sync status
     */
    @Query("UPDATE drops SET needsSync = 0, lastSyncedAt = :timestamp WHERE id IN (:dropIds)")
    suspend fun markDropsSynced(dropIds: List<String>, timestamp: Long = System.currentTimeMillis())

    /**
     * Get drop statistics
     */
    @Query("""
        SELECT status, COUNT(*) as count
        FROM drops
        GROUP BY status
        ORDER BY count DESC
    """)
    suspend fun getDropStatusStatistics(): Map<String, Int>

    /**
     * Get average completion time by priority
     */
    @Query("""
        SELECT priority, AVG(completedAt - assignedAt) as avgCompletionTime
        FROM drops
        WHERE status = 'COMPLETED' AND assignedAt IS NOT NULL AND completedAt IS NOT NULL
        GROUP BY priority
        ORDER BY priority DESC
    """)
    suspend fun getAverageCompletionTimeByPriority(): Map<Int, Long>
}