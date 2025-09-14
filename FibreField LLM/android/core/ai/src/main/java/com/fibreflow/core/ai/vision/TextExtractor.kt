package com.fibreflow.core.ai.vision

import android.graphics.Bitmap
import com.fibreflow.core.common.result.Result
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text extractor using ML Kit OCR
 * Extracts text from photos for installation workflow
 */
@Singleton
class TextExtractor @Inject constructor() {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extract text from bitmap
     */
    suspend fun extractText(
        bitmap: Bitmap,
        textType: String = "general"
    ): Result<TextExtractionResult> {
        return try {
            Timber.d("Extracting text from bitmap, type: $textType")

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = textRecognizer.process(inputImage).await()

            val extractedText = visionText.text
            val textBlocks = visionText.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    confidence = block.confidence,
                    boundingBox = block.boundingBox,
                    lines = block.lines.map { line ->
                        TextLine(
                            text = line.text,
                            confidence = line.confidence,
                            boundingBox = line.boundingBox,
                            elements = line.elements.map { element ->
                                TextElement(
                                    text = element.text,
                                    confidence = element.confidence,
                                    boundingBox = element.boundingBox
                                )
                            }
                        )
                    }
                )
            }

            val result = TextExtractionResult(
                textType = textType,
                fullText = extractedText,
                textBlocks = textBlocks,
                confidence = calculateOverallConfidence(textBlocks),
                extractionTimestamp = System.currentTimeMillis()
            )

            Timber.d("Text extraction completed: ${extractedText.length} characters, ${textBlocks.size} blocks")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting text")
            Result.Error(e)
        }
    }

    /**
     * Extract specific data patterns from text
     */
    suspend fun extractData(
        bitmap: Bitmap,
        dataType: String
    ): Result<DataExtractionResult> {
        return try {
            val textResult = extractText(bitmap, dataType)
            if (textResult is Result.Error) {
                return textResult
            }

            val extractionResult = textResult as Result.Success
            val fullText = extractionResult.data.fullText

            val extractedData = when (dataType) {
                "POWER_METER_READING" -> extractPowerMeterReading(fullText)
                "DROP_NUMBER" -> extractDropNumber(fullText)
                "SERIAL_NUMBER" -> extractSerialNumber(fullText)
                "ADDRESS" -> extractAddress(fullText)
                else -> listOf(DataItem("raw_text", fullText, 1.0f))
            }

            val result = DataExtractionResult(
                dataType = dataType,
                extractedData = extractedData,
                sourceText = fullText,
                confidence = calculateDataConfidence(extractedData),
                extractionTimestamp = System.currentTimeMillis()
            )

            Timber.d("Data extraction completed for $dataType: ${extractedData.size} items")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting data")
            Result.Error(e)
        }
    }

    /**
     * Validate extracted text quality
     */
    fun validateTextQuality(
        text: String,
        expectedLength: IntRange? = null,
        expectedPattern: Regex? = null
    ): Result<TextValidationResult> {
        return try {
            val isValidLength = expectedLength?.contains(text.length) ?: true
            val matchesPattern = expectedPattern?.matches(text) ?: true
            val hasValidCharacters = text.matches(Regex("^[A-Za-z0-9\\s\\-\\.]+$"))

            val isValid = isValidLength && matchesPattern && hasValidCharacters

            val result = TextValidationResult(
                text = text,
                isValid = isValid,
                validationChecks = listOf(
                    ValidationCheck("length", isValidLength, expectedLength?.toString() ?: "any"),
                    ValidationCheck("pattern", matchesPattern, expectedPattern?.pattern ?: "any"),
                    ValidationCheck("characters", hasValidCharacters, "alphanumeric + spaces")
                ),
                validationTimestamp = System.currentTimeMillis()
            )

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Clean up text recognizer resources
     */
    fun cleanup() {
        try {
            textRecognizer.close()
            Timber.d("Text extractor cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up text extractor")
        }
    }

    /**
     * Calculate overall confidence from text blocks
     */
    private fun calculateOverallConfidence(textBlocks: List<TextBlock>): Float {
        if (textBlocks.isEmpty()) return 0.0f

        val totalConfidence = textBlocks.sumOf { it.confidence.toDouble() }.toFloat()
        return totalConfidence / textBlocks.size
    }

    /**
     * Calculate confidence for extracted data
     */
    private fun calculateDataConfidence(dataItems: List<DataItem>): Float {
        if (dataItems.isEmpty()) return 0.0f

        val totalConfidence = dataItems.sumOf { it.confidence.toDouble() }.toFloat()
        return totalConfidence / dataItems.size
    }

    /**
     * Extract power meter reading from text
     */
    private fun extractPowerMeterReading(text: String): List<DataItem> {
        val readings = mutableListOf<DataItem>()

        // Look for numeric patterns that could be meter readings
        val meterPatterns = listOf(
            Regex("(\\d{5,8})"), // 5-8 digits
            Regex("Reading:\\s*(\\d{5,8})"),
            Regex("Meter:\\s*(\\d{5,8})")
        )

        for (pattern in meterPatterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val value = match.groupValues[1]
                readings.add(DataItem("power_meter_reading", value, 0.9f))
            }
        }

        return readings.distinctBy { it.value }
    }

    /**
     * Extract drop number from text
     */
    private fun extractDropNumber(text: String): List<DataItem> {
        val dropNumbers = mutableListOf<DataItem>()

        // Look for drop number patterns
        val dropPatterns = listOf(
            Regex("Drop\\s*#?\\s*([A-Z0-9]{3,10})", RegexOption.IGNORE_CASE),
            Regex("([A-Z]{2,3}\\d{3,6})"), // e.g., "ABC123"
            Regex("Drop:\\s*([A-Z0-9]{3,10})", RegexOption.IGNORE_CASE)
        )

        for (pattern in dropPatterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val value = match.groupValues[1].uppercase()
                dropNumbers.add(DataItem("drop_number", value, 0.85f))
            }
        }

        return dropNumbers.distinctBy { it.value }
    }

    /**
     * Extract serial number from text
     */
    private fun extractSerialNumber(text: String): List<DataItem> {
        val serialNumbers = mutableListOf<DataItem>()

        // Look for serial number patterns
        val serialPatterns = listOf(
            Regex("SN:\\s*([A-Z0-9]{6,12})", RegexOption.IGNORE_CASE),
            Regex("Serial:\\s*([A-Z0-9]{6,12})", RegexOption.IGNORE_CASE),
            Regex("([A-Z]{3}\\d{6,9})") // e.g., "ONT123456"
        )

        for (pattern in serialPatterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val value = match.groupValues[1].uppercase()
                serialNumbers.add(DataItem("serial_number", value, 0.9f))
            }
        }

        return serialNumbers.distinctBy { it.value }
    }

    /**
     * Extract address from text
     */
    private fun extractAddress(text: String): List<DataItem> {
        // Simple address extraction - look for street patterns
        val addressPatterns = listOf(
            Regex("(\\d+\\s+[A-Za-z0-9\\s,.-]+(?:Street|St|Avenue|Ave|Road|Rd|Drive|Dr|Lane|Ln|Way|Place|Pl|Court|Ct)\\s*,?\\s*[A-Za-z\\s]+\\s*,?\\s*\\d{5})", RegexOption.IGNORE_CASE)
        )

        for (pattern in addressPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return listOf(DataItem("address", match.groupValues[1], 0.8f))
            }
        }

        return emptyList()
    }
}

/**
 * Text extraction result data class
 */
data class TextExtractionResult(
    val textType: String,
    val fullText: String,
    val textBlocks: List<TextBlock>,
    val confidence: Float,
    val extractionTimestamp: Long
) {

    /**
     * Check if text extraction was successful
     */
    val isSuccessful: Boolean
        get() = fullText.isNotBlank() && confidence > 0.5f
}

/**
 * Text block data class
 */
data class TextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: android.graphics.Rect?,
    val lines: List<TextLine>
)

/**
 * Text line data class
 */
data class TextLine(
    val text: String,
    val confidence: Float,
    val boundingBox: android.graphics.Rect?,
    val elements: List<TextElement>
)

/**
 * Text element data class
 */
data class TextElement(
    val text: String,
    val confidence: Float,
    val boundingBox: android.graphics.Rect?
)

/**
 * Data extraction result data class
 */
data class DataExtractionResult(
    val dataType: String,
    val extractedData: List<DataItem>,
    val sourceText: String,
    val confidence: Float,
    val extractionTimestamp: Long
) {

    /**
     * Check if data extraction was successful
     */
    val isSuccessful: Boolean
        get() = extractedData.isNotEmpty() && confidence > 0.6f
}

/**
 * Data item data class
 */
data class DataItem(
    val type: String,
    val value: String,
    val confidence: Float
)

/**
 * Text validation result data class
 */
data class TextValidationResult(
    val text: String,
    val isValid: Boolean,
    val validationChecks: List<ValidationCheck>,
    val validationTimestamp: Long
)

/**
 * Validation check data class
 */
data class ValidationCheck(
    val checkType: String,
    val passed: Boolean,
    val expectedValue: String
)