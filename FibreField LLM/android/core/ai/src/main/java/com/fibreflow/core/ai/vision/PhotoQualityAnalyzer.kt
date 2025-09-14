package com.fibreflow.core.ai.vision

import android.graphics.Bitmap
import com.fibreflow.core.common.result.Result
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Photo quality analyzer for installation workflow
 * Analyzes focus, brightness, contrast, and blur metrics
 */
@Singleton
class PhotoQualityAnalyzer @Inject constructor() {

    /**
     * Analyze photo quality metrics
     */
    fun analyzeQuality(
        bitmap: Bitmap,
        photoType: String
    ): Result<PhotoQualityAnalysis> {
        return try {
            Timber.d("Analyzing photo quality for type: $photoType")

            // Get basic image properties
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Calculate quality metrics
            val brightnessScore = calculateBrightness(pixels)
            val contrastScore = calculateContrast(pixels)
            val focusScore = calculateFocus(pixels, width, height)
            val blurScore = calculateBlur(pixels, width, height)
            val noiseScore = calculateNoise(pixels, width, height)

            // Calculate overall confidence
            val confidence = calculateOverallConfidence(
                brightnessScore, contrastScore, focusScore, blurScore, noiseScore, photoType
            )

            val analysis = PhotoQualityAnalysis(
                brightnessScore = brightnessScore,
                contrastScore = contrastScore,
                focusScore = focusScore,
                blurScore = blurScore,
                noiseScore = noiseScore,
                confidence = confidence,
                imageWidth = width,
                imageHeight = height,
                analysisTimestamp = System.currentTimeMillis()
            )

            Timber.d("Photo quality analysis completed: brightness=%.2f, focus=%.2f, confidence=%.2f",
                brightnessScore, focusScore, confidence)

            Result.Success(analysis)
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing photo quality")
            Result.Error(e)
        }
    }

    /**
     * Calculate brightness score (0.0 to 1.0)
     * Higher values indicate better brightness
     */
    private fun calculateBrightness(pixels: IntArray): Float {
        var totalBrightness = 0f
        val sampleSize = minOf(pixels.size, 10000) // Sample for performance

        for (i in 0 until sampleSize step (pixels.size / sampleSize).coerceAtLeast(1)) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Calculate luminance (perceived brightness)
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            totalBrightness += luminance
        }

        val averageBrightness = totalBrightness / sampleSize

        // Score brightness (optimal range: 0.4 to 0.7)
        return when {
            averageBrightness < 0.2f -> 0.1f // Too dark
            averageBrightness > 0.8f -> 0.2f // Too bright
            averageBrightness in 0.4f..0.7f -> 1.0f // Optimal
            else -> 0.6f // Acceptable
        }
    }

    /**
     * Calculate contrast score (0.0 to 1.0)
     * Higher values indicate better contrast
     */
    private fun calculateContrast(pixels: IntArray): Float {
        var minLuminance = 1.0f
        var maxLuminance = 0.0f
        val sampleSize = minOf(pixels.size, 5000)

        for (i in 0 until sampleSize step (pixels.size / sampleSize).coerceAtLeast(1)) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

            minLuminance = minOf(minLuminance, luminance)
            maxLuminance = maxOf(maxLuminance, luminance)
        }

        val contrast = maxLuminance - minLuminance
        return contrast.coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculate focus score using edge detection (0.0 to 1.0)
     * Higher values indicate sharper focus
     */
    private fun calculateFocus(pixels: IntArray, width: Int, height: Int): Float {
        var edgeStrength = 0f
        var pixelCount = 0

        // Simple edge detection using Sobel-like operator
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = getLuminance(pixels[y * width + x])
                val right = getLuminance(pixels[y * width + x + 1])
                val bottom = getLuminance(pixels[(y + 1) * width + x])

                val gradientX = abs(center - right)
                val gradientY = abs(center - bottom)
                val gradient = sqrt((gradientX * gradientX + gradientY * gradientY).toDouble()).toFloat()

                edgeStrength += gradient
                pixelCount++
            }
        }

        val averageEdgeStrength = if (pixelCount > 0) edgeStrength / pixelCount else 0f
        return averageEdgeStrength.coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculate blur score (0.0 to 1.0)
     * Lower values indicate less blur (better)
     */
    private fun calculateBlur(pixels: IntArray, width: Int, height: Int): Float {
        // Simple blur detection based on high-frequency content
        val focusScore = calculateFocus(pixels, width, height)
        return (1.0f - focusScore).coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculate noise score (0.0 to 1.0)
     * Lower values indicate less noise (better)
     */
    private fun calculateNoise(pixels: IntArray, width: Int, height: Int): Float {
        var totalVariance = 0f
        var pixelCount = 0

        // Calculate local variance as noise indicator
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = getLuminance(pixels[y * width + x])
                val neighbors = listOf(
                    getLuminance(pixels[y * width + x - 1]),
                    getLuminance(pixels[y * width + x + 1]),
                    getLuminance(pixels[(y - 1) * width + x]),
                    getLuminance(pixels[(y + 1) * width + x])
                )

                val averageNeighbor = neighbors.average().toFloat()
                val variance = (center - averageNeighbor) * (center - averageNeighbor)

                totalVariance += variance
                pixelCount++
            }
        }

        val averageVariance = if (pixelCount > 0) totalVariance / pixelCount else 0f
        return averageVariance.coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculate overall confidence score
     */
    private fun calculateOverallConfidence(
        brightness: Float,
        contrast: Float,
        focus: Float,
        blur: Float,
        noise: Float,
        photoType: String
    ): Float {
        // Weight factors based on photo type
        val weights = when (photoType) {
            "POWER_METER_READING" -> {
                // Text/photos need high focus and contrast
                mapOf(
                    "brightness" to 0.2f,
                    "contrast" to 0.3f,
                    "focus" to 0.4f,
                    "blur" to 0.05f,
                    "noise" to 0.05f
                )
            }
            "ONT_POWER_LIGHT", "ONT_LOS_LIGHT", "ONT_PON_LIGHT", "ONT_LAN_LIGHT" -> {
                // Light detection needs good brightness and focus
                mapOf(
                    "brightness" to 0.3f,
                    "contrast" to 0.2f,
                    "focus" to 0.3f,
                    "blur" to 0.1f,
                    "noise" to 0.1f
                )
            }
            else -> {
                // General photos
                mapOf(
                    "brightness" to 0.25f,
                    "contrast" to 0.2f,
                    "focus" to 0.3f,
                    "blur" to 0.15f,
                    "noise" to 0.1f
                )
            }
        }

        val confidence = (brightness * weights["brightness"]!!) +
                         (contrast * weights["contrast"]!!) +
                         (focus * weights["focus"]!!) +
                         ((1.0f - blur) * weights["blur"]!!) +
                         ((1.0f - noise) * weights["noise"]!!)

        return confidence.coerceIn(0.0f, 1.0f)
    }

    /**
     * Get luminance from pixel
     */
    private fun getLuminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }
}

/**
 * Photo quality analysis result data class
 */
data class PhotoQualityAnalysis(
    val brightnessScore: Float,     // 0.0 to 1.0
    val contrastScore: Float,       // 0.0 to 1.0
    val focusScore: Float,          // 0.0 to 1.0
    val blurScore: Float,           // 0.0 to 1.0 (lower is better)
    val noiseScore: Float,          // 0.0 to 1.0 (lower is better)
    val confidence: Float,          // 0.0 to 1.0
    val imageWidth: Int,
    val imageHeight: Int,
    val analysisTimestamp: Long
) {

    /**
     * Check if photo meets quality requirements
     */
    val isAcceptable: Boolean
        get() = confidence >= 0.6f

    /**
     * Get quality grade
     */
    val qualityGrade: String
        get() = when {
            confidence >= 0.8f -> "Excellent"
            confidence >= 0.7f -> "Good"
            confidence >= 0.6f -> "Acceptable"
            confidence >= 0.4f -> "Poor"
            else -> "Unacceptable"
        }

    /**
     * Get improvement suggestions
     */
    val suggestions: List<String>
        get() {
            val suggestions = mutableListOf<String>()

            if (brightnessScore < 0.4f) {
                suggestions.add("Increase lighting or use flash")
            } else if (brightnessScore > 0.8f) {
                suggestions.add("Reduce lighting or avoid direct sunlight")
            }

            if (focusScore < 0.6f) {
                suggestions.add("Ensure camera is focused and steady")
            }

            if (contrastScore < 0.5f) {
                suggestions.add("Improve lighting contrast")
            }

            if (blurScore > 0.4f) {
                suggestions.add("Hold camera steady and avoid motion blur")
            }

            if (noiseScore > 0.3f) {
                suggestions.add("Use better lighting to reduce noise")
            }

            return suggestions.ifEmpty { listOf("Photo quality is acceptable") }
        }
}