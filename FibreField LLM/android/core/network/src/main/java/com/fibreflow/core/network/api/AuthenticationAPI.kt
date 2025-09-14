package com.fibreflow.core.network.api

import com.fibreflow.core.network.models.request.LoginRequest
import com.fibreflow.core.network.models.response.AuthResponse
import com.fibreflow.core.network.models.response.RefreshTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Authentication API endpoints
 * Handles login, token refresh, and logout operations
 */
interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(): Response<RefreshTokenResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("auth/biometric/login")
    suspend fun biometricLogin(@Body request: Map<String, String>): Response<AuthResponse>
}