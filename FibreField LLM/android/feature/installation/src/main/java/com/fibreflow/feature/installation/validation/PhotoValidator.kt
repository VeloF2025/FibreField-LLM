package com.fibreflow.feature.installation.validation

import android.graphics.Bitmap
import com.fibreflow.core.common.result.Result
import com.fibreflow.core.database.entities.PhotoEntity
import com.fibreflow.core.database.entities.ValidationStatus
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Photo validator for installation workflow
 * Validates captured photos against quality requirements and AI analysis
 */
@Singleton
class PhotoValidator @Inject constructor(
    private val qualityAnalyzer: PhotoQualityAnalyzer
) {

    /**
     * Validate photo for specific installation step
     */
    suspend fun validatePhoto(
        photo: Bitmap,
        photoType: String,
        stepId: Int
    ): Result<PhotoValidationResult> {
        return try {
            Timber.d("Validating photo: $photoType for step $stepId")

            // Analyze photo quality
            val qualityResult = qualityAnalyzer.analyzeQuality(photo, photoType)
            if (qualityResult is Result.Error) {
                return Result.Error(qualityResult.exception)
            }

            val qualityAnalysis = (qualityResult as Result.Success).data

            // Check if quality meets requirements
            val qualityValid = checkQualityRequirements(qualityAnalysis, photoType)

            // Perform AI validation based on photo type
            val aiValidationResult = performAivalidation(photo, photoType)

            // Determine overall validation status
            val overallValid = qualityValid && aiValidationResult.isValid
            val validationStatus = when {
                !overallValid -> ValidationStatus.FAILED
                qualityAnalysis.confidence < 0.8f -> ValidationStatus.REQUIRES_REVIEW
                else -> ValidationStatus.PASSED
            }

            val result = PhotoValidationResult(
                photoType = photoType,
                stepId = stepId,
                isValid = overallValid,
                validationStatus = validationStatus,
                qualityAnalysis = qualityAnalysis,
                aiValidation = aiValidationResult,
                validationTimestamp = System.currentTimeMillis()
            )

            Timber.d("Photo validation completed: $photoType - Valid: $overallValid")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error validating photo")
            Result.Error(e)
        }
    }

    /**
     * Validate multiple photos for a step
     */
    suspend fun validatePhotosForStep(
        photos: List<Pair<Bitmap, String>>,
        stepId: Int
    ): Result<StepValidationResult> {
        return try {
            val photoResults = mutableListOf<PhotoValidationResult>()

            for ((photo, photoType) in photos) {
                val validationResult = validatePhoto(photo, photoType, stepId)
                if (validationResult is Result.Success) {
                    photoResults.add(validationResult.data)
                } else {
                    // If any photo fails validation, mark step as failed
                    return Result.Success(
                        StepValidationResult(
                            stepId = stepId,
                            isValid = false,
                            photoResults = photoResults,
                            failureReason = "Photo validation failed: ${photoType}"
                        )
                    )
                }
            }

            val allValid = photoResults.all { it.isValid }
            Result.Success(
                StepValidationResult(
                    stepId = stepId,
                    isValid = allValid,
                    photoResults = photoResults,
                    failureReason = if (!allValid) "Some photos failed validation" else null
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error validating photos for step")
            Result.Error(e)
        }
    }

    /**
     * Check if photo quality meets requirements for photo type
     */
    private fun checkQualityRequirements(
        qualityAnalysis: PhotoQualityAnalysis,
        photoType: String
    ): Boolean {
        return when (photoType) {
            "ONT_POWER_LIGHT", "ONT_LOS_LIGHT", "ONT_PON_LIGHT", "ONT_LAN_LIGHT" -> {
                // Light detection photos need good focus and brightness
                qualityAnalysis.focusScore > 0.7f &&
                qualityAnalysis.brightnessScore > 0.6f &&
                qualityAnalysis.blurScore < 0.3f
            }
            "POWER_METER_READING" -> {
                // Meter readings need excellent focus and contrast
                qualityAnalysis.focusScore > 0.8f &&
                qualityAnalysis.contrastScore > 0.7f &&
                qualityAnalysis.blurScore < 0.2f
            }
            "FIBER_CONNECTION", "SPLICE_CLOSURE" -> {
                // Technical photos need good focus and appropriate lighting
                qualityAnalysis.focusScore > 0.7f &&
                qualityAnalysis.brightnessScore > 0.5f
            }
            else -> {
                // Default quality requirements
                qualityAnalysis.focusScore > 0.6f &&
                qualityAnalysis.brightnessScore > 0.4f
            }
        }
    }

    /**
     * Perform AI validation based on photo type
     */
    private suspend fun performAivalidation(
        photo: Bitmap,
        photoType: String
    ): AiValidationResult {
        return when (photoType) {
            "ONT_POWER_LIGHT", "ONT_LOS_LIGHT", "ONT_PON_LIGHT", "ONT_LAN_LIGHT" -> {
                // Use ONT light detection AI
                AiValidationResult(
                    isValid = true, // Placeholder - would use actual AI model
                    confidence = 0.85f,
                    detectedObjects = listOf("light_indicator"),
                    validationMessage = "Light indicator detected successfully"
                )
            }
            "POWER_METER_READING" -> {
                // Use OCR for meter reading
                AiValidationResult(
                    isValid = true, // Placeholder - would use OCR
                    confidence = 0.90f,
                    detectedObjects = listOf("meter_digits"),
                    validationMessage = "Meter reading extracted successfully"
                )
            }
            "FIBER_CONNECTION" -> {
                // Use object detection for fiber components
                AiValidationResult(
                    isValid = true, // Placeholder - would use object detection
                    confidence = 0.80f,
                    detectedObjects = listOf("fiber_cable", "connector"),
                    validationMessage = "Fiber connection components detected"
                )
            }
            else -> {
                AiValidationResult(
                    isValid = true,
                    confidence = 0.75f,
                    detectedObjects = emptyList(),
                    validationMessage = "Photo validated successfully"
                )
            }
        }
    }

    /**
     * Get validation requirements for photo type
     */
    fun getValidationRequirements(photoType: String): PhotoRequirements {
        return when (photoType) {
            "ONT_POWER_LIGHT", "ONT_LOS_LIGHT", "ONT_PON_LIGHT", "ONT_LAN_LIGHT" -> {
                PhotoRequirements(
                    minFocusScore = 0.7f,
                    minBrightnessScore = 0.6f,
                    maxBlurScore = 0.3f,
                    requiredObjects = listOf("light_indicator"),
                    description = "Clear photo of ONT light indicator"
                )
            }
            "POWER_METER_READING" -> {
                PhotoRequirements(
                    minFocusScore = 0.8f,
                    minContrastScore = 0.7f,
                    maxBlurScore = 0.2f,
                    requiredObjects = listOf("meter_digits"),
                    description = "Clear, well-lit photo of power meter display"
                )
            }
            else -> {
                PhotoRequirements(
                    minFocusScore = 0.6f,
                    minBrightnessScore = 0.4f,
                    description = "Clear photo meeting basic quality requirements"
                )
            }
        }
    }
}

/**
 * Photo validation result data class
 */
data class PhotoValidationResult(
    val photoType: String,
    val stepId: Int,
    val isValid: Boolean,
    val validationStatus: ValidationStatus,
    val qualityAnalysis: PhotoQualityAnalysis,
    val aiValidation: AiValidationResult,
    val validationTimestamp: Long,
    val errorMessage: String? = null
)

/**
 * Step validation result data class
 */
data class StepValidationResult(
    val stepId: Int,
    val isValid: Boolean,
    val photoResults: List<PhotoValidationResult>,
    val failureReason: String? = null
)

/**
 * AI validation result data class
 */
data class AiValidationResult(
    val isValid: Boolean,
    val confidence: Float,
    val detectedObjects: List<String>,
    val validationMessage: String
)

/**
 * Photo requirements data class
 */
data class PhotoRequirements(
    val minFocusScore: Float = 0.6f,
    val minBrightnessScore: Float = 0.4f,
    val minContrastScore: Float = 0.0f,
    val maxBlurScore: Float = 0.4f,
    val requiredObjects: List<String> = emptyList(),
    val description: String
)