package com.fibreflow.core.common

import android.graphics.Bitmap
import android.graphics.Color
import com.fibreflow.core.database.entities.DropEntity
import com.fibreflow.core.database.entities.DropStatus
import com.fibreflow.core.network.models.request.LoginRequest
import com.fibreflow.core.network.models.response.AuthResponse

/**
 * Test utilities for creating test data and common test operations
 */
object TestUtils {

    // Test data generators
    fun createTestDrop(
        id: Long = 1L,
        projectId: Long = 1L,
        customerName: String = "Test Customer",
        address: String = "123 Test Street, Cape Town",
        latitude: Double = -33.9249,
        longitude: Double = 18.4241,
        status: DropStatus = DropStatus.AVAILABLE,
        estimatedInstallTime: Int = 120,
        notes: String = "Test drop for unit testing",
        needsSync: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ): DropEntity {
        return DropEntity(
            id = id,
            projectId = projectId,
            customerName = customerName,
            address = address,
            latitude = latitude,
            longitude = longitude,
            status = status,
            estimatedInstallTime = estimatedInstallTime,
            notes = notes,
            needsSync = needsSync,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun createTestAuthResponse(
        accessToken: String = "test-access-token-123",
        refreshToken: String = "test-refresh-token-456",
        expiresIn: Int = 3600,
        tokenType: String = "Bearer"
    ): AuthResponse {
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            tokenType = tokenType
        )
    }

    fun createTestLoginRequest(
        username: String = "testuser",
        password: String = "testpass123"
    ): LoginRequest {
        return LoginRequest(username, password)
    }

    fun createTestBitmap(
        width: Int = 100,
        height: Int = 100,
        color: Int = Color.BLUE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    // Test constants
    const val TEST_USERNAME = "test_technician"
    const val TEST_PASSWORD = "secure_password_123"
    const val TEST_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlRlc3QgVXNlciIsImlhdCI6MTUxNjIzOTAyMn0"
    const val TEST_REFRESH_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlRlc3QgUmVmcmVzaCIsImlhdCI6MTUxNjIzOTAyMn0"

    // Test error messages
    const val ERROR_NETWORK = "Network error occurred"
    const val ERROR_AUTH = "Authentication failed"
    const val ERROR_DATABASE = "Database operation failed"
    const val ERROR_VALIDATION = "Validation failed"

    // Test performance metrics
    fun createTestPerformanceMetrics(): Map<String, Any> {
        return mapOf(
            "inference_count" to 25,
            "average_latency_ms" to 150.5,
            "success_rate" to 0.92,
            "memory_usage_mb" to 45.2,
            "cpu_usage_percent" to 12.8
        )
    }
}