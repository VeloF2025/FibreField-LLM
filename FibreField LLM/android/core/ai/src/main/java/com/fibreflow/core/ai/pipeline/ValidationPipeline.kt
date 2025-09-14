package com.fibreflow.core.ai.pipeline

import android.util.Log
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * AI Validation Pipeline for FibreField
 *
 * End-to-end pipeline for photo validation and AI-powered guidance:
 * 1. Photo capture and preprocessing
 * 2. Multi-model AI analysis (quality, content, equipment)
 * 3. Result aggregation and confidence scoring
 * 4. Contextual guidance generation
 * 5. Manual override handling
 *
 * Provides unified interface for all AI validation operations.
 */
@ViewModelScoped
class ValidationPipeline @Inject constructor(
    private val modelCoordinator: ModelCoordinator
) {

    companion object {
        private const val TAG = "ValidationPipeline"
        private const val PIPELINE_TIMEOUT_MS = 15000L // 15 seconds
        private const val MIN_CONFIDENCE_THRESHOLD = 0.7f
    }

    private val pipelineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Execute complete validation pipeline for installation step
     */
    suspend fun validateInstallationStep(
        photoData: ByteArray,
        stepContext: InstallationStepContext,
        options: ValidationOptions = ValidationOptions()
    ): Result<ValidationOutcome> = withContext(Dispatchers.Default) {
        try {
            Log.i(TAG, "Starting validation pipeline for step: ${stepContext.stepName}")

            val startTime = System.currentTimeMillis()

            // Phase 1: AI Model Validation
            val aiValidation = withTimeout(PIPELINE_TIMEOUT_MS) {
                modelCoordinator.executePhotoValidationPipeline(photoData, stepContext)
            }

            val aiResult = when (aiValidation) {
                is Result.Success -> aiValidation.data
                is Result.Error -> {
                    Log.w(TAG, "AI validation failed, attempting fallback", aiValidation.exception)
                    return@withContext handleValidationFailure(aiValidation.exception, stepContext, options)
                }
            }

            // Phase 2: Confidence Assessment
            val confidenceAssessment = assessOverallConfidence(aiResult, stepContext)

            // Phase 3: Generate Guidance (if enabled)
            val guidance = if (options.generateGuidance && confidenceAssessment.needsGuidance) {
                modelCoordinator.generateContextualGuidance(aiResult, stepContext)
            } else {
                Result.Success("")
            }

            // Phase 4: Determine Final Outcome
            val outcome = determineValidationOutcome(
                aiResult = aiResult,
                confidenceAssessment = confidenceAssessment,
                guidance = guidance,
                options = options
            )

            val totalTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Validation pipeline completed in ${totalTime}ms with outcome: ${outcome.status}")

            Result.Success(outcome)

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Validation pipeline timeout", e)
            Result.Error(RuntimeException("Validation timeout - please try again"))
        } catch (e: Exception) {
            Log.e(TAG, "Validation pipeline failed", e)
            Result.Error(e)
        }
    }

    /**
     * Stream validation progress and results in real-time
     */
    fun streamValidationProcess(
        photoData: ByteArray,
        stepContext: InstallationStepContext,
        options: ValidationOptions = ValidationOptions()
    ): Flow<Result<ValidationProgress>> = flow {
        try {
            emit(Result.Success(ValidationProgress(
                stage = PipelineStage.INITIALIZING,
                message = "Initializing validation...",
                progress = 0.0f
            )))

            // Preload models if needed
            if (options.preloadModels) {
                emit(Result.Success(ValidationProgress(
                    stage = PipelineStage.MODEL_LOADING,
                    message = "Loading AI models...",
                    progress = 0.1f
                )))

                val preloadResult = modelCoordinator.preloadValidationModels()
                if (preloadResult is Result.Error) {
                    Log.w(TAG, "Model preloading failed", preloadResult.exception)
                }
            }

            emit(Result.Success(ValidationProgress(
                stage = PipelineStage.ANALYZING,
                message = "Analyzing photo...",
                progress = 0.2f
            )))

            // Stream AI validation feedback
            modelCoordinator.streamValidationFeedback(photoData, stepContext)
                .collect { feedbackResult ->
                    when (feedbackResult) {
                        is Result.Success -> {
                            val progress = ValidationProgress(
                                stage = when (feedbackResult.data.stage) {
                                    ValidationStage.QUALITY_CHECK -> PipelineStage.QUALITY_CHECK
                                    ValidationStage.CONTENT_ANALYSIS -> PipelineStage.CONTENT_ANALYSIS
                                    ValidationStage.EQUIPMENT_VALIDATION -> PipelineStage.EQUIPMENT_VALIDATION
                                    ValidationStage.COMPLETE -> PipelineStage.GENERATING_GUIDANCE
                                },
                                message = feedbackResult.data.message,
                                progress = feedbackResult.data.progress,
                                issues = feedbackResult.data.issues,
                                recommendations = feedbackResult.data.recommendations
                            )
                            emit(Result.Success(progress))
                        }
                        is Result.Error -> {
                            emit(Result.Success(ValidationProgress(
                                stage = PipelineStage.ERROR,
                                message = "Analysis failed",
                                progress = 0.0f,
                                issues = listOf("AI analysis error occurred")
                            )))
                        }
                    }
                }

            // Generate final guidance if needed
            if (options.generateGuidance) {
                emit(Result.Success(ValidationProgress(
                    stage = PipelineStage.GENERATING_GUIDANCE,
                    message = "Generating guidance...",
                    progress = 0.9f
                )))

                // Note: In real implementation, would get the validation result and generate guidance
                delay(500) // Simulate guidance generation
            }

            emit(Result.Success(ValidationProgress(
                stage = PipelineStage.COMPLETE,
                message = "Validation complete",
                progress = 1.0f
            )))

        } catch (e: Exception) {
            Log.e(TAG, "Streaming validation failed", e)
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Validate installation completeness across all steps
     */
    suspend fun validateInstallationCompleteness(
        completedSteps: List<CompletedStep>,
        installationContext: InstallationContext
    ): Result<CompletenessValidation> = withContext(Dispatchers.Default) {
        try {
            // This would integrate with LLM for completeness assessment
            // For now, return a basic validation

            val missingSteps = identifyMissingSteps(completedSteps, installationContext.equipmentType)
            val safetyConcerns = assessSafetyConcerns(completedSteps)

            Result.Success(CompletenessValidation(
                isComplete = missingSteps.isEmpty() && safetyConcerns.isEmpty(),
                missingSteps = missingSteps,
                safetyConcerns = safetyConcerns,
                confidence = calculateCompletenessConfidence(missingSteps.size, safetyConcerns.size),
                recommendations = generateCompletenessRecommendations(missingSteps, safetyConcerns)
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Completeness validation failed", e)
            Result.Error(e)
        }
    }

    /**
     * Handle manual override of AI validation results
     */
    suspend fun processManualOverride(
        originalResult: ValidationOutcome,
        overrideReason: String,
        technicianNotes: String,
        overrideBy: String
    ): Result<ValidationOutcome> = withContext(Dispatchers.Default) {
        try {
            // Create override record
            val override = ManualOverride(
                originalResult = originalResult,
                overrideReason = overrideReason,
                technicianNotes = technicianNotes,
                overrideBy = overrideBy,
                timestamp = System.currentTimeMillis()
            )

            // Generate new outcome with override
            val overriddenOutcome = ValidationOutcome(
                status = ValidationStatus.MANUALLY_APPROVED,
                confidence = 1.0f, // Manual override = 100% confidence
                issues = emptyList(),
                recommendations = listOf("Manual override applied: $overrideReason"),
                guidance = "Installation approved via manual override by $overrideBy. Notes: $technicianNotes",
                extractedData = originalResult.extractedData,
                override = override
            )

            Log.i(TAG, "Manual override processed by $overrideBy: $overrideReason")
            Result.Success(overriddenOutcome)

        } catch (e: Exception) {
            Log.e(TAG, "Manual override processing failed", e)
            Result.Error(e)
        }
    }

    /**
     * Get pipeline performance metrics
     */
    fun getPipelineMetrics(): PipelineMetrics {
        // Would track actual metrics in real implementation
        return PipelineMetrics(
            averageValidationTimeMs = 3000L,
            successRate = 0.95f,
            averageConfidence = 0.85f,
            totalValidations = 150
        )
    }

    /**
     * Optimize pipeline for current device conditions
     */
    suspend fun optimizeForDevice(
        batteryLevel: Int,
        availableMemoryMB: Int,
        networkAvailable: Boolean
    ): Result<PipelineOptimization> = withContext(Dispatchers.Default) {
        try {
            val modelOptimization = modelCoordinator.optimizeForDeviceConditions(
                batteryLevel, availableMemoryMB, networkAvailable
            )

            if (modelOptimization is Result.Success) {
                val optimization = PipelineOptimization(
                    enableStreaming = modelOptimization.data.enableStreaming,
                    useSimplifiedValidation = modelOptimization.data.useSimplifiedModels,
                    disableGuidance = modelOptimization.data.disableLLMGuidance,
                    maxConcurrentValidations = modelOptimization.data.maxConcurrentModels,
                    optimizations = modelOptimization.data.optimizations
                )

                Result.Success(optimization)
            } else {
                Result.Error(modelOptimization.exception ?: RuntimeException("Optimization failed"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Pipeline optimization failed", e)
            Result.Error(e)
        }
    }

    /**
     * Shutdown pipeline and cleanup resources
     */
    fun shutdown() {
        pipelineScope.cancel()
        modelCoordinator.shutdown()
        Log.i(TAG, "ValidationPipeline shutdown complete")
    }

    // Private helper methods

    private suspend fun handleValidationFailure(
        exception: Throwable,
        stepContext: InstallationStepContext,
        options: ValidationOptions
    ): Result<ValidationOutcome> {
        return if (options.allowFallbackValidation) {
            // Provide basic fallback validation
            val fallbackOutcome = ValidationOutcome(
                status = ValidationStatus.REQUIRES_MANUAL_REVIEW,
                confidence = 0.3f,
                issues = listOf("AI validation failed: ${exception.message}"),
                recommendations = listOf(
                    "Please review the photo manually",
                    "Ensure all required elements are visible",
                    "Consider retaking the photo if quality is poor"
                ),
                guidance = "AI validation encountered an error. Please perform manual verification of the installation step.",
                extractedData = emptyMap()
            )
            Result.Success(fallbackOutcome)
        } else {
            Result.Error(exception)
        }
    }

    private fun assessOverallConfidence(
        aiResult: PhotoValidationResult,
        stepContext: InstallationStepContext
    ): ConfidenceAssessment {
        val baseConfidence = aiResult.confidence
        val issuePenalty = aiResult.issues.size * 0.1f
        val adjustedConfidence = (baseConfidence - issuePenalty).coerceIn(0f, 1f)

        return ConfidenceAssessment(
            overallConfidence = adjustedConfidence,
            needsGuidance = adjustedConfidence < MIN_CONFIDENCE_THRESHOLD,
            requiresManualReview = adjustedConfidence < 0.5f,
            criticalIssues = aiResult.issues.filter { isCriticalIssue(it, stepContext) }
        )
    }

    private fun determineValidationOutcome(
        aiResult: PhotoValidationResult,
        confidenceAssessment: ConfidenceAssessment,
        guidance: Result<String>,
        options: ValidationOptions
    ): ValidationOutcome {
        val guidanceText = (guidance as? Result.Success)?.data ?: ""

        return when {
            confidenceAssessment.overallConfidence >= MIN_CONFIDENCE_THRESHOLD && aiResult.isValid -> {
                ValidationOutcome(
                    status = ValidationStatus.PASSED,
                    confidence = confidenceAssessment.overallConfidence,
                    issues = aiResult.issues,
                    recommendations = aiResult.recommendations,
                    guidance = guidanceText,
                    extractedData = aiResult.extractedData
                )
            }
            confidenceAssessment.requiresManualReview -> {
                ValidationOutcome(
                    status = ValidationStatus.REQUIRES_MANUAL_REVIEW,
                    confidence = confidenceAssessment.overallConfidence,
                    issues = aiResult.issues,
                    recommendations = aiResult.recommendations + listOf("Manual review required due to low confidence"),
                    guidance = guidanceText,
                    extractedData = aiResult.extractedData
                )
            }
            else -> {
                ValidationOutcome(
                    status = ValidationStatus.FAILED,
                    confidence = confidenceAssessment.overallConfidence,
                    issues = aiResult.issues,
                    recommendations = aiResult.recommendations,
                    guidance = guidanceText,
                    extractedData = aiResult.extractedData
                )
            }
        }
    }

    private fun isCriticalIssue(issue: String, context: InstallationStepContext): Boolean {
        val criticalKeywords = listOf(
            "power", "safety", "connection", "damage",
            "barcode", "serial", "critical"
        )
        return criticalKeywords.any { issue.contains(it, ignoreCase = true) }
    }

    private fun identifyMissingSteps(completedSteps: List<CompletedStep>, equipmentType: String): List<String> {
        // Basic step identification - would be more sophisticated in real implementation
        val requiredSteps = getRequiredStepsForEquipment(equipmentType)
        val completedStepNames = completedSteps.map { it.stepName }

        return requiredSteps.filter { it !in completedStepNames }
    }

    private fun assessSafetyConcerns(completedSteps: List<CompletedStep>): List<String> {
        // Basic safety assessment
        val concerns = mutableListOf<String>()

        val hasPowerCheck = completedSteps.any { it.stepName.contains("power", ignoreCase = true) }
        if (!hasPowerCheck) {
            concerns.add("Power verification step not completed")
        }

        val hasConnectionCheck = completedSteps.any {
            it.stepName.contains("connection", ignoreCase = true) ||
            it.stepName.contains("cable", ignoreCase = true)
        }
        if (!hasConnectionCheck) {
            concerns.add("Cable connection verification not completed")
        }

        return concerns
    }

    private fun calculateCompletenessConfidence(missingSteps: Int, safetyConcerns: Int): Float {
        val penalty = (missingSteps + safetyConcerns * 2) * 0.1f
        return (1.0f - penalty).coerceIn(0f, 1f)
    }

    private fun generateCompletenessRecommendations(
        missingSteps: List<String>,
        safetyConcerns: List<String>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        missingSteps.forEach { step ->
            recommendations.add("Complete missing step: $step")
        }

        safetyConcerns.forEach { concern ->
            recommendations.add("Address safety concern: $concern")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Installation appears complete")
        }

        return recommendations
    }

    private fun getRequiredStepsForEquipment(equipmentType: String): List<String> {
        // Basic step requirements - would be more comprehensive in real implementation
        return when (equipmentType.lowercase()) {
            "ont", "onu" -> listOf(
                "ONT Installation",
                "Cable Connection",
                "Power Verification",
                "Light Status Check",
                "Signal Test"
            )
            "fiber cable" -> listOf(
                "Cable Routing",
                "Connection Points",
                "Cable Tension",
                "Protection Installation"
            )
            else -> listOf(
                "Equipment Installation",
                "Connection Verification",
                "Power Check",
                "Functional Test"
            )
        }
    }
}

/**
 * Validation pipeline options
 */
data class ValidationOptions(
    val generateGuidance: Boolean = true,
    val allowFallbackValidation: Boolean = true,
    val preloadModels: Boolean = true,
    val enableStreaming: Boolean = true,
    val strictMode: Boolean = false
)

/**
 * Overall validation outcome
 */
data class ValidationOutcome(
    val status: ValidationStatus,
    val confidence: Float,
    val issues: List<String>,
    val recommendations: List<String>,
    val guidance: String,
    val extractedData: Map<String, Any>,
    val override: ManualOverride? = null
)

/**
 * Validation status
 */
enum class ValidationStatus {
    PASSED,
    FAILED,
    REQUIRES_MANUAL_REVIEW,
    MANUALLY_APPROVED,
    ERROR
}

/**
 * Pipeline stage for progress tracking
 */
enum class PipelineStage {
    INITIALIZING,
    MODEL_LOADING,
    ANALYZING,
    QUALITY_CHECK,
    CONTENT_ANALYSIS,
    EQUIPMENT_VALIDATION,
    GENERATING_GUIDANCE,
    COMPLETE,
    ERROR
}

/**
 * Validation progress update
 */
data class ValidationProgress(
    val stage: PipelineStage,
    val message: String,
    val progress: Float,
    val issues: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)

/**
 * Manual override record
 */
data class ManualOverride(
    val originalResult: ValidationOutcome,
    val overrideReason: String,
    val technicianNotes: String,
    val overrideBy: String,
    val timestamp: Long
)

/**
 * Installation completeness validation
 */
data class CompletenessValidation(
    val isComplete: Boolean,
    val missingSteps: List<String>,
    val safetyConcerns: List<String>,
    val confidence: Float,
    val recommendations: List<String>
)

/**
 * Completed installation step
 */
data class CompletedStep(
    val stepName: String,
    val completionTime: Long,
    val validationResult: ValidationOutcome?
)

/**
 * Confidence assessment
 */
private data class ConfidenceAssessment(
    val overallConfidence: Float,
    val needsGuidance: Boolean,
    val requiresManualReview: Boolean,
    val criticalIssues: List<String>
)

/**
 * Pipeline performance metrics
 */
data class PipelineMetrics(
    val averageValidationTimeMs: Long,
    val successRate: Float,
    val averageConfidence: Float,
    val totalValidations: Int
)

/**
 * Pipeline optimization settings
 */
data class PipelineOptimization(
    val enableStreaming: Boolean,
    val useSimplifiedValidation: Boolean,
    val disableGuidance: Boolean,
    val maxConcurrentValidations: Int,
    val optimizations: List<String>
)