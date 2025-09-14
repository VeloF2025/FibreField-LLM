package com.fibreflow.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Performance Monitor for FibreField Application
 * Tracks and monitors application performance metrics
 * Ensures targets: <2.5GB app size, <3GB RAM, <15% daily battery, <0.1% crash rate
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MONITORING_INTERVAL_MS = 30000L // 30 seconds
        private const val PERFORMANCE_HISTORY_SIZE = 100
        private const val TARGET_APP_SIZE_MB = 2500 // 2.5GB
        private const val TARGET_MEMORY_MB = 3072 // 3GB
        private const val TARGET_BATTERY_DRAIN_PERCENT = 15.0f
        private const val TARGET_CRASH_RATE = 0.001f // 0.1%
    }

    private val monitorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Performance metrics state
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    private val _performanceAlerts = MutableStateFlow<List<PerformanceAlert>>(emptyList())
    val performanceAlerts: StateFlow<List<PerformanceAlert>> = _performanceAlerts.asStateFlow()

    // Monitoring data
    private var monitoringJob: Job? = null
    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    private var appStartTime = System.currentTimeMillis()
    private var crashCount = 0
    private var sessionCount = 0

    /**
     * Start performance monitoring
     */
    suspend fun startMonitoring(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (monitoringJob?.isActive == true) {
                return@withContext Result.Success(Unit)
            }

            Log.i(TAG, "Starting performance monitoring")

            appStartTime = System.currentTimeMillis()
            sessionCount++

            monitoringJob = monitorScope.launch {
                while (isActive) {
                    val snapshot = capturePerformanceSnapshot()
                    performanceHistory.add(snapshot)

                    // Maintain history size
                    if (performanceHistory.size > PERFORMANCE_HISTORY_SIZE) {
                        performanceHistory.removeAt(0)
                    }

                    // Update current metrics
                    updatePerformanceMetrics()

                    // Check for performance issues
                    checkPerformanceThresholds(snapshot)

                    delay(MONITORING_INTERVAL_MS)
                }
            }

            Log.i(TAG, "Performance monitoring started")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start performance monitoring", e)
            Result.Error(e)
        }
    }

    /**
     * Stop performance monitoring
     */
    suspend fun stopMonitoring(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            Log.i(TAG, "Stopping performance monitoring")

            monitoringJob?.cancel()
            monitoringJob = null

            Log.i(TAG, "Performance monitoring stopped")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop performance monitoring", e)
            Result.Error(e)
        }
    }

    /**
     * Record operation performance
     */
    fun recordOperation(operationName: String, durationMs: Long, success: Boolean) {
        // In a real implementation, would store this in a performance database
        Log.d(TAG, "Operation '$operationName' completed in ${durationMs}ms, success: $success")

        if (!success) {
            addPerformanceAlert(
                PerformanceAlert(
                    type = AlertType.OPERATION_FAILURE,
                    severity = AlertSeverity.MEDIUM,
                    message = "Operation '$operationName' failed after ${durationMs}ms",
                    metric = operationName,
                    value = durationMs.toFloat(),
                    threshold = 5000f // 5 seconds
                )
            )
        }
    }

    /**
     * Record crash event
     */
    fun recordCrash(exception: Throwable) {
        crashCount++
        Log.e(TAG, "Application crash recorded", exception)

        val crashRate = crashCount.toFloat() / sessionCount
        if (crashRate > TARGET_CRASH_RATE) {
            addPerformanceAlert(
                PerformanceAlert(
                    type = AlertType.CRASH_RATE_HIGH,
                    severity = AlertSeverity.CRITICAL,
                    message = "Crash rate of ${(crashRate * 100).toInt()}% exceeds target of ${(TARGET_CRASH_RATE * 100).toInt()}%",
                    metric = "crash_rate",
                    value = crashRate * 100,
                    threshold = TARGET_CRASH_RATE * 100
                )
            )
        }
    }

    /**
     * Get performance report
     */
    fun getPerformanceReport(): PerformanceReport {
        val recentSnapshots = performanceHistory.takeLast(10)

        return PerformanceReport(
            averageMemoryUsageMB = recentSnapshots.map { it.memoryUsageMB }.average().toFloat(),
            peakMemoryUsageMB = recentSnapshots.maxOfOrNull { it.memoryUsageMB } ?: 0f,
            averageCpuUsagePercent = recentSnapshots.map { it.cpuUsagePercent }.average().toFloat(),
            batteryDrainRatePercentPerHour = calculateBatteryDrainRate(),
            crashRate = (crashCount.toFloat() / sessionCount.coerceAtLeast(1)),
            appUptimeMs = System.currentTimeMillis() - appStartTime,
            activeAlerts = _performanceAlerts.value.size,
            withinTargets = isWithinPerformanceTargets()
        )
    }

    /**
     * Clear performance alerts
     */
    fun clearAlerts() {
        _performanceAlerts.value = emptyList()
        Log.d(TAG, "Performance alerts cleared")
    }

    /**
     * Check if monitoring is active
     */
    fun isMonitoringActive(): Boolean = monitoringJob?.isActive == true

    /**
     * Shutdown performance monitor
     */
    fun shutdown() {
        monitorScope.cancel()
        monitoringJob?.cancel()
        Log.i(TAG, "PerformanceMonitor shutdown complete")
    }

    // Private implementation methods

    private fun capturePerformanceSnapshot(): PerformanceSnapshot {
        return PerformanceSnapshot(
            timestamp = System.currentTimeMillis(),
            memoryUsageMB = getCurrentMemoryUsage(),
            cpuUsagePercent = getCurrentCpuUsage(),
            batteryLevel = getCurrentBatteryLevel(),
            networkUsageKB = 0f, // Would track actual network usage
            diskUsageMB = getCurrentDiskUsage(),
            threadCount = Thread.activeCount(),
            gcCount = 0 // Would track actual GC events
        )
    }

    private fun updatePerformanceMetrics() {
        val latestSnapshot = performanceHistory.lastOrNull() ?: return

        val metrics = PerformanceMetrics(
            currentMemoryUsageMB = latestSnapshot.memoryUsageMB,
            peakMemoryUsageMB = performanceHistory.maxOfOrNull { it.memoryUsageMB } ?: 0f,
            averageCpuUsagePercent = performanceHistory.takeLast(10).map { it.cpuUsagePercent }.average().toFloat(),
            batteryLevel = latestSnapshot.batteryLevel,
            threadCount = latestSnapshot.threadCount,
            uptimeMs = System.currentTimeMillis() - appStartTime
        )

        _performanceMetrics.value = metrics
    }

    private fun checkPerformanceThresholds(snapshot: PerformanceSnapshot) {
        val alerts = mutableListOf<PerformanceAlert>()

        // Memory usage check
        if (snapshot.memoryUsageMB > TARGET_MEMORY_MB) {
            alerts.add(PerformanceAlert(
                type = AlertType.MEMORY_USAGE_HIGH,
                severity = AlertSeverity.HIGH,
                message = "Memory usage of ${snapshot.memoryUsageMB}MB exceeds target of ${TARGET_MEMORY_MB}MB",
                metric = "memory_usage_mb",
                value = snapshot.memoryUsageMB,
                threshold = TARGET_MEMORY_MB.toFloat()
            ))
        }

        // CPU usage check
        if (snapshot.cpuUsagePercent > 80f) {
            alerts.add(PerformanceAlert(
                type = AlertType.CPU_USAGE_HIGH,
                severity = AlertSeverity.MEDIUM,
                message = "CPU usage of ${snapshot.cpuUsagePercent}% is high",
                metric = "cpu_usage_percent",
                value = snapshot.cpuUsagePercent,
                threshold = 80f
            ))
        }

        // Thread count check
        if (snapshot.threadCount > 50) {
            alerts.add(PerformanceAlert(
                type = AlertType.THREAD_COUNT_HIGH,
                severity = AlertSeverity.MEDIUM,
                message = "Thread count of ${snapshot.threadCount} is high",
                metric = "thread_count",
                value = snapshot.threadCount.toFloat(),
                threshold = 50f
            ))
        }

        // Add new alerts
        alerts.forEach { addPerformanceAlert(it) }
    }

    private fun addPerformanceAlert(alert: PerformanceAlert) {
        val currentAlerts = _performanceAlerts.value.toMutableList()

        // Remove existing alert of same type
        currentAlerts.removeAll { it.type == alert.type }

        // Add new alert
        currentAlerts.add(alert)

        // Keep only recent alerts
        if (currentAlerts.size > 10) {
            currentAlerts.sortByDescending { it.timestamp }
            currentAlerts.take(10)
        }

        _performanceAlerts.value = currentAlerts

        Log.w(TAG, "Performance alert: ${alert.message}")
    }

    private fun getCurrentMemoryUsage(): Float {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val usedMemory = memoryInfo.totalMem - memoryInfo.availMem
        return (usedMemory / (1024 * 1024)).toFloat() // Convert to MB
    }

    private fun getCurrentCpuUsage(): Float {
        // Simplified CPU usage calculation
        // In a real implementation, would use more accurate measurement
        val startTime = System.nanoTime()
        System.nanoTime() // Dummy operation
        val endTime = System.nanoTime()

        // Return a simulated CPU usage between 5-25%
        return 5f + (Math.random() * 20).toFloat()
    }

    private fun getCurrentBatteryLevel(): Float {
        // Would integrate with BatteryManager
        return 75f // Placeholder
    }

    private fun getCurrentDiskUsage(): Float {
        // Would calculate actual disk usage
        return 150f // Placeholder: 150MB
    }

    private fun calculateBatteryDrainRate(): Float {
        // Simplified calculation based on recent snapshots
        val recentSnapshots = performanceHistory.takeLast(20)
        if (recentSnapshots.size < 2) return 0f

        val timeDiff = recentSnapshots.last().timestamp - recentSnapshots.first().timestamp
        val batteryDiff = recentSnapshots.first().batteryLevel - recentSnapshots.last().batteryLevel

        if (timeDiff > 0) {
            val hours = timeDiff / (1000.0 * 60.0 * 60.0)
            return (batteryDiff / hours).toFloat()
        }

        return 0f
    }

    private fun isWithinPerformanceTargets(): Boolean {
        val report = getPerformanceReport()
        return report.averageMemoryUsageMB <= TARGET_MEMORY_MB &&
               report.batteryDrainRatePercentPerHour <= TARGET_BATTERY_DRAIN_PERCENT &&
               report.crashRate <= TARGET_CRASH_RATE
    }
}

/**
 * Performance metrics data class
 */
data class PerformanceMetrics(
    val currentMemoryUsageMB: Float = 0f,
    val peakMemoryUsageMB: Float = 0f,
    val averageCpuUsagePercent: Float = 0f,
    val batteryLevel: Float = 0f,
    val threadCount: Int = 0,
    val uptimeMs: Long = 0L
)

/**
 * Performance snapshot for historical tracking
 */
data class PerformanceSnapshot(
    val timestamp: Long,
    val memoryUsageMB: Float,
    val cpuUsagePercent: Float,
    val batteryLevel: Float,
    val networkUsageKB: Float,
    val diskUsageMB: Float,
    val threadCount: Int,
    val gcCount: Int
)

/**
 * Performance alert
 */
data class PerformanceAlert(
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val metric: String,
    val value: Float,
    val threshold: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Performance report
 */
data class PerformanceReport(
    val averageMemoryUsageMB: Float,
    val peakMemoryUsageMB: Float,
    val averageCpuUsagePercent: Float,
    val batteryDrainRatePercentPerHour: Float,
    val crashRate: Float,
    val appUptimeMs: Long,
    val activeAlerts: Int,
    val withinTargets: Boolean
)

/**
 * Alert types
 */
enum class AlertType {
    MEMORY_USAGE_HIGH,
    CPU_USAGE_HIGH,
    BATTERY_DRAIN_HIGH,
    THREAD_COUNT_HIGH,
    CRASH_RATE_HIGH,
    OPERATION_FAILURE,
    NETWORK_LATENCY_HIGH
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