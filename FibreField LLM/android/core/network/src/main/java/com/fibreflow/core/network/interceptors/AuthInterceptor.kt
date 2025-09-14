package com.fibreflow.core.network.interceptors

import com.fibreflow.core.common.result.Result
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor for handling JWT authentication tokens
 * Automatically adds Authorization header to requests
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for login and refresh endpoints
        val url = originalRequest.url.toString()
        if (isAuthEndpoint(url)) {
            return chain.proceed(originalRequest)
        }

        // Get access token
        val tokenResult = tokenManager.getAccessToken()
        if (tokenResult is Result.Success) {
            val token = tokenResult.data
            val authenticatedRequest = originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = chain.proceed(authenticatedRequest)

            // Handle 401 - token expired
            if (response.code == 401) {
                Timber.d("Received 401, attempting token refresh")
                return handleTokenRefresh(chain, originalRequest)
            }

            return response
        } else {
            Timber.w("No access token available")
            return chain.proceed(originalRequest)
        }
    }

    private fun handleTokenRefresh(chain: Interceptor.Chain, originalRequest: okhttp3.Request): Response {
        return when (val refreshResult = tokenManager.refreshAccessToken()) {
            is Result.Success -> {
                val newToken = refreshResult.data
                val retryRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $newToken")
                    .build()
                chain.proceed(retryRequest)
            }
            is Result.Error -> {
                Timber.e("Token refresh failed: ${refreshResult.exception}")
                // Return original 401 response
                chain.proceed(originalRequest)
            }
        }
    }

    private fun isAuthEndpoint(url: String): Boolean {
        return url.contains("/auth/login") ||
               url.contains("/auth/refresh") ||
               url.contains("/auth/biometric/login")
    }
}

/**
 * Interface for token management
 * Implementation should handle secure token storage
 */
interface TokenManager {
    fun getAccessToken(): Result<String>
    fun getRefreshToken(): Result<String>
    fun saveTokens(accessToken: String, refreshToken: String): Result<Unit>
    fun refreshAccessToken(): Result<String>
    fun clearTokens(): Result<Unit>
}