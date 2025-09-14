package com.fibreflow.domain.authentication

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.authentication.entities.AuthToken
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token Manager for JWT token handling and secure storage
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_NAME = "fibrefield_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val TOKEN_REFRESH_BUFFER_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Store authentication tokens securely
     */
    fun storeTokens(tokens: AuthToken) {
        try {
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
                .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
                .putLong(KEY_EXPIRES_AT, tokens.expiresAt)
                .apply()

            Log.d(TAG, "Tokens stored successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to store tokens", e)
        }
    }

    /**
     * Retrieve stored authentication tokens
     */
    fun getStoredTokens(): AuthToken? {
        return try {
            val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

            if (accessToken != null && refreshToken != null && expiresAt > 0) {
                AuthToken(accessToken, refreshToken, expiresAt)
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve tokens", e)
            null
        }
    }

    /**
     * Check if stored access token is still valid
     */
    fun isTokenValid(tokens: AuthToken? = null): Boolean {
        val tokenToCheck = tokens ?: getStoredTokens()
        return tokenToCheck?.let { isTokenValid(it) } ?: false
    }

    /**
     * Check if token is valid (not expired with buffer)
     */
    fun isTokenValid(token: AuthToken): Boolean {
        val currentTime = System.currentTimeMillis()
        return (token.expiresAt - TOKEN_REFRESH_BUFFER_MS) > currentTime
    }

    /**
     * Check if token needs refresh
     */
    fun shouldRefreshToken(token: AuthToken): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeToExpiry = token.expiresAt - currentTime
        return timeToExpiry < TOKEN_REFRESH_BUFFER_MS
    }

    /**
     * Extract technician ID from JWT token
     */
    fun getTechnicianIdFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val json = JSONObject(payload)

            json.optString("sub") ?: json.optString("technician_id")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse token", e)
            null
        }
    }

    /**
     * Extract token expiration time from JWT
     */
    fun getTokenExpirationTime(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val json = JSONObject(payload)

            json.optLong("exp") * 1000 // Convert to milliseconds

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse token expiration", e)
            null
        }
    }

    /**
     * Clear all stored tokens
     */
    fun clearTokens() {
        try {
            prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .apply()

            Log.d(TAG, "Tokens cleared successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear tokens", e)
        }
    }

    /**
     * Get token information for diagnostics
     */
    fun getTokenInfo(): TokenInfo {
        val tokens = getStoredTokens()
        val isValid = tokens?.let { isTokenValid(it) } ?: false
        val shouldRefresh = tokens?.let { shouldRefreshToken(it) } ?: false

        return TokenInfo(
            hasTokens = tokens != null,
            isValid = isValid,
            shouldRefresh = shouldRefresh,
            expiresAt = tokens?.expiresAt,
            timeToExpiry = tokens?.let { it.expiresAt - System.currentTimeMillis() }
        )
    }

    /**
     * Validate token format
     */
    fun isValidTokenFormat(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return false

            // Try to decode header and payload
            Base64.decode(parts[0], Base64.URL_SAFE)
            Base64.decode(parts[1], Base64.URL_SAFE)

            true

        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Token information for diagnostics
 */
data class TokenInfo(
    val hasTokens: Boolean,
    val isValid: Boolean,
    val shouldRefresh: Boolean,
    val expiresAt: Long?,
    val timeToExpiry: Long?
)