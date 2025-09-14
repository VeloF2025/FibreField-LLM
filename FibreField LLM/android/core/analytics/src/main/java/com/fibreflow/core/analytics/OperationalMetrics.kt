package com.fibreflow.core.performance

import com.fibreflow.core.common.result.Result
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Operational Metrics for tracking business-level performance
 * Measures user workflows, feature usage, and operational efficiency
 */
@Singleton
class OperationalMetrics @Inject constructor(
    private val performanceMonitor: PerformanceMonitor
) {

    companion object {
        private const val TAG = "OperationalMetrics"
        private const val METRICS_RETENTION_HOURS = 168 // 7 days
        private const val BATCH_SIZE = 100
    }

    private val operationMetrics = ConcurrentHashMap<String, OperationMetric>()
    private val workflowMetrics = ConcurrentHashMap<String, WorkflowMetric>()
    private val featureUsageMetrics = ConcurrentHashMap<String, FeatureUsageMetric>()
    private val userSessionMetrics = ConcurrentHashMap<String, UserSessionMetric>()

    private val operationCounter = AtomicLong(0)
    private val sessionCounter = AtomicLong(0)

    /**
     * Record operation performance
     */
    fun recordOperation(
        operationName: String,
        userId: String,
        durationMs: Long,
        success: Boolean,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val operationId = generateOperationId()
        val timestamp = System.currentTimeMillis()

        val metric = OperationMetric(
            operationId = operationId,
            operationName = operationName,
            userId = userId,
            durationMs = durationMs,
            success = success,
            timestamp = timestamp,
            metadata = metadata
        )

        operationMetrics[operationId] = metric

        // Update aggregated metrics
        updateAggregatedMetrics(operationName, durationMs, success)

        // Record in performance monitor
        performanceMonitor.recordOperation(operationName, durationMs, success)

        Timber.d("Recorded operation: $operationName, duration: ${durationMs}ms, success: $success")
    }

    /**
     * Start workflow tracking
     */
    fun startWorkflow(
        workflowName: String,
        userId: String,
        sessionId: String
    ): String {
        val workflowId = generateWorkflowId()
        val startTime = System.currentTimeMillis()

        val metric = WorkflowMetric(
            workflowId = workflowId,
            workflowName = workflowName,
            userId = userId,
            sessionId = sessionId,
            startTime = startTime,
            steps = mutableListOf()
        )

        workflowMetrics[workflowId] = metric

        Timber.d("Started workflow tracking: $workflowName for user $userId")
        return workflowId
    }

    /**
     * Record workflow step
     */
    fun recordWorkflowStep(
        workflowId: String,
        stepName: String,
        durationMs: Long,
        success: Boolean
    ) {
        val workflow = workflowMetrics[workflowId] ?: return

        val step = WorkflowStep(
            stepName = stepName,
            durationMs = durationMs,
            success = success,
            timestamp = System.currentTimeMillis()
        )

        workflow.steps.add(step)
        workflow.totalDurationMs += durationMs

        if (!success) {
            workflow.failedSteps++
        }

        Timber.d("Recorded workflow step: $stepName in workflow ${workflow.workflowName}")
    }

    /**
     * Complete workflow tracking
     */
    fun completeWorkflow(workflowId: String, overallSuccess: Boolean) {
        val workflow = workflowMetrics[workflowId] ?: return

        workflow.endTime = System.currentTimeMillis()
        workflow.overallSuccess = overallSuccess
        workflow.completed = true

        val totalDuration = workflow.endTime - workflow.startTime
        Timber.d("Completed workflow: ${workflow.workflowName}, duration: ${totalDuration}ms, success: $overallSuccess")
    }

    /**
     * Record feature usage
     */
    fun recordFeatureUsage(
        featureName: String,
        userId: String,
        usageType: UsageType,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val featureId = generateFeatureId()
        val timestamp = System.currentTimeMillis()

        val metric = FeatureUsageMetric(
            featureId = featureId,
            featureName = featureName,
            userId = userId,
            usageType = usageType,
            timestamp = timestamp,
            metadata = metadata
        )

        featureUsageMetrics[featureId] = metric

        // Update feature usage statistics
        updateFeatureUsageStats(featureName, usageType)

        Timber.d("Recorded feature usage: $featureName by user $userId")
    }

    /**
     * Start user session tracking
     */
    fun startUserSession(userId: String, deviceInfo: Map<String, Any> = emptyMap()): String {
        val sessionId = generateSessionId()
        val startTime = System.currentTimeMillis()

        val metric = UserSessionMetric(
            sessionId = sessionId,
            userId = userId,
            startTime = startTime,
            deviceInfo = deviceInfo,
            featureUsage = mutableListOf()
        )

        userSessionMetrics[sessionId] = metric

        Timber.d("Started user session tracking for user: $userId")
        return sessionId
    }

    /**
     * End user session tracking
     */
    fun endUserSession(sessionId: String) {
        val session = userSessionMetrics[sessionId] ?: return

        session.endTime = System.currentTimeMillis()
        session.durationMs = session.endTime - session.startTime

        Timber.d("Ended user session: $sessionId, duration: ${session.durationMs}ms")
    }

    /**
     * Get operational performance report
     */
    fun getOperationalReport(timeRangeHours: Int = 24): OperationalReport {
        val cutoffTime = System.currentTimeMillis() - (timeRangeHours * 60 * 60 * 1000L)

        // Filter recent metrics
        val recentOperations = operationMetrics.values.filter { it.timestamp >= cutoffTime }
        val recentWorkflows = workflowMetrics.values.filter { it.startTime >= cutoffTime }
        val recentFeatures = featureUsageMetrics.values.filter { it.timestamp >= cutoffTime }

        // Calculate metrics
        val totalOperations = recentOperations.size
        val successfulOperations = recentOperations.count { it.success }
        val averageOperationTime = recentOperations.map { it.durationMs.toDouble() }.average()

        val totalWorkflows = recentWorkflows.size
        val successfulWorkflows = recentWorkflows.count { it.overallSuccess }
        val averageWorkflowTime = recentWorkflows.map { it.totalDurationMs.toDouble() }.average()

        val featureUsageByType = recentFeatures.groupBy { it.usageType }
            .mapValues { it.value.size }

        return OperationalReport(
            timeRangeHours = timeRangeHours,
            totalOperations = totalOperations,
            successfulOperations = successfulOperations,
            operationSuccessRate = if (totalOperations > 0) successfulOperations.toDouble() / totalOperations else 0.0,
            averageOperationTimeMs = averageOperationTime,
            totalWorkflows = totalWorkflows,
            successfulWorkflows = successfulWorkflows,
            workflowSuccessRate = if (totalWorkflows > 0) successfulWorkflows.toDouble() / totalWorkflows else 0.0,
            averageWorkflowTimeMs = averageWorkflowTime,
            featureUsageStats = featureUsageByType,
            topUsedFeatures = getTopUsedFeatures(recentFeatures),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get user engagement metrics
     */
    fun getUserEngagementMetrics(userId: String, days: Int = 7): UserEngagementMetrics {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)

        val userOperations = operationMetrics.values.filter {
            it.userId == userId && it.timestamp >= cutoffTime
        }

        val userWorkflows = workflowMetrics.values.filter {
            it.userId == userId && it.startTime >= cutoffTime
        }

        val userSessions = userSessionMetrics.values.filter {
            it.userId == userId && it.startTime >= cutoffTime
        }

        val averageSessionDuration = userSessions.mapNotNull { it.durationMs }.average()
        val featuresUsed = userOperations.map { it.operationName }.distinct().size

        return UserEngagementMetrics(
            userId = userId,
            timeRangeDays = days,
            totalOperations = userOperations.size,
            totalWorkflows = userWorkflows.size,
            totalSessions = userSessions.size,
            averageSessionDurationMs = averageSessionDuration,
            uniqueFeaturesUsed = featuresUsed,
            mostUsedFeature = userOperations.groupBy { it.operationName }
                .maxByOrNull { it.value.size }?.key ?: "None",
            engagementScore = calculateEngagementScore(userOperations.size, userWorkflows.size, userSessions.size),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Clean up old metrics data
     */
    fun cleanupOldMetrics(olderThanHours: Int = METRICS_RETENTION_HOURS) {
        val cutoffTime = System.currentTimeMillis() - (olderThanHours * 60 * 60 * 1000L)

        val operationsRemoved = operationMetrics.values.removeAll { it.timestamp < cutoffTime }
        val workflowsRemoved = workflowMetrics.values.removeAll { it.startTime < cutoffTime }
        val featuresRemoved = featureUsageMetrics.values.removeAll { it.timestamp < cutoffTime }
        val sessionsRemoved = userSessionMetrics.values.removeAll { it.startTime < cutoffTime }

        Timber.d("Cleaned up old metrics: $operationsRemoved operations, $workflowsRemoved workflows, $featuresRemoved features, $sessionsRemoved sessions")
    }

    // Private helper methods

    private fun updateAggregatedMetrics(operationName: String, durationMs: Long, success: Boolean) {
        // In a real implementation, this would update aggregated statistics
        // For now, we just log the operation
        Timber.v("Updated aggregated metrics for operation: $operationName")
    }

    private fun updateFeatureUsageStats(featureName: String, usageType: UsageType) {
        // In a real implementation, this would update feature usage statistics
        Timber.v("Updated feature usage stats for: $featureName")
    }

    private fun getTopUsedFeatures(features: Collection<FeatureUsageMetric>): List<String> {
        return features.groupBy { it.featureName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    private fun calculateEngagementScore(operations: Int, workflows: Int, sessions: Int): Double {
        // Simple engagement score calculation
        val operationScore = minOf(operations / 10.0, 1.0) // Max at 10 operations
        val workflowScore = minOf(workflows / 5.0, 1.0)  // Max at 5 workflows
        val sessionScore = minOf(sessions / 7.0, 1.0)    // Max at 7 sessions per week

        return (operationScore + workflowScore + sessionScore) / 3.0
    }

    private fun generateOperationId(): String = "op_${operationCounter.incrementAndGet()}"
    private fun generateWorkflowId(): String = "wf_${System.currentTimeMillis()}"
    private fun generateFeatureId(): String = "ft_${System.currentTimeMillis()}"
    private fun generateSessionId(): String = "sess_${sessionCounter.incrementAndGet()}"
}

/**
 * Data classes for operational metrics
 */

data class OperationMetric(
    val operationId: String,
    val operationName: String,
    val userId: String,
    val durationMs: Long,
    val success: Boolean,
    val timestamp: Long,
    val metadata: Map<String, Any> = emptyMap()
)

data class WorkflowMetric(
    val workflowId: String,
    val workflowName: String,
    val userId: String,
    val sessionId: String,
    val startTime: Long,
    var endTime: Long? = null,
    var totalDurationMs: Long = 0,
    var completed: Boolean = false,
    var overallSuccess: Boolean = false,
    var failedSteps: Int = 0,
    val steps: MutableList<WorkflowStep> = mutableListOf()
)

data class WorkflowStep(
    val stepName: String,
    val durationMs: Long,
    val success: Boolean,
    val timestamp: Long
)

data class FeatureUsageMetric(
    val featureId: String,
    val featureName: String,
    val userId: String,
    val usageType: UsageType,
    val timestamp: Long,
    val metadata: Map<String, Any> = emptyMap()
)

data class UserSessionMetric(
    val sessionId: String,
    val userId: String,
    val startTime: Long,
    var endTime: Long? = null,
    var durationMs: Long? = null,
    val deviceInfo: Map<String, Any> = emptyMap(),
    val featureUsage: MutableList<String> = mutableListOf()
)

enum class UsageType {
    VIEW,
    INTERACTION,
    COMPLETION,
    ERROR,
    ABANDONMENT
}

data class OperationalReport(
    val timeRangeHours: Int,
    val totalOperations: Int,
    val successfulOperations: Int,
    val operationSuccessRate: Double,
    val averageOperationTimeMs: Double,
    val totalWorkflows: Int,
    val successfulWorkflows: Int,
    val workflowSuccessRate: Double,
    val averageWorkflowTimeMs: Double,
    val featureUsageStats: Map<UsageType, Int>,
    val topUsedFeatures: List<String>,
    val timestamp: Long
)

data class UserEngagementMetrics(
    val userId: String,
    val timeRangeDays: Int,
    val totalOperations: Int,
    val totalWorkflows: Int,
    val totalSessions: Int,
    val averageSessionDurationMs: Double,
    val uniqueFeaturesUsed: Int,
    val mostUsedFeature: String,
    val engagementScore: Double,
    val timestamp: Long
)