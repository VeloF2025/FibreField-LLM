package com.fibreflow.feature.installation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera manager for photo capture during installation workflow
 * Handles CameraX integration with real-time validation feedback
 */
@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Initialize camera with lifecycle owner
     */
    suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ): Result<Unit> {
        return try {
            val provider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider = provider

            // Select back camera
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // Setup preview
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(surfaceProvider)
            }

            // Setup image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(android.view.Surface.ROTATION_0)
                .build()

            // Setup image analysis for real-time validation
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Bind use cases
            val useCases = mutableListOf<UseCase>()
            preview?.let { useCases.add(it) }
            imageCapture?.let { useCases.add(it) }
            imageAnalysis?.let { useCases.add(it) }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            Timber.d("Camera initialized successfully")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize camera")
            Result.Error(e)
        }
    }

    /**
     * Capture photo with specified configuration
     */
    suspend fun capturePhoto(
        photoType: String,
        quality: PhotoQuality = PhotoQuality.HIGH
    ): Result<Bitmap> {
        return try {
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
                createTempFile(suffix = ".jpg")
            ).build()

            imageCapture?.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Timber.d("Photo captured successfully: ${outputFileResults.savedUri}")
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Timber.e(exception, "Photo capture failed")
                    }
                }
            )

            // For now, return a placeholder - in real implementation,
            // this would convert the captured image to Bitmap
            Result.Error(Exception("Photo capture not fully implemented"))
        } catch (e: Exception) {
            Timber.e(e, "Error capturing photo")
            Result.Error(e)
        }
    }

    /**
     * Start real-time image analysis for validation feedback
     */
    fun startImageAnalysis(
        analyzer: (Bitmap) -> Unit
    ): Result<Unit> {
        return try {
            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    analyzer(bitmap)
                } catch (e: Exception) {
                    Timber.e(e, "Error in image analysis")
                } finally {
                    imageProxy.close()
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start image analysis")
            Result.Error(e)
        }
    }

    /**
     * Stop image analysis
     */
    fun stopImageAnalysis(): Result<Unit> {
        return try {
            imageAnalysis?.clearAnalyzer()
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error stopping image analysis")
            Result.Error(e)
        }
    }

    /**
     * Get camera capabilities and settings
     */
    fun getCameraCapabilities(): CameraCapabilities {
        val cameraInfo = camera?.cameraInfo
        return CameraCapabilities(
            hasFlash = cameraInfo?.hasFlashUnit() ?: false,
            maxZoomRatio = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1.0f,
            minZoomRatio = camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1.0f,
            isFocusSupported = true, // Assume focus is supported
            supportedPhotoFormats = listOf("JPEG", "PNG")
        )
    }

    /**
     * Set zoom level
     */
    fun setZoomLevel(zoomRatio: Float): Result<Unit> {
        return try {
            camera?.cameraControl?.setZoomRatio(zoomRatio)
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error setting zoom level")
            Result.Error(e)
        }
    }

    /**
     * Enable/disable flash
     */
    fun setFlashMode(enabled: Boolean): Result<Unit> {
        return try {
            val flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            imageCapture?.flashMode = flashMode
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error setting flash mode")
            Result.Error(e)
        }
    }

    /**
     * Clean up camera resources
     */
    fun cleanup() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            Timber.d("Camera resources cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up camera resources")
        }
    }

    /**
     * Convert ImageProxy to Bitmap for analysis
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val image = imageProxy.image ?: throw IllegalStateException("Image is null")

        // Convert YUV to JPEG
        val yuvImage = YuvImage(
            image.planes[0].buffer.array(),
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height),
            90,
            outputStream
        )

        val jpegBytes = outputStream.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    /**
     * Create temporary file for photo capture
     */
    private fun createTempFile(suffix: String): java.io.File {
        val tempDir = context.cacheDir
        return java.io.File.createTempFile("photo_", suffix, tempDir)
    }
}

/**
 * Camera capabilities data class
 */
data class CameraCapabilities(
    val hasFlash: Boolean,
    val maxZoomRatio: Float,
    val minZoomRatio: Float,
    val isFocusSupported: Boolean,
    val supportedPhotoFormats: List<String>
)

/**
 * Photo quality enum
 */
enum class PhotoQuality {
    LOW,
    MEDIUM,
    HIGH
}