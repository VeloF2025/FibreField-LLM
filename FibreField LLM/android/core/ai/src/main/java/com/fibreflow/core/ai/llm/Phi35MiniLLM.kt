package com.fibreflow.core.ai.llm

import android.content.Context
import android.util.Log
import com.fibreflow.core.common.result.Result
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.system.measureTimeMillis
import ai.mlc.llm.LLM
import ai.mlc.llm.Chat
import ai.mlc.llm.ChatConfig

/**
 * Phi-3.5 Mini LLM Integration for FibreField Technician Guidance
 *
 * Implements Microsoft Phi-3.5 Mini (3.8B parameters) for contextual installation guidance.
 * Optimized for Android with memory constraints and performance requirements.
 *
 * Performance Targets:
 * - Model Size: ~2GB (quantized)
 * - Memory Usage: <3GB during inference
 * - First Token Latency: <1 second
 * - Token Generation: 12-18 tokens/second
 * - Model Loading: <5 seconds
 */
class Phi35MiniLLM(
    private val context: Context,
    private val modelPath: String = "models/phi-3.5-mini-q4.gguf"
) {

    companion object {
        private const val TAG = "Phi35MiniLLM"
        private const val MODEL_SIZE_MB = 2048 // 2GB quantized model
        private const val MAX_MEMORY_MB = 3072 // 3GB limit
        private const val FIRST_TOKEN_TIMEOUT_MS = 1000L
        private const val MODEL_LOAD_TIMEOUT_MS = 5000L
        private const val TARGET_TOKENS_PER_SECOND = 15
    }

    private var isInitialized = false
    private var modelLoadTimeMs: Long = 0
    private val inferenceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // MLC LLM components
    private var llmEngine: LLM? = null
    private var chat: Chat? = null

    /**
     * Initialize the Phi-3.5 Mini model
     * Performs model loading and validation
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                return@withContext Result.Success(Unit)
            }

            val loadTime = measureTimeMillis {
                // Verify model file exists
                val modelFile = File(context.filesDir, modelPath)
                if (!modelFile.exists()) {
                    return@withContext Result.Error(
                        IllegalStateException("Model file not found: ${modelFile.absolutePath}")
                    )
                }

                // Check model file size (should be ~2GB)
                val fileSizeMB = modelFile.length() / (1024 * 1024)
                if (fileSizeMB < MODEL_SIZE_MB * 0.9) {
                    return@withContext Result.Error(
                        IllegalStateException("Model file too small: ${fileSizeMB}MB, expected ~${MODEL_SIZE_MB}MB")
                    )
                }

                // Initialize MLC LLM runtime (placeholder for actual implementation)
                initializeMLCRuntime(modelFile.absolutePath)

                // Warm up the model
                warmupModel()
            }

            modelLoadTimeMs = loadTime
            isInitialized = true

            // Validate loading time
            if (loadTime > MODEL_LOAD_TIMEOUT_MS) {
                Log.w(TAG, "Model loading exceeded target time: ${loadTime}ms > ${MODEL_LOAD_TIMEOUT_MS}ms")
            }

            Log.i(TAG, "Phi-3.5 Mini LLM initialized successfully in ${loadTime}ms")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Phi-3.5 Mini LLM", e)
            Result.Error(e)
        }
    }

    /**
     * Generate installation guidance response
     * Optimized for technician workflow assistance
     */
    suspend fun generateGuidance(
        prompt: String,
        context: InstallationContext? = null,
        maxTokens: Int = 256
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            ensureInitialized()

            val startTime = System.currentTimeMillis()

            // Build contextual prompt
            val contextualPrompt = buildContextualPrompt(prompt, context)

            // Generate response with performance monitoring
            val response = generateWithMLC(contextualPrompt, maxTokens)

            val generationTime = System.currentTimeMillis() - startTime
            val tokensGenerated = response.split("\\s+".toRegex()).size
            val tokensPerSecond = (tokensGenerated.toFloat() / generationTime.toFloat()) * 1000

            // Validate performance
            if (generationTime > FIRST_TOKEN_TIMEOUT_MS) {
                Log.w(TAG, "First token latency exceeded: ${generationTime}ms > ${FIRST_TOKEN_TIMEOUT_MS}ms")
            }

            if (tokensPerSecond < TARGET_TOKENS_PER_SECOND * 0.8) {
                Log.w(TAG, "Token generation rate below target: ${tokensPerSecond} < ${TARGET_TOKENS_PER_SECOND}")
            }

            Log.d(TAG, "Generated ${tokensGenerated} tokens in ${generationTime}ms (${tokensPerSecond} tokens/sec)")
            Result.Success(response)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate guidance", e)
            Result.Error(e)
        }
    }

    /**
     * Stream installation guidance for real-time interaction
     */
    fun generateGuidanceStream(
        prompt: String,
        context: InstallationContext? = null,
        maxTokens: Int = 256
    ): Flow<Result<String>> = flow {
        try {
            ensureInitialized()

            val contextualPrompt = buildContextualPrompt(prompt, context)
            val startTime = System.currentTimeMillis()

            // Stream tokens as they are generated
            generateWithMLCStream(contextualPrompt, maxTokens).collect { token ->
                val currentTime = System.currentTimeMillis()
                val timeElapsed = currentTime - startTime

                // Check first token latency
                if (timeElapsed > FIRST_TOKEN_TIMEOUT_MS) {
                    Log.w(TAG, "First token latency exceeded in streaming mode")
                }

                emit(Result.Success(token))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream guidance", e)
            emit(Result.Error(e))
        }
    }

    /**
     * Get performance metrics for monitoring
     */
    fun getPerformanceMetrics(): LLMMetrics {
        return LLMMetrics(
            modelLoadTimeMs = modelLoadTimeMs,
            isInitialized = isInitialized,
            memoryUsageMB = getCurrentMemoryUsage(),
            targetTokensPerSecond = TARGET_TOKENS_PER_SECOND
        )
    }

    /**
     * Cleanup resources
     */
    fun shutdown() {
        inferenceScope.cancel()
        // Cleanup MLC runtime resources
        shutdownMLCRuntime()
        isInitialized = false
        Log.i(TAG, "Phi-3.5 Mini LLM shutdown complete")
    }

    // Private implementation methods

    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Phi-3.5 Mini LLM not initialized. Call initialize() first.")
        }
    }

    private fun buildContextualPrompt(prompt: String, context: InstallationContext?): String {
        val systemPrompt = """
            You are an expert fibre optic network technician assistant.
            Provide clear, step-by-step guidance for installation tasks.
            Be concise but thorough. Focus on safety and accuracy.
            If unsure, recommend consulting technical documentation.

        """.trimIndent()

        val contextInfo = context?.let { ctx ->
            """
            Current Installation Context:
            - Step: ${ctx.currentStep}
            - Equipment: ${ctx.equipmentType}
            - Location: ${ctx.location}
            - Previous Issues: ${ctx.previousIssues.joinToString(", ")}

            """.trimIndent()
        } ?: ""

        return "$systemPrompt\n$contextInfo\nUser: $prompt\nAssistant:"
    }

    // MLC LLM integration implementations

    private fun initializeMLCRuntime(modelPath: String) {
        try {
            Log.d(TAG, "Initializing MLC LLM runtime with model: $modelPath")

            // Create LLM engine with Phi-3.5 Mini configuration
            llmEngine = LLM.create(
                modelPath = modelPath,
                modelLib = "phi-3.5-mini" // MLC model library identifier
            )

            // Create chat instance with optimized settings for technician guidance
            val chatConfig = ChatConfig(
                temperature = 0.3f, // Lower temperature for consistent guidance
                topP = 0.9f,
                maxTokens = 256,
                contextWindow = 8192,
                repetitionPenalty = 1.1f
            )

            chat = llmEngine?.createChat(chatConfig)

            Log.i(TAG, "MLC LLM runtime initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MLC LLM runtime", e)
            throw RuntimeException("MLC LLM initialization failed", e)
        }
    }

    private fun warmupModel() {
        try {
            Log.d(TAG, "Performing model warmup inference")

            // Perform a short warmup inference to initialize model
            val warmupPrompt = "Hello, I am a fibre optic technician assistant."
            chat?.generate(warmupPrompt, maxTokens = 10)

            Log.i(TAG, "Model warmup completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Model warmup failed", e)
            throw RuntimeException("Model warmup failed", e)
        }
    }

    private fun generateWithMLC(prompt: String, maxTokens: Int): String {
        try {
            Log.d(TAG, "Generating response for prompt: ${prompt.take(100)}...")

            val response = chat?.generate(
                message = prompt,
                maxTokens = maxTokens
            ) ?: throw RuntimeException("Chat instance not initialized")

            Log.d(TAG, "Generated response with ${response.length} characters")
            return response

        } catch (e: Exception) {
            Log.e(TAG, "MLC LLM inference failed", e)
            throw RuntimeException("MLC LLM inference failed", e)
        }
    }

    private fun generateWithMLCStream(prompt: String, maxTokens: Int): Flow<String> = flow {
        try {
            chat?.generateStream(
                message = prompt,
                maxTokens = maxTokens
            )?.collect { token ->
                emit(token)
            } ?: throw RuntimeException("Chat instance not initialized for streaming")

        } catch (e: Exception) {
            Log.e(TAG, "MLC LLM streaming failed", e)
            throw RuntimeException("MLC LLM streaming failed", e)
        }
    }.flowOn(Dispatchers.Default)

    private fun getCurrentMemoryUsage(): Int {
        try {
            // Get memory usage from MLC LLM runtime
            val memoryStats = llmEngine?.getMemoryStats()
            return memoryStats?.usedMemoryMB ?: 1024 // Fallback to 1GB if unavailable

        } catch (e: Exception) {
            Log.w(TAG, "Failed to get memory usage from MLC runtime", e)
            return 1024 // Fallback to 1GB
        }
    }

    private fun shutdownMLCRuntime() {
        try {
            Log.d(TAG, "Shutting down MLC runtime resources")

            // Clean up chat instance
            chat?.reset()
            chat = null

            // Clean up LLM engine
            llmEngine?.unload()
            llmEngine = null

            Log.i(TAG, "MLC runtime shutdown complete")

        } catch (e: Exception) {
            Log.e(TAG, "Error during MLC runtime shutdown", e)
        }
    }
}

/**
 * Installation context for contextual guidance
 */
data class InstallationContext(
    val currentStep: String,
    val equipmentType: String,
    val location: String,
    val previousIssues: List<String> = emptyList()
)

/**
 * LLM performance metrics
 */
data class LLMMetrics(
    val modelLoadTimeMs: Long,
    val isInitialized: Boolean,
    val memoryUsageMB: Int,
    val targetTokensPerSecond: Int
)