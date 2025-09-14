package com.fibreflow.core.network.api

import com.fibreflow.core.network.models.response.InstallationResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Installation Management API endpoints
 * Handles installation creation, updates, and photo uploads
 */
interface InstallationAPI {

    /**
     * Create new installation
     */
    @POST("installations")
    suspend fun createInstallation(
        @Body installation: InstallationCreateRequest
    ): Response<InstallationResponse>

    /**
     * Get installation details
     */
    @GET("installations/{installationId}")
    suspend fun getInstallation(
        @Path("installationId") installationId: String
    ): Response<InstallationResponse>

    /**
     * Update installation progress
     */
    @PUT("installations/{installationId}/progress")
    suspend fun updateProgress(
        @Path("installationId") installationId: String,
        @Body progress: InstallationProgressUpdate
    ): Response<InstallationResponse>

    /**
     * Update installation status
     */
    @PUT("installations/{installationId}/status")
    suspend fun updateStatus(
        @Path("installationId") installationId: String,
        @Body statusUpdate: InstallationStatusUpdate
    ): Response<InstallationResponse>

    /**
     * Complete installation
     */
    @POST("installations/{installationId}/complete")
    suspend fun completeInstallation(
        @Path("installationId") installationId: String,
        @Body completion: InstallationCompletion
    ): Response<InstallationResponse>

    /**
     * Get technician's installations
     */
    @GET("technicians/{technicianId}/installations")
    suspend fun getTechnicianInstallations(
        @Path("technicianId") technicianId: String,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<InstallationResponse>>

    /**
     * Get installations by drop
     */
    @GET("drops/{dropId}/installations")
    suspend fun getDropInstallations(
        @Path("dropId") dropId: String,
        @Query("limit") limit: Int = 10
    ): Response<List<InstallationResponse>>

    /**
     * Upload installation photo
     */
    @Multipart
    @POST("installations/{installationId}/photos")
    suspend fun uploadPhoto(
        @Path("installationId") installationId: String,
        @Part photo: MultipartBody.Part,
        @Part("step_name") stepName: RequestBody,
        @Part("sequence_number") sequenceNumber: RequestBody,
        @Part("latitude") latitude: RequestBody? = null,
        @Part("longitude") longitude: RequestBody? = null,
        @Part("notes") notes: RequestBody? = null
    ): Response<PhotoUploadResponse>

    /**
     * Upload multiple photos in batch
     */
    @Multipart
    @POST("installations/{installationId}/photos/batch")
    suspend fun uploadPhotosBatch(
        @Path("installationId") installationId: String,
        @Part photos: List<MultipartBody.Part>,
        @Part("metadata") metadata: RequestBody // JSON string with photo metadata
    ): Response<BatchUploadResponse>

    /**
     * Get installation photos
     */
    @GET("installations/{installationId}/photos")
    suspend fun getInstallationPhotos(
        @Path("installationId") installationId: String,
        @Query("step_name") stepName: String? = null
    ): Response<List<PhotoResponse>>

    /**
     * Delete installation photo
     */
    @DELETE("installations/{installationId}/photos/{photoId}")
    suspend fun deletePhoto(
        @Path("installationId") installationId: String,
        @Path("photoId") photoId: String
    ): Response<DeleteResponse>

    /**
     * Validate installation photos
     */
    @POST("installations/{installationId}/validate")
    suspend fun validateInstallation(
        @Path("installationId") installationId: String,
        @Body validation: InstallationValidationRequest
    ): Response<InstallationValidationResponse>

    /**
     * Get installation validation results
     */
    @GET("installations/{installationId}/validation")
    suspend fun getValidationResults(
        @Path("installationId") installationId: String
    ): Response<InstallationValidationResponse>

    /**
     * Report installation issue
     */
    @POST("installations/{installationId}/issues")
    suspend fun reportIssue(
        @Path("installationId") installationId: String,
        @Body issue: InstallationIssueReport
    ): Response<IssueReportResponse>

    /**
     * Get installation issues
     */
    @GET("installations/{installationId}/issues")
    suspend fun getInstallationIssues(
        @Path("installationId") installationId: String
    ): Response<List<InstallationIssue>>

    /**
     * Sync installation data
     */
    @POST("installations/{installationId}/sync")
    suspend fun syncInstallation(
        @Path("installationId") installationId: String,
        @Body syncData: InstallationSyncData
    ): Response<SyncResponse>

    /**
     * Get installation statistics
     */
    @GET("installations/stats")
    suspend fun getInstallationStats(
        @Query("technician_id") technicianId: String? = null,
        @Query("start_date") startDate: Long? = null,
        @Query("end_date") endDate: Long? = null
    ): Response<InstallationStatsResponse>
}

// Request/Response models for Installation API

data class InstallationCreateRequest(
    val dropId: String,
    val technicianId: String,
    val equipmentType: String,
    val priority: Int = 1,
    val notes: String? = null,
    val scheduledDate: Long? = null
)

data class InstallationProgressUpdate(
    val currentStep: String,
    val progress: Float, // 0.0 to 1.0
    val stepNotes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class InstallationStatusUpdate(
    val status: String,
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class InstallationCompletion(
    val success: Boolean,
    val completionNotes: String? = null,
    val issues: List<String>? = null,
    val recommendations: List<String>? = null,
    val completionTime: Long = System.currentTimeMillis()
)

data class PhotoUploadResponse(
    val photoId: String,
    val uploadUrl: String,
    val thumbnailUrl: String?,
    val status: String
)

data class BatchUploadResponse(
    val uploaded: Int,
    val failed: Int,
    val results: List<PhotoUploadResult>
)

data class PhotoUploadResult(
    val filename: String,
    val success: Boolean,
    val photoId: String?,
    val error: String?
)

data class PhotoResponse(
    val id: String,
    val installationId: String,
    val stepName: String,
    val sequenceNumber: Int,
    val uploadUrl: String,
    val thumbnailUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val capturedAt: Long,
    val uploadedAt: Long,
    val validationStatus: String?
)

data class DeleteResponse(
    val success: Boolean,
    val message: String
)

data class InstallationValidationRequest(
    val validateAllPhotos: Boolean = true,
    val stepValidations: Map<String, Boolean>? = null,
    val priority: String = "normal" // "low", "normal", "high"
)

data class InstallationValidationResponse(
    val installationId: String,
    val overallStatus: String, // "pending", "in_progress", "completed", "failed"
    val validationScore: Float, // 0.0 to 1.0
    val stepValidations: Map<String, StepValidationResult>,
    val issues: List<ValidationIssue>,
    val recommendations: List<String>,
    val completedAt: Long?
)

data class StepValidationResult(
    val stepName: String,
    val status: String, // "pending", "passed", "failed", "warning"
    val confidence: Float,
    val issues: List<String>,
    val recommendations: List<String>
)

data class ValidationIssue(
    val type: String, // "photo_quality", "missing_photo", "validation_error"
    val severity: String, // "low", "medium", "high", "critical"
    val description: String,
    val stepName: String?,
    val photoId: String?
)

data class InstallationIssueReport(
    val issueType: String,
    val severity: String,
    val description: String,
    val stepName: String?,
    val photos: List<String>? = null,
    val location: IssueLocation? = null
)

data class IssueReportResponse(
    val issueId: String,
    val status: String,
    val estimatedResolution: Long?
)

data class InstallationIssue(
    val id: String,
    val installationId: String,
    val issueType: String,
    val severity: String,
    val description: String,
    val reportedBy: String,
    val reportedAt: Long,
    val status: String,
    val resolvedAt: Long?,
    val resolution: String?
)

data class IssueLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?
)

data class InstallationSyncData(
    val localVersion: Long,
    val localChanges: List<SyncChange>,
    val lastSyncTime: Long?
)

data class SyncChange(
    val type: String, // "create", "update", "delete"
    val entityType: String, // "installation", "photo", "validation"
    val entityId: String,
    val data: Map<String, Any>,
    val timestamp: Long
)

data class SyncResponse(
    val success: Boolean,
    val syncedChanges: Int,
    val conflicts: List<SyncConflict>,
    val serverVersion: Long
)

data class SyncConflict(
    val entityType: String,
    val entityId: String,
    val conflictType: String, // "version", "data"
    val localData: Map<String, Any>,
    val serverData: Map<String, Any>,
    val resolution: String? // "use_local", "use_server", "merge"
)

data class InstallationStatsResponse(
    val totalInstallations: Int,
    val completedInstallations: Int,
    val averageCompletionTime: Long,
    val successRate: Float,
    val commonIssues: List<IssueStats>,
    val technicianStats: Map<String, TechnicianStats>
)

data class IssueStats(
    val issueType: String,
    val count: Int,
    val averageResolutionTime: Long
)

data class TechnicianStats(
    val technicianId: String,
    val totalInstallations: Int,
    val completedInstallations: Int,
    val averageCompletionTime: Long,
    val successRate: Float
)