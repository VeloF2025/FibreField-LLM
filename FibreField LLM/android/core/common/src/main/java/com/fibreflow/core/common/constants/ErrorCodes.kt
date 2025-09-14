// 🟢 WORKING: Standardized error codes for consistent error handling
package com.fibreflow.core.common.constants

/**
 * Standardized error codes for the FibreField application.
 * These codes provide consistent error handling across all modules.
 */
object ErrorCodes {
    
    // Authentication errors (1000-1999)
    const val AUTH_INVALID_CREDENTIALS = 1001
    const val AUTH_TOKEN_EXPIRED = 1002
    const val AUTH_BIOMETRIC_NOT_AVAILABLE = 1003
    const val AUTH_BIOMETRIC_FAILURE = 1004
    const val AUTH_NETWORK_UNREACHABLE = 1005
    const val AUTH_ACCOUNT_LOCKED = 1006
    
    // Database errors (2000-2999)
    const val DB_CONNECTION_FAILED = 2001
    const val DB_CONSTRAINT_VIOLATION = 2002
    const val DB_DATA_CORRUPTION = 2003
    const val DB_MIGRATION_FAILED = 2004
    const val DB_ENCRYPTION_FAILED = 2005
    const val DB_DISK_FULL = 2006
    
    // Network errors (3000-3999)
    const val NETWORK_NO_CONNECTION = 3001
    const val NETWORK_TIMEOUT = 3002
    const val NETWORK_SERVER_ERROR = 3003
    const val NETWORK_CLIENT_ERROR = 3004
    const val NETWORK_SSL_ERROR = 3005
    const val NETWORK_PARSE_ERROR = 3006
    
    // Photo validation errors (4000-4999)
    const val PHOTO_CAPTURE_FAILED = 4001
    const val PHOTO_VALIDATION_FAILED = 4002
    const val PHOTO_SIZE_EXCEEDED = 4003
    const val PHOTO_QUALITY_LOW = 4004
    const val PHOTO_PROCESSING_FAILED = 4005
    const val PHOTO_STORAGE_FAILED = 4006
    const val PHOTO_UPLOAD_FAILED = 4007
    
    // AI/ML errors (5000-5999)
    const val AI_MODEL_LOAD_FAILED = 5001
    const val AI_INFERENCE_FAILED = 5002
    const val AI_MEMORY_INSUFFICIENT = 5003
    const val AI_MODEL_NOT_FOUND = 5004
    const val AI_PROCESSING_TIMEOUT = 5005
    const val AI_UNEXPECTED_RESULT = 5006
    
    // Location errors (6000-6999)
    const val LOCATION_PERMISSION_DENIED = 6001
    const val LOCATION_PROVIDER_DISABLED = 6002
    const val LOCATION_ACCURACY_LOW = 6003
    const val LOCATION_TIMEOUT = 6004
    const val LOCATION_OUTSIDE_BOUNDARY = 6005
    
    // Sync errors (7000-7999)
    const val SYNC_QUEUE_FULL = 7001
    const val SYNC_CONFLICT_DETECTED = 7002
    const val SYNC_DATA_CORRUPTION = 7003
    const val SYNC_SERVER_REJECTED = 7004
    const val SYNC_NETWORK_UNAVAILABLE = 7005
    const val SYNC_RETRY_LIMIT_EXCEEDED = 7006
    
    // Installation workflow errors (8000-8999)
    const val INSTALLATION_DROP_NOT_FOUND = 8001
    const val INSTALLATION_ALREADY_STARTED = 8002
    const val INSTALLATION_NOT_AUTHORIZED = 8003
    const val INSTALLATION_INCOMPLETE_DATA = 8004
    const val INSTALLATION_SUBMISSION_FAILED = 8005
    const val INSTALLATION_VALIDATION_FAILED = 8006
    
    // Hardware errors (9000-9999)
    const val CAMERA_UNAVAILABLE = 9001
    const val CAMERA_PERMISSION_DENIED = 9002
    const val STORAGE_INSUFFICIENT = 9003
    const val BATTERY_LOW = 9004
    const val DEVICE_INCOMPATIBLE = 9005
    
    // System errors (10000-10999)
    const val SYSTEM_OUT_OF_MEMORY = 10001
    const val SYSTEM_PERMISSION_DENIED = 10002
    const val SYSTEM_RESOURCE_UNAVAILABLE = 10003
    const val SYSTEM_CONFIGURATION_ERROR = 10004
    const val SYSTEM_UNEXPECTED_ERROR = 10005
}

/**
 * Error categories for grouping related error codes
 */
enum class ErrorCategory {
    AUTHENTICATION,
    DATABASE,
    NETWORK,
    PHOTO_VALIDATION,
    AI_ML,
    LOCATION,
    SYNC,
    INSTALLATION,
    HARDWARE,
    SYSTEM
}

/**
 * Maps error codes to their categories
 */
fun getErrorCategory(errorCode: Int): ErrorCategory {
    return when (errorCode) {
        in 1000..1999 -> ErrorCategory.AUTHENTICATION
        in 2000..2999 -> ErrorCategory.DATABASE
        in 3000..3999 -> ErrorCategory.NETWORK
        in 4000..4999 -> ErrorCategory.PHOTO_VALIDATION
        in 5000..5999 -> ErrorCategory.AI_ML
        in 6000..6999 -> ErrorCategory.LOCATION
        in 7000..7999 -> ErrorCategory.SYNC
        in 8000..8999 -> ErrorCategory.INSTALLATION
        in 9000..9999 -> ErrorCategory.HARDWARE
        in 10000..10999 -> ErrorCategory.SYSTEM
        else -> ErrorCategory.SYSTEM
    }
}

/**
 * Get user-friendly error message for error code
 */
fun getUserFriendlyMessage(errorCode: Int): String {
    return when (errorCode) {
        ErrorCodes.AUTH_INVALID_CREDENTIALS -> "Invalid username or password. Please try again."
        ErrorCodes.AUTH_TOKEN_EXPIRED -> "Your session has expired. Please log in again."
        ErrorCodes.AUTH_BIOMETRIC_NOT_AVAILABLE -> "Biometric authentication is not available on this device."
        ErrorCodes.AUTH_BIOMETRIC_FAILURE -> "Biometric authentication failed. Please try again."
        ErrorCodes.AUTH_NETWORK_UNREACHABLE -> "Unable to connect to authentication server. Check your internet connection."
        
        ErrorCodes.NETWORK_NO_CONNECTION -> "No internet connection. Please check your network settings."
        ErrorCodes.NETWORK_TIMEOUT -> "Request timed out. Please try again."
        ErrorCodes.NETWORK_SERVER_ERROR -> "Server error. Please try again later."
        
        ErrorCodes.PHOTO_CAPTURE_FAILED -> "Failed to capture photo. Please try again."
        ErrorCodes.PHOTO_VALIDATION_FAILED -> "Photo validation failed. Please review the requirements and retake."
        ErrorCodes.PHOTO_SIZE_EXCEEDED -> "Photo file size is too large. Please try again."
        ErrorCodes.PHOTO_QUALITY_LOW -> "Photo quality is too low. Please ensure good lighting and focus."
        
        ErrorCodes.LOCATION_PERMISSION_DENIED -> "Location permission is required. Please enable location access."
        ErrorCodes.LOCATION_PROVIDER_DISABLED -> "Location services are disabled. Please enable GPS."
        ErrorCodes.LOCATION_ACCURACY_LOW -> "Location accuracy is too low. Please move to an open area."
        ErrorCodes.LOCATION_OUTSIDE_BOUNDARY -> "You are outside the project boundary. Please move closer to the work area."
        
        ErrorCodes.INSTALLATION_DROP_NOT_FOUND -> "Drop number not found. Please verify the drop number."
        ErrorCodes.INSTALLATION_NOT_AUTHORIZED -> "You are not authorized to work on this installation."
        ErrorCodes.INSTALLATION_INCOMPLETE_DATA -> "Installation data is incomplete. Please complete all required fields."
        
        ErrorCodes.CAMERA_UNAVAILABLE -> "Camera is not available. Please check if another app is using the camera."
        ErrorCodes.CAMERA_PERMISSION_DENIED -> "Camera permission is required to capture photos."
        ErrorCodes.STORAGE_INSUFFICIENT -> "Insufficient storage space. Please free up some space."
        
        else -> "An unexpected error occurred. Please try again or contact support."
    }
}