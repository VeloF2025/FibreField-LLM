// 🟢 WORKING: Project entity with complete fields from PRD
package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing a fiber installation project
 * Contains project boundaries, statistics, and metadata
 */
@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["active"]),
        Index(value = ["project_name"]),
        Index(value = ["created_at"])
    ]
)
data class ProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "project_id")
    val projectId: Int,
    
    @ColumnInfo(name = "project_name")
    val projectName: String,
    
    /**
     * GeoJSON polygon defining project boundaries
     */
    @ColumnInfo(name = "boundary_polygon")
    val boundaryPolygon: String,
    
    /**
     * Total number of drops in the project
     */
    @ColumnInfo(name = "total_drops")
    val totalDrops: Int,
    
    /**
     * Number of completed installations
     */
    @ColumnInfo(name = "completed_drops")
    val completedDrops: Int = 0,
    
    /**
     * Number of active/in-progress installations
     */
    @ColumnInfo(name = "active_drops")
    val activeDrops: Int = 0,
    
    /**
     * Number of failed installations requiring remediation
     */
    @ColumnInfo(name = "failed_drops")
    val failedDrops: Int = 0,
    
    /**
     * Whether the project is currently active
     */
    @ColumnInfo(name = "active")
    val active: Boolean = true,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date,
    
    /**
     * Additional project metadata as JSON
     * Can include contractor info, deadlines, special requirements
     */
    @ColumnInfo(name = "metadata")
    val metadata: String? = null
) {
    
    /**
     * Calculate completion percentage
     */
    val completionPercentage: Float
        get() = if (totalDrops > 0) {
            (completedDrops.toFloat() / totalDrops) * 100f
        } else 0f
    
    /**
     * Get remaining drops to complete
     */
    val remainingDrops: Int
        get() = totalDrops - completedDrops - failedDrops
    
    /**
     * Check if project is nearing completion (>90%)
     */
    val isNearingCompletion: Boolean
        get() = completionPercentage >= 90f
    
    /**
     * Check if project has issues (failed drops > 5% of total)
     */
    val hasSignificantIssues: Boolean
        get() = totalDrops > 0 && (failedDrops.toFloat() / totalDrops) > 0.05f
}