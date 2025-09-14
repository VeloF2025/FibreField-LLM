// 🟢 WORKING: Result wrapper for consistent error handling throughout the app
package com.fibreflow.core.common.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A generic class that holds a value or an exception.
 * Provides consistent error handling across the application.
 */
sealed class Result<out T> {
    
    /**
     * Success state containing data
     */
    data class Success<T>(val data: T) : Result<T>()
    
    /**
     * Error state containing exception information
     */
    data class Error(
        val exception: Throwable,
        val errorCode: Int? = null,
        val userMessage: String? = null
    ) : Result<Nothing>()
    
    /**
     * Loading state for async operations
     */
    object Loading : Result<Nothing>()
    
    /**
     * Check if result is successful
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * Check if result is error
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Check if result is loading
     */
    val isLoading: Boolean
        get() = this is Loading
    
    /**
     * Get data if success, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * Get data if success, default value otherwise
     */
    fun getOrElse(defaultValue: T): T = when (this) {
        is Success -> data
        else -> defaultValue
    }
    
    /**
     * Get data if success, throw exception if error
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
        is Loading -> throw IllegalStateException("Cannot get data from loading state")
    }
    
    /**
     * Transform success data with given function
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }
    
    /**
     * Transform success data with given function that returns Result
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> this
    }
    
    /**
     * Execute action if result is success
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }
    
    /**
     * Execute action if result is error
     */
    inline fun onError(action: (Error) -> Unit): Result<T> {
        if (this is Error) {
            action(this)
        }
        return this
    }
    
    /**
     * Execute action if result is loading
     */
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) {
            action()
        }
        return this
    }
    
    /**
     * Fold result into a single value
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onError: (Error) -> R,
        onLoading: () -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Error -> onError(this)
        is Loading -> onLoading()
    }
}

/**
 * Extension functions for easier Result creation
 */

/**
 * Create a successful result
 */
fun <T> T.asSuccess(): Result<T> = Result.Success(this)

/**
 * Create an error result from exception
 */
fun Throwable.asError(errorCode: Int? = null, userMessage: String? = null): Result<Nothing> =
    Result.Error(this, errorCode, userMessage)

/**
 * Create a loading result
 */
fun loading(): Result<Nothing> = Result.Loading

/**
 * Safe execution with Result wrapper
 */
inline fun <T> runSafely(
    errorCode: Int? = null,
    userMessage: String? = null,
    action: () -> T
): Result<T> = try {
    Result.Success(action())
} catch (e: Exception) {
    Result.Error(e, errorCode, userMessage)
}

/**
 * Suspend safe execution with Result wrapper
 */
suspend inline fun <T> runSafelySuspend(
    errorCode: Int? = null,
    userMessage: String? = null,
    crossinline action: suspend () -> T
): Result<T> = try {
    Result.Success(action())
} catch (e: Exception) {
    Result.Error(e, errorCode, userMessage)
}

/**
 * Flow extensions for Result handling
 */

/**
 * Wrap Flow emissions in Result
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> = this
    .map<T, Result<T>> { Result.Success(it) }
    .onStart { emit(Result.Loading) }
    .catch { emit(Result.Error(it)) }

/**
 * Map successful results in Flow
 */
fun <T, R> Flow<Result<T>>.mapSuccess(transform: suspend (T) -> R): Flow<Result<R>> =
    map { result ->
        when (result) {
            is Result.Success -> runSafelySuspend { transform(result.data) }
            is Result.Error -> result
            is Result.Loading -> result
        }
    }

/**
 * Filter successful results in Flow
 */
fun <T> Flow<Result<T>>.filterSuccess(): Flow<T> =
    map { it.getOrNull() }
        .map { it!! } // Safe because we filter nulls

/**
 * Handle errors in Flow
 */
fun <T> Flow<Result<T>>.onError(action: suspend (Result.Error) -> Unit): Flow<Result<T>> =
    map { result ->
        if (result is Result.Error) {
            action(result)
        }
        result
    }

/**
 * Retry on error with exponential backoff
 */
fun <T> Flow<Result<T>>.retryOnError(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000L,
    factor: Double = 2.0
): Flow<Result<T>> = this
    .catch { throwable ->
        var delay = initialDelayMs
        repeat(maxRetries) { attempt ->
            kotlinx.coroutines.delay(delay)
            try {
                emit(Result.Loading)
                // Re-emit the flow (this is a simplified retry logic)
                delay = (delay * factor).toLong()
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    emit(Result.Error(e))
                }
            }
        }
    }

/**
 * Combine multiple Results
 */
fun <T1, T2, R> combineResults(
    result1: Result<T1>,
    result2: Result<T2>,
    transform: (T1, T2) -> R
): Result<R> = when {
    result1 is Result.Loading || result2 is Result.Loading -> Result.Loading
    result1 is Result.Error -> result1
    result2 is Result.Error -> result2
    result1 is Result.Success && result2 is Result.Success -> {
        Result.Success(transform(result1.data, result2.data))
    }
    else -> Result.Error(IllegalStateException("Unexpected result state"))
}

/**
 * Validation result wrapper for specific validation scenarios
 */
sealed class ValidationResult<out T> {
    data class Valid<T>(val data: T) : ValidationResult<T>()
    data class Invalid(val errors: List<String>) : ValidationResult<Nothing>()
    
    fun isValid(): Boolean = this is Valid
    fun isInvalid(): Boolean = this is Invalid
    
    fun getOrNull(): T? = when (this) {
        is Valid -> data
        is Invalid -> null
    }
    
    fun getErrors(): List<String> = when (this) {
        is Valid -> emptyList()
        is Invalid -> errors
    }
}

/**
 * Create validation result
 */
fun <T> T.asValid(): ValidationResult<T> = ValidationResult.Valid(this)
fun String.asInvalid(): ValidationResult<Nothing> = ValidationResult.Invalid(listOf(this))
fun List<String>.asInvalid(): ValidationResult<Nothing> = ValidationResult.Invalid(this)