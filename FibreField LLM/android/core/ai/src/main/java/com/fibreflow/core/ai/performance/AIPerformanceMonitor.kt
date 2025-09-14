package com.fibreflow.core.ai.performance

import android.content.Context
import android.os.Debug
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Performance Monitor
 * Monitors and tracks AI model performance metrics in real-time
 */
@Singleton
class AIPerformanceMonitor @Inject constructor(
    private val context: Context
) {

    private val metricsHistory = ConcurrentHashMap<String, MutableList<ModelMetrics>>()
    private val activeOperations = ConcurrentHashMap<String, OperationContext>()
    private val operationCounter = AtomicLong(0)

    /**
     * Start monitoring an AI operation
     */
    fun startOperation(
        modelName: String,
        modelVersion: String,
        operationType: String,
        inputType: String
    ): String {
        val operationId = generateOperationId()
        val startTime = System.currentTimeMillis()
        val startMemory = getCurrentMemoryUsage()
        val startCpu = getCurrentCpuUsage()

        val context = OperationContext(
            operationId = operationId,
            modelName = modelName,
            modelVersion = modelVersion,
            operationType = operationType,
            inputType = inputType,
            startTime = startTime,
            startMemoryMB = startMemory,
            startCpuPercent = startCpu,
            startBatteryLevel = getCurrentBatteryLevel()
        )

        activeOperations[operationId] = context

        Timber.d("Started monitoring AI operation: $operationId for model $modelName")
        return operationId
    }

    /**
     * End monitoring an AI operation and record metrics
     */
    fun endOperation(
        operationId: String,
        accuracy: Float? = null,
        precision: Float? = null,
        recall: Float? = null,
        confidence: Float? = null,
        inputSize: Int = 0,
        outputSize: Int = 0,
        customMetrics: Map<String, Any> = emptyMap()
    ): ModelMetrics? {
        val context = activeOperations.remove(operationId) ?: return null

        val endTime = System.currentTimeMillis()
        val inferenceTime = endTime - context.startTime

        val endMemory = getCurrentMemoryUsage()
        val memoryUsage = endMemory - context.startMemoryMB

        val endCpu = getCurrentCpuUsage()
        val cpuUsage = endCpu - context.startCpuPercent

        val endBatteryLevel = getCurrentBatteryLevel()
        val batteryDrain = context.startBatteryLevel - endBatteryLevel

        // Calculate F1 score if precision and recall are available
        val f1Score = if (precision != null && recall != null && precision + recall > 0) {
            2 * (precision * recall) / (precision + recall)
        } else null

        val metrics = ModelMetrics(
            modelName = context.modelName,
            modelVersion = context.modelVersion,
            timestamp = endTime,
            inferenceTimeMs = inferenceTime,
            memoryUsageMB = memoryUsage,
            cpuUsagePercent = cpuUsage,
            batteryDrainPercent = batteryDrain,
            accuracy = accuracy,
            precision = precision,
            recall = recall,
            f1Score = f1Score,
            confidence = confidence,
            modelSizeMB = getModelSize(context.modelName),
            inputSize = inputSize,
            outputSize = outputSize,
            deviceModel = getDeviceModel(),
            androidVersion = getAndroidVersion(),
            availableMemoryMB = getAvailableMemory(),
            operationType = context.operationType,
            inputType = context.inputType,
            customMetrics = customMetrics
        )

        // Store metrics in history
        storeMetrics(metrics)

        // Check for performance alerts
        checkPerformanceAlerts(metrics)

        Timber.d("Ended monitoring AI operation: $operationId, inference time: ${inferenceTime}ms")
        return metrics
    }

    /**
     * Record a performance alert
     */
    fun recordAlert(
        modelName: String,
        alertType: AlertType,
        severity: AlertSeverity,
        message: String,
        metrics: ModelMetrics? = null,
        threshold: Any? = null,
        actualValue: Any? = null
    ) {
        val alert = PerformanceAlert(
            alertId = generateAlertId(),
            modelName = modelName,
            alertType = alertType,
            severity = severity,
            message = message,
            metrics = metrics,
            threshold = threshold,
            actualValue = actualValue
        )

        Timber.w("Performance alert: $message")

        // In a real implementation, this would be stored in a database
        // and potentially sent to monitoring systems
    }

    /**
     * Get aggregated metrics for a model
     */
    fun getAggregatedMetrics(
        modelName: String,
        timeRangeHours: Int = 24
    ): AggregatedModelMetrics? {
        val cutoffTime = System.currentTimeMillis() - (timeRangeHours * 60 * 60 * 1000L)
        val metrics = metricsHistory[modelName]?.filter { it.timestamp >= cutoffTime } ?: return null

        if (metrics.isEmpty()) return null

        val latestVersion = metrics.maxByOrNull { it.timestamp }?.modelVersion ?: "unknown"

        return AggregatedModelMetrics(
            modelName = modelName,
            modelVersion = latestVersion,
            timeRange = TimeRange(cutoffTime, System.currentTimeMillis()),
            averageInferenceTimeMs = metrics.map { it.inferenceTimeMs.toDouble() }.average(),
            maxInferenceTimeMs = metrics.maxOf { it.inferenceTimeMs },
            minInferenceTimeMs = metrics.minOf { it.inferenceTimeMs },
            p95InferenceTimeMs = calculatePercentile(metrics.map { it.inferenceTimeMs.toDouble() }, 95.0),
            averageMemoryUsageMB = metrics.map { it.memoryUsageMB.toDouble() }.average(),
            maxMemoryUsageMB = metrics.maxOf { it.memoryUsageMB },
            averageCpuUsagePercent = metrics.map { it.cpuUsagePercent.toDouble() }.average(),
            averageBatteryDrainPercent = metrics.map { it.batteryDrainPercent.toDouble() }.average(),
            averageAccuracy = metrics.mapNotNull { it.accuracy?.toDouble() }.average().takeIf { it.isNaN().not() },
            averagePrecision = metrics.mapNotNull { it.precision?.toDouble() }.average().takeIf { it.isNaN().not() },
            averageRecall = metrics.mapNotNull { it.recall?.toDouble() }.average().takeIf { it.isNaN().not() },
            averageF1Score = metrics.mapNotNull { it.f1Score?.toDouble() }.average().takeIf { it.isNaN().not() },
            totalInferences = metrics.size,
            successfulInferences = metrics.count { it.inferenceTimeMs < 5000 }, // Assuming <5s is successful
            failedInferences = metrics.count { it.inferenceTimeMs >= 5000 },
            performanceTrend = calculateTrend(metrics.map { it.performanceScore }),
            accuracyTrend = calculateTrend(metrics.mapNotNull { it.accuracyScore }),
            deviceStats = mapOf("current_device" to metrics.size) // Simplified
        )
    }

    /**
     * Get performance recommendations for a model
     */
    fun getOptimizationRecommendations(modelName: String): List<OptimizationRecommendation> {
        val aggregatedMetrics = getAggregatedMetrics(modelName) ?: return emptyList()

        val recommendations = mutableListOf<OptimizationRecommendation>()

        // High inference time recommendation
        if (aggregatedMetrics.averageInferenceTimeMs > 3000) {
            recommendations.add(
                OptimizationRecommendation(
                    recommendationId = generateRecommendationId(),
                    modelName = modelName,
                    recommendationType = RecommendationType.MODEL_QUANTIZATION,
                    priority = RecommendationPriority.HIGH,
                    title = "Model Quantization",
                    description = "Quantize the model to reduce inference time",
                    expectedImprovement = "30-50% faster inference",
                    implementationEffort = ImplementationEffort.MEDIUM
                )
            )
        }

        // High memory usage recommendation
        if (aggregatedMetrics.averageMemoryUsageMB > 400) {
            recommendations.add(
                OptimizationRecommendation(
                    recommendationId = generateRecommendationId(),
                    modelName = modelName,
                    recommendationType = RecommendationType.MEMORY_OPTIMIZATION,
                    priority = RecommendationPriority.HIGH,
                    title = "Memory Optimization",
                    description = "Optimize memory usage through tensor sharing and cleanup",
                    expectedImprovement = "20-40% less memory usage",
                    implementationEffort = ImplementationEffort.HIGH
                )
            )
        }

        // High battery drain recommendation
        if (aggregatedMetrics.averageBatteryDrainPercent > 3) {
            recommendations.add(
                OptimizationRecommendation(
                    recommendationId = generateRecommendationId(),
                    modelName = modelName,
                    recommendationType = RecommendationType.BATCH_PROCESSING,
                    priority = RecommendationPriority.MEDIUM,
                    title = "Batch Processing",
                    description = "Implement batch processing to reduce battery drain",
                    expectedImprovement = "15-25% less battery usage",
                    implementationEffort = ImplementationEffort.MEDIUM
                )
            )
        }

        return recommendations
    }

    /**
     * Clear old metrics data
     */
    fun clearOldMetrics(olderThanHours: Int = 168) { // 7 days default
        val cutoffTime = System.currentTimeMillis() - (olderThanHours * 60 * 60 * 1000L)

        metricsHistory.values.forEach { metrics ->
            metrics.removeIf { it.timestamp < cutoffTime }
        }

        Timber.d("Cleared metrics older than $olderThanHours hours")
    }

    /**
     * Get current performance statistics
     */
    fun getPerformanceStatistics(): PerformanceStatistics {
        val totalOperations = metricsHistory.values.sumOf { it.size }
        val totalModels = metricsHistory.size
        val activeOperations = activeOperations.size

        return PerformanceStatistics(
            totalOperations = totalOperations,
            totalModels = totalModels,
            activeOperations = activeOperations,
            averageInferenceTime = calculateAverageInferenceTime(),
            totalAlerts = 0, // Would be tracked separately
            timestamp = System.currentTimeMillis()
        )
    }

    // Private helper methods

    private fun storeMetrics(metrics: ModelMetrics) {
        metricsHistory.computeIfAbsent(metrics.modelName) { mutableListOf() }.add(metrics)

        // Keep only last 1000 metrics per model to prevent memory issues
        val modelMetrics = metricsHistory[metrics.modelName]!!
        if (modelMetrics.size > 1000) {
            modelMetrics.removeAt(0)
        }
    }

    private fun checkPerformanceAlerts(metrics: ModelMetrics) {
        // Check inference time
        if (metrics.inferenceTimeMs > 5000) {
            recordAlert(
                modelName = metrics.modelName,
                alertType = AlertType.HIGH_INFERENCE_TIME,
                severity = AlertSeverity.HIGH,
                message = "High inference time: ${metrics.inferenceTimeMs}ms",
                metrics = metrics,
                threshold = 5000,
                actualValue = metrics.inferenceTimeMs
            )
        }

        // Check memory usage
        if (metrics.memoryUsageMB > 600) {
            recordAlert(
                modelName = metrics.modelName,
                alertType = AlertType.HIGH_MEMORY_USAGE,
                severity = AlertSeverity.MEDIUM,
                message = "High memory usage: ${metrics.memoryUsageMB}MB",
                metrics = metrics,
                threshold = 600,
                actualValue = metrics.memoryUsageMB
            )
        }

        // Check battery drain
        if (metrics.batteryDrainPercent > 5) {
            recordAlert(
                modelName = metrics.modelName,
                alertType = AlertType.HIGH_BATTERY_DRAIN,
                severity = AlertSeverity.MEDIUM,
                message = "High battery drain: ${metrics.batteryDrainPercent}%",
                metrics = metrics,
                threshold = 5,
                actualValue = metrics.batteryDrainPercent
            )
        }
    }

    private fun calculatePercentile(values: List<Double>, percentile: Double): Double {
        if (values.isEmpty()) return 0.0
        val sortedValues = values.sorted()
        val index = (percentile / 100.0 * (sortedValues.size - 1)).toInt()
        return sortedValues[index]
    }

    private fun calculateTrend(values: List<Float>): Trend {
        if (values.size < 2) return Trend.UNKNOWN

        val recent = values.takeLast(5).average()
        val previous = values.dropLast(5).takeLast(5).average()

        val change = recent - previous
        val threshold = 0.05f // 5% change threshold

        return when {
            change > threshold -> Trend.IMPROVING
            change < -threshold -> Trend.DECLINING
            else -> Trend.STABLE
        }
    }

    private fun calculateAverageInferenceTime(): Double {
        val allMetrics = metricsHistory.values.flatten()
        return if (allMetrics.isNotEmpty()) {
            allMetrics.map { it.inferenceTimeMs.toDouble() }.average()
        } else 0.0
    }

    // System information methods (simplified implementations)
    private fun getCurrentMemoryUsage(): Float = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
    private fun getCurrentCpuUsage(): Float = 0.0f // Would need system calls to implement
    private fun getCurrentBatteryLevel(): Float = 0.0f // Would need BatteryManager
    private fun getModelSize(modelName: String): Float = 50.0f // Placeholder
    private fun getDeviceModel(): String = android.os.Build.MODEL
    private fun getAndroidVersion(): String = android.os.Build.VERSION.RELEASE
    private fun getAvailableMemory(): Float = Runtime.getRuntime().freeMemory() / (1024f * 1024f)

    // ID generation methods
    private fun generateOperationId(): String = "op_${operationCounter.incrementAndGet()}"
    private fun generateAlertId(): String = "alert_${System.currentTimeMillis()}"
    private fun generateRecommendationId(): String = "rec_${System.currentTimeMillis()}"
}

/**
 * Context for tracking an active AI operation
 */
private data class OperationContext(
    val operationId: String,
    val modelName: String,
    val modelVersion: String,
    val operationType: String,
    val inputType: String,
    val startTime: Long,
    val startMemoryMB: Float,
    val startCpuPercent: Float,
    val startBatteryLevel: Float
)

/**
 * Performance statistics summary
 */
data class PerformanceStatistics(
    val totalOperations: Int,
    val totalModels: Int,
    val activeOperations: Int,
    val averageInferenceTime: Double,
    val totalAlerts: Int,
    val timestamp: Long
)