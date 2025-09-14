package com.fibreflow.domain.authentication.entities

/**
 * Domain entity representing biometric authentication credentials
 */
data class BiometricCredentials(
    val userId: String,
    val biometricToken: String,
    val deviceId: String,
    val isEnabled: Boolean = true,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {

    /**
     * Check if biometric credentials are expired (90 days)
     */
    val isExpired: Boolean
        get() = (System.currentTimeMillis() - createdAt) > (90 * 24 * 60 * 60 * 1000L) // 90 days

    /**
     * Check if biometric credentials are stale (30 days since last use)
     */
    val isStale: Boolean
        get() = (System.currentTimeMillis() - lastUsed) > (30 * 24 * 60 * 60 * 1000L) // 30 days

    /**
     * Get days since last use
     */
    val daysSinceLastUse: Long
        get() = (System.currentTimeMillis() - lastUsed) / (24 * 60 * 60 * 1000L)
}