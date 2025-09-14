package com.fibreflow.domain.authentication

import android.content.Context
import android.util.Log
import com.fibreflow.core.common.result.Result
import com.fibreflow.core.database.dao.SessionDao
import com.fibreflow.core.database.entities.SessionEntity
import com.fibreflow.core.network.api.AuthenticationAPI
import com.fibreflow.core.network.models.request.LoginRequest
import com.fibreflow.core.network.models.response.AuthResponse
import com.fibreflow.domain.authentication.entities.AuthToken
import com.fibreflow.domain.authentication.entities.BiometricCredentials
import com.fibreflow.domain.authentication.entities.Technician
import com.fibreflow.domain.authentication.repositories.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication Service for FibreField
 * Handles user authentication, session management, and biometric integration
 */
@Singleton
class AuthenticationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val biometricManager: BiometricManager,
    private val sessionDao: SessionDao,
    private val authAPI: AuthenticationAPI
) {

    companion object {
        private const val TAG = "AuthenticationService"
        private const val SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Authentication state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Current user
    private var currentTechnician: Technician? = null

    init {
        // Check for existing session on initialization
        serviceScope.launch {
            checkExistingSession()
        }
    }

    /**
     * Login with email and password
     */
    suspend fun login(email: String, password: String): Result<Technician> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Attempting login for: $email")

            // Validate input
            if (email.isBlank() || password.isBlank()) {
                return@withContext Result.Error(IllegalArgumentException("Email and password required"))
            }

            // Call API
            val loginRequest = LoginRequest(email, password)
            val response = authAPI.login(loginRequest)

            if (response.isSuccessful) {
                response.body()?.let { authResponse ->
                    // Store tokens
                    val tokens = AuthToken(
                        accessToken = authResponse.accessToken,
                        refreshToken = authResponse.refreshToken,
                        expiresAt = System.currentTimeMillis() + (authResponse.expiresIn * 1000)
                    )

                    tokenManager.storeTokens(tokens)

                    // Create technician object
                    val technician = Technician(
                        id = authResponse.userId,
                        email = email,
                        name = authResponse.userName,
                        role = authResponse.role,
                        isActive = true
                    )

                    // Store in repository
                    authRepository.saveTechnician(technician)

                    // Create session
                    createSession(technician)

                    // Update state
                    currentTechnician = technician
                    _authState.value = AuthState.Authenticated(technician)

                    Log.i(TAG, "Login successful for technician: ${technician.name}")
                    Result.Success(technician)

                } ?: Result.Error(RuntimeException("Invalid response from server"))

            } else {
                val error = when (response.code()) {
                    401 -> "Invalid email or password"
                    403 -> "Account is disabled"
                    429 -> "Too many login attempts"
                    else -> "Login failed"
                }
                Result.Error(RuntimeException(error))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Result.Error(e)
        }
    }

    /**
     * Login with biometric authentication
     */
    suspend fun loginWithBiometrics(): Result<Technician> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Attempting biometric login")

            // Check if biometric credentials exist
            val credentials = biometricManager.getStoredCredentials()
                ?: return@withContext Result.Error(RuntimeException("No biometric credentials found"))

            // Perform biometric authentication
            val biometricResult = biometricManager.authenticate()
            if (biometricResult is Result.Error) {
                return@withContext biometricResult
            }

            // Get technician from stored credentials
            val technician = authRepository.getTechnicianById(credentials.technicianId)
                ?: return@withContext Result.Error(RuntimeException("Technician not found"))

            // Validate stored tokens are still valid
            val tokens = tokenManager.getStoredTokens()
            if (tokens != null && tokenManager.isTokenValid(tokens)) {
                // Use existing valid tokens
                createSession(technician)
                currentTechnician = technician
                _authState.value = AuthState.Authenticated(technician)

                Log.i(TAG, "Biometric login successful for technician: ${technician.name}")
                Result.Success(technician)
            } else {
                // Tokens expired, need password login
                Result.Error(RuntimeException("Session expired, please login with password"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Biometric login failed", e)
            Result.Error(e)
        }
    }

    /**
     * Logout current user
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Logging out current user")

            // Clear stored tokens
            tokenManager.clearTokens()

            // Clear biometric credentials if requested
            biometricManager.clearStoredCredentials()

            // Clear current session
            clearSession()

            // Update state
            currentTechnician = null
            _authState.value = AuthState.Unauthenticated

            Log.i(TAG, "Logout successful")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Logout failed", e)
            Result.Error(e)
        }
    }

    /**
     * Refresh authentication tokens
     */
    suspend fun refreshTokens(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentTokens = tokenManager.getStoredTokens()
                ?: return@withContext Result.Error(RuntimeException("No tokens to refresh"))

            // Call refresh API
            val response = authAPI.refreshToken(currentTokens.refreshToken)

            if (response.isSuccessful) {
                response.body()?.let { refreshResponse ->
                    val newTokens = AuthToken(
                        accessToken = refreshResponse.accessToken,
                        refreshToken = refreshResponse.refreshToken ?: currentTokens.refreshToken,
                        expiresAt = System.currentTimeMillis() + (refreshResponse.expiresIn * 1000)
                    )

                    tokenManager.storeTokens(newTokens)
                    Log.i(TAG, "Token refresh successful")
                    Result.Success(Unit)

                } ?: Result.Error(RuntimeException("Invalid refresh response"))

            } else {
                // Refresh failed, logout user
                logout()
                Result.Error(RuntimeException("Session expired, please login again"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            Result.Error(e)
        }
    }

    /**
     * Enable biometric authentication for current user
     */
    suspend fun enableBiometrics(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val technician = currentTechnician
                ?: return@withContext Result.Error(RuntimeException("No authenticated user"))

            // Verify password first
            val verifyResponse = authAPI.verifyPassword(password)
            if (!verifyResponse.isSuccessful) {
                return@withContext Result.Error(RuntimeException("Password verification failed"))
            }

            // Store biometric credentials
            val credentials = BiometricCredentials(
                technicianId = technician.id,
                enrolledAt = System.currentTimeMillis()
            )

            biometricManager.storeCredentials(credentials)

            Log.i(TAG, "Biometric authentication enabled for technician: ${technician.name}")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable biometrics", e)
            Result.Error(e)
        }
    }

    /**
     * Check if user is currently authenticated
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }

    /**
     * Get current authenticated technician
     */
    fun getCurrentTechnician(): Technician? {
        return currentTechnician
    }

    /**
     * Get valid access token
     */
    suspend fun getValidAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val tokens = tokenManager.getStoredTokens()
            ?: return@withContext Result.Error(RuntimeException("No stored tokens"))

        if (tokenManager.isTokenValid(tokens)) {
            Result.Success(tokens.accessToken)
        } else {
            // Try to refresh tokens
            val refreshResult = refreshTokens()
            if (refreshResult is Result.Success) {
                val newTokens = tokenManager.getStoredTokens()
                Result.Success(newTokens?.accessToken ?: "")
            } else {
                Result.Error(RuntimeException("Token refresh failed"))
            }
        }
    }

    /**
     * Check if biometric authentication is available
     */
    fun isBiometricAvailable(): Boolean {
        return biometricManager.isBiometricAvailable()
    }

    /**
     * Check if biometric authentication is enabled for current user
     */
    fun isBiometricEnabled(): Boolean {
        return biometricManager.hasStoredCredentials()
    }

    // Private helper methods

    private suspend fun checkExistingSession() {
        try {
            val tokens = tokenManager.getStoredTokens()
            if (tokens != null && tokenManager.isTokenValid(tokens)) {
                // Valid tokens exist, check if we have technician data
                val technicianId = tokenManager.getTechnicianIdFromToken(tokens.accessToken)
                if (technicianId != null) {
                    val technician = authRepository.getTechnicianById(technicianId)
                    if (technician != null) {
                        currentTechnician = technician
                        _authState.value = AuthState.Authenticated(technician)
                        createSession(technician)
                        Log.i(TAG, "Existing session restored for technician: ${technician.name}")
                        return
                    }
                }
            }

            // No valid session
            _authState.value = AuthState.Unauthenticated

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check existing session", e)
            _authState.value = AuthState.Unauthenticated
        }
    }

    private suspend fun createSession(technician: Technician) {
        try {
            val session = SessionEntity(
                technicianId = technician.id,
                loginTime = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + SESSION_TIMEOUT_MS,
                isActive = true
            )

            sessionDao.insertSession(session)
            Log.d(TAG, "Session created for technician: ${technician.id}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
        }
    }

    private suspend fun clearSession() {
        try {
            currentTechnician?.id?.let { technicianId ->
                sessionDao.deactivateSession(technicianId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear session", e)
        }
    }

    /**
     * Shutdown service
     */
    fun shutdown() {
        serviceScope.cancel()
        Log.i(TAG, "AuthenticationService shutdown complete")
    }
}

/**
 * Authentication state
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    data class Authenticated(val technician: Technician) : AuthState()
    data class Error(val message: String) : AuthState()
}