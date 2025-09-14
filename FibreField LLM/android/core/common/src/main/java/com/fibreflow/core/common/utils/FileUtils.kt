// 🟢 WORKING: File management utilities for storage and operations
package com.fibreflow.core.common.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Comprehensive file management utilities for the FibreField application
 */
object FileUtils {
    
    /**
     * Get the app's private files directory
     */
    fun getAppFilesDir(context: Context): File {
        return context.filesDir
    }
    
    /**
     * Get the app's cache directory
     */
    fun getCacheDir(context: Context): File {
        return context.cacheDir
    }
    
    /**
     * Get the external files directory (SD card)
     */
    fun getExternalFilesDir(context: Context, type: String? = null): File? {
        return context.getExternalFilesDir(type)
    }
    
    /**
     * Get photos directory
     */
    fun getPhotosDir(context: Context): File {
        val photosDir = File(getAppFilesDir(context), "photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        return photosDir
    }
    
    /**
     * Get thumbnails directory
     */
    fun getThumbnailsDir(context: Context): File {
        val thumbnailsDir = File(getAppFilesDir(context), "thumbnails")
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }
        return thumbnailsDir
    }
    
    /**
     * Get models directory for AI models
     */
    fun getModelsDir(context: Context): File {
        val modelsDir = File(getAppFilesDir(context), "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return modelsDir
    }
    
    /**
     * Get temporary directory for processing
     */
    fun getTempDir(context: Context): File {
        val tempDir = File(getCacheDir(context), "temp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return tempDir
    }
    
    /**
     * Create a unique filename with timestamp
     */
    fun generateUniqueFilename(
        prefix: String = "file",
        suffix: String = "",
        extension: String = ""
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val randomId = UUID.randomUUID().toString().take(8)
        
        return buildString {
            append(prefix)
            if (suffix.isNotEmpty()) append("_$suffix")
            append("_${timestamp}_$randomId")
            if (extension.isNotEmpty()) append(".$extension")
        }
    }
    
    /**
     * Check if external storage is available for write
     */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
    
    /**
     * Check if external storage is available for read
     */
    fun isExternalStorageReadable(): Boolean {
        val state = Environment.getExternalStorageState()
        return state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
    }
    
    /**
     * Get available storage space in bytes
     */
    fun getAvailableSpace(path: File): Long {
        val stat = StatFs(path.absolutePath)
        return stat.blockSizeLong * stat.availableBlocksLong
    }
    
    /**
     * Get available storage space in MB
     */
    fun getAvailableSpaceMB(path: File): Double {
        return getAvailableSpace(path) / (1024.0 * 1024.0)
    }
    
    /**
     * Check if there's enough space for file
     */
    fun hasEnoughSpace(path: File, requiredBytes: Long, bufferPercentage: Double = 0.1): Boolean {
        val available = getAvailableSpace(path)
        val requiredWithBuffer = (requiredBytes * (1 + bufferPercentage)).toLong()
        return available >= requiredWithBuffer
    }
    
    /**
     * Get file size in bytes
     */
    fun getFileSize(file: File): Long {
        return if (file.exists() && file.isFile) file.length() else 0L
    }
    
    /**
     * Get directory size recursively
     */
    fun getDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists() && directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }
    
    /**
     * Format file size as human readable string
     */
    fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }
    
    /**
     * Copy file from source to destination
     */
    suspend fun copyFile(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!source.exists()) return@withContext false
            
            // Create parent directories if they don't exist
            destination.parentFile?.mkdirs()
            
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: IOException) {
            false
        }
    }
    
    /**
     * Move file from source to destination
     */
    suspend fun moveFile(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!source.exists()) return@withContext false
            
            // Try atomic move first
            if (source.renameTo(destination)) {
                return@withContext true
            }
            
            // Fallback to copy and delete
            if (copyFile(source, destination)) {
                source.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete file or directory recursively
     */
    fun deleteRecursively(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    deleteRecursively(child)
                }
            }
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Create directory structure if it doesn't exist
     */
    fun ensureDirectoryExists(directory: File): Boolean {
        return if (directory.exists()) {
            directory.isDirectory
        } else {
            directory.mkdirs()
        }
    }
    
    /**
     * Calculate MD5 hash of file
     */
    suspend fun calculateMD5(file: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate SHA-256 hash of file
     */
    suspend fun calculateSHA256(file: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if two files have the same content (using hash comparison)
     */
    suspend fun filesHaveSameContent(file1: File, file2: File): Boolean {
        if (!file1.exists() || !file2.exists()) return false
        if (file1.length() != file2.length()) return false
        
        val hash1 = calculateSHA256(file1)
        val hash2 = calculateSHA256(file2)
        
        return hash1 != null && hash2 != null && hash1 == hash2
    }
    
    /**
     * Clean up old files in directory based on age
     */
    suspend fun cleanupOldFiles(
        directory: File,
        maxAgeMillis: Long,
        fileExtension: String? = null
    ): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext deletedCount
        }
        
        val cutoffTime = System.currentTimeMillis() - maxAgeMillis
        
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoffTime) {
                if (fileExtension == null || file.extension.equals(fileExtension, ignoreCase = true)) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
        }
        
        return@withContext deletedCount
    }
    
    /**
     * Compress files into a ZIP archive
     */
    suspend fun createZipArchive(
        files: List<File>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            outputFile.parentFile?.mkdirs()
            
            ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
                files.forEach { file ->
                    if (file.exists() && file.isFile) {
                        val entry = ZipEntry(file.name)
                        zipOut.putNextEntry(entry)
                        
                        file.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        
                        zipOut.closeEntry()
                    }
                }
            }
            true
        } catch (e: IOException) {
            false
        }
    }
    
    /**
     * Read file content as string
     */
    suspend fun readFileAsString(file: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
    }
    
    /**
     * Write string content to file
     */
    suspend fun writeStringToFile(
        content: String,
        file: File,
        append: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            file.parentFile?.mkdirs()
            
            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }
            true
        } catch (e: IOException) {
            false
        }
    }
    
    /**
     * Get file extension
     */
    fun getFileExtension(filename: String): String {
        val lastDotIndex = filename.lastIndexOf('.')
        return if (lastDotIndex != -1 && lastDotIndex < filename.length - 1) {
            filename.substring(lastDotIndex + 1).lowercase()
        } else {
            ""
        }
    }
    
    /**
     * Get filename without extension
     */
    fun getFilenameWithoutExtension(filename: String): String {
        val lastDotIndex = filename.lastIndexOf('.')
        return if (lastDotIndex != -1) {
            filename.substring(0, lastDotIndex)
        } else {
            filename
        }
    }
    
    /**
     * Check if file has valid image extension
     */
    fun isImageFile(filename: String): Boolean {
        val extension = getFileExtension(filename)
        return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }
    
    /**
     * Check if file has valid video extension
     */
    fun isVideoFile(filename: String): Boolean {
        val extension = getFileExtension(filename)
        return extension in listOf("mp4", "avi", "mov", "mkv", "wmv", "flv", "webm")
    }
    
    /**
     * Get MIME type from file extension
     */
    fun getMimeTypeFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * Create a backup of a file
     */
    suspend fun createBackup(
        sourceFile: File,
        backupDir: File? = null
    ): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!sourceFile.exists()) return@withContext null
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupName = "${getFilenameWithoutExtension(sourceFile.name)}_backup_$timestamp.${getFileExtension(sourceFile.name)}"
            
            val backupFile = if (backupDir != null) {
                ensureDirectoryExists(backupDir)
                File(backupDir, backupName)
            } else {
                File(sourceFile.parent, backupName)
            }
            
            if (copyFile(sourceFile, backupFile)) {
                backupFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * List files in directory with optional filtering
     */
    fun listFiles(
        directory: File,
        recursive: Boolean = false,
        extensionFilter: List<String>? = null,
        includeHidden: Boolean = false
    ): List<File> {
        val result = mutableListOf<File>()
        
        if (!directory.exists() || !directory.isDirectory) {
            return result
        }
        
        directory.listFiles()?.forEach { file ->
            if (!includeHidden && file.isHidden) return@forEach
            
            when {
                file.isFile -> {
                    val shouldInclude = extensionFilter?.let { filters ->
                        filters.any { filter ->
                            file.extension.equals(filter, ignoreCase = true)
                        }
                    } ?: true
                    
                    if (shouldInclude) {
                        result.add(file)
                    }
                }
                file.isDirectory && recursive -> {
                    result.addAll(listFiles(file, recursive, extensionFilter, includeHidden))
                }
            }
        }
        
        return result.sortedBy { it.name }
    }
}

/**
 * Extension functions for File class
 */

/**
 * Get human readable file size
 */
fun File.getFormattedSize(): String {
    return FileUtils.formatFileSize(this.length())
}

/**
 * Check if file is an image
 */
fun File.isImage(): Boolean {
    return FileUtils.isImageFile(this.name)
}

/**
 * Check if file is a video
 */
fun File.isVideo(): Boolean {
    return FileUtils.isVideoFile(this.name)
}

/**
 * Get MIME type of file
 */
fun File.getMimeType(): String {
    return FileUtils.getMimeTypeFromExtension(this.extension)
}

/**
 * Delete file and return success status
 */
fun File.deleteSafely(): Boolean {
    return try {
        this.delete()
    } catch (e: Exception) {
        false
    }
}

/**
 * Check if file is older than specified time
 */
fun File.isOlderThan(ageMillis: Long): Boolean {
    return System.currentTimeMillis() - this.lastModified() > ageMillis
}

/**
 * Calculate file hash
 */
suspend fun File.calculateHash(algorithm: String = "SHA-256"): String? {
    return when (algorithm.uppercase()) {
        "MD5" -> FileUtils.calculateMD5(this)
        "SHA-256" -> FileUtils.calculateSHA256(this)
        else -> null
    }
}