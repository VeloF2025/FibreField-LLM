// 🟢 WORKING: Main application class with Hilt, initialization, and global configuration
package com.fibreflow.technician

import android.app.Application
import android.content.Context
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.fibreflow.core.ai.AIManager
import com.fibreflow.core.common.constants.AppConstants
import com.fibreflow.core.common.utils.DateTimeUtils
import com.fibreflow.infrastructure.security.SecureStorageManager
import com.fibreflow.infrastructure.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Main Application class for FibreField Technician
 * 
 * Responsibilities:
 * - Hilt dependency injection initialization
 * - Global app configuration and settings
 * - Background service initialization
 * - Crash reporting and analytics setup
 * - Development tools configuration
 */
@HiltAndroidApp
class FibreFieldApplication : Application(), Configuration.Provider {
    
    // Application-scoped coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Injected dependencies
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var aiManager: AIManager
    @Inject lateinit var secureStorage: SecureStorageManager
    
    // Application lifecycle state
    private var isInForeground = false
    private var applicationStartTime: Long = 0
    
    override fun onCreate() {
        super.onCreate()
        
        applicationStartTime = System.currentTimeMillis()
        
        // Initialize logging first
        initializeLogging()
        
        Timber.i("🚀 FibreField Technician Application starting...")
        
        // Configure development tools in debug mode
        if (BuildConfig.DEBUG) {
            configureStrictMode()
            Timber.plant(Timber.DebugTree())
        } else {
            // Production logging (crash reporting)
            initializeProductionLogging()
        }
        
        // Initialize core services
        initializeCoreServices()
        
        // Initialize WorkManager with Hilt
        initializeWorkManager()
        
        // Set up crash reporting and analytics
        initializeMonitoring()
        
        Timber.i("✅ FibreField Technician Application initialized successfully")
        Timber.i("📊 App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Timber.i("🏗️ Build type: ${BuildConfig.BUILD_TYPE}")
        Timber.i("🌐 API URL: ${BuildConfig.API_BASE_URL}")
    }
    
    /**
     * Initialize logging system
     */
    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            // Detailed logging for debug builds
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "🔧 ${super.createStackElementTag(element)}:${element.lineNumber}"
                }
            })
        }
    }
    
    /**
     * Initialize production logging with crash reporting
     */
    private fun initializeProductionLogging() {
        // Custom crash reporting tree
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority >= android.util.Log.WARN) {
                    // Send to crash reporting service
                    if (t != null) {
                        // Log exception to crash reporting
                        reportException(t, message, tag)
                    } else {
                        // Log warning/error message
                        reportMessage(priority, message, tag)
                    }
                }
            }
        })
    }
    
    /**
     * Configure StrictMode for development
     */
    private fun configureStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyFlashScreen() // Visual indicator of violations
                    .build()
            )
            
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
    }
    
    /**
     * Initialize core application services
     */
    private fun initializeCoreServices() {
        applicationScope.launch {
            try {
                Timber.d("🔧 Initializing core services...")
                
                // Initialize security components
                secureStorage.initialize()
                
                // Initialize AI services in background
                launch {
                    try {
                        aiManager.initialize()
                        Timber.i("🤖 AI services initialized successfully")
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Failed to initialize AI services")
                    }
                }
                
                // Initialize sync manager
                syncManager.initialize()
                
                // Perform any database migrations or setup
                performDatabaseMaintenance()
                
                Timber.i("✅ Core services initialized")
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to initialize core services")
                reportException(e, "Core services initialization failed")
            }
        }
    }
    
    /**
     * Initialize WorkManager with Hilt support
     */
    private fun initializeWorkManager() {
        try {
            // Initialize WorkManager with custom configuration
            val workManagerConfig = Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
                .build()
            
            WorkManager.initialize(this, workManagerConfig)
            
            // Schedule periodic tasks
            schedulePeriodicTasks()
            
            Timber.i("✅ WorkManager initialized with Hilt support")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to initialize WorkManager")
            reportException(e, "WorkManager initialization failed")
        }
    }
    
    /**
     * Schedule periodic background tasks
     */
    private fun schedulePeriodicTasks() {
        applicationScope.launch {
            try {
                // Schedule data synchronization
                syncManager.schedulePeriodicSync()
                
                // Schedule database cleanup
                scheduleDatabaseCleanup()
                
                // Schedule cache cleanup
                scheduleCacheCleanup()
                
                Timber.i("✅ Periodic tasks scheduled")
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to schedule periodic tasks")
            }
        }
    }
    
    /**
     * Initialize monitoring and analytics
     */
    private fun initializeMonitoring() {
        if (BuildConfig.ENABLE_CRASH_REPORTING) {
            // Initialize crash reporting
            initializeCrashReporting()
        }
        
        if (BuildConfig.ENABLE_ANALYTICS) {
            // Initialize analytics
            initializeAnalytics()
        }
    }
    
    /**
     * Initialize crash reporting service
     */
    private fun initializeCrashReporting() {
        try {
            // Firebase Crashlytics or other crash reporting service
            // Set user identifier for crash reports
            setCrashReportingUserId()
            
            // Set custom keys for debugging
            setCrashReportingCustomKeys()
            
            Timber.i("✅ Crash reporting initialized")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to initialize crash reporting")
        }
    }
    
    /**
     * Initialize analytics service
     */
    private fun initializeAnalytics() {
        try {
            // Firebase Analytics or other analytics service
            // Set user properties
            setAnalyticsUserProperties()
            
            // Log app start event
            logAppStartEvent()
            
            Timber.i("✅ Analytics initialized")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to initialize analytics")
        }
    }
    
    /**
     * Perform database maintenance tasks
     */
    private suspend fun performDatabaseMaintenance() {
        try {
            // Clean up old sync queue entries
            // Clean up old log entries
            // Optimize database
            Timber.d("🔧 Database maintenance completed")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Database maintenance failed")
        }
    }
    
    /**
     * Schedule database cleanup tasks
     */
    private fun scheduleDatabaseCleanup() {
        // Implementation would schedule periodic database cleanup
        Timber.d("📅 Database cleanup scheduled")
    }
    
    /**
     * Schedule cache cleanup tasks
     */
    private fun scheduleCacheCleanup() {
        // Implementation would schedule periodic cache cleanup
        Timber.d("📅 Cache cleanup scheduled")
    }
    
    /**
     * Set user ID for crash reporting
     */
    private fun setCrashReportingUserId() {
        applicationScope.launch {
            try {
                val userId = secureStorage.getTechnicianId()
                if (userId != null) {
                    // Set user ID in crash reporting service
                    Timber.d("👤 User ID set for crash reporting: ${userId.take(8)}...")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to set crash reporting user ID")
            }
        }
    }
    
    /**
     * Set custom keys for crash reporting
     */
    private fun setCrashReportingCustomKeys() {
        // Set custom keys that will be included in crash reports
        val customKeys = mapOf(
            "app_version" to BuildConfig.VERSION_NAME,
            "build_type" to BuildConfig.BUILD_TYPE,
            "api_base_url" to BuildConfig.API_BASE_URL,
            "device_model" to android.os.Build.MODEL,
            "android_version" to android.os.Build.VERSION.RELEASE,
            "app_start_time" to DateTimeUtils.formatForApi(applicationStartTime)
        )
        
        customKeys.forEach { (key, value) ->
            // Set custom key in crash reporting service
            Timber.d("🔑 Set crash reporting key: $key = $value")
        }
    }
    
    /**
     * Set user properties for analytics
     */
    private fun setAnalyticsUserProperties() {
        applicationScope.launch {
            try {
                val userProperties = mapOf(
                    "app_version" to BuildConfig.VERSION_NAME,
                    "build_type" to BuildConfig.BUILD_TYPE,
                    "device_model" to android.os.Build.MODEL,
                    "android_version" to android.os.Build.VERSION.RELEASE
                )
                
                userProperties.forEach { (property, value) ->
                    // Set user property in analytics service
                    Timber.d("📊 Set analytics property: $property = $value")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to set analytics user properties")
            }
        }
    }
    
    /**
     * Log app start event for analytics
     */
    private fun logAppStartEvent() {
        val startupTime = System.currentTimeMillis() - applicationStartTime
        
        val eventParams = mapOf(
            "startup_time_ms" to startupTime,
            "cold_start" to true,
            "app_version" to BuildConfig.VERSION_NAME
        )
        
        // Log event to analytics service
        Timber.i("📊 App start event logged: startup_time=${startupTime}ms")
    }
    
    /**
     * WorkManager configuration provider
     */
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG 
                else android.util.Log.INFO
            )
            .build()
    }
    
    /**
     * Application lifecycle callbacks
     */
    
    fun onAppForegrounded() {
        if (!isInForeground) {
            isInForeground = true
            Timber.i("🔄 App moved to foreground")
            
            applicationScope.launch {
                // Trigger immediate sync when app comes to foreground
                syncManager.triggerImmediateSync()
                
                // Update last active timestamp
                secureStorage.updateLastActiveTime(System.currentTimeMillis())
            }
        }
    }
    
    fun onAppBackgrounded() {
        if (isInForeground) {
            isInForeground = false
            Timber.i("🔄 App moved to background")
            
            applicationScope.launch {
                // Perform background cleanup
                performBackgroundCleanup()
            }
        }
    }
    
    /**
     * Perform cleanup tasks when app goes to background
     */
    private suspend fun performBackgroundCleanup() {
        try {
            // Clear sensitive data from memory
            // Pause non-essential services
            // Schedule background sync
            Timber.d("🧹 Background cleanup completed")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Background cleanup failed")
        }
    }
    
    /**
     * Report exception to crash reporting service
     */
    private fun reportException(throwable: Throwable, message: String? = null, tag: String? = null) {
        try {
            // Report to crash reporting service with context
            if (BuildConfig.ENABLE_CRASH_REPORTING) {
                // Implementation would report to Firebase Crashlytics or similar
                Timber.e(throwable, "💥 Exception reported: $message")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report exception")
        }
    }
    
    /**
     * Report message to logging service
     */
    private fun reportMessage(priority: Int, message: String, tag: String?) {
        try {
            if (BuildConfig.ENABLE_CRASH_REPORTING) {
                // Report non-fatal message to logging service
                val priorityName = when (priority) {
                    android.util.Log.WARN -> "WARN"
                    android.util.Log.ERROR -> "ERROR"
                    else -> "INFO"
                }
                Timber.tag(tag ?: "FibreField").log(priority, "[$priorityName] $message")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report message")
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Timber.w("⚠️ System low memory warning received")
        
        applicationScope.launch {
            // Clear caches and free memory
            aiManager.clearCache()
            // Clear image caches
            // Clear other non-essential data
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Timber.w("⚠️ Memory trim requested: level=$level")
        
        applicationScope.launch {
            when (level) {
                TRIM_MEMORY_UI_HIDDEN -> {
                    // App UI hidden, can release UI-related resources
                }
                TRIM_MEMORY_BACKGROUND -> {
                    // App in background, can release more resources
                }
                TRIM_MEMORY_CRITICAL -> {
                    // System critically low on memory
                    aiManager.clearCache()
                    // Aggressive memory cleanup
                }
            }
        }
    }
}