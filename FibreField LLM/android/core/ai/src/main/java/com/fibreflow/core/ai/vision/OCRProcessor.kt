package com.fibreflow.core.ai.vision

import android.graphics.Bitmap
import com.fibreflow.core.common.result.Result
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR processor for specialized text recognition
 * Handles specific patterns like drop numbers, serial numbers, and meter readings
 */
@Singleton
class OCRProcessor @Inject constructor(
    private val textExtractor: TextExtractor
) {

    /**
     * Process OCR for installation-specific data
     */
    suspend fun processInstallationData(
        bitmap: Bitmap,
        dataTypes: List<String>
    ): Result<OCRProcessingResult> {
        return try {
            Timber.d("Processing OCR for installation data types: $dataTypes")

            val allResults = mutableListOf<DataExtractionResult>()

            for (dataType in dataTypes) {
                val extractionResult = textExtractor.extractData(bitmap, dataType)
                if (extractionResult is Result.Success) {
                    allResults.add(extractionResult.data)
                }
            }

            val result = OCRProcessingResult(
                processedDataTypes = dataTypes,
                extractionResults = allResults,
                overallConfidence = calculateOverallConfidence(allResults),
                processingTimestamp = System.currentTimeMillis()
            )

            Timber.d("OCR processing completed: ${allResults.size} data types processed")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error processing OCR")
            Result.Error(e)
        }
    }

    /**
     * Extract drop number with enhanced validation
     */
    suspend fun extractDropNumber(
        bitmap: Bitmap
    ): Result<DropNumberResult> {
        return try {
            val extractionResult = textExtractor.extractData(bitmap, "DROP_NUMBER")
            if (extractionResult is Result.Error) {
                return extractionResult
            }

            val data = (extractionResult as Result.Success).data
            val dropNumbers = data.extractedData.filter { it.type == "drop_number" }

            val bestMatch = dropNumbers.maxByOrNull { it.confidence }

            val result = DropNumberResult(
                dropNumber = bestMatch?.value,
                confidence = bestMatch?.confidence ?: 0.0f,
                alternatives = dropNumbers.map { it.value },
                extractionTimestamp = System.currentTimeMillis()
            )

            Timber.d("Drop number extraction: ${result.dropNumber} (confidence: ${result.confidence})")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting drop number")
            Result.Error(e)
        }
    }

    /**
     * Extract power meter reading with validation
     */
    suspend fun extractPowerMeterReading(
        bitmap: Bitmap
    ): Result<PowerMeterResult> {
        return try {
            val extractionResult = textExtractor.extractData(bitmap, "POWER_METER_READING")
            if (extractionResult is Result.Error) {
                return extractionResult
            }

            val data = (extractionResult as Result.Success).data
            val readings = data.extractedData.filter { it.type == "power_meter_reading" }

            val bestMatch = readings.maxByOrNull { it.confidence }

            val result = PowerMeterResult(
                reading = bestMatch?.value,
                confidence = bestMatch?.confidence ?: 0.0f,
                unit = "kWh", // Assume kWh for power meter
                alternatives = readings.map { it.value },
                extractionTimestamp = System.currentTimeMillis()
            )

            Timber.d("Power meter extraction: ${result.reading} ${result.unit} (confidence: ${result.confidence})")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting power meter reading")
            Result.Error(e)
        }
    }

    /**
     * Extract serial number with format validation
     */
    suspend fun extractSerialNumber(
        bitmap: Bitmap,
        expectedFormat: SerialFormat = SerialFormat.ONT
    ): Result<SerialNumberResult> {
        return try {
            val extractionResult = textExtractor.extractData(bitmap, "SERIAL_NUMBER")
            if (extractionResult is Result.Error) {
                return extractionResult
            }

            val data = (extractionResult as Result.Success).data
            val serialNumbers = data.extractedData.filter { it.type == "serial_number" }

            // Filter by expected format
            val validSerials = serialNumbers.filter { serial ->
                validateSerialFormat(serial.value, expectedFormat)
            }

            val bestMatch = validSerials.maxByOrNull { it.confidence }

            val result = SerialNumberResult(
                serialNumber = bestMatch?.value,
                format = expectedFormat,
                confidence = bestMatch?.confidence ?: 0.0f,
                isValidFormat = bestMatch?.value?.let { validateSerialFormat(it, expectedFormat) } ?: false,
                alternatives = validSerials.map { it.value },
                extractionTimestamp = System.currentTimeMillis()
            )

            Timber.d("Serial number extraction: ${result.serialNumber} (format: ${result.format}, valid: ${result.isValidFormat})")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting serial number")
            Result.Error(e)
        }
    }

    /**
     * Batch process multiple OCR tasks
     */
    suspend fun batchProcess(
        bitmaps: List<Pair<Bitmap, String>>
    ): Result<List<OCRProcessingResult>> {
        return try {
            val results = mutableListOf<OCRProcessingResult>()

            for ((bitmap, dataType) in bitmaps) {
                val result = processInstallationData(bitmap, listOf(dataType))
                if (result is Result.Success) {
                    results.add(result.data)
                }
            }

            Timber.d("Batch OCR processing completed: ${results.size}/${bitmaps.size} successful")
            Result.Success(results)
        } catch (e: Exception) {
            Timber.e(e, "Error in batch OCR processing")
            Result.Error(e)
        }
    }

    /**
     * Validate serial number format
     */
    private fun validateSerialFormat(serial: String, format: SerialFormat): Boolean {
        return when (format) {
            SerialFormat.ONT -> serial.matches(Regex("^[A-Z]{3}\\d{6,9}$"))
            SerialFormat.GENERIC -> serial.matches(Regex("^[A-Z0-9]{6,12}$"))
            SerialFormat.NUMERIC -> serial.matches(Regex("^\\d{8,12}$"))
        }
    }

    /**
     * Calculate overall confidence from multiple results
     */
    private fun calculateOverallConfidence(results: List<DataExtractionResult>): Float {
        if (results.isEmpty()) return 0.0f

        val totalConfidence = results.sumOf { it.confidence.toDouble() }.toFloat()
        return totalConfidence / results.size
    }
}

/**
 * OCR processing result data class
 */
data class OCRProcessingResult(
    val processedDataTypes: List<String>,
    val extractionResults: List<DataExtractionResult>,
    val overallConfidence: Float,
    val processingTimestamp: Long
) {

    /**
     * Check if OCR processing was successful
     */
    val isSuccessful: Boolean
        get() = extractionResults.isNotEmpty() && overallConfidence > 0.6f

    /**
     * Get all extracted data items
     */
    val allExtractedData: List<DataItem>
        get() = extractionResults.flatMap { it.extractedData }
}

/**
 * Drop number extraction result data class
 */
data class DropNumberResult(
    val dropNumber: String?,
    val confidence: Float,
    val alternatives: List<String>,
    val extractionTimestamp: Long
) {

    /**
     * Check if drop number extraction was successful
     */
    val isSuccessful: Boolean
        get() = dropNumber != null && confidence > 0.7f
}

/**
 * Power meter extraction result data class
 */
data class PowerMeterResult(
    val reading: String?,
    val confidence: Float,
    val unit: String,
    val alternatives: List<String>,
    val extractionTimestamp: Long
) {

    /**
     * Check if power meter extraction was successful
     */
    val isSuccessful: Boolean
        get() = reading != null && confidence > 0.7f

    /**
     * Get reading as number (if possible)
     */
    val numericReading: Double?
        get() = reading?.toDoubleOrNull()
}

/**
 * Serial number extraction result data class
 */
data class SerialNumberResult(
    val serialNumber: String?,
    val format: SerialFormat,
    val confidence: Float,
    val isValidFormat: Boolean,
    val alternatives: List<String>,
    val extractionTimestamp: Long
) {

    /**
     * Check if serial number extraction was successful
     */
    val isSuccessful: Boolean
        get() = serialNumber != null && confidence > 0.7f && isValidFormat
}

/**
 * Serial number format enum
 */
enum class SerialFormat {
    ONT,        // ONT format: ABC123456
    GENERIC,    // Generic alphanumeric
    NUMERIC     // Numeric only
}