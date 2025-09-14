package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing user sessions
 */
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["technician_id"]),
        Index(value = ["is_active"]),
        Index(value = ["created_at"])
    ]
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "technician_id")
    val technicianId: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "last_activity")
    val lastActivity: Date,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Date
)