package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing AI validation results
 */
@Entity(
    tableName = "validation_results",
    indices = [
        Index(value = ["photo_id"]),
        Index(value = ["validation_type"]),
        Index(value = ["created_at"])
    ]
)
data class ValidationResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "validation_id")
    val validationId: Long,

    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "validation_type")
    val validationType: String, // "light_detection", "barcode", "quality"

    @ColumnInfo(name = "is_valid")
    val isValid: Boolean,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "validation_data")
    val validationData: String, // JSON with detailed results

    @ColumnInfo(name = "created_at")
    val createdAt: Date
)