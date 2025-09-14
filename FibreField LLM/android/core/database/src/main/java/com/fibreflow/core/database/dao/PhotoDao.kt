package com.fibreflow.core.database.dao

import androidx.room.*
import com.fibreflow.core.database.entities.PhotoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Photo operations
 * Handles all database operations related to installation photos and validation
 */
@Dao
interface PhotoDao {

    /**
     * Insert a new photo
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    /**
     * Insert multiple photos
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>): List<Long>

    /**
     * Update an existing photo
     */
    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    /**
     * Update multiple photos
     */
    @Update
    suspend fun updatePhotos(photos: List<PhotoEntity>)

    /**
     * Delete a photo
     */
    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    /**
     * Delete multiple photos
     */
    @Delete
    suspend fun deletePhotos(photos: List<PhotoEntity>)

    /**
     * Get photo by ID
     */
    @Query("SELECT * FROM photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: String): PhotoEntity?

    /**
     * Get photo by ID as Flow
     */
    @Query("SELECT * FROM photos WHERE id = :photoId")
    fun getPhotoByIdFlow(photoId: String): Flow<PhotoEntity?>

    /**
     * Get all photos for an installation
     */
    @Query("SELECT * FROM photos WHERE installationId = :installationId ORDER BY sequenceNumber ASC, capturedAt ASC")
    suspend fun getPhotosByInstallation(installationId: String): List<PhotoEntity>

    /**
     * Get all photos for an installation as Flow
     */
    @Query("SELECT * FROM photos WHERE installationId = :installationId ORDER BY sequenceNumber ASC, capturedAt ASC")
    fun getPhotosByInstallationFlow(installationId: String): Flow<List<PhotoEntity>>

    /**
     * Get photos by step
     */
    @Query("SELECT * FROM photos WHERE installationId = :installationId AND stepName = :stepName ORDER BY sequenceNumber ASC")
    suspend fun getPhotosByStep(installationId: String, stepName: String): List<PhotoEntity>

    /**
     * Get photos requiring validation
     */
    @Query("SELECT * FROM photos WHERE validationStatus = 'PENDING' ORDER BY capturedAt ASC")
    suspend fun getPhotosRequiringValidation(): List<PhotoEntity>

    /**
     * Get photos requiring validation as Flow
     */
    @Query("SELECT * FROM photos WHERE validationStatus = 'PENDING' ORDER BY capturedAt ASC")
    fun getPhotosRequiringValidationFlow(): Flow<List<PhotoEntity>>

    /**
     * Get validated photos
     */
    @Query("SELECT * FROM photos WHERE validationStatus = 'PASSED' ORDER BY validatedAt DESC")
    suspend fun getValidatedPhotos(): List<PhotoEntity>

    /**
     * Get failed validation photos
     */
    @Query("SELECT * FROM photos WHERE validationStatus = 'FAILED' ORDER BY validatedAt DESC")
    suspend fun getFailedValidationPhotos(): List<PhotoEntity>

    /**
     * Update photo validation status
     */
    @Query("UPDATE photos SET validationStatus = :status, validatedAt = :timestamp, validationNotes = :notes WHERE id = :photoId")
    suspend fun updatePhotoValidationStatus(photoId: String, status: String, notes: String?, timestamp: Long = System.currentTimeMillis())

    /**
     * Update photo validation results
     */
    @Query("UPDATE photos SET validationStatus = :status, validatedAt = :timestamp, validationConfidence = :confidence, detectedObjects = :objects, extractedText = :text, validationNotes = :notes WHERE id = :photoId")
    suspend fun updatePhotoValidationResults(
        photoId: String,
        status: String,
        confidence: Float,
        objects: String?, // JSON string of detected objects
        text: String?,    // Extracted text
        notes: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Mark photo for sync
     */
    @Query("UPDATE photos SET needsSync = 1 WHERE id = :photoId")
    suspend fun markPhotoForSync(photoId: String)

    /**
     * Mark photo as synced
     */
    @Query("UPDATE photos SET needsSync = 0, lastSyncedAt = :timestamp WHERE id = :photoId")
    suspend fun markPhotoSynced(photoId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Get photos needing sync
     */
    @Query("SELECT * FROM photos WHERE needsSync = 1 ORDER BY capturedAt ASC")
    suspend fun getPhotosNeedingSync(): List<PhotoEntity>

    /**
     * Update photo file path
     */
    @Query("UPDATE photos SET filePath = :filePath, fileSizeBytes = :fileSize WHERE id = :photoId")
    suspend fun updatePhotoFilePath(photoId: String, filePath: String, fileSize: Long)

    /**
     * Update photo metadata
     */
    @Query("UPDATE photos SET latitude = :latitude, longitude = :longitude, altitude = :altitude, bearing = :bearing WHERE id = :photoId")
    suspend fun updatePhotoLocation(photoId: String, latitude: Double?, longitude: Double?, altitude: Double?, bearing: Float?)

    /**
     * Update photo quality metrics
     */
    @Query("UPDATE photos SET brightness = :brightness, sharpness = :sharpness, contrast = :contrast, qualityScore = :qualityScore WHERE id = :photoId")
    suspend fun updatePhotoQualityMetrics(photoId: String, brightness: Float?, sharpness: Float?, contrast: Float?, qualityScore: Float?)

    /**
     * Get photos by quality score range
     */
    @Query("SELECT * FROM photos WHERE qualityScore BETWEEN :minScore AND :maxScore ORDER BY qualityScore DESC")
    suspend fun getPhotosByQualityRange(minScore: Float, maxScore: Float): List<PhotoEntity>

    /**
     * Get low quality photos
     */
    @Query("SELECT * FROM photos WHERE qualityScore < 0.6 ORDER BY qualityScore ASC")
    suspend fun getLowQualityPhotos(): List<PhotoEntity>

    /**
     * Get photo count by validation status
     */
    @Query("SELECT COUNT(*) FROM photos WHERE validationStatus = :status")
    suspend fun getPhotoCountByValidationStatus(status: String): Int

    /**
     * Get total photo count
     */
    @Query("SELECT COUNT(*) FROM photos")
    suspend fun getTotalPhotoCount(): Int

    /**
     * Get photos captured within date range
     */
    @Query("SELECT * FROM photos WHERE capturedAt BETWEEN :startDate AND :endDate ORDER BY capturedAt DESC")
    suspend fun getPhotosInDateRange(startDate: Long, endDate: Long): List<PhotoEntity>

    /**
     * Delete photos older than cutoff date
     */
    @Query("DELETE FROM photos WHERE capturedAt < :cutoffDate")
    suspend fun deleteOldPhotos(cutoffDate: Long): Int

    /**
     * Get photos by technician
     */
    @Query("SELECT p.* FROM photos p INNER JOIN installations i ON p.installationId = i.id WHERE i.technicianId = :technicianId ORDER BY p.capturedAt DESC")
    suspend fun getPhotosByTechnician(technicianId: String): List<PhotoEntity>

    /**
     * Get average quality score by step
     */
    @Query("""
        SELECT stepName, AVG(qualityScore) as avgQuality, COUNT(*) as photoCount
        FROM photos
        WHERE qualityScore IS NOT NULL
        GROUP BY stepName
        ORDER BY avgQuality DESC
    """)
    suspend fun getAverageQualityByStep(): Map<String, Pair<Float, Int>>

    /**
     * Get validation accuracy statistics
     */
    @Query("""
        SELECT validationStatus, COUNT(*) as count,
               AVG(validationConfidence) as avgConfidence
        FROM photos
        WHERE validationStatus IS NOT NULL
        GROUP BY validationStatus
    """)
    suspend fun getValidationStatistics(): Map<String, Pair<Int, Float>>

    /**
     * Bulk update sync status
     */
    @Query("UPDATE photos SET needsSync = 0, lastSyncedAt = :timestamp WHERE id IN (:photoIds)")
    suspend fun markPhotosSynced(photoIds: List<String>, timestamp: Long = System.currentTimeMillis())

    /**
     * Get photos with detected objects containing specific text
     */
    @Query("SELECT * FROM photos WHERE detectedObjects LIKE '%' || :objectName || '%' ORDER BY capturedAt DESC")
    suspend fun searchPhotosByDetectedObject(objectName: String): List<PhotoEntity>

    /**
     * Get photos with extracted text containing specific text
     */
    @Query("SELECT * FROM photos WHERE extractedText LIKE '%' || :searchText || '%' ORDER BY capturedAt DESC")
    suspend fun searchPhotosByExtractedText(searchText: String): List<PhotoEntity>
}