package com.fibreflow.core.network.api

import com.fibreflow.core.network.models.response.ActivationResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Activation Processing API endpoints
 * Handles service activation, testing, and status monitoring
 */
interface ActivationAPI {

    /**
     * Schedule activation for completed installation
     */
    @POST("activations")
    suspend fun scheduleActivation(
        @Body activationRequest: ActivationScheduleRequest
    ): Response<ActivationResponse>

    /**
     * Get activation details
     */
    @GET("activations/{activationId}")
    suspend fun getActivation(
        @Path("activationId") activationId: String
    ): Response<ActivationResponse>

    /**
     * Update activation status
     */
    @PUT("activations/{activationId}/status")
    suspend fun updateActivationStatus(
        @Path("activationId") activationId: String,
        @Body statusUpdate: ActivationStatusUpdate
    ): Response<ActivationResponse>

    /**
     * Start activation process
     */
    @POST("activations/{activationId}/start")
    suspend fun startActivation(
        @Path("activationId") activationId: String,
        @Body startRequest: ActivationStartRequest = ActivationStartRequest()
    ): Response<ActivationResponse>

    /**
     * Complete activation
     */
    @POST("activations/{activationId}/complete")
    suspend fun completeActivation(
        @Path("activationId") activationId: String,
        @Body completion: ActivationCompletion
    ): Response<ActivationResponse>

    /**
     * Run activation tests
     */
    @POST("activations/{activationId}/tests")
    suspend fun runActivationTests(
        @Path("activationId") activationId: String,
        @Body testRequest: ActivationTestRequest
    ): Response<ActivationTestResponse>

    /**
     * Get activation test results
     */
    @GET("activations/{activationId}/tests")
    suspend fun getActivationTestResults(
        @Path("activationId") activationId: String,
        @Query("test_type") testType: String? = null
    ): Response<ActivationTestResponse>

    /**
     * Get technician's activations
     */
    @GET("technicians/{technicianId}/activations")
    suspend fun getTechnicianActivations(
        @Path("technicianId") technicianId: String,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<ActivationResponse>>

    /**
     * Get activations by drop
     */
    @GET("drops/{dropId}/activations")
    suspend fun getDropActivations(
        @Path("dropId") dropId: String,
        @Query("limit") limit: Int = 10
    ): Response<List<ActivationResponse>>

    /**
     * Report activation issue
     */
    @POST("activations/{activationId}/issues")
    suspend fun reportActivationIssue(
        @Path("activationId") activationId: String,
        @Body issue: ActivationIssueReport
    ): Response<ActivationIssueResponse>

    /**
     * Get activation issues
     */
    @GET("activations/{activationId}/issues")
    suspend fun getActivationIssues(
        @Path("activationId") activationId: String
    ): Response<List<ActivationIssue>>

    /**
     * Reschedule activation
     */
    @POST("activations/{activationId}/reschedule")
    suspend fun rescheduleActivation(
        @Path("activationId") activationId: String,
        @Body rescheduleRequest: ActivationRescheduleRequest
    ): Response<ActivationResponse>

    /**
     * Cancel activation
     */
    @POST("activations/{activationId}/cancel")
    suspend fun cancelActivation(
        @Path("activationId") activationId: String,
        @Body cancellation: ActivationCancellation
    ): Response<ActivationResponse>

    /**
     * Get activation statistics
     */
    @GET("activations/stats")
    suspend fun getActivationStats(
        @Query("technician_id") technicianId: String? = null,
        @Query("start_date") startDate: Long? = null,
        @Query("end_date") endDate: Long? = null
    ): Response<ActivationStatsResponse>

    /**
     * Bulk update activation statuses
     */
    @POST("activations/bulk/status")
    suspend fun bulkUpdateActivationStatus(
        @Body bulkUpdate: BulkActivationUpdate
    ): Response<BulkActivationResponse>
}

// Request/Response models for Activation API

data class ActivationScheduleRequest(
    val dropId: String,
    val technicianId: String,
    val scheduledDate: Long,
    val priority: String = "NORMAL", // "LOW", "NORMAL", "HIGH", "CRITICAL"
    val serviceType: String,
    val notes: String? = null,
    val estimatedDuration: Long? = null // minutes
)

data class ActivationStatusUpdate(
    val status: String,
    val notes: String? = null,
    val progress: Float? = null, // 0.0 to 1.0
    val timestamp: Long = System.currentTimeMillis()
)

data class ActivationStartRequest(
    val technicianId: String,
    val equipmentUsed: List<String>? = null,
    val notes: String? = null
)

data class ActivationCompletion(
    val success: Boolean,
    val completionNotes: String? = null,
    val testResults: Map<String, Boolean>? = null,
    val issues: List<String>? = null,
    val recommendations: List<String>? = null,
    val completionTime: Long = System.currentTimeMillis()
)

data class ActivationTestRequest(
    val testTypes: List<String>, // "connectivity", "speed", "signal_strength", etc.
    val priority: String = "normal",
    val timeoutSeconds: Int = 300
)

data class ActivationTestResponse(
    val activationId: String,
    val overallStatus: String, // "pending", "running", "completed", "failed"
    val testResults: Map<String, TestResult>,
    val summary: TestSummary,
    val completedAt: Long?
)

data class TestResult(
    val testType: String,
    val status: String, // "passed", "failed", "warning", "not_run"
    val result: String? = null,
    val details: Map<String, Any>? = null,
    val durationMs: Long? = null,
    val error: String? = null
)

data class TestSummary(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val warningTests: Int,
    val overallScore: Float // 0.0 to 1.0
)

data class ActivationIssueReport(
    val issueType: String,
    val severity: String,
    val description: String,
    val testResults: Map<String, Any>? = null,
    val photos: List<String>? = null,
    val location: IssueLocation? = null
)

data class ActivationIssueResponse(
    val issueId: String,
    val status: String,
    val estimatedResolution: Long?
)

data class ActivationIssue(
    val id: String,
    val activationId: String,
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

data class ActivationRescheduleRequest(
    val newScheduledDate: Long,
    val reason: String,
    val notes: String? = null
)

data class ActivationCancellation(
    val reason: String,
    val notes: String? = null,
    val canReschedule: Boolean = true
)

data class ActivationStatsResponse(
    val totalActivations: Int,
    val completedActivations: Int,
    val successRate: Float,
    val averageCompletionTime: Long,
    val commonIssues: List<IssueStats>,
    val technicianStats: Map<String, TechnicianActivationStats>
)

data class IssueStats(
    val issueType: String,
    val count: Int,
    val averageResolutionTime: Long
)

data class TechnicianActivationStats(
    val technicianId: String,
    val totalActivations: Int,
    val completedActivations: Int,
    val successRate: Float,
    val averageCompletionTime: Long
)

data class BulkActivationUpdate(
    val activationIds: List<String>,
    val status: String,
    val notes: String? = null
)

data class BulkActivationResponse(
    val successful: Int,
    val failed: Int,
    val errors: List<String>
)