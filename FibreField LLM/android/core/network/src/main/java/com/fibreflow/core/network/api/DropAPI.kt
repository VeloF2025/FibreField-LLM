package com.fibreflow.core.network.api

import com.fibreflow.core.network.models.response.DropResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * Drop Management API endpoints
 */
interface DropAPI {

    /**
     * Get drop details
     */
    @GET("drops/{dropId}")
    suspend fun getDrop(
        @Path("dropId") dropId: String
    ): Response<DropResponse>

    /**
     * Get drops by location (bounding box)
     */
    @GET("drops/location")
    suspend fun getDropsByLocation(
        @Query("north") north: Double,
        @Query("south") south: Double,
        @Query("east") east: Double,
        @Query("west") west: Double,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 100
    ): Response<List<DropResponse>>

    /**
     * Assign drop to technician
     */
    @POST("drops/{dropId}/assign")
    suspend fun assignDrop(
        @Path("dropId") dropId: String,
        @Body assignment: DropAssignment
    ): Response<DropResponse>

    /**
     * Unassign drop from technician
     */
    @POST("drops/{dropId}/unassign")
    suspend fun unassignDrop(
        @Path("dropId") dropId: String,
        @Body unassignment: DropUnassignment = DropUnassignment()
    ): Response<DropResponse>

    /**
     * Update drop status
     */
    @PUT("drops/{dropId}/status")
    suspend fun updateDropStatus(
        @Path("dropId") dropId: String,
        @Body statusUpdate: DropStatusUpdate
    ): Response<DropResponse>

    /**
     * Update drop location
     */
    @PUT("drops/{dropId}/location")
    suspend fun updateDropLocation(
        @Path("dropId") dropId: String,
        @Body locationUpdate: DropLocationUpdate
    ): Response<DropResponse>

    /**
     * Get drop installation history
     */
    @GET("drops/{dropId}/history")
    suspend fun getDropHistory(
        @Path("dropId") dropId: String,
        @Query("limit") limit: Int = 10
    ): Response<List<DropHistoryResponse>>

    /**
     * Report drop issue
     */
    @POST("drops/{dropId}/issues")
    suspend fun reportDropIssue(
        @Path("dropId") dropId: String,
        @Body issue: DropIssueReport
    ): Response<DropIssueResponse>

    /**
     * Bulk update drop statuses
     */
    @POST("drops/bulk/status")
    suspend fun bulkUpdateStatus(
        @Body bulkUpdate: BulkStatusUpdate
    ): Response<BulkUpdateResponse>
}

// Request/Response models for Drop API

data class DropAssignment(
    val technicianId: String,
    val priority: Int = 1,
    val notes: String? = null
)

data class DropUnassignment(
    val reason: String? = null,
    val notes: String? = null
)

data class DropStatusUpdate(
    val status: String,
    val notes: String? = null,
    val completionData: CompletionData? = null
)

data class DropLocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val notes: String? = null
)

data class DropHistoryResponse(
    val id: String,
    val dropId: String,
    val action: String,
    val technicianId: String?,
    val timestamp: Long,
    val details: Map<String, Any>?
)

data class DropIssueReport(
    val issueType: String,
    val severity: String,
    val description: String,
    val photos: List<String>? = null
)

data class DropIssueResponse(
    val issueId: String,
    val status: String,
    val estimatedResolution: Long?
)

data class BulkStatusUpdate(
    val dropIds: List<String>,
    val status: String,
    val notes: String? = null
)

data class BulkUpdateResponse(
    val successful: Int,
    val failed: Int,
    val errors: List<String>
)

data class CompletionData(
    val completionTime: Long,
    val success: Boolean,
    val notes: String? = null,
    val issues: List<String>? = null
)