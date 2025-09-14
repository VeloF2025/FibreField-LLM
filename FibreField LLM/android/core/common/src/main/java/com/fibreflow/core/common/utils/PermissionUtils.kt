// 🟢 WORKING: Permission management utilities for Android runtime permissions
package com.fibreflow.core.common.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Comprehensive permission management utilities for the FibreField application
 */
object PermissionUtils {
    
    // Permission request codes
    const val REQUEST_CAMERA_PERMISSION = 1001
    const val REQUEST_LOCATION_PERMISSION = 1002
    const val REQUEST_STORAGE_PERMISSION = 1003
    const val REQUEST_PHONE_PERMISSION = 1004
    const val REQUEST_MULTIPLE_PERMISSIONS = 1005
    
    // Permission groups for the FibreField app
    val CAMERA_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA
    )
    
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    val PHONE_PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE
    )
    
    val NOTIFICATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
    
    // All essential permissions for the app
    val ALL_ESSENTIAL_PERMISSIONS = CAMERA_PERMISSIONS + 
                                   LOCATION_PERMISSIONS + 
                                   STORAGE_PERMISSIONS +
                                   NOTIFICATION_PERMISSIONS
    
    /**
     * Check if a single permission is granted
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if all permissions in an array are granted
     */
    fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            isPermissionGranted(context, permission)
        }
    }
    
    /**
     * Check camera permission
     */
    fun isCameraPermissionGranted(context: Context): Boolean {
        return arePermissionsGranted(context, CAMERA_PERMISSIONS)
    }
    
    /**
     * Check location permissions
     */
    fun isLocationPermissionGranted(context: Context): Boolean {
        return arePermissionsGranted(context, LOCATION_PERMISSIONS)
    }
    
    /**
     * Check fine location permission specifically
     */
    fun isFineLocationPermissionGranted(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    
    /**
     * Check storage permissions
     */
    fun isStoragePermissionGranted(context: Context): Boolean {
        return arePermissionsGranted(context, STORAGE_PERMISSIONS)
    }
    
    /**
     * Check phone permissions
     */
    fun isPhonePermissionGranted(context: Context): Boolean {
        return arePermissionsGranted(context, PHONE_PERMISSIONS)
    }
    
    /**
     * Check notification permissions (Android 13+)
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arePermissionsGranted(context, NOTIFICATION_PERMISSIONS)
        } else {
            true // Not required on older versions
        }
    }
    
    /**
     * Check if all essential permissions are granted
     */
    fun areAllEssentialPermissionsGranted(context: Context): Boolean {
        return arePermissionsGranted(context, ALL_ESSENTIAL_PERMISSIONS)
    }
    
    /**
     * Get list of missing permissions
     */
    fun getMissingPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter { permission ->
            !isPermissionGranted(context, permission)
        }
    }
    
    /**
     * Get missing essential permissions
     */
    fun getMissingEssentialPermissions(context: Context): List<String> {
        return getMissingPermissions(context, ALL_ESSENTIAL_PERMISSIONS)
    }
    
    /**
     * Check if permission was permanently denied (user selected "Don't ask again")
     */
    fun isPermissionPermanentlyDenied(activity: Activity, permission: String): Boolean {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
               !isPermissionGranted(activity, permission)
    }
    
    /**
     * Check if any permission in array was permanently denied
     */
    fun areAnyPermissionsPermanentlyDenied(activity: Activity, permissions: Array<String>): Boolean {
        return permissions.any { permission ->
            isPermissionPermanentlyDenied(activity, permission)
        }
    }
    
    /**
     * Check if we should show rationale for permission
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    /**
     * Check if we should show rationale for any permission in array
     */
    fun shouldShowRationaleForAny(activity: Activity, permissions: Array<String>): Boolean {
        return permissions.any { permission ->
            shouldShowRationale(activity, permission)
        }
    }
    
    /**
     * Request a single permission
     */
    fun requestPermission(activity: Activity, permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
    }
    
    /**
     * Request multiple permissions
     */
    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }
    
    /**
     * Request camera permission
     */
    fun requestCameraPermission(activity: Activity) {
        requestPermissions(activity, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSION)
    }
    
    /**
     * Request location permissions
     */
    fun requestLocationPermissions(activity: Activity) {
        requestPermissions(activity, LOCATION_PERMISSIONS, REQUEST_LOCATION_PERMISSION)
    }
    
    /**
     * Request storage permissions
     */
    fun requestStoragePermissions(activity: Activity) {
        requestPermissions(activity, STORAGE_PERMISSIONS, REQUEST_STORAGE_PERMISSION)
    }
    
    /**
     * Request all essential permissions
     */
    fun requestEssentialPermissions(activity: Activity) {
        val missingPermissions = getMissingEssentialPermissions(activity)
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(activity, missingPermissions.toTypedArray(), REQUEST_MULTIPLE_PERMISSIONS)
        }
    }
    
    /**
     * Handle permission request result
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): PermissionResult {
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        
        for (i in permissions.indices) {
            if (i < grantResults.size) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    granted.add(permissions[i])
                } else {
                    denied.add(permissions[i])
                }
            }
        }
        
        return PermissionResult(
            requestCode = requestCode,
            grantedPermissions = granted,
            deniedPermissions = denied,
            allGranted = denied.isEmpty()
        )
    }
    
    /**
     * Open app settings page
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings if app details not available
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
    
    /**
     * Get user-friendly permission name
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Precise Location"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate Location"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage Access"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage Modification"
            Manifest.permission.READ_MEDIA_IMAGES -> "Photo Access"
            Manifest.permission.READ_MEDIA_VIDEO -> "Video Access"
            Manifest.permission.READ_PHONE_STATE -> "Phone Information"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission.substringAfterLast(".")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.titlecase() } }
        }
    }
    
    /**
     * Get permission explanation for rationale
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "Camera access is required to capture installation photos and scan barcodes."
            Manifest.permission.ACCESS_FINE_LOCATION -> "Precise location is needed to verify you're at the correct installation site."
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Location access helps verify your proximity to installation sites."
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage access is needed to save and manage installation photos."
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage modification is required to save photos and documents."
            Manifest.permission.READ_MEDIA_IMAGES -> "Photo access is needed to manage installation images."
            Manifest.permission.READ_MEDIA_VIDEO -> "Video access is needed to manage installation recordings."
            Manifest.permission.READ_PHONE_STATE -> "Phone information helps identify your device for security purposes."
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications keep you informed about installation updates and sync status."
            else -> "This permission is required for the app to function properly."
        }
    }
    
    /**
     * Check if permission is critical for app functionality
     */
    fun isCriticalPermission(permission: String): Boolean {
        return permission in listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    /**
     * Get permissions needed for specific features
     */
    fun getPermissionsForFeature(feature: AppFeature): Array<String> {
        return when (feature) {
            AppFeature.PHOTO_CAPTURE -> CAMERA_PERMISSIONS + STORAGE_PERMISSIONS
            AppFeature.LOCATION_TRACKING -> LOCATION_PERMISSIONS
            AppFeature.FILE_MANAGEMENT -> STORAGE_PERMISSIONS
            AppFeature.DEVICE_INFO -> PHONE_PERMISSIONS
            AppFeature.NOTIFICATIONS -> NOTIFICATION_PERMISSIONS
            AppFeature.FULL_FUNCTIONALITY -> ALL_ESSENTIAL_PERMISSIONS
        }
    }
    
    /**
     * Check if feature permissions are granted
     */
    fun areFeaturePermissionsGranted(context: Context, feature: AppFeature): Boolean {
        return arePermissionsGranted(context, getPermissionsForFeature(feature))
    }
    
    /**
     * Create permission summary for display
     */
    fun createPermissionSummary(context: Context): PermissionSummary {
        val cameraGranted = isCameraPermissionGranted(context)
        val locationGranted = isLocationPermissionGranted(context)
        val storageGranted = isStoragePermissionGranted(context)
        val notificationGranted = isNotificationPermissionGranted(context)
        
        val grantedCount = listOf(cameraGranted, locationGranted, storageGranted, notificationGranted).count { it }
        val totalCount = 4
        
        return PermissionSummary(
            cameraGranted = cameraGranted,
            locationGranted = locationGranted,
            storageGranted = storageGranted,
            notificationGranted = notificationGranted,
            allGranted = grantedCount == totalCount,
            grantedCount = grantedCount,
            totalCount = totalCount,
            completionPercentage = (grantedCount.toFloat() / totalCount * 100).toInt()
        )
    }
}

/**
 * Data classes for permission handling
 */
data class PermissionResult(
    val requestCode: Int,
    val grantedPermissions: List<String>,
    val deniedPermissions: List<String>,
    val allGranted: Boolean
) {
    val hasGrantedPermissions: Boolean get() = grantedPermissions.isNotEmpty()
    val hasDeniedPermissions: Boolean get() = deniedPermissions.isNotEmpty()
}

data class PermissionSummary(
    val cameraGranted: Boolean,
    val locationGranted: Boolean,
    val storageGranted: Boolean,
    val notificationGranted: Boolean,
    val allGranted: Boolean,
    val grantedCount: Int,
    val totalCount: Int,
    val completionPercentage: Int
) {
    val hasMinimumPermissions: Boolean get() = cameraGranted && locationGranted
    val missingCriticalPermissions: List<String> get() {
        val missing = mutableListOf<String>()
        if (!cameraGranted) missing.add("Camera")
        if (!locationGranted) missing.add("Location")
        return missing
    }
}

enum class AppFeature {
    PHOTO_CAPTURE,
    LOCATION_TRACKING,
    FILE_MANAGEMENT,
    DEVICE_INFO,
    NOTIFICATIONS,
    FULL_FUNCTIONALITY
}

/**
 * Extension functions for easy permission checking
 */

/**
 * Check if context has camera permission
 */
fun Context.hasCameraPermission(): Boolean {
    return PermissionUtils.isCameraPermissionGranted(this)
}

/**
 * Check if context has location permission
 */
fun Context.hasLocationPermission(): Boolean {
    return PermissionUtils.isLocationPermissionGranted(this)
}

/**
 * Check if context has storage permission
 */
fun Context.hasStoragePermission(): Boolean {
    return PermissionUtils.isStoragePermissionGranted(this)
}

/**
 * Check if context has all essential permissions
 */
fun Context.hasAllEssentialPermissions(): Boolean {
    return PermissionUtils.areAllEssentialPermissionsGranted(this)
}