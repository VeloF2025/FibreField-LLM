package com.fibreflow.infrastructure.sync

import com.fibreflow.core.common.result.Result
import com.fibreflow.core.network.api.InstallationAPI
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for handling photo uploads to the server
 * Manages single and batch photo uploads with retry logic
 */
@Singleton
class PhotoUploadService @Inject constructor(
    private val installationApi: InstallationAPI,
    private val offlineQueue: OfflineQueue
) {

    companion object {
        const val MAX_RETRY_ATTEMPTS = 3
        const val MAX_FILE_SIZE_MB = 10
        const val SUPPORTED_IMAGE_TYPES = arrayOf("image/jpeg", "image/png", "image/webp")
    }

    /**
     * Upload single photo for installation
     */
    suspend fun uploadPhoto(
        installationId: String,
        photoFile: File,
        stepName: String,
        sequenceNumber: Int,
        latitude: Double? = null,
        longitude: Double? = null,
        notes: String? = null
    ): Result<PhotoUploadResult> {
        return try {
            Timber.d("Uploading photo for installation: $installationId, step: $stepName")

            // Validate photo file
            val validationResult = validatePhotoFile(photoFile)
            if (validationResult is Result.Error) {
                return validationResult
            }

            // Create multipart request
            val multipartBody = createPhotoMultipart(
                photoFile = photoFile,
                stepName = stepName,
                sequenceNumber = sequenceNumber,
                latitude = latitude,
                longitude = longitude,
                notes = notes
            )

            // Attempt upload
            val response = installationApi.uploadPhoto(
                installationId = installationId,
                photo = multipartBody
            )

            if (response.isSuccessful) {
                val uploadResponse = response.body()
                if (uploadResponse != null) {
                    Timber.d("Photo uploaded successfully: ${uploadResponse.photoId}")
                    Result.Success(
                        PhotoUploadResult(
                            success = true,
                            photoId = uploadResponse.photoId,
                            uploadUrl = uploadResponse.uploadUrl,
                            thumbnailUrl = uploadResponse.thumbnailUrl
                        )
                    )
                } else {
                    Result.Error(Exception("Upload response was null"))
                }
            } else {
                val errorMsg = "Upload failed: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)

                // Queue for retry
                queuePhotoForRetry(installationId, photoFile, stepName, sequenceNumber, latitude, longitude, notes)

                Result.Error(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error uploading photo")
            Result.Error(e)
        }
    }

    /**
     * Upload multiple photos in batch
     */
    suspend fun uploadPhotosBatch(
        installationId: String,
        photos: List<PhotoUploadData>
    ): Result<BatchUploadResult> {
        return try {
            Timber.d("Uploading ${photos.size} photos in batch for installation: $installationId")

            // Validate all photos
            val validationErrors = mutableListOf<String>()
            val validPhotos = mutableListOf<PhotoUploadData>()

            photos.forEach { photo ->
                val validation = validatePhotoFile(photo.file)
                if (validation is Result.Success) {
                    validPhotos.add(photo)
                } else {
                    validationErrors.add("${photo.file.name}: ${(validation as Result.Error).exception.message}")
                }
            }

            if (validPhotos.isEmpty()) {
                return Result.Error(Exception("No valid photos to upload: ${validationErrors.joinToString(", ")}"))
            }

            // Create batch multipart request
            val multipartParts = validPhotos.map { photo ->
                createPhotoMultipart(
                    photoFile = photo.file,
                    stepName = photo.stepName,
                    sequenceNumber = photo.sequenceNumber,
                    latitude = photo.latitude,
                    longitude = photo.longitude,
                    notes = photo.notes
                )
            }

            val metadataJson = createBatchMetadataJson(validPhotos)
            val metadataBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())

            // Attempt batch upload
            val response = installationApi.uploadPhotosBatch(
                installationId = installationId,
                photos = multipartParts,
                metadata = metadataBody
            )

            if (response.isSuccessful) {
                val batchResponse = response.body()
                if (batchResponse != null) {
                    Timber.d("Batch upload completed: ${batchResponse.uploaded} uploaded, ${batchResponse.failed} failed")

                    Result.Success(
                        BatchUploadResult(
                            totalPhotos = photos.size,
                            uploaded = batchResponse.uploaded,
                            failed = batchResponse.failed + validationErrors.size,
                            results = batchResponse.results.map { result ->
                                PhotoUploadResult(
                                    success = result.success,
                                    photoId = result.photoId,
                                    error = result.error
                                )
                            },
                            validationErrors = validationErrors
                        )
                    )
                } else {
                    Result.Error(Exception("Batch upload response was null"))
                }
            } else {
                val errorMsg = "Batch upload failed: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                Result.Error(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in batch photo upload")
            Result.Error(e)
        }
    }

    /**
     * Retry failed photo uploads
     */
    suspend fun retryFailedUploads(installationId: String): Result<Int> {
        return try {
            val queuedPhotos = offlineQueue.getQueuedItems(installationId)
                .filterIsInstance<PhotoUploadData>()

            if (queuedPhotos.isEmpty()) {
                return Result.Success(0)
            }

            var successCount = 0
            var failureCount = 0

            for (photo in queuedPhotos) {
                val result = uploadPhoto(
                    installationId = installationId,
                    photoFile = photo.file,
                    stepName = photo.stepName,
                    sequenceNumber = photo.sequenceNumber,
                    latitude = photo.latitude,
                    longitude = photo.longitude,
                    notes = photo.notes
                )

                if (result is Result.Success) {
                    offlineQueue.removeFromQueue(photo)
                    successCount++
                } else {
                    failureCount++
                }
            }

            Timber.d("Retry completed: $successCount successful, $failureCount failed")
            Result.Success(successCount)

        } catch (e: Exception) {
            Timber.e(e, "Error retrying photo uploads")
            Result.Error(e)
        }
    }

    /**
     * Validate photo file before upload
     */
    private fun validatePhotoFile(file: File): Result<Unit> {
        // Check if file exists
        if (!file.exists()) {
            return Result.Error(Exception("Photo file does not exist"))
        }

        // Check file size
        val fileSizeMB = file.length() / (1024.0 * 1024.0)
        if (fileSizeMB > MAX_FILE_SIZE_MB) {
            return Result.Error(Exception("Photo file too large: ${fileSizeMB}MB (max ${MAX_FILE_SIZE_MB}MB)"))
        }

        // Check file type
        val mimeType = getMimeType(file)
        if (mimeType !in SUPPORTED_IMAGE_TYPES) {
            return Result.Error(Exception("Unsupported file type: $mimeType"))
        }

        return Result.Success(Unit)
    }

    /**
     * Create multipart body for photo upload
     */
    private fun createPhotoMultipart(
        photoFile: File,
        stepName: String,
        sequenceNumber: Int,
        latitude: Double?,
        longitude: Double?,
        notes: String?
    ): MultipartBody.Part {
        val requestFile = photoFile.asRequestBody(getMimeType(photoFile).toMediaTypeOrNull())
        val photoPart = MultipartBody.Part.createFormData("photo", photoFile.name, requestFile)

        // Note: In a real implementation, you'd need to modify the API to accept
        // these as separate parts or combine them into the photo part
        return photoPart
    }

    /**
     * Create JSON metadata for batch upload
     */
    private fun createBatchMetadataJson(photos: List<PhotoUploadData>): String {
        val metadataList = photos.map { photo ->
            mapOf(
                "filename" to photo.file.name,
                "stepName" to photo.stepName,
                "sequenceNumber" to photo.sequenceNumber,
                "latitude" to photo.latitude,
                "longitude" to photo.longitude,
                "notes" to photo.notes
            )
        }

        // Simple JSON creation - in production, use a proper JSON library
        return metadataList.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { metadata ->
            metadata.entries.joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { "\"${it.key}\":\"${it.value}\"" }
        }
    }

    /**
     * Queue photo for retry
     */
    private fun queuePhotoForRetry(
        installationId: String,
        photoFile: File,
        stepName: String,
        sequenceNumber: Int,
        latitude: Double?,
        longitude: Double?,
        notes: String?
    ) {
        try {
            val photoData = PhotoUploadData(
                file = photoFile,
                stepName = stepName,
                sequenceNumber = sequenceNumber,
                latitude = latitude,
                longitude = longitude,
                notes = notes
            )
            offlineQueue.addToQueue(photoData)
            Timber.d("Photo queued for retry: ${photoFile.name}")
        } catch (e: Exception) {
            Timber.e(e, "Error queuing photo for retry")
        }
    }

    /**
     * Get MIME type from file
     */
    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}

/**
 * Data class for photo upload information
 */
data class PhotoUploadData(
    val file: File,
    val stepName: String,
    val sequenceNumber: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null
)

/**
 * Result of photo upload operation
 */
data class PhotoUploadResult(
    val success: Boolean,
    val photoId: String? = null,
    val uploadUrl: String? = null,
    val thumbnailUrl: String? = null,
    val error: String? = null
)

/**
 * Result of batch photo upload operation
 */
data class BatchUploadResult(
    val totalPhotos: Int,
    val uploaded: Int,
    val failed: Int,
    val results: List<PhotoUploadResult>,
    val validationErrors: List<String> = emptyList()
)