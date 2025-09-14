package com.fibreflow.core.ai.vision.models

import android.content.Context
import android.graphics.Bitmap
import com.fibreflow.core.common.result.Result
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONT Detection Model using TensorFlow Lite
 * Specialized model for detecting ONT devices and their light indicators
 */
@Singleton
class ONTDetectionModel @Inject constructor(
    private val context: Context
) {

    private var interpreter: Interpreter? = null
    private val modelInputSize = 224 // pixels
    private val modelOutputSize = 4 // number of light types (POWER, LOS, PON, LAN)

    /**
     * Initialize the TensorFlow Lite model
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            Timber.d("Initializing ONT detection model")

            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Use 4 threads for better performance
                setUseNNAPI(true) // Use NNAPI for hardware acceleration
            }

            interpreter = Interpreter(modelBuffer, options)
            Timber.d("ONT detection model initialized successfully")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize ONT detection model")
            Result.Error(e)
        }
    }

    /**
     * Detect ONT and analyze lights
     */
    suspend fun detectONT(
        bitmap: Bitmap,
        ontModel: String = "generic"
    ): Result<ONTModelResult> {
        return try {
            if (interpreter == null) {
                return Result.Error(Exception("Model not initialized"))
            }

            Timber.d("Running ONT detection on bitmap: ${bitmap.width}x${bitmap.height}")

            // Preprocess bitmap for model input
            val inputBuffer = preprocessBitmap(bitmap)

            // Prepare output buffer
            val outputBuffer = Array(1) { FloatArray(modelOutputSize) }

            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)

            // Process results
            val lightProbabilities = outputBuffer[0]
            val lightStates = interpretLightStates(lightProbabilities)

            val result = ONTModelResult(
                ontDetected = isONTDetected(lightProbabilities),
                ontModel = ontModel,
                lightProbabilities = lightProbabilities.toList(),
                detectedLightStates = lightStates,
                confidence = calculateOverallConfidence(lightProbabilities),
                detectionTimestamp = System.currentTimeMillis()
            )

            Timber.d("ONT detection completed: detected=${result.ontDetected}, confidence=${result.confidence}")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error running ONT detection")
            Result.Error(e)
        }
    }

    /**
     * Analyze specific light indicators
     */
    suspend fun analyzeLightIndicators(
        bitmap: Bitmap,
        lightType: String
    ): Result<LightAnalysisResult> {
        return try {
            val fullResult = detectONT(bitmap)
            if (fullResult is Result.Error) {
                return fullResult
            }

            val ontResult = (fullResult as Result.Success).data
            val lightIndex = getLightIndex(lightType)
            val probability = ontResult.lightProbabilities.getOrNull(lightIndex) ?: 0.0f
            val state = ontResult.detectedLightStates[lightType] ?: LightState.UNKNOWN

            val result = LightAnalysisResult(
                lightType = lightType,
                probability = probability,
                detectedState = state,
                confidence = probability,
                analysisTimestamp = System.currentTimeMillis()
            )

            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing light indicators")
            Result.Error(e)
        }
    }

    /**
     * Preprocess bitmap for model input
     */
    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        // Resize bitmap to model input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)

        // Create input buffer
        val inputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Convert bitmap to RGB float values
        val pixels = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(pixels, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        return inputBuffer
    }

    /**
     * Load TensorFlow Lite model file
     */
    private fun loadModelFile(): MappedByteBuffer {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd("models/ont_detection_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel

        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Check if ONT is detected based on model output
     */
    private fun isONTDetected(probabilities: FloatArray): Boolean {
        // ONT is detected if any light has probability > 0.5
        return probabilities.any { it > 0.5f }
    }

    /**
     * Interpret light states from model probabilities
     */
    private fun interpretLightStates(probabilities: FloatArray): Map<String, LightState> {
        val lightTypes = arrayOf("POWER", "LOS", "PON", "LAN")
        val states = mutableMapOf<String, LightState>()

        probabilities.forEachIndexed { index, probability ->
            val lightType = lightTypes.getOrNull(index) ?: "UNKNOWN"
            val state = when {
                probability > 0.7f -> LightState.ON
                probability > 0.3f -> LightState.BLINKING
                else -> LightState.OFF
            }
            states[lightType] = state
        }

        return states
    }

    /**
     * Calculate overall confidence
     */
    private fun calculateOverallConfidence(probabilities: FloatArray): Float {
        return probabilities.average().toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * Get light index for light type
     */
    private fun getLightIndex(lightType: String): Int {
        return when (lightType.uppercase()) {
            "POWER" -> 0
            "LOS" -> 1
            "PON" -> 2
            "LAN" -> 3
            else -> 0
        }
    }

    /**
     * Clean up model resources
     */
    fun cleanup() {
        try {
            interpreter?.close()
            interpreter = null
            Timber.d("ONT detection model cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up ONT detection model")
        }
    }
}

/**
 * ONT model result data class
 */
data class ONTModelResult(
    val ontDetected: Boolean,
    val ontModel: String,
    val lightProbabilities: List<Float>,
    val detectedLightStates: Map<String, LightState>,
    val confidence: Float,
    val detectionTimestamp: Long
) {

    /**
     * Check if detection was successful
     */
    val isSuccessful: Boolean
        get() = ontDetected && confidence > 0.6f

    /**
     * Get summary of light states
     */
    val lightSummary: String
        get() = detectedLightStates.entries.joinToString(", ") { "${it.key}: ${it.value}" }
}

/**
 * Light analysis result data class
 */
data class LightAnalysisResult(
    val lightType: String,
    val probability: Float,
    val detectedState: LightState,
    val confidence: Float,
    val analysisTimestamp: Long
) {

    /**
     * Check if analysis was successful
     */
    val isSuccessful: Boolean
        get() = confidence > 0.5f
}

/**
 * Light state enum
 */
enum class LightState {
    ON,
    OFF,
    BLINKING,
    UNKNOWN
}