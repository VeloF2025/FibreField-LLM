package com.fibreflow.core.ai.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.fibreflow.core.common.result.Result
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONT Light Detector for analyzing ONT indicator lights
 * Detects Power, LOS, PON, and LAN light states and colors
 */
@Singleton
class ONTLightDetector @Inject constructor() {

    /**
     * Detect ONT lights from bitmap
     */
    suspend fun detectLights(
        bitmap: Bitmap,
        ontModel: ONTModel = ONTModel.GENERIC
    ): Result<ONTDetectionResult> {
        return try {
            Timber.d("Detecting ONT lights for model: $ontModel")

            // Analyze the bitmap for light indicators
            val lightAnalysis = analyzeLightIndicators(bitmap, ontModel)

            val result = ONTDetectionResult(
                ontModel = ontModel,
                detectedLights = lightAnalysis,
                overallConfidence = calculateOverallConfidence(lightAnalysis),
                detectionTimestamp = System.currentTimeMillis()
            )

            Timber.d("ONT light detection completed: ${lightAnalysis.size} lights detected")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error detecting ONT lights")
            Result.Error(e)
        }
    }

    /**
     * Analyze bitmap for light indicators
     */
    private fun analyzeLightIndicators(
        bitmap: Bitmap,
        ontModel: ONTModel
    ): List<LightDetection> {
        val lights = mutableListOf<LightDetection>()

        // Get expected light positions for the ONT model
        val expectedLights = getExpectedLights(ontModel)

        for (expectedLight in expectedLights) {
            val detection = detectLight(bitmap, expectedLight)
            lights.add(detection)
        }

        return lights
    }

    /**
     * Detect individual light
     */
    private fun detectLight(
        bitmap: Bitmap,
        expectedLight: ExpectedLight
    ): LightDetection {
        // Define search region around expected position
        val searchRegion = defineSearchRegion(bitmap, expectedLight)

        // Analyze pixels in search region
        val pixelAnalysis = analyzePixels(bitmap, searchRegion)

        // Determine light state and color
        val lightState = determineLightState(pixelAnalysis)
        val detectedColor = determineLightColor(pixelAnalysis)

        return LightDetection(
            lightType = expectedLight.type,
            expectedPosition = expectedLight.position,
            searchRegion = searchRegion,
            detectedState = lightState,
            detectedColor = detectedColor,
            confidence = calculateLightConfidence(pixelAnalysis, expectedLight),
            pixelAnalysis = pixelAnalysis
        )
    }

    /**
     * Define search region for light detection
     */
    private fun defineSearchRegion(
        bitmap: Bitmap,
        expectedLight: ExpectedLight
    ): SearchRegion {
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        // Calculate search region based on expected position and tolerance
        val regionWidth = (bitmapWidth * 0.15).toInt() // 15% of image width
        val regionHeight = (bitmapHeight * 0.10).toInt() // 10% of image height

        val centerX = (expectedLight.position.x * bitmapWidth).toInt()
        val centerY = (expectedLight.position.y * bitmapHeight).toInt()

        val left = maxOf(0, centerX - regionWidth / 2)
        val top = maxOf(0, centerY - regionHeight / 2)
        val right = minOf(bitmapWidth, centerX + regionWidth / 2)
        val bottom = minOf(bitmapHeight, centerY + regionHeight / 2)

        return SearchRegion(left, top, right, bottom)
    }

    /**
     * Analyze pixels in search region
     */
    private fun analyzePixels(
        bitmap: Bitmap,
        region: SearchRegion
    ): PixelAnalysis {
        var totalBrightness = 0f
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        var pixelCount = 0

        // Sample pixels in the region
        val stepX = maxOf(1, (region.right - region.left) / 10)
        val stepY = maxOf(1, (region.bottom - region.top) / 10)

        for (x in region.left until region.right step stepX) {
            for (y in region.top until region.bottom step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                redSum += red
                greenSum += green
                blueSum += blue

                // Calculate brightness (luminance)
                val brightness = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f
                totalBrightness += brightness
                pixelCount++
            }
        }

        val averageBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 0f
        val averageRed = if (pixelCount > 0) redSum / pixelCount else 0
        val averageGreen = if (pixelCount > 0) greenSum / pixelCount else 0
        val averageBlue = if (pixelCount > 0) blueSum / pixelCount else 0

        return PixelAnalysis(
            averageBrightness = averageBrightness,
            averageRed = averageRed,
            averageGreen = averageGreen,
            averageBlue = averageBlue,
            pixelCount = pixelCount
        )
    }

    /**
     * Determine light state (ON/OFF)
     */
    private fun determineLightState(analysis: PixelAnalysis): LightState {
        // Thresholds for determining if light is ON
        val brightnessThreshold = 0.3f // 30% brightness
        val colorIntensityThreshold = 100 // RGB value

        val isBrightEnough = analysis.averageBrightness > brightnessThreshold
        val hasColorIntensity = (analysis.averageRed + analysis.averageGreen + analysis.averageBlue) > colorIntensityThreshold

        return if (isBrightEnough && hasColorIntensity) LightState.ON else LightState.OFF
    }

    /**
     * Determine light color
     */
    private fun determineLightColor(analysis: PixelAnalysis): LightColor {
        if (analysis.averageBrightness < 0.2f) {
            return LightColor.OFF
        }

        val red = analysis.averageRed
        val green = analysis.averageGreen
        val blue = analysis.averageBlue

        // Determine dominant color
        return when {
            red > green + 30 && red > blue + 30 -> LightColor.RED
            green > red + 30 && green > blue + 30 -> LightColor.GREEN
            blue > red + 30 && blue > green + 30 -> LightColor.BLUE
            red > 150 && green > 150 && blue < 100 -> LightColor.YELLOW // Red + Green
            else -> LightColor.WHITE
        }
    }

    /**
     * Calculate confidence for light detection
     */
    private fun calculateLightConfidence(
        analysis: PixelAnalysis,
        expectedLight: ExpectedLight
    ): Float {
        // Base confidence on brightness and color consistency
        val brightnessConfidence = minOf(analysis.averageBrightness * 2.5f, 1.0f)
        val colorConfidence = if (analysis.averageBrightness > 0.2f) 0.8f else 0.2f

        return (brightnessConfidence + colorConfidence) / 2.0f
    }

    /**
     * Calculate overall confidence
     */
    private fun calculateOverallConfidence(lights: List<LightDetection>): Float {
        if (lights.isEmpty()) return 0.0f

        val totalConfidence = lights.sumOf { it.confidence.toDouble() }.toFloat()
        return totalConfidence / lights.size
    }

    /**
     * Get expected lights for ONT model
     */
    private fun getExpectedLights(model: ONTModel): List<ExpectedLight> {
        return when (model) {
            ONTModel.GENERIC -> listOf(
                ExpectedLight(LightType.POWER, RelativePosition(0.2f, 0.3f)),
                ExpectedLight(LightType.LOS, RelativePosition(0.4f, 0.3f)),
                ExpectedLight(LightType.PON, RelativePosition(0.6f, 0.3f)),
                ExpectedLight(LightType.LAN, RelativePosition(0.8f, 0.3f))
            )
            ONTModel.HUAWEI -> listOf(
                ExpectedLight(LightType.POWER, RelativePosition(0.15f, 0.25f)),
                ExpectedLight(LightType.LOS, RelativePosition(0.35f, 0.25f)),
                ExpectedLight(LightType.PON, RelativePosition(0.55f, 0.25f)),
                ExpectedLight(LightType.LAN, RelativePosition(0.75f, 0.25f))
            )
            ONTModel.ZTE -> listOf(
                ExpectedLight(LightType.POWER, RelativePosition(0.2f, 0.35f)),
                ExpectedLight(LightType.LOS, RelativePosition(0.4f, 0.35f)),
                ExpectedLight(LightType.PON, RelativePosition(0.6f, 0.35f)),
                ExpectedLight(LightType.LAN, RelativePosition(0.8f, 0.35f))
            )
        }
    }
}

/**
 * ONT detection result data class
 */
data class ONTDetectionResult(
    val ontModel: ONTModel,
    val detectedLights: List<LightDetection>,
    val overallConfidence: Float,
    val detectionTimestamp: Long
) {

    /**
     * Check if detection was successful
     */
    val isSuccessful: Boolean
        get() = detectedLights.isNotEmpty() && overallConfidence > 0.6f

    /**
     * Get summary of light states
     */
    val lightSummary: Map<LightType, LightState>
        get() = detectedLights.associate { it.lightType to it.detectedState }

    /**
     * Check if all critical lights are ON
     */
    val allCriticalLightsOn: Boolean
        get() {
            val criticalLights = listOf(LightType.POWER, LightType.PON)
            return criticalLights.all { lightType ->
                lightSummary[lightType] == LightState.ON
            }
        }
}

/**
 * Light detection data class
 */
data class LightDetection(
    val lightType: LightType,
    val expectedPosition: RelativePosition,
    val searchRegion: SearchRegion,
    val detectedState: LightState,
    val detectedColor: LightColor,
    val confidence: Float,
    val pixelAnalysis: PixelAnalysis
)

/**
 * Expected light data class
 */
data class ExpectedLight(
    val type: LightType,
    val position: RelativePosition
)

/**
 * Pixel analysis data class
 */
data class PixelAnalysis(
    val averageBrightness: Float,
    val averageRed: Int,
    val averageGreen: Int,
    val averageBlue: Int,
    val pixelCount: Int
)

/**
 * Search region data class
 */
data class SearchRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Relative position data class (0.0 to 1.0)
 */
data class RelativePosition(
    val x: Float, // 0.0 = left, 1.0 = right
    val y: Float  // 0.0 = top, 1.0 = bottom
)

/**
 * Light type enum
 */
enum class LightType {
    POWER,
    LOS,
    PON,
    LAN
}

/**
 * Light state enum
 */
enum class LightState {
    ON,
    OFF,
    BLINKING
}

/**
 * Light color enum
 */
enum class LightColor {
    RED,
    GREEN,
    BLUE,
    YELLOW,
    WHITE,
    OFF
}

/**
 * ONT model enum
 */
enum class ONTModel {
    GENERIC,
    HUAWEI,
    ZTE
}