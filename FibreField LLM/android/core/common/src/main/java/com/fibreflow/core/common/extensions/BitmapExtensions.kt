// 🟢 WORKING: Bitmap utility extensions for image processing
package com.fibreflow.core.common.extensions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * Resize bitmap to specified maximum dimensions while maintaining aspect ratio
 */
fun Bitmap.resizeToFit(maxWidth: Int, maxHeight: Int): Bitmap {
    if (width <= maxWidth && height <= maxHeight) {
        return this
    }
    
    val ratio = min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    val newWidth = (width * ratio).toInt()
    val newHeight = (height * ratio).toInt()
    
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

/**
 * Create thumbnail with specified size
 */
fun Bitmap.createThumbnail(size: Int = 150): Bitmap {
    val ratio = if (width > height) {
        size.toFloat() / width
    } else {
        size.toFloat() / height
    }
    
    val newWidth = (width * ratio).toInt()
    val newHeight = (height * ratio).toInt()
    
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

/**
 * Compress bitmap to JPEG with specified quality
 */
fun Bitmap.compressToJpeg(quality: Int = 85): ByteArray {
    val outputStream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    return outputStream.toByteArray()
}

/**
 * Save bitmap to file with compression
 */
fun Bitmap.saveToFile(
    file: File,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 85
): Boolean {
    return try {
        FileOutputStream(file).use { out ->
            compress(format, quality, out)
        }
        true
    } catch (e: IOException) {
        false
    }
}

/**
 * Rotate bitmap by specified degrees
 */
fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    
    val matrix = Matrix().apply {
        postRotate(degrees)
    }
    
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * Get bitmap size in bytes
 */
fun Bitmap.sizeInBytes(): Int {
    return allocationByteCount
}

/**
 * Get bitmap size in MB
 */
fun Bitmap.sizeInMB(): Double {
    return sizeInBytes().toDouble() / (1024 * 1024)
}

/**
 * Check if bitmap is within size limits
 */
fun Bitmap.isWithinSizeLimits(maxSizeMB: Int): Boolean {
    return sizeInMB() <= maxSizeMB
}

/**
 * Crop bitmap to square from center
 */
fun Bitmap.cropToSquare(): Bitmap {
    val size = min(width, height)
    val x = (width - size) / 2
    val y = (height - size) / 2
    
    return Bitmap.createBitmap(this, x, y, size, size)
}

/**
 * Apply basic image enhancement
 */
fun Bitmap.enhance(): Bitmap {
    // Basic brightness and contrast adjustment
    val config = this.config ?: Bitmap.Config.ARGB_8888
    val enhanced = Bitmap.createBitmap(width, height, config)
    
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    
    // Simple contrast and brightness enhancement
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = ((pixel shr 16) and 0xFF).let { minOf(255, (it * 1.1).toInt()) }
        val g = ((pixel shr 8) and 0xFF).let { minOf(255, (it * 1.1).toInt()) }
        val b = (pixel and 0xFF).let { minOf(255, (it * 1.1).toInt()) }
        val a = (pixel shr 24) and 0xFF
        
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    
    enhanced.setPixels(pixels, 0, width, 0, 0, width, height)
    return enhanced
}

/**
 * Calculate image sharpness score (0.0 to 1.0)
 */
fun Bitmap.calculateSharpness(): Double {
    // Convert to grayscale and calculate edge detection
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    
    var totalEdgeStrength = 0.0
    var edgePixels = 0
    
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val index = y * width + x
            val pixel = pixels[index]
            
            // Convert to grayscale
            val gray = (((pixel shr 16) and 0xFF) * 0.299 +
                       ((pixel shr 8) and 0xFF) * 0.587 +
                       (pixel and 0xFF) * 0.114).toInt()
            
            // Simple edge detection using Sobel operator
            val gx = -pixels[(y - 1) * width + (x - 1)] - 2 * pixels[y * width + (x - 1)] - pixels[(y + 1) * width + (x - 1)] +
                     pixels[(y - 1) * width + (x + 1)] + 2 * pixels[y * width + (x + 1)] + pixels[(y + 1) * width + (x + 1)]
            
            val gy = -pixels[(y - 1) * width + (x - 1)] - 2 * pixels[(y - 1) * width + x] - pixels[(y - 1) * width + (x + 1)] +
                     pixels[(y + 1) * width + (x - 1)] + 2 * pixels[(y + 1) * width + x] + pixels[(y + 1) * width + (x + 1)]
            
            val edgeStrength = kotlin.math.sqrt((gx * gx + gy * gy).toDouble())
            
            if (edgeStrength > 50) { // Threshold for meaningful edges
                totalEdgeStrength += edgeStrength
                edgePixels++
            }
        }
    }
    
    return if (edgePixels > 0) {
        minOf(1.0, totalEdgeStrength / (edgePixels * 255.0))
    } else {
        0.0
    }
}

/**
 * Extract EXIF data from image file
 */
fun File.getExifOrientation(): Int {
    return try {
        val exif = ExifInterface(absolutePath)
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }
}

/**
 * Correct bitmap orientation based on EXIF data
 */
fun Bitmap.correctOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        else -> return this
    }
    
    return try {
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    } catch (e: OutOfMemoryError) {
        this
    }
}

/**
 * Load and decode bitmap from file with proper orientation and size limits
 */
fun File.decodeBitmap(
    maxWidth: Int = 1920,
    maxHeight: Int = 1080,
    correctOrientation: Boolean = true
): Bitmap? {
    return try {
        // First decode bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(absolutePath, options)
        
        // Calculate sample size
        options.inSampleSize = calculateSampleSize(options, maxWidth, maxHeight)
        options.inJustDecodeBounds = false
        
        // Decode bitmap
        var bitmap = BitmapFactory.decodeFile(absolutePath, options)
        
        // Correct orientation if needed
        if (correctOrientation && bitmap != null) {
            val orientation = getExifOrientation()
            bitmap = bitmap.correctOrientation(orientation)
        }
        
        bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * Calculate sample size for bitmap decoding
 */
private fun calculateSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var sampleSize = 1
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        
        while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
            sampleSize *= 2
        }
    }
    
    return sampleSize
}