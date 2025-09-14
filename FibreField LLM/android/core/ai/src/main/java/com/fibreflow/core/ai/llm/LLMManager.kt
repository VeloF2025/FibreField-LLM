package com.fibreflow.core.ai.llm

import android.content.Context
import android.util.Log
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM Manager for FibreField Technician Guidance
 *
 * High-level interface for Phi-3.5 Mini LLM operations with:
 * - Lifecycle management
 * - Context-aware guidance
 * - Performance monitoring
 * - Error handling and fallbacks
 */
@Singleton
class LLMManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LLMManager"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private var phi35MiniLLM: Phi35MiniLLM? = null
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Initialize LLM components
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing LLM Manager...")

            // Initialize Phi-3.5 Mini LLM
            phi35MiniLLM = Phi35MiniLLM(context)
            val initResult = phi35MiniLLM?.initialize()

            if (initResult is Result.Error) {
                Log.e(TAG, "Failed to initialize Phi-3.5 Mini LLM", initResult.exception)
                return@withContext Result.Error(initResult.exception)
            }

            Log.i(TAG, "LLM Manager initialized successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM Manager", e)
            Result.Error(e)
        }
    }

    /**
     * Generate installation guidance with retry logic
     */
    suspend fun generateInstallationGuidance(
        userQuery: String,
        installationContext: InstallationContext? = null,
        maxRetries: Int = MAX_RETRIES
    ): Result<String> = withContext(Dispatchers.Default) {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                ensureInitialized()

                val guidance = phi35MiniLLM?.generateGuidance(
                    prompt = userQuery,
                    context = installationContext,
                    maxTokens = 512
                )

                if (guidance is Result.Success) {
                    return@withContext guidance
                } else if (guidance is Result.Error) {
                    lastException = guidance.exception
                    Log.w(TAG, "LLM guidance attempt ${attempt + 1} failed", guidance.exception)

                    if (attempt < maxRetries - 1) {
                        delay(RETRY_DELAY_MS * (attempt + 1))
                    }
                }

            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "LLM guidance attempt ${attempt + 1} failed", e)

                if (attempt < maxRetries - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }

        Log.e(TAG, "All LLM guidance attempts failed")
        Result.Error(lastException ?: RuntimeException("Unknown LLM error"))
    }

    /**
     * Stream installation guidance for real-time interaction
     */
    fun generateInstallationGuidanceStream(
        userQuery: String,
        installationContext: InstallationContext? = null
    ): Flow<Result<String>> = flow {
        try {
            ensureInitialized()

            phi35MiniLLM?.generateGuidanceStream(
                prompt = userQuery,
                context = installationContext,
                maxTokens = 512
            )?.collect { result ->
                emit(result)
            } ?: emit(Result.Error(RuntimeException("LLM not available")))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream guidance", e)
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Generate step-by-step installation instructions
     */
    suspend fun generateStepByStepInstructions(
        installationType: String,
        equipmentDetails: Map<String, String> = emptyMap()
    ): Result<List<String>> = withContext(Dispatchers.Default) {
        try {
            ensureInitialized()

            val prompt = buildStepByStepPrompt(installationType, equipmentDetails)

            val guidance = phi35MiniLLM?.generateGuidance(
                prompt = prompt,
                context = InstallationContext(
                    currentStep = "Planning",
                    equipmentType = installationType,
                    location = equipmentDetails["location"] ?: "Unknown"
                ),
                maxTokens = 1024
            )

            if (guidance is Result.Success) {
                val steps = parseStepsFromGuidance(guidance.data)
                Result.Success(steps)
            } else if (guidance is Result.Error) {
                Result.Error(guidance.exception)
            } else {
                Result.Error(RuntimeException("Unknown guidance result"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate step-by-step instructions", e)
            Result.Error(e)
        }
    }

    /**
     * Generate troubleshooting guidance for installation issues
     */
    suspend fun generateTroubleshootingGuidance(
        issueDescription: String,
        installationContext: InstallationContext
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            ensureInitialized()

            val prompt = """
                Technician is experiencing an installation issue:

                Issue: $issueDescription

                Provide specific troubleshooting steps and safety considerations.
                Focus on most common causes first.
                Include when to escalate to technical support.
            """.trimIndent()

            phi35MiniLLM?.generateGuidance(
                prompt = prompt,
                context = installationContext,
                maxTokens = 768
            ) ?: Result.Error(RuntimeException("LLM not available"))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate troubleshooting guidance", e)
            Result.Error(e)
        }
    }

    /**
     * Validate installation completeness
     */
    suspend fun validateInstallationCompleteness(
        completedSteps: List<String>,
        installationContext: InstallationContext
    ): Result<InstallationValidationResult> = withContext(Dispatchers.Default) {
        try {
            ensureInitialized()

            val prompt = """
                Review the completed installation steps and determine if the installation is complete and safe:

                Completed Steps:
                ${completedSteps.joinToString("\n") { "- $it" }}

                Equipment: ${installationContext.equipmentType}
                Location: ${installationContext.location}

                Provide validation result with any missing critical steps or safety concerns.
            """.trimIndent()

            val guidance = phi35MiniLLM?.generateGuidance(
                prompt = prompt,
                context = installationContext,
                maxTokens = 512
            )

            if (guidance is Result.Success) {
                val validation = parseValidationFromGuidance(guidance.data)
                Result.Success(validation)
            } else if (guidance is Result.Error) {
                Result.Error(guidance.exception)
            } else {
                Result.Error(RuntimeException("Unknown validation result"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate installation completeness", e)
            Result.Error(e)
        }
    }

    /**
     * Get LLM performance metrics
     */
    fun getPerformanceMetrics(): LLMMetrics? {
        return phi35MiniLLM?.getPerformanceMetrics()
    }

    /**
     * Check if LLM is ready for use
     */
    fun isReady(): Boolean {
        return phi35MiniLLM?.getPerformanceMetrics()?.isInitialized == true
    }

    /**
     * Cleanup resources
     */
    fun shutdown() {
        managerScope.cancel()
        phi35MiniLLM?.shutdown()
        phi35MiniLLM = null
        Log.i(TAG, "LLM Manager shutdown complete")
    }

    // Private helper methods

    private fun ensureInitialized() {
        if (!isReady()) {
            throw IllegalStateException("LLM Manager not initialized. Call initialize() first.")
        }
    }

    private fun buildStepByStepPrompt(
        installationType: String,
        equipmentDetails: Map<String, String>
    ): String {
        val equipmentInfo = equipmentDetails.entries.joinToString("\n") { "${it.key}: ${it.value}" }

        return """
            Generate detailed step-by-step instructions for $installationType installation.

            Equipment Details:
            $equipmentInfo

            Include:
            1. Pre-installation safety checks
            2. Required tools and materials
            3. Sequential installation steps
            4. Testing and verification procedures
            5. Safety considerations throughout

            Format as numbered steps with clear, actionable instructions.
        """.trimIndent()
    }

    private fun parseStepsFromGuidance(guidance: String): List<String> {
        // Simple parsing - in real implementation, would use more sophisticated NLP
        return guidance
            .split(Regex("\\d+\\.\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .take(20) // Limit to reasonable number of steps
    }

    private fun parseValidationFromGuidance(guidance: String): InstallationValidationResult {
        // Simple validation parsing - would be enhanced with better NLP
        val isComplete = guidance.contains("complete", ignoreCase = true) ||
                        guidance.contains("successful", ignoreCase = true)

        val hasIssues = guidance.contains("issue", ignoreCase = true) ||
                       guidance.contains("concern", ignoreCase = true) ||
                       guidance.contains("missing", ignoreCase = true)

        val recommendations = guidance
            .split(Regex("[•\\n]"))
            .filter { it.contains("recommend", ignoreCase = true) ||
                     it.contains("ensure", ignoreCase = true) ||
                     it.contains("check", ignoreCase = true) }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return InstallationValidationResult(
            isComplete = isComplete,
            hasSafetyConcerns = hasIssues,
            recommendations = recommendations,
            confidence = if (isComplete && !hasIssues) 0.9f else 0.7f
        )
    }
}

/**
 * Installation validation result
 */
data class InstallationValidationResult(
    val isComplete: Boolean,
    val hasSafetyConcerns: Boolean,
    val recommendations: List<String>,
    val confidence: Float
)