package com.fibreflow.domain.authentication.entities

/**
 * Domain entity representing authentication tokens
 */
data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val expiresAt: Long = System.currentTimeMillis() + (expiresIn * 1000)
) {

    /**
     * Check if access token is expired
     */
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt

    /**
     * Check if access token is about to expire (within 5 minutes)
     */
    val isExpiringSoon: Boolean
        get() = (expiresAt - System.currentTimeMillis()) < (5 * 60 * 1000) // 5 minutes

    /**
     * Get remaining time until expiration in seconds
     */
    val remainingTimeSeconds: Long
        get() = maxOf(0, (expiresAt - System.currentTimeMillis()) / 1000)
}