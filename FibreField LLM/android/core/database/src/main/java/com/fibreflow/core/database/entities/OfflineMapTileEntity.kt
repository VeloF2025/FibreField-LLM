package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing cached offline map tiles
 */
@Entity(
    tableName = "offline_map_tiles",
    indices = [
        Index(value = ["zoom_level", "x", "y"]),
        Index(value = ["last_accessed"])
    ]
)
data class OfflineMapTileEntity(
    @PrimaryKey
    @ColumnInfo(name = "tile_id")
    val tileId: String, // "zoom_x_y"

    @ColumnInfo(name = "zoom_level")
    val zoomLevel: Int,

    @ColumnInfo(name = "x")
    val x: Int,

    @ColumnInfo(name = "y")
    val y: Int,

    @ColumnInfo(name = "tile_data")
    val tileData: ByteArray,

    @ColumnInfo(name = "file_size")
    val fileSize: Long,

    @ColumnInfo(name = "last_accessed")
    val lastAccessed: Date,

    @ColumnInfo(name = "created_at")
    val createdAt: Date
)