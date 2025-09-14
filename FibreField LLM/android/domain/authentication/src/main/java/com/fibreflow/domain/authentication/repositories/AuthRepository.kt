package com.fibreflow.domain.authentication.repositories

import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.authentication.entities.AuthToken
import com.fibreflow.domain.authentication.entities.BiometricCredentials
import com.fibreflow.domain.authentication.entities.Technician
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for authentication operations
 * Handles login, token management, and biometric authentication
 */
interface AuthRepository {

    /**
     * Login with username and password
     */
    suspend fun login(username: String, password: String): Result<AuthToken>

    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshToken(): Result<AuthToken>

    /**
     * Logout current user
     */
    suspend fun logout(): Result<Unit>

    /**
     * Login using biometric authentication
     */
    suspend fun biometricLogin(biometricToken: String): Result<AuthToken>

    /**
     * Get current user information
     */
    suspend fun getCurrentUser(): Result<Technician>

    /**
     * Save authentication tokens securely
     */
    suspend fun saveTokens(token: AuthToken): Result<Unit>

    /**
     * Get stored access token
     */
    suspend fun getAccessToken(): Result<String>

    /**
     * Get stored refresh token
     */
    suspend fun getRefreshToken(): Result<String>

    /**
     * Clear all stored authentication data
     */
    suspend fun clearTokens(): Result<Unit>

    /**
     * Check if user is currently authenticated
     */
    suspend fun isAuthenticated(): Boolean

    /**
     * Get authentication state as flow
     */
    fun getAuthState(): Flow<Boolean>

    /**
     * Save biometric credentials
     */
    suspend fun saveBiometricCredentials(credentials: BiometricCredentials): Result<Unit>

    /**
     * Get stored biometric credentials
     */
    suspend fun getBiometricCredentials(userId: String): Result<BiometricCredentials>

    /**
     * Check if biometric authentication is available
     */
    suspend fun isBiometricAvailable(): Boolean

    /**
     * Enable biometric authentication
     */
    suspend fun enableBiometric(): Result<Unit>

    /**
     * Disable biometric authentication
     */
    suspend fun disableBiometric(): Result<Unit>

    /**
     * Validate if stored tokens are still valid
     */
    suspend fun validateTokens(): Boolean
}