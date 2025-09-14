package com.fibreflow.core.network.api

import com.fibreflow.core.network.models.response.ProjectResponse
import com.fibreflow.core.network.models.response.DropResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * Project Management API endpoints
 * Handles project and drop data synchronization
 */
interface ProjectAPI {

    /**
     * Get all projects accessible to the technician
     */
    @GET("projects")
    suspend fun getProjects(
        @Query("technician_id") technicianId: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<ProjectResponse>>

    /**
     * Get specific project details
     */
    @GET("projects/{projectId}")
    suspend fun getProject(
        @Path("projectId") projectId: String
    ): Response<ProjectResponse>

    /**
     * Get drops for a specific project
     */
    @GET("projects/{projectId}/drops")
    suspend fun getProjectDrops(
        @Path("projectId") projectId: String,
        @Query("status") status: String? = null,
        @Query("assigned_to") assignedTo: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<DropResponse>>

    /**
     * Search projects by location or address
     */
    @GET("projects/search")
    suspend fun searchProjects(
        @Query("query") query: String,
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
        @Query("radius_km") radiusKm: Double? = null,
        @Query("limit") limit: Int = 20
    ): Response<List<ProjectResponse>>

    /**
     * Get project statistics
     */
    @GET("projects/stats")
    suspend fun getProjectStats(
        @Query("technician_id") technicianId: String? = null
    ): Response<ProjectStatsResponse>

    /**
     * Update project status (admin only)
     */
    @PUT("projects/{projectId}/status")
    suspend fun updateProjectStatus(
        @Path("projectId") projectId: String,
        @Body statusUpdate: ProjectStatusUpdate
    ): Response<ProjectResponse>
}

// Request/Response models for Project API

data class ProjectStatsResponse(
    val totalProjects: Int,
    val activeProjects: Int,
    val completedProjects: Int,
    val totalDrops: Int,
    val assignedDrops: Int,
    val completedDrops: Int
)

data class ProjectStatusUpdate(
    val status: String,
    val notes: String? = null
)