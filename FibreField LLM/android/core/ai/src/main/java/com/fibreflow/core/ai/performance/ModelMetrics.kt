package com.fibreflow.core.ai.performance

/**
 * Data classes for AI model performance metrics
 */

/**
 * Comprehensive metrics for AI model performance
 */
data class ModelMetrics(
    val modelName: String,
    val modelVersion: String,
    val timestamp: Long = System.currentTimeMillis(),

    // Performance metrics
    val inferenceTimeMs: Long,
    val memoryUsageMB: Float,
    val cpuUsagePercent: Float,
    val batteryDrainPercent: Float,

    // Accuracy metrics
    val accuracy: Float? = null, // 0.0 to 1.0
    val precision: Float? = null, // 0.0 to 1.0
    val recall: Float? = null, // 0.0 to 1.0
    val f1Score: Float? = null, // 0.0 to 1.0

    // Model-specific metrics
    val confidence: Float? = null, // 0.0 to 1.0
    val falsePositiveRate: Float? = null,
    val falseNegativeRate: Float? = null,

    // Resource usage
    val modelSizeMB: Float,
    val inputSize: Int, // bytes
    val outputSize: Int, // bytes

    // Device context
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val availableMemoryMB: Float? = null,

    // Additional metadata
    val operationType: String, // "inference", "training", "validation"
    val inputType: String, // "image", "text", "audio"
    val customMetrics: Map<String, Any> = emptyMap()
) {

    /**
     * Check if metrics indicate good performance
     */
    val isPerformanceGood: Boolean
        get() = inferenceTimeMs < 3000 && // Less than 3 seconds
               memoryUsageMB < 500 && // Less than 500MB
               batteryDrainPercent < 5 // Less than 5% battery drain

    /**
     * Check if accuracy metrics are available and good
     */
    val isAccuracyGood: Boolean
        get() = accuracy != null && accuracy > 0.8f &&
               (f1Score == null || f1Score > 0.75f)

    /**
     * Get performance score (0.0 to 1.0)
     */
    val performanceScore: Float
        get() {
            val timeScore = (1.0f - (inferenceTimeMs.toFloat() / 5000.0f)).coerceIn(0.0f, 1.0f)
            val memoryScore = (1.0f - (memoryUsageMB / 1000.0f)).coerceIn(0.0f, 1.0f)
            val batteryScore = (1.0f - (batteryDrainPercent / 10.0f)).coerceIn(0.0f, 1.0f)

            return (timeScore + memoryScore + batteryScore) / 3.0f
        }

    /**
     * Get accuracy score (0.0 to 1.0)
     */
    val accuracyScore: Float
        get() = when {
            accuracy != null && f1Score != null -> (accuracy + f1Score) / 2.0f
            accuracy != null -> accuracy
            f1Score != null -> f1Score
            else -> 0.0f
        }
}

/**
 * Aggregated metrics for a model over time
 */
data class AggregatedModelMetrics(
    val modelName: String,
    val modelVersion: String,
    val timeRange: TimeRange,

    // Aggregated performance metrics
    val averageInferenceTimeMs: Double,
    val maxInferenceTimeMs: Long,
    val minInferenceTimeMs: Long,
    val p95InferenceTimeMs: Double, // 95th percentile

    val averageMemoryUsageMB: Double,
    val maxMemoryUsageMB: Float,
    val averageCpuUsagePercent: Double,
    val averageBatteryDrainPercent: Double,

    // Aggregated accuracy metrics
    val averageAccuracy: Double? = null,
    val averagePrecision: Double? = null,
    val averageRecall: Double? = null,
    val averageF1Score: Double? = null,

    // Usage statistics
    val totalInferences: Int,
    val successfulInferences: Int,
    val failedInferences: Int,

    // Performance trends
    val performanceTrend: Trend, // IMPROVING, DECLINING, STABLE
    val accuracyTrend: Trend,

    // Device statistics
    val deviceStats: Map<String, Int> // device model -> count
) {

    val successRate: Double
        get() = if (totalInferences > 0) successfulInferences.toDouble() / totalInferences else 0.0

    val failureRate: Double
        get() = 1.0 - successRate
}

/**
 * Time range for metrics aggregation
 */
data class TimeRange(
    val startTime: Long,
    val endTime: Long
) {
    val durationMs: Long
        get() = endTime - startTime

    val durationHours: Double
        get() = durationMs / (1000.0 * 60.0 * 60.0)
}

/**
 * Performance trend indicator
 */
enum class Trend {
    IMPROVING,
    DECLINING,
    STABLE,
    UNKNOWN
}

/**
 * Model performance alert
 */
data class PerformanceAlert(
    val alertId: String,
    val modelName: String,
    val alertType: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metrics: ModelMetrics? = null,
    val threshold: Any? = null,
    val actualValue: Any? = null
)

/**
 * Types of performance alerts
 */
enum class AlertType {
    HIGH_INFERENCE_TIME,
    HIGH_MEMORY_USAGE,
    HIGH_BATTERY_DRAIN,
    LOW_ACCURACY,
    HIGH_ERROR_RATE,
    MODEL_DEGRADATION,
    DEVICE_COMPATIBILITY
}

/**
 * Alert severity levels
 */
enum class AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Model performance benchmark
 */
data class ModelBenchmark(
    val benchmarkId: String,
    val modelName: String,
    val modelVersion: String,
    val benchmarkType: BenchmarkType,
    val deviceModel: String,
    val androidVersion: String,

    // Benchmark results
    val score: Float,
    val normalizedScore: Float, // 0.0 to 1.0
    val percentileRank: Float? = null, // Compared to other devices

    val inferenceTimeMs: Long,
    val memoryUsageMB: Float,
    val batteryDrainPercent: Float,

    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Types of benchmarks
 */
enum class BenchmarkType {
    INFERENCE_SPEED,
    MEMORY_EFFICIENCY,
    BATTERY_EFFICIENCY,
    ACCURACY,
    COMPREHENSIVE
}

/**
 * Model optimization recommendation
 */
data class OptimizationRecommendation(
    val recommendationId: String,
    val modelName: String,
    val recommendationType: RecommendationType,
    val priority: RecommendationPriority,
    val title: String,
    val description: String,
    val expectedImprovement: String,
    val implementationEffort: ImplementationEffort,
    val timestamp: Long = System.currentTimeMillis(),
    val currentMetrics: ModelMetrics? = null,
    val projectedMetrics: ModelMetrics? = null
)

/**
 * Types of optimization recommendations
 */
enum class RecommendationType {
    MODEL_QUANTIZATION,
    INPUT_OPTIMIZATION,
    MEMORY_OPTIMIZATION,
    BATCH_PROCESSING,
    CACHING_STRATEGY,
    HARDWARE_ACCELERATION,
    MODEL_VERSION_UPDATE
}

/**
 * Recommendation priority levels
 */
enum class RecommendationPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Implementation effort levels
 */
enum class ImplementationEffort {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}