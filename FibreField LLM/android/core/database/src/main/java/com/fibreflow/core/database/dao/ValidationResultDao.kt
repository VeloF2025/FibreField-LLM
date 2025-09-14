package com.fibreflow.core.database.dao

import androidx.room.*
import com.fibreflow.core.database.entities.ValidationResultEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Validation Result operations
 * Handles all database operations related to AI validation results
 */
@Dao
interface ValidationResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValidationResult(result: ValidationResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValidationResults(results: List<ValidationResultEntity>): List<Long>

    @Update
    suspend fun updateValidationResult(result: ValidationResultEntity)

    @Delete
    suspend fun deleteValidationResult(result: ValidationResultEntity)

    @Query("SELECT * FROM validation_results WHERE id = :resultId")
    suspend fun getValidationResultById(resultId: String): ValidationResultEntity?

    @Query("SELECT * FROM validation_results WHERE photoId = :photoId ORDER BY timestamp DESC")
    suspend fun getValidationResultsByPhoto(photoId: String): List<ValidationResultEntity>

    @Query("SELECT * FROM validation_results WHERE installationId = :installationId ORDER BY timestamp DESC")
    suspend fun getValidationResultsByInstallation(installationId: String): List<ValidationResultEntity>

    @Query("SELECT * FROM validation_results WHERE validationStatus = :status ORDER BY timestamp DESC")
    suspend fun getValidationResultsByStatus(status: String): List<ValidationResultEntity>

    @Query("SELECT COUNT(*) FROM validation_results WHERE validationStatus = :status")
    suspend fun getValidationResultCountByStatus(status: String): Int

    @Query("DELETE FROM validation_results WHERE timestamp < :cutoffDate")
    suspend fun deleteOldValidationResults(cutoffDate: Long): Int
}