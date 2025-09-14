package com.fibreflow.core.database.entities

import androidx.room.*
import com.fibreflow.core.database.converters.DateConverters
import java.util.*

/**
 * Entity for tracking data synchronization operations
 * Stores sync metadata and conflict resolution information
 */
@Entity(
    tableName = "sync_operations",
    indices = [
        Index(value = ["entity_type", "entity_id"]),
        Index(value = ["sync_status"]),
        Index(value = ["last_sync_attempt"]),
        Index(value = ["priority"]),
        Index(value = ["entity_type", "sync_status"])
    ]
)
@TypeConverters(DateConverters::class)
data class SyncEntity(
    @PrimaryKey
    @ColumnInfo(name = "sync_id")
    val syncId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String, // "installation", "photo", "drop", etc.

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation_type")
    val operationType: SyncOperationType,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "priority")
    val priority: Int = 1, // 1=low, 5=high

    @ColumnInfo(name = "local_version")
    val localVersion: Long,

    @ColumnInfo(name = "server_version")
    val serverVersion: Long? = null,

    @ColumnInfo(name = "local_data")
    val localData: String? = null, // JSON string

    @ColumnInfo(name = "server_data")
    val serverData: String? = null, // JSON string

    @ColumnInfo(name = "conflict_resolution")
    val conflictResolution: ConflictResolution? = null,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 3,

    @ColumnInfo(name = "last_sync_attempt")
    val lastSyncAttempt: Date? = null,

    @ColumnInfo(name = "next_sync_attempt")
    val nextSyncAttempt: Date? = null,

    @ColumnInfo(name = "sync_error")
    val syncError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date()
) {

    /**
     * Check if sync operation can be retried
     */
    val canRetry: Boolean
        get() = retryCount < maxRetries && syncStatus != SyncStatus.SYNCED

    /**
     * Check if operation is ready for sync
     */
    val isReadyForSync: Boolean
        get() = syncStatus in listOf(SyncStatus.PENDING, SyncStatus.FAILED)

    /**
     * Check if operation has conflicts
     */
    val hasConflicts: Boolean
        get() = syncStatus == SyncStatus.CONFLICT

    /**
     * Get time since last sync attempt
     */
    val timeSinceLastAttempt: Long?
        get() = lastSyncAttempt?.let { Date().time - it.time }

    /**
     * Check if operation is overdue for sync
     */
    val isOverdue: Boolean
        get() = nextSyncAttempt?.let { Date().after(it) } ?: true

    /**
     * Increment retry count
     */
    fun incrementRetry(): SyncEntity {
        return copy(
            retryCount = retryCount + 1,
            lastSyncAttempt = Date(),
            updatedAt = Date()
        )
    }

    /**
     * Mark as synced
     */
    fun markSynced(serverVersion: Long? = null): SyncEntity {
        return copy(
            syncStatus = SyncStatus.SYNCED,
            serverVersion = serverVersion,
            lastSyncAttempt = Date(),
            syncError = null,
            updatedAt = Date()
        )
    }

    /**
     * Mark as failed
     */
    fun markFailed(error: String): SyncEntity {
        return copy(
            syncStatus = SyncStatus.FAILED,
            syncError = error,
            lastSyncAttempt = Date(),
            updatedAt = Date()
        )
    }

    /**
     * Mark as conflicted
     */
    fun markConflicted(serverData: String, serverVersion: Long): SyncEntity {
        return copy(
            syncStatus = SyncStatus.CONFLICT,
            serverData = serverData,
            serverVersion = serverVersion,
            lastSyncAttempt = Date(),
            updatedAt = Date()
        )
    }

    /**
     * Resolve conflict
     */
    fun resolveConflict(resolution: ConflictResolution): SyncEntity {
        return copy(
            conflictResolution = resolution,
            syncStatus = SyncStatus.PENDING,
            updatedAt = Date()
        )
    }
}

/**
 * Sync operation types
 */
enum class SyncOperationType {
    CREATE,
    UPDATE,
    DELETE,
    BULK_UPDATE
}

/**
 * Conflict resolution strategies
 */
enum class ConflictResolution {
    USE_LOCAL,      // Use local data
    USE_SERVER,     // Use server data
    MERGE,          // Merge both (if possible)
    MANUAL          // Requires manual resolution
}

/**
 * Sync statistics entity
 */
@Entity(tableName = "sync_statistics")
@TypeConverters(DateConverters::class)
data class SyncStatisticsEntity(
    @PrimaryKey
    @ColumnInfo(name = "stats_id")
    val statsId: String,

    @ColumnInfo(name = "period_start")
    val periodStart: Date,

    @ColumnInfo(name = "period_end")
    val periodEnd: Date,

    @ColumnInfo(name = "total_operations")
    val totalOperations: Int,

    @ColumnInfo(name = "successful_operations")
    val successfulOperations: Int,

    @ColumnInfo(name = "failed_operations")
    val failedOperations: Int,

    @ColumnInfo(name = "conflicted_operations")
    val conflictedOperations: Int,

    @ColumnInfo(name = "average_sync_time_ms")
    val averageSyncTimeMs: Long,

    @ColumnInfo(name = "data_uploaded_bytes")
    val dataUploadedBytes: Long,

    @ColumnInfo(name = "data_downloaded_bytes")
    val dataDownloadedBytes: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date()
) {

    val successRate: Double
        get() = if (totalOperations > 0) successfulOperations.toDouble() / totalOperations else 0.0

    val failureRate: Double
        get() = 1.0 - successRate
}

/**
 * Sync queue entity for managing sync operations
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["entity_type"]),
        Index(value = ["priority"]),
        Index(value = ["status"]),
        Index(value = ["next_attempt"])
    ]
)
@TypeConverters(DateConverters::class)
data class SyncQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "queue_id")
    val queueId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation")
    val operation: String,

    @ColumnInfo(name = "data")
    val data: String, // JSON

    @ColumnInfo(name = "priority")
    val priority: Int = 1,

    @ColumnInfo(name = "status")
    val status: String = "PENDING",

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 3,

    @ColumnInfo(name = "last_attempt")
    val lastAttempt: Date? = null,

    @ColumnInfo(name = "next_attempt")
    val nextAttempt: Date? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date()
)