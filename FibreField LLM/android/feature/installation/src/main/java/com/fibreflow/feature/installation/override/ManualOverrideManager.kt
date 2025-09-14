package com.fibreflow.feature.installation.override

import android.util.Log
import com.fibreflow.core.ai.pipeline.ValidationOutcome
import com.fibreflow.core.ai.pipeline.ValidationStatus
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Manual Override Manager for FibreField
 *
 * Handles technician manual overrides of AI validation results:
 * - Override approval workflow
 * - Feedback collection and analytics
 * - Override tracking and auditing
 * - Confidence improvement through learning
 *
 * Ensures <10% override rate target through continuous improvement.
 */
@ViewModelScoped
class ManualOverrideManager @Inject constructor(
    private val feedbackCollector: FeedbackCollector
) {

    companion object {
        private const val TAG = "ManualOverrideManager"
        private const val MAX_OVERRIDE_RATE = 0.10f // 10% target
        private const val MIN_FEEDBACK_LENGTH = 10
    }

    private val overrideScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _overrideEvents = MutableSharedFlow<OverrideEvent>()

    // Override tracking
    private var totalValidations = 0
    private var totalOverrides = 0
    private val recentOverrides = mutableListOf<OverrideRecord>()

    /**
     * Request manual override for AI validation result
     */
    suspend fun requestOverride(
        originalResult: ValidationOutcome,
        overrideRequest: OverrideRequest
    ): Result<OverrideResult> = withContext(Dispatchers.Default) {
        try {
            Log.i(TAG, "Processing override request for step: ${overrideRequest.stepName}")

            // Validate override request
            val validation = validateOverrideRequest(overrideRequest)
            if (validation is Result.Error) {
                return@withContext validation
            }

            // Check override rate limits
            if (shouldThrottleOverride()) {
                return@withContext Result.Error(RuntimeException("Override rate too high - please improve photo quality"))
            }

            // Create override record
            val overrideRecord = OverrideRecord(
                id = generateOverrideId(),
                originalResult = originalResult,
                request = overrideRequest,
                timestamp = System.currentTimeMillis(),
                status = OverrideStatus.PENDING
            )

            // Process override based on type
            val result = when (overrideRequest.type) {
                OverrideType.APPROVE_WITH_NOTES -> processApprovalOverride(overrideRecord)
                OverrideType.REJECT_WITH_CORRECTION -> processCorrectionOverride(overrideRecord)
                OverrideType.ESCALATE_TO_SUPERVISOR -> processEscalationOverride(overrideRecord)
            }

            if (result is Result.Success) {
                // Record override for analytics
                recordOverride(overrideRecord)

                // Emit override event
                _overrideEvents.emit(OverrideEvent.OverrideProcessed(overrideRecord))

                // Collect feedback for AI improvement
                collectFeedbackForImprovement(overrideRecord)
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "Override request failed", e)
            Result.Error(e)
        }
    }

    /**
     * Get override statistics and analytics
     */
    fun getOverrideAnalytics(): OverrideAnalytics {
        val overrideRate = if (totalValidations > 0) {
            totalOverrides.toFloat() / totalValidations
        } else 0f

        val recentOverrideRate = calculateRecentOverrideRate()

        return OverrideAnalytics(
            totalValidations = totalValidations,
            totalOverrides = totalOverrides,
            overrideRate = overrideRate,
            recentOverrideRate = recentOverrideRate,
            isWithinTarget = overrideRate <= MAX_OVERRIDE_RATE,
            topOverrideReasons = getTopOverrideReasons(),
            overrideTrends = getOverrideTrends()
        )
    }

    /**
     * Stream override events for UI updates
     */
    fun getOverrideEvents(): Flow<OverrideEvent> = _overrideEvents.asSharedFlow()

    /**
     * Get override history for auditing
     */
    fun getOverrideHistory(
        technicianId: String? = null,
        limit: Int = 50
    ): List<OverrideRecord> {
        return recentOverrides
            .filter { technicianId == null || it.request.technicianId == technicianId }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    /**
     * Suggest improvements based on override patterns
     */
    suspend fun getImprovementSuggestions(): Result<List<ImprovementSuggestion>> = withContext(Dispatchers.Default) {
        try {
            val analytics = getOverrideAnalytics()

            if (analytics.overrideRate > MAX_OVERRIDE_RATE) {
                val suggestions = mutableListOf<ImprovementSuggestion>()

                // Analyze common issues
                val commonReasons = analytics.topOverrideReasons.take(3)
                commonReasons.forEach { reason ->
                    suggestions.add(ImprovementSuggestion(
                        type = SuggestionType.PHOTO_IMPROVEMENT,
                        title = "Improve photo quality for ${reason.category}",
                        description = "Address ${reason.reason} issues to reduce overrides",
                        impact = SuggestionImpact.HIGH
                    ))
                }

                // Training suggestions
                if (analytics.recentOverrideRate > analytics.overrideRate) {
                    suggestions.add(ImprovementSuggestion(
                        type = SuggestionType.TRAINING,
                        title = "Additional training recommended",
                        description = "Override rate increasing - consider refresher training",
                        impact = SuggestionImpact.MEDIUM
                    ))
                }

                Result.Success(suggestions)
            } else {
                Result.Success(emptyList())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate improvement suggestions", e)
            Result.Error(e)
        }
    }

    /**
     * Validate if override should be allowed
     */
    fun canOverride(technicianId: String, stepName: String): OverrideEligibility {
        val technicianOverrides = recentOverrides.count { it.request.technicianId == technicianId }
        val stepOverrides = recentOverrides.count { it.request.stepName == stepName }

        val isEligible = technicianOverrides < 5 && stepOverrides < 3 // Reasonable limits

        return OverrideEligibility(
            canOverride = isEligible,
            reason = if (!isEligible) "Override limit exceeded" else null,
            remainingOverrides = maxOf(0, 5 - technicianOverrides)
        )
    }

    /**
     * Shutdown manager and cleanup resources
     */
    fun shutdown() {
        overrideScope.cancel()
        Log.i(TAG, "ManualOverrideManager shutdown complete")
    }

    // Private implementation methods

    private fun validateOverrideRequest(request: OverrideRequest): Result<Unit> {
        if (request.reason.length < MIN_FEEDBACK_LENGTH) {
            return Result.Error(RuntimeException("Override reason too short - minimum $MIN_FEEDBACK_LENGTH characters"))
        }

        if (request.technicianId.isBlank()) {
            return Result.Error(RuntimeException("Technician ID required"))
        }

        if (request.stepName.isBlank()) {
            return Result.Error(RuntimeException("Step name required"))
        }

        return Result.Success(Unit)
    }

    private fun shouldThrottleOverride(): Boolean {
        val recentRate = calculateRecentOverrideRate()
        return recentRate > MAX_OVERRIDE_RATE * 1.5f // 15% threshold
    }

    private suspend fun processApprovalOverride(record: OverrideRecord): Result<OverrideResult> {
        // Create approved outcome
        val approvedOutcome = ValidationOutcome(
            status = ValidationStatus.MANUALLY_APPROVED,
            confidence = 1.0f, // Manual approval = 100% confidence
            issues = emptyList(),
            recommendations = listOf("Manually approved by technician"),
            guidance = "Installation step approved via manual override: ${record.request.reason}",
            extractedData = record.originalResult.extractedData,
            override = com.fibreflow.core.ai.pipeline.ManualOverride(
                originalResult = record.originalResult,
                overrideReason = record.request.reason,
                technicianNotes = record.request.notes,
                overrideBy = record.request.technicianId,
                timestamp = record.timestamp
            )
        )

        return Result.Success(OverrideResult(
            outcome = approvedOutcome,
            record = record.copy(status = OverrideStatus.APPROVED),
            requiresSupervisorApproval = false
        ))
    }

    private suspend fun processCorrectionOverride(record: OverrideRecord): Result<OverrideResult> {
        // For corrections, mark as needing review
        val correctedOutcome = record.originalResult.copy(
            status = ValidationStatus.REQUIRES_MANUAL_REVIEW,
            recommendations = record.originalResult.recommendations + "Correction requested: ${record.request.reason}"
        )

        return Result.Success(OverrideResult(
            outcome = correctedOutcome,
            record = record.copy(status = OverrideStatus.CORRECTION_REQUESTED),
            requiresSupervisorApproval = true
        ))
    }

    private suspend fun processEscalationOverride(record: OverrideRecord): Result<OverrideResult> {
        // Escalation requires supervisor approval
        return Result.Success(OverrideResult(
            outcome = record.originalResult,
            record = record.copy(status = OverrideStatus.ESCALATED),
            requiresSupervisorApproval = true,
            supervisorNotificationRequired = true
        ))
    }

    private fun recordOverride(record: OverrideRecord) {
        totalValidations++
        if (record.status != OverrideStatus.PENDING) {
            totalOverrides++
        }

        recentOverrides.add(record)

        // Keep only recent overrides (last 100)
        if (recentOverrides.size > 100) {
            recentOverrides.removeAt(0)
        }
    }

    private suspend fun collectFeedbackForImprovement(record: OverrideRecord) {
        feedbackCollector.collectOverrideFeedback(
            technicianId = record.request.technicianId,
            stepName = record.request.stepName,
            originalIssues = record.originalResult.issues,
            overrideReason = record.request.reason,
            notes = record.request.notes,
            wasHelpful = true // Assume override was necessary
        )
    }

    private fun calculateRecentOverrideRate(): Float {
        val recent = recentOverrides.takeLast(20) // Last 20 validations
        val recentOverrideCount = recent.count { it.status != OverrideStatus.PENDING }
        return if (recent.isNotEmpty()) recentOverrideCount.toFloat() / recent.size else 0f
    }

    private fun getTopOverrideReasons(): List<OverrideReasonStats> {
        val reasonCounts = mutableMapOf<String, Int>()

        recentOverrides.forEach { record ->
            val category = categorizeOverrideReason(record.request.reason)
            reasonCounts[category] = reasonCounts.getOrDefault(category, 0) + 1
        }

        return reasonCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { OverrideReasonStats(it.key, it.value) }
    }

    private fun getOverrideTrends(): List<TrendPoint> {
        // Simple trend calculation - in real implementation would be more sophisticated
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        val trends = mutableListOf<TrendPoint>()
        for (i in 6 downTo 0) {
            val dayStart = now - (i * dayMs)
            val dayEnd = dayStart + dayMs
            val dayOverrides = recentOverrides.count { it.timestamp in dayStart..dayEnd }
            trends.add(TrendPoint(dayStart, dayOverrides))
        }

        return trends
    }

    private fun categorizeOverrideReason(reason: String): String {
        return when {
            reason.contains("lighting", ignoreCase = true) -> "Lighting Issues"
            reason.contains("angle", ignoreCase = true) -> "Camera Angle"
            reason.contains("blur", ignoreCase = true) -> "Image Quality"
            reason.contains("barcode", ignoreCase = true) -> "Barcode Detection"
            reason.contains("text", ignoreCase = true) -> "Text Recognition"
            reason.contains("equipment", ignoreCase = true) -> "Equipment Issues"
            else -> "Other"
        }
    }

    private fun generateOverrideId(): String {
        return "OVR_${System.currentTimeMillis()}_${totalOverrides}"
    }
}

/**
 * Override request data
 */
data class OverrideRequest(
    val technicianId: String,
    val stepName: String,
    val type: OverrideType,
    val reason: String,
    val notes: String = "",
    val evidence: List<String> = emptyList() // Photo URLs or other evidence
)

/**
 * Override result
 */
data class OverrideResult(
    val outcome: ValidationOutcome,
    val record: OverrideRecord,
    val requiresSupervisorApproval: Boolean = false,
    val supervisorNotificationRequired: Boolean = false
)

/**
 * Override record for auditing
 */
data class OverrideRecord(
    val id: String,
    val originalResult: ValidationOutcome,
    val request: OverrideRequest,
    val timestamp: Long,
    val status: OverrideStatus
)

/**
 * Override types
 */
enum class OverrideType {
    APPROVE_WITH_NOTES,
    REJECT_WITH_CORRECTION,
    ESCALATE_TO_SUPERVISOR
}

/**
 * Override status
 */
enum class OverrideStatus {
    PENDING,
    APPROVED,
    CORRECTION_REQUESTED,
    ESCALATED,
    REJECTED
}

/**
 * Override eligibility check
 */
data class OverrideEligibility(
    val canOverride: Boolean,
    val reason: String?,
    val remainingOverrides: Int
)

/**
 * Override analytics
 */
data class OverrideAnalytics(
    val totalValidations: Int,
    val totalOverrides: Int,
    val overrideRate: Float,
    val recentOverrideRate: Float,
    val isWithinTarget: Boolean,
    val topOverrideReasons: List<OverrideReasonStats>,
    val overrideTrends: List<TrendPoint>
)

/**
 * Override reason statistics
 */
data class OverrideReasonStats(
    val reason: String,
    val count: Int
)

/**
 * Trend data point
 */
data class TrendPoint(
    val timestamp: Long,
    val value: Int
)

/**
 * Improvement suggestion
 */
data class ImprovementSuggestion(
    val type: SuggestionType,
    val title: String,
    val description: String,
    val impact: SuggestionImpact
)

/**
 * Suggestion types
 */
enum class SuggestionType {
    PHOTO_IMPROVEMENT,
    TRAINING,
    EQUIPMENT_CHECK,
    PROCESS_CHANGE
}

/**
 * Suggestion impact levels
 */
enum class SuggestionImpact {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Override events for UI updates
 */
sealed class OverrideEvent {
    data class OverrideProcessed(val record: OverrideRecord) : OverrideEvent()
    data class OverrideRateAlert(val rate: Float, val threshold: Float) : OverrideEvent()
    data class ImprovementSuggested(val suggestions: List<ImprovementSuggestion>) : OverrideEvent()
}