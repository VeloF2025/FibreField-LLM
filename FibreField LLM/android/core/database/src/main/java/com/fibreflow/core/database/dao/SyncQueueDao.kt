package com.fibreflow.core.database.dao

import androidx.room.*
import com.fibreflow.core.database.entities.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Sync Queue operations
 * Handles all database operations related to offline data synchronization
 */
@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItem(item: SyncQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItems(items: List<SyncQueueEntity>): List<Long>

    @Update
    suspend fun updateSyncItem(item: SyncQueueEntity)

    @Delete
    suspend fun deleteSyncItem(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE id = :itemId")
    suspend fun getSyncItemById(itemId: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY priority DESC, createdAt ASC")
    suspend fun getPendingSyncItems(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY priority DESC, createdAt ASC")
    fun getPendingSyncItemsFlow(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'FAILED' ORDER BY retryCount ASC, createdAt ASC")
    suspend fun getFailedSyncItems(): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = :status, updatedAt = :timestamp WHERE id = :itemId")
    suspend fun updateSyncStatus(itemId: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastAttemptAt = :timestamp, updatedAt = :timestamp WHERE id = :itemId")
    suspend fun incrementRetryCount(itemId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED' AND updatedAt < :cutoffDate")
    suspend fun deleteCompletedItems(cutoffDate: Long): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    suspend fun getSyncItemCountByStatus(status: String): Int
}