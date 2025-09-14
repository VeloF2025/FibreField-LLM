package com.fibreflow.core.ai.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Battery Manager for FibreField AI Operations
 *
 * Monitors battery state and manages AI inference based on power conditions:
 * - Battery level monitoring
 * - Charging state detection
 * - Power-aware AI processing
 * - Battery usage analytics
 *
 * Ensures AI operations stay within <15% daily battery drain target.
 */
@Singleton
class BatteryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "BatteryManager"
        private const val BATTERY_CHECK_INTERVAL_MS = 30000L // 30 seconds
        private const val LOW_BATTERY_THRESHOLD = 20 // 20%
        private const val CRITICAL_BATTERY_THRESHOLD = 10 // 10%
        private const val TARGET_DAILY_DRAIN_PERCENT = 15.0f // 15% max daily drain
        private const val MEASUREMENT_PERIOD_HOURS = 24 // Track over 24 hours
    }

    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Battery state flows
    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private val _powerOptimization = MutableStateFlow(PowerOptimization.NORMAL)
    val powerOptimization: StateFlow<PowerOptimization> = _powerOptimization.asStateFlow()

    // Battery monitoring
    private var batteryReceiver: BatteryReceiver? = null
    private var monitoringJob: Job? = null
    private var isMonitoring = false

    // Battery usage tracking
    private var sessionStartLevel = -1
    private var sessionStartTime = 0L
    private val batteryHistory = mutableListOf<BatteryReading>()
    private var dailyDrainEstimate = 0f

    /**
     * Start battery monitoring
     */
    suspend fun startMonitoring(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            if (isMonitoring) {
                return@withContext Result.Success(Unit)
            }

            Log.i(TAG, "Starting battery monitoring")

            // Register battery receiver
            batteryReceiver = BatteryReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }

            context.registerReceiver(batteryReceiver, filter)

            // Start periodic monitoring
            monitoringJob = managerScope.launch {
                while (isActive) {
                    updateBatteryState()
                    updatePowerOptimization()
                    cleanupOldReadings()
                    delay(BATTERY_CHECK_INTERVAL_MS)
                }
            }

            // Record session start
            recordSessionStart()

            isMonitoring = true
            Log.i(TAG, "Battery monitoring started successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start battery monitoring", e)
            Result.Error(e)
        }
    }

    /**
     * Stop battery monitoring
     */
    suspend fun stopMonitoring(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            if (!isMonitoring) {
                return@withContext Result.Success(Unit)
            }

            Log.i(TAG, "Stopping battery monitoring")

            // Cancel monitoring job
            monitoringJob?.cancel()
            monitoringJob = null

            // Unregister receiver
            batteryReceiver?.let { context.unregisterReceiver(it) }
            batteryReceiver = null

            // Record session end
            recordSessionEnd()

            isMonitoring = false
            Log.i(TAG, "Battery monitoring stopped")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop battery monitoring", e)
            Result.Error(e)
        }
    }

    /**
     * Get current battery analytics
     */
    fun getBatteryAnalytics(): BatteryAnalytics {
        val currentState = _batteryState.value
        val readings = batteryHistory.toList()

        // Calculate drain rate
        val drainRate = calculateDrainRate(readings)

        // Estimate daily drain
        val estimatedDailyDrain = if (drainRate > 0) {
            drainRate * 24 // hours in a day
        } else {
            dailyDrainEstimate
        }

        // Check if within target
        val withinTarget = estimatedDailyDrain <= TARGET_DAILY_DRAIN_PERCENT

        return BatteryAnalytics(
            currentLevel = currentState.level,
            isCharging = currentState.isCharging,
            temperatureCelsius = currentState.temperatureCelsius,
            voltageMv = currentState.voltageMv,
            drainRatePerHour = drainRate,
            estimatedDailyDrain = estimatedDailyDrain,
            withinTarget = withinTarget,
            powerOptimization = _powerOptimization.value,
            sessionDurationMs = System.currentTimeMillis() - sessionStartTime,
            totalReadings = readings.size
        )
    }

    /**
     * Check if AI operations should be restricted based on battery
     */
    fun shouldRestrictAIOperations(): Boolean {
        val state = _batteryState.value
        val optimization = _powerOptimization.value

        return when (optimization) {
            PowerOptimization.AGGRESSIVE -> true // Always restrict in aggressive mode
            PowerOptimization.CONSERVATIVE -> state.level <= LOW_BATTERY_THRESHOLD
            PowerOptimization.NORMAL -> state.level <= CRITICAL_BATTERY_THRESHOLD
        }
    }

    /**
     * Get recommended AI processing parameters based on battery state
     */
    fun getRecommendedAIParameters(): AIProcessingParameters {
        val state = _batteryState.value
        val optimization = _powerOptimization.value

        return when (optimization) {
            PowerOptimization.AGGRESSIVE -> AIProcessingParameters(
                enableInference = false,
                maxConcurrentModels = 0,
                reduceModelPrecision = true,
                skipHeavyProcessing = true,
                reason = "Battery critically low"
            )
            PowerOptimization.CONSERVATIVE -> AIProcessingParameters(
                enableInference = state.level > LOW_BATTERY_THRESHOLD,
                maxConcurrentModels = 1,
                reduceModelPrecision = state.level <= LOW_BATTERY_THRESHOLD,
                skipHeavyProcessing = state.level <= CRITICAL_BATTERY_THRESHOLD,
                reason = "Conserving battery"
            )
            PowerOptimization.NORMAL -> AIProcessingParameters(
                enableInference = true,
                maxConcurrentModels = 2,
                reduceModelPrecision = false,
                skipHeavyProcessing = false,
                reason = "Normal battery operation"
            )
        }
    }

    /**
     * Record AI operation for battery impact analysis
     */
    fun recordAIOperation(operation: AIOperation) {
        // In a real implementation, this would track battery drain per operation
        // For now, we just log it
        Log.d(TAG, "Recorded AI operation: ${operation.type} (${operation.durationMs}ms)")
    }

    /**
     * Check if monitoring is active
     */
    fun isMonitoringActive(): Boolean = isMonitoring

    /**
     * Shutdown battery manager
     */
    fun shutdown() {
        managerScope.cancel()
        if (isMonitoring) {
            // Note: This would ideally be suspend, but keeping simple for now
            monitoringJob?.cancel()
            batteryReceiver?.let { context.unregisterReceiver(it) }
        }
    }

    // Private implementation methods

    private fun updateBatteryState() {
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val batteryLevel = if (scale > 0) (level * 100) / scale else 0
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL
                val temperatureCelsius = temperature / 10f

                val newState = BatteryState(
                    level = batteryLevel,
                    isCharging = isCharging,
                    temperatureCelsius = temperatureCelsius,
                    voltageMv = voltage,
                    lastUpdated = System.currentTimeMillis()
                )

                // Record reading for history
                batteryHistory.add(BatteryReading(
                    level = batteryLevel,
                    isCharging = isCharging,
                    timestamp = System.currentTimeMillis()
                ))

                // Maintain history size
                if (batteryHistory.size > 100) {
                    batteryHistory.removeAt(0)
                }

                _batteryState.value = newState
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update battery state", e)
        }
    }

    private fun updatePowerOptimization() {
        val state = _batteryState.value
        val analytics = getBatteryAnalytics()

        val optimization = when {
            state.level <= CRITICAL_BATTERY_THRESHOLD -> PowerOptimization.AGGRESSIVE
            state.level <= LOW_BATTERY_THRESHOLD || !analytics.withinTarget -> PowerOptimization.CONSERVATIVE
            else -> PowerOptimization.NORMAL
        }

        _powerOptimization.value = optimization
    }

    private fun calculateDrainRate(readings: List<BatteryReading>): Float {
        if (readings.size < 2) return 0f

        // Only consider discharging readings
        val dischargingReadings = readings.filterNot { it.isCharging }
        if (dischargingReadings.size < 2) return 0f

        val sortedReadings = dischargingReadings.sortedBy { it.timestamp }
        val first = sortedReadings.first()
        val last = sortedReadings.last()

        val timeDiffHours = (last.timestamp - first.timestamp) / (1000.0 * 60.0 * 60.0)
        val levelDiff = first.level - last.level

        return if (timeDiffHours > 0) levelDiff / timeDiffHours else 0f
    }

    private fun cleanupOldReadings() {
        val cutoffTime = System.currentTimeMillis() - (MEASUREMENT_PERIOD_HOURS * 60 * 60 * 1000L)
        batteryHistory.removeAll { it.timestamp < cutoffTime }
    }

    private fun recordSessionStart() {
        sessionStartTime = System.currentTimeMillis()
        updateBatteryState() // Get current level
        sessionStartLevel = _batteryState.value.level
    }

    private fun recordSessionEnd() {
        val sessionEndLevel = _batteryState.value.level
        val sessionDurationHours = (System.currentTimeMillis() - sessionStartTime) / (1000.0 * 60.0 * 60.0)

        if (sessionDurationHours > 0 && sessionStartLevel > 0) {
            val sessionDrain = sessionStartLevel - sessionEndLevel
            dailyDrainEstimate = sessionDrain / sessionDurationHours * 24 // Extrapolate to daily
        }
    }

    // Battery broadcast receiver
    private inner class BatteryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> {
                    Log.w(TAG, "Battery low - activating conservative mode")
                    _powerOptimization.value = PowerOptimization.CONSERVATIVE
                }
                Intent.ACTION_BATTERY_OKAY -> {
                    Log.i(TAG, "Battery OK - returning to normal mode")
                    updatePowerOptimization() // Recalculate based on current state
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.i(TAG, "Power connected - battery charging")
                    updateBatteryState()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.i(TAG, "Power disconnected - battery discharging")
                    updateBatteryState()
                }
            }
        }
    }
}

/**
 * Battery state data class
 */
data class BatteryState(
    val level: Int = 0,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 0f,
    val voltageMv: Int = 0,
    val lastUpdated: Long = 0L
)

/**
 * Power optimization levels
 */
enum class PowerOptimization {
    NORMAL,      // Standard AI processing
    CONSERVATIVE, // Reduced AI processing
    AGGRESSIVE   // Minimal AI processing
}

/**
 * Battery reading for history tracking
 */
data class BatteryReading(
    val level: Int,
    val isCharging: Boolean,
    val timestamp: Long
)

/**
 * Battery analytics
 */
data class BatteryAnalytics(
    val currentLevel: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val drainRatePerHour: Float,
    val estimatedDailyDrain: Float,
    val withinTarget: Boolean,
    val powerOptimization: PowerOptimization,
    val sessionDurationMs: Long,
    val totalReadings: Int
)

/**
 * AI processing parameters based on battery state
 */
data class AIProcessingParameters(
    val enableInference: Boolean,
    val maxConcurrentModels: Int,
    val reduceModelPrecision: Boolean,
    val skipHeavyProcessing: Boolean,
    val reason: String
)

/**
 * AI operation for battery impact tracking
 */
data class AIOperation(
    val type: String,
    val durationMs: Long,
    val modelUsed: String? = null,
    val powerConsumedMah: Float? = null
)