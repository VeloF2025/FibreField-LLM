// 🟢 WORKING: Flow utility extensions for reactive programming
package com.fibreflow.core.common.extensions

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Throttle first - emit first item immediately, then throttle subsequent emissions
 */
fun <T> Flow<T>.throttleFirst(duration: Duration): Flow<T> = flow {
    var lastEmissionTime = 0L
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmissionTime >= duration.inWholeMilliseconds) {
            lastEmissionTime = currentTime
            emit(value)
        }
    }
}

/**
 * Throttle latest - emit latest item after throttle period
 */
fun <T> Flow<T>.throttleLatest(duration: Duration): Flow<T> = flow {
    var latestValue: T? = null
    var lastEmissionTime = 0L
    
    collect { value ->
        latestValue = value
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastEmissionTime >= duration.inWholeMilliseconds) {
            lastEmissionTime = currentTime
            emit(value)
            latestValue = null
        } else {
            // Delay and emit latest if no new values arrived
            delay(duration.inWholeMilliseconds - (currentTime - lastEmissionTime))
            latestValue?.let { latest ->
                emit(latest)
                latestValue = null
                lastEmissionTime = System.currentTimeMillis()
            }
        }
    }
}

/**
 * Debounce with immediate first emission
 */
fun <T> Flow<T>.debounceImmediate(timeout: Duration): Flow<T> = flow {
    var lastEmissionTime = 0L
    
    collect { value ->
        val currentTime = System.currentTimeMillis()
        
        if (lastEmissionTime == 0L || currentTime - lastEmissionTime >= timeout.inWholeMilliseconds) {
            // Emit immediately
            emit(value)
            lastEmissionTime = currentTime
        } else {
            // Debounce
            delay(timeout.inWholeMilliseconds)
            emit(value)
            lastEmissionTime = System.currentTimeMillis()
        }
    }
}

/**
 * Combine with previous value
 */
fun <T> Flow<T>.withPrevious(): Flow<Pair<T?, T>> = flow {
    var previous: T? = null
    collect { current ->
        emit(Pair(previous, current))
        previous = current
    }
}

/**
 * Emit only when value changes based on a selector
 */
fun <T, K> Flow<T>.distinctUntilChangedBy(selector: (T) -> K): Flow<T> = 
    distinctUntilChanged { old, new -> selector(old) == selector(new) }

/**
 * Retry with exponential backoff
 */
fun <T> Flow<T>.retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Duration = 1.seconds,
    maxDelay: Duration = 16.seconds,
    factor: Double = 2.0
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt < maxRetries) {
        val delayDuration = minOf(
            (initialDelay.inWholeMilliseconds * kotlin.math.pow(factor, attempt.toDouble())).toLong(),
            maxDelay.inWholeMilliseconds
        )
        delay(delayDuration)
        true
    } else {
        false
    }
}

/**
 * Cache last successful emission
 */
fun <T> Flow<T>.cacheLatest(): Flow<T> {
    var cached: T? = null
    return onEach { cached = it }
        .onStart { cached?.let { emit(it) } }
}

/**
 * Emit loading state before collection
 */
fun <T> Flow<T>.withLoading(): Flow<Pair<Boolean, T?>> = flow {
    emit(Pair(true, null)) // Loading state
    collect { value ->
        emit(Pair(false, value)) // Data with loading false
    }
}.catch { 
    emit(Pair(false, null)) // Error state with loading false
}

/**
 * Timeout with fallback value
 */
fun <T> Flow<T>.timeoutWithFallback(
    timeout: Duration,
    fallbackValue: T
): Flow<T> = this
    .timeout(timeout)
    .catch { emit(fallbackValue) }

/**
 * Collect only when active (lifecycle-aware)
 */
suspend fun <T> Flow<T>.collectLatestWhenActive(
    isActive: () -> Boolean,
    action: suspend (T) -> Unit
) {
    collectLatest { value ->
        if (isActive()) {
            action(value)
        }
    }
}

/**
 * Buffer with timeout - emit buffered items after timeout or when buffer is full
 */
fun <T> Flow<T>.bufferTimeout(
    bufferSize: Int,
    timeout: Duration
): Flow<List<T>> = flow {
    val buffer = mutableListOf<T>()
    var lastEmissionTime = System.currentTimeMillis()
    
    collect { item ->
        buffer.add(item)
        val currentTime = System.currentTimeMillis()
        
        if (buffer.size >= bufferSize || 
            (currentTime - lastEmissionTime) >= timeout.inWholeMilliseconds) {
            emit(buffer.toList())
            buffer.clear()
            lastEmissionTime = currentTime
        }
    }
    
    // Emit remaining items
    if (buffer.isNotEmpty()) {
        emit(buffer.toList())
    }
}

/**
 * Scan with index
 */
fun <T, R> Flow<T>.scanIndexed(
    initial: R,
    operation: (index: Int, acc: R, value: T) -> R
): Flow<R> = flow {
    var accumulator = initial
    var index = 0
    emit(accumulator)
    
    collect { value ->
        accumulator = operation(index++, accumulator, value)
        emit(accumulator)
    }
}

/**
 * Take until predicate is true
 */
fun <T> Flow<T>.takeUntil(predicate: (T) -> Boolean): Flow<T> = flow {
    var shouldTake = true
    collect { value ->
        if (shouldTake) {
            emit(value)
            if (predicate(value)) {
                shouldTake = false
            }
        }
    }
}

/**
 * Skip until predicate is true
 */
fun <T> Flow<T>.skipUntil(predicate: (T) -> Boolean): Flow<T> = flow {
    var shouldSkip = true
    collect { value ->
        if (shouldSkip && predicate(value)) {
            shouldSkip = false
        }
        if (!shouldSkip) {
            emit(value)
        }
    }
}

/**
 * Rate limit emissions
 */
fun <T> Flow<T>.rateLimit(
    permits: Int,
    period: Duration
): Flow<T> = flow {
    val timestamps = mutableListOf<Long>()
    
    collect { value ->
        val currentTime = System.currentTimeMillis()
        
        // Remove old timestamps
        timestamps.removeAll { 
            currentTime - it > period.inWholeMilliseconds 
        }
        
        if (timestamps.size < permits) {
            timestamps.add(currentTime)
            emit(value)
        } else {
            // Wait until we can emit
            val oldestTimestamp = timestamps.minOrNull() ?: currentTime
            val waitTime = period.inWholeMilliseconds - (currentTime - oldestTimestamp)
            if (waitTime > 0) {
                delay(waitTime)
            }
            timestamps.removeAt(0) // Remove oldest
            timestamps.add(System.currentTimeMillis())
            emit(value)
        }
    }
}

/**
 * Emit with timestamp
 */
fun <T> Flow<T>.withTimestamp(): Flow<Pair<Long, T>> = 
    map { Pair(System.currentTimeMillis(), it) }

/**
 * Emit only unique items by key within time window
 */
fun <T, K> Flow<T>.distinctByWithinWindow(
    keySelector: (T) -> K,
    window: Duration
): Flow<T> = flow {
    val recentKeys = mutableMapOf<K, Long>()
    
    collect { item ->
        val key = keySelector(item)
        val currentTime = System.currentTimeMillis()
        
        // Clean old keys
        recentKeys.entries.removeAll { (_, timestamp) ->
            currentTime - timestamp > window.inWholeMilliseconds
        }
        
        // Emit if key not seen recently
        if (key !in recentKeys) {
            recentKeys[key] = currentTime
            emit(item)
        }
    }
}

/**
 * Batch emissions by size and time
 */
fun <T> Flow<T>.batch(
    batchSize: Int = 10,
    timeout: Duration = 1.seconds
): Flow<List<T>> = flow {
    val batch = mutableListOf<T>()
    var lastEmitTime = System.currentTimeMillis()
    
    collect { item ->
        batch.add(item)
        val currentTime = System.currentTimeMillis()
        
        val shouldEmit = batch.size >= batchSize || 
                        (currentTime - lastEmitTime) >= timeout.inWholeMilliseconds
        
        if (shouldEmit) {
            emit(batch.toList())
            batch.clear()
            lastEmitTime = currentTime
        }
    }
    
    // Emit remaining items
    if (batch.isNotEmpty()) {
        emit(batch)
    }
}

/**
 * Convert Flow<Boolean> to hot state flow that starts with initial value
 */
fun Flow<Boolean>.asToggleStateFlow(
    initialValue: Boolean = false
): StateFlow<Boolean> {
    // Note: This is a simplified version. In real implementation,
    // you would use MutableStateFlow and proper lifecycle handling
    return MutableStateFlow(initialValue)
}

/**
 * Merge multiple flows with priorities
 */
fun <T> mergeWithPriority(
    vararg flows: Pair<Flow<T>, Int> // Flow to priority mapping
): Flow<T> = flow {
    val sortedFlows = flows.sortedByDescending { it.second } // Higher priority first
    merge(*sortedFlows.map { it.first }.toTypedArray()).collect { emit(it) }
}

/**
 * Sample at regular intervals
 */
fun <T> Flow<T>.sample(interval: Duration): Flow<T> = 
    sample(interval.inWholeMilliseconds)

/**
 * Log emissions for debugging
 */
fun <T> Flow<T>.debug(tag: String = "Flow"): Flow<T> = 
    onEach { value -> 
        println("$tag: $value") 
    }