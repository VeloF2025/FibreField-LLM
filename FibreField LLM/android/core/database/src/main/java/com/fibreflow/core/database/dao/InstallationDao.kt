package com.fibreflow.core.database.dao

import androidx.room.*
import com.fibreflow.core.database.entities.InstallationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Installation operations
 * Handles all database operations related to fibre optic installations
 */
@Dao
interface InstallationDao {

    /**
     * Insert a new installation
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallation(installation: InstallationEntity): Long

    /**
     * Insert multiple installations
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallations(installations: List<InstallationEntity>): List<Long>

    /**
     * Update an existing installation
     */
    @Update
    suspend fun updateInstallation(installation: InstallationEntity)

    /**
     * Update multiple installations
     */
    @Update
    suspend fun updateInstallations(installations: List<InstallationEntity>)

    /**
     * Delete an installation
     */
    @Delete
    suspend fun deleteInstallation(installation: InstallationEntity)

    /**
     * Delete multiple installations
     */
    @Delete
    suspend fun deleteInstallations(installations: List<InstallationEntity>)

    /**
     * Get installation by ID
     */
    @Query("SELECT * FROM installations WHERE id = :installationId")
    suspend fun getInstallationById(installationId: String): InstallationEntity?

    /**
     * Get installation by ID as Flow for reactive updates
     */
    @Query("SELECT * FROM installations WHERE id = :installationId")
    fun getInstallationByIdFlow(installationId: String): Flow<InstallationEntity?>

    /**
     * Get all installations for a technician
     */
    @Query("SELECT * FROM installations WHERE technicianId = :technicianId ORDER BY createdAt DESC")
    suspend fun getInstallationsByTechnician(technicianId: String): List<InstallationEntity>

    /**
     * Get all installations for a technician as Flow
     */
    @Query("SELECT * FROM installations WHERE technicianId = :technicianId ORDER BY createdAt DESC")
    fun getInstallationsByTechnicianFlow(technicianId: String): Flow<List<InstallationEntity>>

    /**
     * Get installations by drop ID
     */
    @Query("SELECT * FROM installations WHERE dropId = :dropId ORDER BY createdAt DESC")
    suspend fun getInstallationsByDrop(dropId: String): List<InstallationEntity>

    /**
     * Get installations by status
     */
    @Query("SELECT * FROM installations WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun getInstallationsByStatus(status: String): List<InstallationEntity>

    /**
     * Get installations by status as Flow
     */
    @Query("SELECT * FROM installations WHERE status = :status ORDER BY updatedAt DESC")
    fun getInstallationsByStatusFlow(status: String): Flow<List<InstallationEntity>>

    /**
     * Get pending installations (not completed)
     */
    @Query("SELECT * FROM installations WHERE status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY createdAt ASC")
    suspend fun getPendingInstallations(): List<InstallationEntity>

    /**
     * Get pending installations as Flow
     */
    @Query("SELECT * FROM installations WHERE status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY createdAt ASC")
    fun getPendingInstallationsFlow(): Flow<List<InstallationEntity>>

    /**
     * Get completed installations within date range
     */
    @Query("SELECT * FROM installations WHERE status = 'COMPLETED' AND completedAt BETWEEN :startDate AND :endDate ORDER BY completedAt DESC")
    suspend fun getCompletedInstallationsInRange(startDate: Long, endDate: Long): List<InstallationEntity>

    /**
     * Get installations requiring sync
     */
    @Query("SELECT * FROM installations WHERE needsSync = 1 ORDER BY updatedAt ASC")
    suspend fun getInstallationsNeedingSync(): List<InstallationEntity>

    /**
     * Mark installation as needing sync
     */
    @Query("UPDATE installations SET needsSync = 1, updatedAt = :timestamp WHERE id = :installationId")
    suspend fun markInstallationForSync(installationId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark installation as synced
     */
    @Query("UPDATE installations SET needsSync = 0, lastSyncedAt = :timestamp WHERE id = :installationId")
    suspend fun markInstallationSynced(installationId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update installation status
     */
    @Query("UPDATE installations SET status = :status, updatedAt = :timestamp WHERE id = :installationId")
    suspend fun updateInstallationStatus(installationId: String, status: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update installation progress
     */
    @Query("UPDATE installations SET currentStep = :currentStep, progress = :progress, updatedAt = :timestamp WHERE id = :installationId")
    suspend fun updateInstallationProgress(installationId: String, currentStep: String, progress: Float, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark installation as completed
     */
    @Query("UPDATE installations SET status = 'COMPLETED', completedAt = :timestamp, updatedAt = :timestamp WHERE id = :installationId")
    suspend fun markInstallationCompleted(installationId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Get installation count by status
     */
    @Query("SELECT COUNT(*) FROM installations WHERE status = :status")
    suspend fun getInstallationCountByStatus(status: String): Int

    /**
     * Get total installation count
     */
    @Query("SELECT COUNT(*) FROM installations")
    suspend fun getTotalInstallationCount(): Int

    /**
     * Get installations created within date range
     */
    @Query("SELECT * FROM installations WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    suspend fun getInstallationsInDateRange(startDate: Long, endDate: Long): List<InstallationEntity>

    /**
     * Delete old completed installations (cleanup)
     */
    @Query("DELETE FROM installations WHERE status = 'COMPLETED' AND completedAt < :cutoffDate")
    suspend fun deleteOldCompletedInstallations(cutoffDate: Long): Int

    /**
     * Search installations by equipment type
     */
    @Query("SELECT * FROM installations WHERE equipmentType LIKE '%' || :equipmentType || '%' ORDER BY createdAt DESC")
    suspend fun searchInstallationsByEquipment(equipmentType: String): List<InstallationEntity>

    /**
     * Get installations with validation issues
     */
    @Query("SELECT * FROM installations WHERE hasValidationIssues = 1 ORDER BY updatedAt DESC")
    suspend fun getInstallationsWithValidationIssues(): List<InstallationEntity>

    /**
     * Update validation issues flag
     */
    @Query("UPDATE installations SET hasValidationIssues = :hasIssues, updatedAt = :timestamp WHERE id = :installationId")
    suspend fun updateValidationIssuesFlag(installationId: String, hasIssues: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * Get average installation time by equipment type
     */
    @Query("""
        SELECT equipmentType, AVG(completedAt - createdAt) as avgTime
        FROM installations
        WHERE status = 'COMPLETED' AND equipmentType IS NOT NULL
        GROUP BY equipmentType
        ORDER BY avgTime ASC
    """)
    suspend fun getAverageInstallationTimeByEquipment(): Map<String, Long>

    /**
     * Bulk update sync status
     */
    @Query("UPDATE installations SET needsSync = 0, lastSyncedAt = :timestamp WHERE id IN (:installationIds)")
    suspend fun markInstallationsSynced(installationIds: List<String>, timestamp: Long = System.currentTimeMillis())
}