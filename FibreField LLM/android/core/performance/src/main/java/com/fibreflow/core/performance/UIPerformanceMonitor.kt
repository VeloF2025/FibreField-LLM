package com.fibreflow.core.performance

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI Performance Monitor for tracking user interface responsiveness
 * Monitors frame drops, layout times, and user interaction delays
 */
@Singleton
class UIPerformanceMonitor @Inject constructor(
    private val performanceMonitor: PerformanceMonitor
) {

    companion object {
        private const val TAG = "UIPerformanceMonitor"
        private const val TARGET_FRAME_TIME_MS = 16.67f // 60 FPS
        private const val SLOW_FRAME_THRESHOLD_MS = 32f // 30 FPS
        private const val FROZEN_FRAME_THRESHOLD_MS = 700f // Android's frozen frame threshold
        private const val MONITORING_INTERVAL_MS = 1000L // 1 second
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val uiMetrics = ConcurrentHashMap<String, UIMetrics>()
    private val frameMetrics = ConcurrentHashMap<String, FrameMetricsData>()
    private var choreographerCallback: Choreographer.FrameCallback? = null
    private var isMonitoring = false

    private val frameCount = AtomicLong(0)
    private val slowFrameCount = AtomicLong(0)
    private val frozenFrameCount = AtomicLong(0)

    /**
     * Start UI performance monitoring
     */
    fun startMonitoring(activity: Activity) {
        if (isMonitoring) return

        isMonitoring = true
        resetMetrics()

        // Start frame monitoring
        startFrameMonitoring()

        // Start periodic UI responsiveness checks
        startPeriodicMonitoring()

        Timber.d("UI Performance monitoring started")
    }

    /**
     * Stop UI performance monitoring
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false

        // Stop frame monitoring
        stopFrameMonitoring()

        // Cancel periodic monitoring
        mainHandler.removeCallbacksAndMessages(null)

        Timber.d("UI Performance monitoring stopped")
    }

    /**
     * Record UI interaction performance
     */
    fun recordInteraction(
        interactionName: String,
        startTime: Long,
        endTime: Long,
        success: Boolean = true
    ) {
        val duration = endTime - startTime
        val interactionId = generateInteractionId()

        val metrics = UIMetrics(
            interactionId = interactionId,
            interactionName = interactionName,
            durationMs = duration,
            success = success,
            timestamp = System.currentTimeMillis()
        )

        uiMetrics[interactionId] = metrics

        // Check for slow interactions
        if (duration > 100) { // Over 100ms is considered slow
            Timber.w("Slow UI interaction: $interactionName took ${duration}ms")

            // Record performance alert
            performanceMonitor.recordOperation(
                "ui_interaction_$interactionName",
                duration.toInt(),
                success
            )
        }

        Timber.v("Recorded UI interaction: $interactionName, duration: ${duration}ms")
    }

    /**
     * Record layout inflation time
     */
    fun recordLayoutInflation(layoutName: String, durationMs: Long) {
        val layoutId = generateLayoutId()

        val metrics = LayoutMetrics(
            layoutId = layoutId,
            layoutName = layoutName,
            inflationTimeMs = durationMs,
            timestamp = System.currentTimeMillis()
        )

        // Store in UI metrics map with layout prefix
        uiMetrics["layout_$layoutId"] = metrics

        if (durationMs > 50) { // Over 50ms is slow
            Timber.w("Slow layout inflation: $layoutName took ${durationMs}ms")
        }

        Timber.v("Recorded layout inflation: $layoutName, duration: ${durationMs}ms")
    }

    /**
     * Get UI performance report
     */
    fun getUIPerformanceReport(): UIPerformanceReport {
        val totalFrames = frameCount.get()
        val slowFrames = slowFrameCount.get()
        val frozenFrames = frozenFrameCount.get()

        val frameDropRate = if (totalFrames > 0) (slowFrames.toDouble() / totalFrames) * 100 else 0.0
        val frozenFrameRate = if (totalFrames > 0) (frozenFrames.toDouble() / totalFrames) * 100 else 0.0

        // Calculate average interaction times
        val interactions = uiMetrics.values.filterIsInstance<UIMetrics>()
        val averageInteractionTime = interactions.map { it.durationMs.toDouble() }.average()

        // Calculate layout performance
        val layouts = uiMetrics.values.filterIsInstance<LayoutMetrics>()
        val averageLayoutTime = layouts.map { it.inflationTimeMs.toDouble() }.average()

        return UIPerformanceReport(
            monitoringActive = isMonitoring,
            totalFrames = totalFrames,
            slowFrames = slowFrames,
            frozenFrames = frozenFrames,
            frameDropRate = frameDropRate,
            frozenFrameRate = frozenFrameRate,
            averageInteractionTimeMs = averageInteractionTime,
            averageLayoutTimeMs = averageLayoutTime,
            totalInteractions = interactions.size,
            totalLayouts = layouts.size,
            isWithinTargets = isWithinUITargets(frameDropRate, frozenFrameRate, averageInteractionTime),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get UI performance recommendations
     */
    fun getUIPerformanceRecommendations(): List<UIPerformanceRecommendation> {
        val report = getUIPerformanceReport()
        val recommendations = mutableListOf<UIPerformanceRecommendation>()

        // Frame drop recommendations
        if (report.frameDropRate > 10.0) {
            recommendations.add(
                UIPerformanceRecommendation(
                    type = UIRecommendationType.OPTIMIZE_RENDERING,
                    priority = UIRecommendationPriority.HIGH,
                    title = "Optimize UI Rendering",
                    description = "Frame drop rate is ${report.frameDropRate}%, which affects user experience",
                    expectedImprovement = "Reduce frame drops by 50-70%",
                    implementationEffort = UIImplementationEffort.MEDIUM
                )
            )
        }

        // Frozen frame recommendations
        if (report.frozenFrameRate > 1.0) {
            recommendations.add(
                UIPerformanceRecommendation(
                    type = UIRecommendationType.REDUCE_BLOCKING_OPERATIONS,
                    priority = UIRecommendationPriority.CRITICAL,
                    title = "Eliminate Blocking Operations",
                    description = "Frozen frame rate is ${report.frozenFrameRate}%, causing poor user experience",
                    expectedImprovement = "Eliminate frozen frames completely",
                    implementationEffort = UIImplementationEffort.HIGH
                )
            )
        }

        // Interaction time recommendations
        if (report.averageInteractionTimeMs > 100) {
            recommendations.add(
                UIPerformanceRecommendation(
                    type = UIRecommendationType.OPTIMIZE_INTERACTIONS,
                    priority = UIRecommendationPriority.MEDIUM,
                    title = "Optimize User Interactions",
                    description = "Average interaction time is ${report.averageInteractionTimeMs}ms, should be under 100ms",
                    expectedImprovement = "20-40% faster interactions",
                    implementationEffort = UIImplementationEffort.MEDIUM
                )
            )
        }

        // Layout inflation recommendations
        if (report.averageLayoutTimeMs > 50) {
            recommendations.add(
                UIPerformanceRecommendation(
                    type = UIRecommendationType.OPTIMIZE_LAYOUTS,
                    priority = UIRecommendationPriority.MEDIUM,
                    title = "Optimize Layout Inflation",
                    description = "Average layout inflation time is ${report.averageLayoutTimeMs}ms",
                    expectedImprovement = "30-50% faster layout inflation",
                    implementationEffort = UIImplementationEffort.LOW
                )
            )
        }

        return recommendations.sortedByDescending { it.priority.ordinal }
    }

    /**
     * Clear UI metrics data
     */
    fun clearMetrics() {
        uiMetrics.clear()
        frameMetrics.clear()
        resetMetrics()
        Timber.d("UI performance metrics cleared")
    }

    // Private helper methods

    private fun startFrameMonitoring() {
        choreographerCallback = Choreographer.FrameCallback { frameTimeNanos ->
            frameCount.incrementAndGet()

            // Calculate frame time in milliseconds
            val frameTimeMs = (frameTimeNanos - getLastFrameTimeNanos()) / 1_000_000.0f

            // Track slow frames
            if (frameTimeMs > SLOW_FRAME_THRESHOLD_MS) {
                slowFrameCount.incrementAndGet()
            }

            // Track frozen frames
            if (frameTimeMs > FROZEN_FRAME_THRESHOLD_MS) {
                frozenFrameCount.incrementAndGet()
                Timber.w("Frozen frame detected: ${frameTimeMs}ms")
            }

            // Continue monitoring if active
            if (isMonitoring) {
                Choreographer.getInstance().postFrameCallback(choreographerCallback)
            }
        }

        Choreographer.getInstance().postFrameCallback(choreographerCallback)
    }

    private fun stopFrameMonitoring() {
        choreographerCallback?.let {
            Choreographer.getInstance().removeFrameCallback(it)
        }
        choreographerCallback = null
    }

    private fun startPeriodicMonitoring() {
        val monitoringRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return

                // Perform periodic UI responsiveness checks
                checkUIResponsiveness()

                // Schedule next check
                mainHandler.postDelayed(this, MONITORING_INTERVAL_MS)
            }
        }

        mainHandler.post(monitoringRunnable)
    }

    private fun checkUIResponsiveness() {
        val startTime = System.currentTimeMillis()

        // Post a task to main thread and measure delay
        mainHandler.post {
            val delay = System.currentTimeMillis() - startTime

            if (delay > 100) { // Over 100ms delay indicates UI thread congestion
                Timber.w("UI thread responsiveness issue: ${delay}ms delay")

                // Record performance alert
                performanceMonitor.recordOperation(
                    "ui_responsiveness_check",
                    delay.toInt(),
                    true
                )
            }
        }
    }

    private fun resetMetrics() {
        frameCount.set(0)
        slowFrameCount.set(0)
        frozenFrameCount.set(0)
    }

    private fun isWithinUITargets(
        frameDropRate: Double,
        frozenFrameRate: Double,
        averageInteractionTime: Double
    ): Boolean {
        return frameDropRate < 5.0 && // Less than 5% frame drops
               frozenFrameRate < 0.5 && // Less than 0.5% frozen frames
               averageInteractionTime < 100 // Less than 100ms average interaction time
    }

    private fun getLastFrameTimeNanos(): Long {
        // In a real implementation, you'd track the previous frame time
        // For now, return current time minus target frame time
        return System.nanoTime() - (TARGET_FRAME_TIME_MS * 1_000_000).toLong()
    }

    private fun generateInteractionId(): String = "ui_${System.currentTimeMillis()}"
    private fun generateLayoutId(): String = "layout_${System.currentTimeMillis()}"
}

/**
 * Data classes for UI performance monitoring
 */

data class UIMetrics(
    val interactionId: String,
    val interactionName: String,
    val durationMs: Long,
    val success: Boolean,
    val timestamp: Long
)

data class LayoutMetrics(
    val layoutId: String,
    val layoutName: String,
    val inflationTimeMs: Long,
    val timestamp: Long
)

data class FrameMetricsData(
    val frameId: String,
    val frameTimeMs: Float,
    val isSlowFrame: Boolean,
    val isFrozenFrame: Boolean,
    val timestamp: Long
)

data class UIPerformanceReport(
    val monitoringActive: Boolean,
    val totalFrames: Long,
    val slowFrames: Long,
    val frozenFrames: Long,
    val frameDropRate: Double,
    val frozenFrameRate: Double,
    val averageInteractionTimeMs: Double,
    val averageLayoutTimeMs: Double,
    val totalInteractions: Int,
    val totalLayouts: Int,
    val isWithinTargets: Boolean,
    val timestamp: Long
)

data class UIPerformanceRecommendation(
    val type: UIRecommendationType,
    val priority: UIRecommendationPriority,
    val title: String,
    val description: String,
    val expectedImprovement: String,
    val implementationEffort: UIImplementationEffort
)

enum class UIRecommendationType {
    OPTIMIZE_RENDERING,
    REDUCE_BLOCKING_OPERATIONS,
    OPTIMIZE_INTERACTIONS,
    OPTIMIZE_LAYOUTS,
    IMPLEMENT_LAZY_LOADING,
    CACHE_FREQUENTLY_USED_DATA
}

enum class UIRecommendationPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class UIImplementationEffort {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}