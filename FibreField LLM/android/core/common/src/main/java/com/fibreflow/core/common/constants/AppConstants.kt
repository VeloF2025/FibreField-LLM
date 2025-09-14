// 🟢 WORKING: Core application constants used throughout the app
package com.fibreflow.core.common.constants

/**
 * Application-wide constants for consistent configuration
 */
object AppConstants {
    
    // Database constants
    const val DATABASE_NAME = "fibrefield_encrypted.db"
    const val DATABASE_VERSION = 1
    
    // Sync configuration
    const val SYNC_INTERVAL_MINUTES = 15L
    const val MAX_SYNC_RETRIES = 3
    const val SYNC_TIMEOUT_SECONDS = 30L
    
    // Photo validation
    const val MAX_PHOTO_SIZE_MB = 10
    const val PHOTO_QUALITY = 85
    const val THUMBNAIL_SIZE = 150
    
    // Location settings
    const val LOCATION_ACCURACY_THRESHOLD_METERS = 10.0f
    const val DROP_PROXIMITY_THRESHOLD_METERS = 50.0f
    const val LOCATION_UPDATE_INTERVAL_MS = 5000L
    const val LOCATION_FASTEST_INTERVAL_MS = 2000L
    
    // Network configuration
    const val API_TIMEOUT_SECONDS = 30L
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    
    // File paths
    const val PHOTOS_DIRECTORY = "photos"
    const val THUMBNAILS_DIRECTORY = "thumbnails"
    const val MODELS_DIRECTORY = "models"
    const val CACHE_DIRECTORY = "cache"
    
    // AI/ML Configuration
    const val LLM_MODEL_NAME = "phi-3.5-mini-instruct-q4_k_m"
    const val VISION_MODEL_NAME = "ont_light_detector_v2.tflite"
    const val MAX_TOKENS = 512
    const val TEMPERATURE = 0.3f
    const val TOP_P = 0.9f
    
    // Work queue configuration
    const val PHOTO_VALIDATION_WORK_TAG = "photo_validation"
    const val SYNC_WORK_TAG = "data_sync"
    const val CLEANUP_WORK_TAG = "cleanup"
    
    // Security
    const val KEYSTORE_ALIAS = "FibreFieldMasterKey"
    const val BIOMETRIC_TIMEOUT_SECONDS = 30L
    
    // UI Constants
    const val ANIMATION_DURATION_MS = 300
    const val DEBOUNCE_DELAY_MS = 500L
    const val SPLASH_DELAY_MS = 2000L
    
    // Performance
    const val BITMAP_CACHE_SIZE_MB = 50
    const val MODEL_CACHE_SIZE = 3
    const val MEMORY_THRESHOLD_MB = 100
}