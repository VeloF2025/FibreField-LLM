package com.fibreflow.core.performance

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera Performance Monitor for tracking camera-related operations
 * Monitors photo capture, processing, and upload performance
 */
@Singleton
class CameraPerformance @Inject constructor(
    private val performanceMonitor: PerformanceMonitor,
    private val uiPerformanceMonitor: UIPerformanceMonitor
) {

    companion object {
        private const val TAG = "CameraPerformance"
        private const val TARGET_CAPTURE_TIME_MS = 1000L // 1 second
        private const val TARGET_PROCESSING_TIME_MS = 2000L // 2 seconds
        private const val TARGET_UPLOAD_TIME_MS = 5000L // 5 seconds for upload
        private const val PHOTO_SIZE_WARNING_MB = 5.0f
        private const val PHOTO_SIZE_CRITICAL_MB = 10.0f
    }

    private val cameraMetrics = ConcurrentHashMap<String, CameraOperationMetrics>()
    private val photoProcessingMetrics = ConcurrentHashMap<String, PhotoProcessingMetrics>()

    private val totalCaptures = AtomicLong(0)
    private val successfulCaptures = AtomicLong(0)
    private val failedCaptures = AtomicLong(0)

    private val totalUploads = AtomicLong(0)
    private val successfulUploads = AtomicLong(0)
    private val failedUploads = AtomicLong(0)

    /**
     * Record photo capture performance
     */
    fun recordPhotoCapture(
        captureId: String,
        startTime: Long,
        endTime: Long,
        success: Boolean,
        photoSizeBytes: Long,
        cameraResolution: String? = null,
        lightingConditions: String? = null
    ) {
        val captureTime = endTime - startTime
        val photoSizeMB = photoSizeBytes / (1024.0 * 1024.0)

        val metrics = CameraOperationMetrics(
            operationId = captureId,
            operationType = CameraOperationType.CAPTURE,
            durationMs = captureTime,
            success = success,
            photoSizeMB = photoSizeMB.toFloat(),
            cameraResolution = cameraResolution,
            lightingConditions = lightingConditions,
            timestamp = System.currentTimeMillis()
        )

        cameraMetrics[captureId] = metrics

        // Update counters
        totalCaptures.incrementAndGet()
        if (success) {
            successfulCaptures.incrementAndGet()
        } else {
            failedCaptures.incrementAndGet()
        }

        // Check performance thresholds
        if (captureTime > TARGET_CAPTURE_TIME_MS) {
            Timber.w("Slow photo capture: ${captureTime}ms for photo ${captureId}")

            performanceMonitor.recordOperation(
                "photo_capture",
                captureTime.toInt(),
                success
            )
        }

        // Check photo size
        if (photoSizeMB > PHOTO_SIZE_WARNING_MB) {
            Timber.w("Large photo captured: ${photoSizeMB}MB for photo ${captureId}")

            if (photoSizeMB > PHOTO_SIZE_CRITICAL_MB) {
                Timber.e("Critically large photo: ${photoSizeMB}MB for photo ${captureId}")
            }
        }

        Timber.d("Recorded photo capture: $captureId, time: ${captureTime}ms, size: ${photoSizeMB}MB, success: $success")
    }

    /**
     * Record photo processing performance
     */
    fun recordPhotoProcessing(
        processingId: String,
        startTime: Long,
        endTime: Long,
        success: Boolean,
        processingSteps: List<String>,
        inputSizeBytes: Long,
        outputSizeBytes: Long,
        processingType: String // "compression", "filter", "analysis", etc.
    ) {
        val processingTime = endTime - startTime
        val compressionRatio = if (inputSizeBytes > 0) {
            (1.0 - (outputSizeBytes.toDouble() / inputSizeBytes)) * 100
        } else 0.0

        val metrics = PhotoProcessingMetrics(
            processingId = processingId,
            processingTimeMs = processingTime,
            success = success,
            processingSteps = processingSteps,
            inputSizeBytes = inputSizeBytes,
            outputSizeBytes = outputSizeBytes,
            compressionRatioPercent = compressionRatio,
            processingType = processingType,
            timestamp = System.currentTimeMillis()
        )

        photoProcessingMetrics[processingId] = metrics

        // Check processing performance
        if (processingTime > TARGET_PROCESSING_TIME_MS) {
            Timber.w("Slow photo processing: ${processingTime}ms for processing ${processingId}")

            performanceMonitor.recordOperation(
                "photo_processing_$processingType",
                processingTime.toInt(),
                success
            )
        }

        Timber.d("Recorded photo processing: $processingId, time: ${processingTime}ms, compression: ${compressionRatio}%")
    }

    /**
     * Record photo upload performance
     */
    fun recordPhotoUpload(
        uploadId: String,
        startTime: Long,
        endTime: Long,
        success: Boolean,
        fileSizeBytes: Long,
        uploadSpeedMbps: Double? = null,
        networkType: String? = null,
        retryCount: Int = 0
    ) {
        val uploadTime = endTime - startTime
        val fileSizeMB = fileSizeBytes / (1024.0 * 1024.0)
        val uploadSpeed = if (uploadTime > 0) {
            (fileSizeBytes * 8.0) / (uploadTime / 1000.0) / (1024 * 1024) // Mbps
        } else 0.0

        val metrics = PhotoUploadMetrics(
            uploadId = uploadId,
            uploadTimeMs = uploadTime,
            success = success,
            fileSizeMB = fileSizeMB.toFloat(),
            uploadSpeedMbps = uploadSpeedMbps ?: uploadSpeed,
            networkType = networkType,
            retryCount = retryCount,
            timestamp = System.currentTimeMillis()
        )

        cameraMetrics[uploadId] = metrics

        // Update counters
        totalUploads.incrementAndGet()
        if (success) {
            successfulUploads.incrementAndGet()
        } else {
            failedUploads.incrementAndGet()
        }

        // Check upload performance
        if (uploadTime > TARGET_UPLOAD_TIME_MS) {
            Timber.w("Slow photo upload: ${uploadTime}ms for upload ${uploadId}")

            performanceMonitor.recordOperation(
                "photo_upload",
                uploadTime.toInt(),
                success
            )
        }

        Timber.d("Recorded photo upload: $uploadId, time: ${uploadTime}ms, speed: ${uploadSpeed}Mbps, success: $success")
    }

    /**
     * Record camera initialization time
     */
    fun recordCameraInitialization(
        initializationId: String,
        startTime: Long,
        endTime: Long,
        success: Boolean,
        cameraApi: String? = null
    ) {
        val initTime = endTime - startTime

        val metrics = CameraInitializationMetrics(
            initializationId = initializationId,
            initializationTimeMs = initTime,
            success = success,
            cameraApi = cameraApi,
            timestamp = System.currentTimeMillis()
        )

        cameraMetrics[initializationId] = metrics

        if (initTime > 2000) { // Over 2 seconds is slow
            Timber.w("Slow camera initialization: ${initTime}ms")

            performanceMonitor.recordOperation(
                "camera_initialization",
                initTime.toInt(),
                success
            )
        }

        Timber.d("Recorded camera initialization: $initializationId, time: ${initTime}ms, success: $success")
    }

    /**
     * Get camera performance report
     */
    fun getCameraPerformanceReport(): CameraPerformanceReport {
        val captureMetrics = cameraMetrics.values.filterIsInstance<CameraOperationMetrics>()
            .filter { it.operationType == CameraOperationType.CAPTURE }

        val uploadMetrics = cameraMetrics.values.filterIsInstance<PhotoUploadMetrics>()

        val averageCaptureTime = captureMetrics.map { it.durationMs.toDouble() }.average()
        val averageUploadTime = uploadMetrics.map { it.uploadTimeMs.toDouble() }.average()
        val averageUploadSpeed = uploadMetrics.mapNotNull { it.uploadSpeedMbps }.average()

        val processingMetrics = photoProcessingMetrics.values.toList()
        val averageProcessingTime = processingMetrics.map { it.processingTimeMs.toDouble() }.average()
        val averageCompressionRatio = processingMetrics.map { it.compressionRatioPercent }.average()

        val captureSuccessRate = if (totalCaptures.get() > 0) {
            successfulCaptures.get().toDouble() / totalCaptures.get() * 100
        } else 0.0

        val uploadSuccessRate = if (totalUploads.get() > 0) {
            successfulUploads.get().toDouble() / totalUploads.get() * 100
        } else 0.0

        return CameraPerformanceReport(
            totalCaptures = totalCaptures.get(),
            successfulCaptures = successfulCaptures.get(),
            failedCaptures = failedCaptures.get(),
            captureSuccessRate = captureSuccessRate,
            averageCaptureTimeMs = averageCaptureTime,
            averageProcessingTimeMs = averageProcessingTime,
            averageCompressionRatioPercent = averageCompressionRatio,
            totalUploads = totalUploads.get(),
            successfulUploads = successfulUploads.get(),
            failedUploads = failedUploads.get(),
            uploadSuccessRate = uploadSuccessRate,
            averageUploadTimeMs = averageUploadTime,
            averageUploadSpeedMbps = averageUploadSpeed,
            isWithinTargets = isWithinCameraTargets(
                averageCaptureTime,
                averageProcessingTime,
                averageUploadTime
            ),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get camera performance recommendations
     */
    fun getCameraPerformanceRecommendations(): List<CameraPerformanceRecommendation> {
        val report = getCameraPerformanceReport()
        val recommendations = mutableListOf<CameraPerformanceRecommendation>()

        // Capture time recommendations
        if (report.averageCaptureTimeMs > TARGET_CAPTURE_TIME_MS) {
            recommendations.add(
                CameraPerformanceRecommendation(
                    type = CameraRecommendationType.OPTIMIZE_CAPTURE_SETTINGS,
                    priority = CameraRecommendationPriority.HIGH,
                    title = "Optimize Camera Capture Settings",
                    description = "Average capture time is ${report.averageCaptureTimeMs}ms, should be under ${TARGET_CAPTURE_TIME_MS}ms",
                    expectedImprovement = "30-50% faster capture times",
                    implementationEffort = CameraImplementationEffort.MEDIUM
                )
            )
        }

        // Processing time recommendations
        if (report.averageProcessingTimeMs > TARGET_PROCESSING_TIME_MS) {
            recommendations.add(
                CameraPerformanceRecommendation(
                    type = CameraRecommendationType.OPTIMIZE_IMAGE_PROCESSING,
                    priority = CameraRecommendationPriority.HIGH,
                    title = "Optimize Image Processing",
                    description = "Average processing time is ${report.averageProcessingTimeMs}ms, should be under ${TARGET_PROCESSING_TIME_MS}ms",
                    expectedImprovement = "40-60% faster processing",
                    implementationEffort = CameraImplementationEffort.HIGH
                )
            )
        }

        // Upload time recommendations
        if (report.averageUploadTimeMs > TARGET_UPLOAD_TIME_MS) {
            recommendations.add(
                CameraPerformanceRecommendation(
                    type = CameraRecommendationType.OPTIMIZE_UPLOAD_STRATEGY,
                    priority = CameraRecommendationPriority.MEDIUM,
                    title = "Optimize Upload Strategy",
                    description = "Average upload time is ${report.averageUploadTimeMs}ms, should be under ${TARGET_UPLOAD_TIME_MS}ms",
                    expectedImprovement = "25-40% faster uploads",
                    implementationEffort = CameraImplementationEffort.MEDIUM
                )
            )
        }

        // Success rate recommendations
        if (report.captureSuccessRate < 95.0) {
            recommendations.add(
                CameraPerformanceRecommendation(
                    type = CameraRecommendationType.IMPROVE_CAPTURE_RELIABILITY,
                    priority = CameraRecommendationPriority.HIGH,
                    title = "Improve Capture Reliability",
                    description = "Capture success rate is ${report.captureSuccessRate}%, should be over 95%",
                    expectedImprovement = "5-10% improvement in success rate",
                    implementationEffort = CameraImplementationEffort.MEDIUM
                )
            )
        }

        return recommendations.sortedByDescending { it.priority.ordinal }
    }

    /**
     * Clear camera metrics data
     */
    fun clearMetrics() {
        cameraMetrics.clear()
        photoProcessingMetrics.clear()
        totalCaptures.set(0)
        successfulCaptures.set(0)
        failedCaptures.set(0)
        totalUploads.set(0)
        successfulUploads.set(0)
        failedUploads.set(0)
        Timber.d("Camera performance metrics cleared")
    }

    // Private helper methods

    private fun isWithinCameraTargets(
        averageCaptureTime: Double,
        averageProcessingTime: Double,
        averageUploadTime: Double
    ): Boolean {
        return averageCaptureTime < TARGET_CAPTURE_TIME_MS &&
               averageProcessingTime < TARGET_PROCESSING_TIME_MS &&
               averageUploadTime < TARGET_UPLOAD_TIME_MS
    }
}

/**
 * Data classes for camera performance monitoring
 */

data class CameraOperationMetrics(
    val operationId: String,
    val operationType: CameraOperationType,
    val durationMs: Long,
    val success: Boolean,
    val photoSizeMB: Float? = null,
    val cameraResolution: String? = null,
    val lightingConditions: String? = null,
    val timestamp: Long
)

data class PhotoProcessingMetrics(
    val processingId: String,
    val processingTimeMs: Long,
    val success: Boolean,
    val processingSteps: List<String>,
    val inputSizeBytes: Long,
    val outputSizeBytes: Long,
    val compressionRatioPercent: Double,
    val processingType: String,
    val timestamp: Long
)

data class PhotoUploadMetrics(
    val uploadId: String,
    val uploadTimeMs: Long,
    val success: Boolean,
    val fileSizeMB: Float,
    val uploadSpeedMbps: Double,
    val networkType: String? = null,
    val retryCount: Int = 0,
    val timestamp: Long
)

data class CameraInitializationMetrics(
    val initializationId: String,
    val initializationTimeMs: Long,
    val success: Boolean,
    val cameraApi: String? = null,
    val timestamp: Long
)

data class CameraPerformanceReport(
    val totalCaptures: Long,
    val successfulCaptures: Long,
    val failedCaptures: Long,
    val captureSuccessRate: Double,
    val averageCaptureTimeMs: Double,
    val averageProcessingTimeMs: Double,
    val averageCompressionRatioPercent: Double,
    val totalUploads: Long,
    val successfulUploads: Long,
    val failedUploads: Long,
    val uploadSuccessRate: Double,
    val averageUploadTimeMs: Double,
    val averageUploadSpeedMbps: Double,
    val isWithinTargets: Boolean,
    val timestamp: Long
)

data class CameraPerformanceRecommendation(
    val type: CameraRecommendationType,
    val priority: CameraRecommendationPriority,
    val title: String,
    val description: String,
    val expectedImprovement: String,
    val implementationEffort: CameraImplementationEffort
)

enum class CameraOperationType {
    CAPTURE,
    UPLOAD,
    INITIALIZATION
}

enum class CameraRecommendationType {
    OPTIMIZE_CAPTURE_SETTINGS,
    OPTIMIZE_IMAGE_PROCESSING,
    OPTIMIZE_UPLOAD_STRATEGY,
    IMPROVE_CAPTURE_RELIABILITY,
    IMPLEMENT_COMPRESSION,
    CACHE_FREQUENTLY_USED_SETTINGS
}

enum class CameraRecommendationPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class CameraImplementationEffort {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}