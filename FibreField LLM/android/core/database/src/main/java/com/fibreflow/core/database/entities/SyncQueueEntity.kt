package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing items in the offline sync queue
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["created_at"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "sync_id")
    val syncId: Long,

    @ColumnInfo(name = "entity_type")
    val entityType: String, // "installation", "photo", etc.

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation")
    val operation: SyncOperation,

    @ColumnInfo(name = "data")
    val data: String, // JSON data

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_attempt")
    val lastAttempt: Date? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date
)

/**
 * Sync operation enum
 */
enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE
}

/**
 * Sync status enum
 */
enum class SyncStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}