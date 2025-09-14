package com.fibreflow.core.ai.vision

import android.graphics.Bitmap
import com.fibreflow.core.common.result.Result
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Barcode scanner using ML Kit
 * Supports ONT serial numbers, UPS labels, and other installation barcodes
 */
@Singleton
class BarcodeScanner @Inject constructor() {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .build()
    )

    /**
     * Scan barcode from bitmap
     */
    suspend fun scanBarcode(
        bitmap: Bitmap,
        barcodeType: String = "any"
    ): Result<BarcodeResult> {
        return try {
            Timber.d("Scanning barcode from bitmap, type: $barcodeType")

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val barcodeResults = scanner.process(inputImage).await()

            if (barcodeResults.isEmpty()) {
                Timber.d("No barcodes found in image")
                return Result.Success(
                    BarcodeResult(
                        barcodeType = barcodeType,
                        value = null,
                        format = null,
                        confidence = 0.0f,
                        boundingBox = null,
                        scanTimestamp = System.currentTimeMillis()
                    )
                )
            }

            // Get the best barcode result
            val bestBarcode = barcodeResults.first()
            val result = BarcodeResult(
                barcodeType = barcodeType,
                value = bestBarcode.rawValue,
                format = getFormatName(bestBarcode.format),
                confidence = 1.0f, // ML Kit doesn't provide confidence scores
                boundingBox = bestBarcode.boundingBox,
                scanTimestamp = System.currentTimeMillis()
            )

            Timber.d("Barcode scanned successfully: ${result.value} (format: ${result.format})")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning barcode")
            Result.Error(e)
        }
    }

    /**
     * Scan multiple barcodes from bitmap
     */
    suspend fun scanMultipleBarcodes(
        bitmap: Bitmap,
        expectedTypes: List<String> = emptyList()
    ): Result<List<BarcodeResult>> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val barcodeResults = scanner.process(inputImage).await()

            val results = barcodeResults.map { barcode ->
                BarcodeResult(
                    barcodeType = determineBarcodeType(barcode.rawValue ?: ""),
                    value = barcode.rawValue,
                    format = getFormatName(barcode.format),
                    confidence = 1.0f,
                    boundingBox = barcode.boundingBox,
                    scanTimestamp = System.currentTimeMillis()
                )
            }

            Timber.d("Multiple barcodes scanned: ${results.size} found")
            Result.Success(results)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning multiple barcodes")
            Result.Error(e)
        }
    }

    /**
     * Validate barcode format and content
     */
    fun validateBarcode(
        barcodeValue: String,
        expectedType: String
    ): Result<BarcodeValidationResult> {
        return try {
            val isValid = when (expectedType) {
                "ONT_SERIAL" -> validateOntSerial(barcodeValue)
                "UPS_LABEL" -> validateUpsLabel(barcodeValue)
                "POWER_METER" -> validatePowerMeterId(barcodeValue)
                else -> barcodeValue.isNotBlank() && barcodeValue.length >= 3
            }

            val result = BarcodeValidationResult(
                barcodeValue = barcodeValue,
                expectedType = expectedType,
                isValid = isValid,
                validationMessage = if (isValid) "Barcode is valid" else "Barcode format is invalid",
                validationTimestamp = System.currentTimeMillis()
            )

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Clean up scanner resources
     */
    fun cleanup() {
        try {
            scanner.close()
            Timber.d("Barcode scanner cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up barcode scanner")
        }
    }

    /**
     * Get format name from ML Kit format constant
     */
    private fun getFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            else -> "UNKNOWN"
        }
    }

    /**
     * Determine barcode type from value
     */
    private fun determineBarcodeType(value: String): String {
        return when {
            value.startsWith("ONT") || value.matches(Regex("^[A-Z]{3}\\d{6}$")) -> "ONT_SERIAL"
            value.startsWith("1Z") || value.matches(Regex("^1Z\\d{16}$")) -> "UPS_LABEL"
            value.matches(Regex("^\\d{10,12}$")) -> "POWER_METER"
            else -> "UNKNOWN"
        }
    }

    /**
     * Validate ONT serial number format
     */
    private fun validateOntSerial(value: String): Boolean {
        // ONT serial format: typically 3 letters + 6 digits (e.g., "ONT123456")
        return value.matches(Regex("^[A-Z]{3}\\d{6}$"))
    }

    /**
     * Validate UPS label format
     */
    private fun validateUpsLabel(value: String): Boolean {
        // UPS tracking format: 1Z + 16 digits
        return value.matches(Regex("^1Z\\d{16}$"))
    }

    /**
     * Validate power meter ID format
     */
    private fun validatePowerMeterId(value: String): Boolean {
        // Power meter IDs are typically 10-12 digits
        return value.matches(Regex("^\\d{10,12}$"))
    }
}

/**
 * Barcode scan result data class
 */
data class BarcodeResult(
    val barcodeType: String,
    val value: String?,
    val format: String?,
    val confidence: Float,
    val boundingBox: android.graphics.Rect?,
    val scanTimestamp: Long
) {

    /**
     * Check if barcode was successfully scanned
     */
    val isSuccessful: Boolean
        get() = value != null && value.isNotBlank()
}

/**
 * Barcode validation result data class
 */
data class BarcodeValidationResult(
    val barcodeValue: String,
    val expectedType: String,
    val isValid: Boolean,
    val validationMessage: String,
    val validationTimestamp: Long
)