package com.fibreflow.core.ai.feedback

import android.util.Log
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.scopes.SingletonScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Feedback Collector for FibreField
 *
 * Collects and analyzes feedback from manual overrides and user interactions:
 * - Override pattern analysis for AI improvement
 * - Model performance tracking
 * - Continuous learning data collection
 * - Feedback loop for model retraining
 *
 * Enables <10% override rate through data-driven improvements.
 */
@Singleton
class FeedbackCollector @Inject constructor() {

    companion object {
        private const val TAG = "FeedbackCollector"
        private const val MAX_FEEDBACK_ENTRIES = 1000
        private const val FEEDBACK_ANALYSIS_INTERVAL_MS = 24 * 60 * 60 * 1000L // Daily analysis
    }

    private val feedbackScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _feedbackInsights = MutableSharedFlow<FeedbackInsight>()

    // Feedback storage
    private val feedbackEntries = mutableListOf<FeedbackEntry>()
    private var lastAnalysisTime = 0L

    /**
     * Collect feedback from manual override
     */
    suspend fun collectOverrideFeedback(
        technicianId: String,
        stepName: String,
        originalIssues: List<String>,
        overrideReason: String,
        notes: String,
        wasHelpful: Boolean
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val entry = FeedbackEntry(
                id = generateFeedbackId(),
                type = FeedbackType.OVERRIDE,
                technicianId = technicianId,
                stepName = stepName,
                originalIssues = originalIssues,
                overrideReason = overrideReason,
                notes = notes,
                wasHelpful = wasHelpful,
                timestamp = System.currentTimeMillis(),
                metadata = mapOf(
                    "override_type" to "manual_validation",
                    "issues_count" to originalIssues.size.toString()
                )
            )

            addFeedbackEntry(entry)

            // Trigger analysis if needed
            if (shouldAnalyzeFeedback()) {
                analyzeFeedbackPatterns()
            }

            Log.d(TAG, "Collected override feedback for technician $technicianId on step $stepName")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect override feedback", e)
            Result.Error(e)
        }
    }

    /**
     * Collect feedback from AI interaction
     */
    suspend fun collectInteractionFeedback(
        technicianId: String,
        interactionType: InteractionType,
        prompt: String,
        response: String,
        rating: Int, // 1-5 scale
        comments: String = ""
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val entry = FeedbackEntry(
                id = generateFeedbackId(),
                type = FeedbackType.INTERACTION,
                technicianId = technicianId,
                interactionType = interactionType,
                prompt = prompt,
                response = response,
                rating = rating,
                comments = comments,
                timestamp = System.currentTimeMillis(),
                metadata = mapOf(
                    "interaction_type" to interactionType.name,
                    "response_length" to response.length.toString()
                )
            )

            addFeedbackEntry(entry)

            Log.d(TAG, "Collected interaction feedback: rating $rating for ${interactionType.name}")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect interaction feedback", e)
            Result.Error(e)
        }
    }

    /**
     * Collect feedback from validation results
     */
    suspend fun collectValidationFeedback(
        stepName: String,
        confidence: Float,
        issues: List<String>,
        wasAccurate: Boolean,
        correctionNeeded: Boolean
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val entry = FeedbackEntry(
                id = generateFeedbackId(),
                type = FeedbackType.VALIDATION,
                stepName = stepName,
                confidence = confidence,
                issues = issues,
                wasAccurate = wasAccurate,
                correctionNeeded = correctionNeeded,
                timestamp = System.currentTimeMillis(),
                metadata = mapOf(
                    "confidence_level" to confidence.toString(),
                    "issues_count" to issues.size.toString(),
                    "accuracy" to wasAccurate.toString()
                )
            )

            addFeedbackEntry(entry)

            Log.d(TAG, "Collected validation feedback for $stepName: accurate=$wasAccurate")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect validation feedback", e)
            Result.Error(e)
        }
    }

    /**
     * Get feedback analytics and insights
     */
    fun getFeedbackAnalytics(): FeedbackAnalytics {
        val totalEntries = feedbackEntries.size
        val overrideEntries = feedbackEntries.filter { it.type == FeedbackType.OVERRIDE }
        val interactionEntries = feedbackEntries.filter { it.type == FeedbackType.INTERACTION }
        val validationEntries = feedbackEntries.filter { it.type == FeedbackType.VALIDATION }

        // Calculate override rate
        val overrideRate = if (validationEntries.isNotEmpty()) {
            overrideEntries.size.toFloat() / validationEntries.size
        } else 0f

        // Calculate average ratings
        val avgRating = interactionEntries
            .filter { it.rating != null }
            .map { it.rating!! }
            .average()
            .toFloat()

        // Common issues analysis
        val commonIssues = analyzeCommonIssues()

        return FeedbackAnalytics(
            totalFeedbackEntries = totalEntries,
            overrideRate = overrideRate,
            averageInteractionRating = avgRating,
            commonIssues = commonIssues,
            feedbackTrends = getFeedbackTrends(),
            improvementSuggestions = generateImprovementSuggestions()
        )
    }

    /**
     * Stream feedback insights for real-time updates
     */
    fun getFeedbackInsights() = _feedbackInsights.asSharedFlow()

    /**
     * Export feedback data for model retraining
     */
    suspend fun exportFeedbackForRetraining(): Result<FeedbackDataset> = withContext(Dispatchers.Default) {
        try {
            val dataset = FeedbackDataset(
                entries = feedbackEntries.toList(),
                exportTimestamp = System.currentTimeMillis(),
                totalEntries = feedbackEntries.size,
                dataQuality = calculateDataQuality()
            )

            Log.i(TAG, "Exported ${dataset.totalEntries} feedback entries for retraining")
            Result.Success(dataset)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to export feedback data", e)
            Result.Error(e)
        }
    }

    /**
     * Clear old feedback data (data retention policy)
     */
    suspend fun cleanupOldFeedback(maxAgeDays: Int = 90): Result<Int> = withContext(Dispatchers.Default) {
        try {
            val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
            val initialSize = feedbackEntries.size

            feedbackEntries.removeAll { it.timestamp < cutoffTime }

            val removedCount = initialSize - feedbackEntries.size
            Log.i(TAG, "Cleaned up $removedCount old feedback entries")
            Result.Success(removedCount)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old feedback", e)
            Result.Error(e)
        }
    }

    /**
     * Shutdown collector and cleanup resources
     */
    fun shutdown() {
        feedbackScope.cancel()
        feedbackEntries.clear()
        Log.i(TAG, "FeedbackCollector shutdown complete")
    }

    // Private implementation methods

    private fun addFeedbackEntry(entry: FeedbackEntry) {
        feedbackEntries.add(entry)

        // Maintain max entries limit
        if (feedbackEntries.size > MAX_FEEDBACK_ENTRIES) {
            feedbackEntries.removeAt(0) // Remove oldest
        }
    }

    private fun shouldAnalyzeFeedback(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastAnalysisTime) > FEEDBACK_ANALYSIS_INTERVAL_MS
    }

    private suspend fun analyzeFeedbackPatterns() {
        withContext(Dispatchers.Default) {
            try {
                lastAnalysisTime = System.currentTimeMillis()

                val analytics = getFeedbackAnalytics()

                // Generate insights
                val insights = mutableListOf<FeedbackInsight>()

                // Override rate analysis
                if (analytics.overrideRate > 0.10f) { // Above 10%
                    insights.add(FeedbackInsight(
                        type = InsightType.OVERRIDE_RATE_HIGH,
                        title = "High Override Rate Detected",
                        description = "Override rate of ${String.format("%.1f", analytics.overrideRate * 100)}% exceeds target of 10%",
                        severity = InsightSeverity.HIGH,
                        suggestions = listOf(
                            "Review photo quality requirements",
                            "Consider additional technician training",
                            "Analyze common override reasons"
                        )
                    ))
                }

                // Low rating analysis
                if (analytics.averageInteractionRating < 3.5f) {
                    insights.add(FeedbackInsight(
                        type = InsightType.LOW_RATING,
                        title = "Low AI Interaction Ratings",
                        description = "Average rating of ${String.format("%.1f", analytics.averageInteractionRating)} indicates room for improvement",
                        severity = InsightSeverity.MEDIUM,
                        suggestions = listOf(
                            "Review AI response quality",
                            "Improve contextual guidance",
                            "Add more detailed instructions"
                        )
                    ))
                }

                // Common issues insights
                analytics.commonIssues.take(3).forEach { issue ->
                    insights.add(FeedbackInsight(
                        type = InsightType.COMMON_ISSUE,
                        title = "Recurring Issue: ${issue.category}",
                        description = "${issue.count} instances of ${issue.category} reported",
                        severity = if (issue.count > 10) InsightSeverity.HIGH else InsightSeverity.MEDIUM,
                        suggestions = listOf(
                            "Address ${issue.category} in training materials",
                            "Improve validation logic for ${issue.category}",
                            "Update photo capture guidelines"
                        )
                    ))
                }

                // Emit insights
                insights.forEach { insight ->
                    _feedbackInsights.emit(insight)
                }

                Log.i(TAG, "Generated ${insights.size} feedback insights")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze feedback patterns", e)
            }
        }
    }

    private fun analyzeCommonIssues(): List<IssueFrequency> {
        val issueCounts = mutableMapOf<String, Int>()

        feedbackEntries
            .filter { it.type == FeedbackType.OVERRIDE }
            .forEach { entry ->
                val category = categorizeIssue(entry.overrideReason ?: "")
                issueCounts[category] = issueCounts.getOrDefault(category, 0) + 1
            }

        return issueCounts.entries
            .sortedByDescending { it.value }
            .map { IssueFrequency(it.key, it.value) }
    }

    private fun categorizeIssue(reason: String): String {
        return when {
            reason.contains("lighting", ignoreCase = true) -> "Poor Lighting"
            reason.contains("angle", ignoreCase = true) -> "Bad Camera Angle"
            reason.contains("blur", ignoreCase = true) || reason.contains("focus", ignoreCase = true) -> "Image Blur"
            reason.contains("barcode", ignoreCase = true) -> "Barcode Detection"
            reason.contains("text", ignoreCase = true) || reason.contains("ocr", ignoreCase = true) -> "Text Recognition"
            reason.contains("equipment", ignoreCase = true) -> "Equipment Visibility"
            reason.contains("distance", ignoreCase = true) -> "Incorrect Distance"
            else -> "Other Issues"
        }
    }

    private fun getFeedbackTrends(): List<TrendPoint> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        val trends = mutableListOf<TrendPoint>()
        for (i in 6 downTo 0) {
            val dayStart = now - (i * dayMs)
            val dayEnd = dayStart + dayMs
            val dayEntries = feedbackEntries.count { it.timestamp in dayStart..dayEnd }
            trends.add(TrendPoint(dayStart, dayEntries))
        }

        return trends
    }

    private fun generateImprovementSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()
        val analytics = getFeedbackAnalytics()

        if (analytics.overrideRate > 0.10f) {
            suggestions.add("Implement additional photo quality checks")
            suggestions.add("Provide real-time feedback during photo capture")
        }

        if (analytics.averageInteractionRating < 4.0f) {
            suggestions.add("Enhance AI response clarity and specificity")
            suggestions.add("Add more contextual information to prompts")
        }

        analytics.commonIssues.take(2).forEach { issue ->
            suggestions.add("Address ${issue.category} through improved validation rules")
        }

        return suggestions
    }

    private fun calculateDataQuality(): Float {
        if (feedbackEntries.isEmpty()) return 0f

        val entriesWithDetails = feedbackEntries.count { entry ->
            when (entry.type) {
                FeedbackType.OVERRIDE -> entry.overrideReason?.isNotBlank() == true
                FeedbackType.INTERACTION -> entry.rating != null && entry.comments.isNotBlank()
                FeedbackType.VALIDATION -> entry.confidence != null
            }
        }

        return entriesWithDetails.toFloat() / feedbackEntries.size
    }

    private fun generateFeedbackId(): String {
        return "FB_${System.currentTimeMillis()}_${feedbackEntries.size}"
    }
}

/**
 * Feedback entry data class
 */
data class FeedbackEntry(
    val id: String,
    val type: FeedbackType,
    val technicianId: String? = null,
    val stepName: String? = null,
    val interactionType: InteractionType? = null,
    val originalIssues: List<String> = emptyList(),
    val overrideReason: String? = null,
    val notes: String = "",
    val wasHelpful: Boolean? = null,
    val prompt: String? = null,
    val response: String? = null,
    val rating: Int? = null, // 1-5 scale
    val comments: String = "",
    val confidence: Float? = null,
    val issues: List<String> = emptyList(),
    val wasAccurate: Boolean? = null,
    val correctionNeeded: Boolean? = null,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Feedback types
 */
enum class FeedbackType {
    OVERRIDE,
    INTERACTION,
    VALIDATION
}

/**
 * Interaction types for AI feedback
 */
enum class InteractionType {
    LLM_GUIDANCE,
    PHOTO_VALIDATION,
    TROUBLESHOOTING,
    STEP_INSTRUCTIONS
}

/**
 * Feedback analytics
 */
data class FeedbackAnalytics(
    val totalFeedbackEntries: Int,
    val overrideRate: Float,
    val averageInteractionRating: Float,
    val commonIssues: List<IssueFrequency>,
    val feedbackTrends: List<TrendPoint>,
    val improvementSuggestions: List<String>
)

/**
 * Issue frequency data
 */
data class IssueFrequency(
    val category: String,
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
 * Feedback insight
 */
data class FeedbackInsight(
    val type: InsightType,
    val title: String,
    val description: String,
    val severity: InsightSeverity,
    val suggestions: List<String>
)

/**
 * Insight types
 */
enum class InsightType {
    OVERRIDE_RATE_HIGH,
    LOW_RATING,
    COMMON_ISSUE,
    IMPROVEMENT_OPPORTUNITY
}

/**
 * Insight severity levels
 */
enum class InsightSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Dataset for model retraining
 */
data class FeedbackDataset(
    val entries: List<FeedbackEntry>,
    val exportTimestamp: Long,
    val totalEntries: Int,
    val dataQuality: Float
)