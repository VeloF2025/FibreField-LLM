package com.fibreflow.core.network.error

import android.util.Log
import com.fibreflow.core.common.result.Result
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network Error Handler for FibreField API Operations
 * Provides comprehensive error handling and recovery strategies
 * Implements retry logic, error classification, and user-friendly messaging
 */
@Singleton
class ErrorHandler @Inject constructor() {

    companion object {
        private const val TAG = "NetworkErrorHandler"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    /**
     * Handle API errors with automatic retry logic
     */
    suspend fun <T> handleApiCall(
        apiCall: suspend () -> Result<T>,
        retryConfig: RetryConfig = RetryConfig()
    ): Result<T> {
        var lastException: Exception? = null

        repeat(retryConfig.maxAttempts) { attempt ->
            try {
                return apiCall()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "API call attempt ${attempt + 1} failed", e)

                val errorType = classifyError(e)
                val shouldRetry = shouldRetry(errorType, attempt, retryConfig)

                if (!shouldRetry) {
                    break
                }

                if (attempt < retryConfig.maxAttempts - 1) {
                    val delay = calculateRetryDelay(attempt, retryConfig)
                    kotlinx.coroutines.delay(delay)
                }
            }
        }

        // All retries exhausted
        return Result.Error(lastException ?: RuntimeException("Unknown API error"))
    }

    /**
     * Parse HTTP error response
     */
    fun parseHttpError(response: Response<*>): ApiError {
        val errorBody = response.errorBody()?.string()
        val httpCode = response.code()

        return when (httpCode) {
            400 -> ApiError.BadRequest("Invalid request: ${parseErrorMessage(errorBody)}")
            401 -> ApiError.Unauthorized("Authentication required")
            403 -> ApiError.Forbidden("Access denied")
            404 -> ApiError.NotFound("Resource not found")
            409 -> ApiError.Conflict("Data conflict: ${parseErrorMessage(errorBody)}")
            422 -> ApiError.ValidationError("Validation failed: ${parseErrorMessage(errorBody)}")
            429 -> ApiError.RateLimited("Too many requests")
            in 500..599 -> ApiError.ServerError("Server error (${httpCode}): ${parseErrorMessage(errorBody)}")
            else -> ApiError.Unknown("HTTP ${httpCode}: ${parseErrorMessage(errorBody)}")
        }
    }

    /**
     * Get user-friendly error message
     */
    fun getUserFriendlyMessage(error: ApiError): String {
        return when (error) {
            is ApiError.NetworkError -> "Connection problem. Please check your internet and try again."
            is ApiError.Timeout -> "Request timed out. Please try again."
            is ApiError.Unauthorized -> "Please log in again to continue."
            is ApiError.Forbidden -> "You don't have permission for this action."
            is ApiError.NotFound -> "The requested information was not found."
            is ApiError.ServerError -> "Server temporarily unavailable. Please try again later."
            is ApiError.RateLimited -> "Too many requests. Please wait a moment and try again."
            is ApiError.BadRequest -> "Invalid request. Please check your input."
            is ApiError.ValidationError -> "Please correct the highlighted errors and try again."
            is ApiError.Conflict -> "This information has been modified elsewhere. Please refresh and try again."
            is ApiError.Unknown -> "An unexpected error occurred. Please try again."
        }
    }

    /**
     * Determine if error is recoverable
     */
    fun isRecoverable(error: ApiError): Boolean {
        return when (error) {
            is ApiError.NetworkError,
            is ApiError.Timeout,
            is ApiError.ServerError,
            is ApiError.RateLimited -> true
            else -> false
        }
    }

    /**
     * Get recovery suggestions for error
     */
    fun getRecoverySuggestions(error: ApiError): List<String> {
        return when (error) {
            is ApiError.NetworkError -> listOf(
                "Check your internet connection",
                "Try switching between WiFi and mobile data",
                "Wait a moment and try again"
            )
            is ApiError.Timeout -> listOf(
                "Check your internet speed",
                "Try again in a moment",
                "Contact support if problem persists"
            )
            is ApiError.Unauthorized -> listOf(
                "Log out and log back in",
                "Check if your account is still active",
                "Contact your administrator"
            )
            is ApiError.ServerError -> listOf(
                "Try again in a few minutes",
                "Check service status",
                "Contact support if outage continues"
            )
            is ApiError.RateLimited -> listOf(
                "Wait a few minutes before retrying",
                "Reduce request frequency",
                "Contact support for higher limits"
            )
            else -> listOf("Try again", "Contact support if problem persists")
        }
    }

    // Private implementation methods

    private fun classifyError(exception: Exception): ErrorType {
        return when (exception) {
            is HttpException -> ErrorType.HTTP
            is SocketTimeoutException -> ErrorType.TIMEOUT
            is UnknownHostException -> ErrorType.NETWORK
            is IOException -> ErrorType.NETWORK
            else -> ErrorType.UNKNOWN
        }
    }

    private fun shouldRetry(errorType: ErrorType, attempt: Int, config: RetryConfig): Boolean {
        if (attempt >= config.maxAttempts - 1) return false

        return when (errorType) {
            ErrorType.NETWORK -> config.retryOnNetworkError
            ErrorType.TIMEOUT -> config.retryOnTimeout
            ErrorType.HTTP -> config.retryOnHttpError
            ErrorType.UNKNOWN -> config.retryOnUnknownError
        }
    }

    private fun calculateRetryDelay(attempt: Int, config: RetryConfig): Long {
        return when (config.backoffStrategy) {
            BackoffStrategy.LINEAR -> config.baseDelayMs * (attempt + 1)
            BackoffStrategy.EXPONENTIAL -> config.baseDelayMs * (1L shl attempt) // 2^attempt
            BackoffStrategy.FIXED -> config.baseDelayMs
        }.coerceAtMost(config.maxDelayMs)
    }

    private fun parseErrorMessage(errorBody: String?): String {
        return try {
            // In a real implementation, would parse JSON error response
            errorBody?.take(100) ?: "Unknown error"
        } catch (e: Exception) {
            "Error parsing response"
        }
    }
}

/**
 * API Error types
 */
sealed class ApiError {
    data class NetworkError(val message: String) : ApiError()
    data class Timeout(val message: String) : ApiError()
    data class Unauthorized(val message: String) : ApiError()
    data class Forbidden(val message: String) : ApiError()
    data class NotFound(val message: String) : ApiError()
    data class ServerError(val message: String) : ApiError()
    data class RateLimited(val message: String) : ApiError()
    data class BadRequest(val message: String) : ApiError()
    data class ValidationError(val message: String) : ApiError()
    data class Conflict(val message: String) : ApiError()
    data class Unknown(val message: String) : ApiError()
}

/**
 * Error classification types
 */
enum class ErrorType {
    NETWORK,
    TIMEOUT,
    HTTP,
    UNKNOWN
}

/**
 * Retry configuration
 */
data class RetryConfig(
    val maxAttempts: Int = MAX_RETRY_ATTEMPTS,
    val baseDelayMs: Long = RETRY_DELAY_MS,
    val maxDelayMs: Long = 30000L, // 30 seconds
    val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
    val retryOnNetworkError: Boolean = true,
    val retryOnTimeout: Boolean = true,
    val retryOnHttpError: Boolean = false, // Usually don't retry on client errors
    val retryOnUnknownError: Boolean = true
)

/**
 * Backoff strategies for retry delays
 */
enum class BackoffStrategy {
    FIXED,
    LINEAR,
    EXPONENTIAL
}