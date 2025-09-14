package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing app configuration settings
 */
@Entity(
    tableName = "configuration",
    indices = [
        Index(value = ["config_key"]),
        Index(value = ["updated_at"])
    ]
)
data class ConfigurationEntity(
    @PrimaryKey
    @ColumnInfo(name = "config_key")
    val configKey: String,

    @ColumnInfo(name = "config_value")
    val configValue: String,

    @ColumnInfo(name = "config_type")
    val configType: String, // "string", "int", "boolean", "json"

    @ColumnInfo(name = "is_encrypted")
    val isEncrypted: Boolean = false,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date,

    @ColumnInfo(name = "updated_by")
    val updatedBy: String?
)