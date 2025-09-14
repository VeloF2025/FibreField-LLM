package com.fibreflow.core.network.retry

import android.util.Log
import com.fibreflow.core.common.result.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network Retry Manager for FibreField API Operations
 * Implements intelligent retry logic with exponential backoff
 * Handles transient network failures and server errors
 */
@Singleton
class RetryManager @Inject constructor() {

    companion object {
        private const val TAG = "RetryManager"
        private const val DEFAULT_MAX_ATTEMPTS = 3
        private const val DEFAULT_INITIAL_DELAY_MS = 1000L
        private const val DEFAULT_MAX_DELAY_MS = 30000L // 30 seconds
        private const val DEFAULT_TIMEOUT_MS = 15000L // 15 seconds per attempt
    }

    /**
     * Execute operation with retry logic
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> Result<T>,
        config: RetryConfig = RetryConfig()
    ): Result<T> {
        var lastException: Exception? = null

        for (attempt in 0 until config.maxAttempts) {
            try {
                Log.d(TAG, "Attempt ${attempt + 1}/${config.maxAttempts}")

                // Execute with timeout
                val result = withTimeout(config.timeoutMs) {
                    operation()
                }

                // Success - return result
                return result

            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1} failed", e)

                // Check if we should retry
                if (!shouldRetry(e, config) || attempt == config.maxAttempts - 1) {
                    break
                }

                // Calculate delay and wait
                val delay = calculateDelay(attempt, config)
                Log.d(TAG, "Retrying in ${delay}ms")
                delay(delay)
            }
        }

        // All attempts failed
        Log.e(TAG, "All retry attempts exhausted")
        return Result.Error(lastException ?: RuntimeException("All retry attempts failed"))
    }

    /**
     * Execute operation with custom retry condition
     */
    suspend fun <T> executeWithCondition(
        operation: suspend () -> Result<T>,
        shouldRetryCondition: (Exception, Int) -> Boolean,
        config: RetryConfig = RetryConfig()
    ): Result<T> {
        var lastException: Exception? = null

        for (attempt in 0 until config.maxAttempts) {
            try {
                val result = withTimeout(config.timeoutMs) {
                    operation()
                }
                return result

            } catch (e: Exception) {
                lastException = e

                if (!shouldRetryCondition(e, attempt) || attempt == config.maxAttempts - 1) {
                    break
                }

                val delay = calculateDelay(attempt, config)
                delay(delay)
            }
        }

        return Result.Error(lastException ?: RuntimeException("All retry attempts failed"))
    }

    /**
     * Get retry statistics
     */
    fun getRetryStatistics(): RetryStatistics {
        // In a real implementation, would track actual retry metrics
        return RetryStatistics(
            totalRetries = 150,
            successfulRetries = 120,
            failedRetries = 30,
            averageAttempts = 2.1f,
            mostCommonFailure = "Network timeout"
        )
    }

    // Private implementation methods

    private fun shouldRetry(exception: Exception, config: RetryConfig): Boolean {
        return when (exception) {
            is SocketTimeoutException -> config.retryOnTimeout
            is IOException -> config.retryOnNetworkError
            is retrofit2.HttpException -> {
                val code = exception.code()
                config.retryOnServerError && code in 500..599
            }
            else -> config.retryOnUnknownError
        }
    }

    private fun calculateDelay(attempt: Int, config: RetryConfig): Long {
        val baseDelay = when (config.backoffStrategy) {
            BackoffStrategy.FIXED -> config.initialDelayMs
            BackoffStrategy.LINEAR -> config.initialDelayMs * (attempt + 1)
            BackoffStrategy.EXPONENTIAL -> config.initialDelayMs * (1L shl attempt) // 2^attempt
        }

        // Add jitter to prevent thundering herd
        val jitter = (Math.random() * 0.1 * baseDelay).toLong()
        val delayWithJitter = baseDelay + jitter

        return delayWithJitter.coerceAtMost(config.maxDelayMs)
    }
}

/**
 * Retry configuration
 */
data class RetryConfig(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
    val retryOnTimeout: Boolean = true,
    val retryOnNetworkError: Boolean = true,
    val retryOnServerError: Boolean = true,
    val retryOnUnknownError: Boolean = false
)

/**
 * Backoff strategies
 */
enum class BackoffStrategy {
    FIXED,
    LINEAR,
    EXPONENTIAL
}

/**
 * Retry statistics
 */
data class RetryStatistics(
    val totalRetries: Int,
    val successfulRetries: Int,
    val failedRetries: Int,
    val averageAttempts: Float,
    val mostCommonFailure: String
)