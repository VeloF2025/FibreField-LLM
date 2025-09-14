package com.fibreflow.infrastructure.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.fibreflow.core.common.result.Result
import com.fibreflow.infrastructure.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline Manager
 * Manages offline/online state transitions and coordinates offline operations
 */
@Singleton
class OfflineManager @Inject constructor(
    private val context: Context,
    private val syncManager: SyncManager,
    private val offlineToOnlineSync: OfflineToOnlineSync
) {

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Offline state
    private val _isOffline = MutableStateFlow(true) // Start as offline for safety
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _connectionQuality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

    private val _pendingOperations = MutableStateFlow(0)
    val pendingOperations: StateFlow<Int> = _pendingOperations.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMonitoringNetwork = false

    init {
        startNetworkMonitoring()
        updateConnectionState()
    }

    /**
     * Check if device is currently offline
     */
    fun isCurrentlyOffline(): Boolean {
        return _isOffline.value
    }

    /**
     * Get current connection quality
     */
    fun getConnectionQuality(): ConnectionQuality {
        return _connectionQuality.value
    }

    /**
     * Force offline mode (for testing or manual override)
     */
    fun forceOfflineMode(enabled: Boolean) {
        _isOffline.value = enabled
        Timber.d("Offline mode ${if (enabled) "enabled" else "disabled"} (forced)")
    }

    /**
     * Check if an operation can be performed offline
     */
    fun canPerformOffline(operation: OfflineOperation): Boolean {
        return when (operation) {
            OfflineOperation.DATA_VIEWING -> true // Always allow viewing cached data
            OfflineOperation.DATA_ENTRY -> true // Allow data entry, will sync later
            OfflineOperation.PHOTO_CAPTURE -> true // Allow photo capture, will upload later
            OfflineOperation.INSTALLATION_WORKFLOW -> true // Allow workflow progress
            OfflineOperation.REQUIRES_IMMEDIATE_SYNC -> _isOffline.value.not() // Only if online
        }
    }

    /**
     * Queue operation for later execution when online
     */
    suspend fun queueOperationForOnline(operation: OfflineOperationData): Result<Unit> {
        return try {
            if (!_isOffline.value) {
                // If online, execute immediately
                return executeOperationOnline(operation)
            }

            // Queue for later
            val result = offlineToOnlineSync.queueOperation(operation)
            if (result is Result.Success) {
                _pendingOperations.value = _pendingOperations.value + 1
                Timber.d("Operation queued for online execution: ${operation.type}")
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Error queuing operation")
            Result.Error(e)
        }
    }

    /**
     * Process pending operations when coming online
     */
    suspend fun processPendingOperations(): Result<SyncResult> {
        return try {
            Timber.i("Processing pending offline operations")

            val result = offlineToOnlineSync.processPendingOperations()
            if (result is Result.Success) {
                _pendingOperations.value = result.data.operationsProcessed
                Timber.i("Processed ${result.data.operationsProcessed} pending operations")
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Error processing pending operations")
            Result.Error(e)
        }
    }

    /**
     * Get offline storage statistics
     */
    fun getOfflineStorageStats(): OfflineStorageStats {
        return OfflineStorageStats(
            cachedDataSize = 0L, // Would calculate actual cached data size
            pendingUploads = _pendingOperations.value,
            lastSyncTime = System.currentTimeMillis() - 3600000, // Mock: 1 hour ago
            storageAvailable = true // Would check actual storage
        )
    }

    /**
     * Clear offline cache (use with caution)
     */
    suspend fun clearOfflineCache(): Result<Unit> {
        return try {
            Timber.w("Clearing offline cache")

            // Clear pending operations
            offlineToOnlineSync.clearPendingOperations()
            _pendingOperations.value = 0

            // Would also clear cached data here
            Timber.i("Offline cache cleared")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error clearing offline cache")
            Result.Error(e)
        }
    }

    /**
     * Shutdown offline manager
     */
    fun shutdown() {
        stopNetworkMonitoring()
        managerScope.launch {
            clearOfflineCache()
        }
        Timber.i("OfflineManager shutdown complete")
    }

    // Private methods

    private fun startNetworkMonitoring() {
        if (isMonitoringNetwork) return

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                handleNetworkAvailable()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                handleNetworkLost()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                updateConnectionQuality(capabilities)
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        isMonitoringNetwork = true
        Timber.d("Network monitoring started")
    }

    private fun stopNetworkMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering network callback")
            }
        }
        networkCallback = null
        isMonitoringNetwork = false
        Timber.d("Network monitoring stopped")
    }

    private fun handleNetworkAvailable() {
        val wasOffline = _isOffline.value
        _isOffline.value = false

        if (wasOffline) {
            Timber.i("Network connection restored, processing pending operations")
            managerScope.launch {
                processPendingOperations()
            }
        }
    }

    private fun handleNetworkLost() {
        _isOffline.value = true
        _connectionQuality.value = ConnectionQuality.NONE
        Timber.i("Network connection lost, switching to offline mode")
    }

    private fun updateConnectionQuality(capabilities: NetworkCapabilities) {
        val quality = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionQuality.EXCELLENT
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Could check signal strength here
                ConnectionQuality.GOOD
            }
            else -> ConnectionQuality.POOR
        }

        _connectionQuality.value = quality
    }

    private fun updateConnectionState() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        _isOffline.value = activeNetwork == null || capabilities == null ||
                          !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        if (capabilities != null) {
            updateConnectionQuality(capabilities)
        } else {
            _connectionQuality.value = ConnectionQuality.NONE
        }
    }

    private suspend fun executeOperationOnline(operation: OfflineOperationData): Result<Unit> {
        return try {
            // Execute operation immediately since we're online
            Timber.d("Executing operation online: ${operation.type}")

            // This would delegate to the appropriate service based on operation type
            when (operation.type) {
                "SYNC_DATA" -> syncManager.performImmediateSync()
                "UPLOAD_PHOTO" -> Result.Success(Unit) // Would call photo upload service
                else -> Result.Success(Unit)
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error executing operation online")
            Result.Error(e)
        }
    }
}

/**
 * Connection quality levels
 */
enum class ConnectionQuality {
    EXCELLENT, // WiFi
    GOOD,      // Cellular with good signal
    POOR,      // Cellular with poor signal
    NONE,      // No connection
    UNKNOWN
}

/**
 * Types of offline operations
 */
enum class OfflineOperation {
    DATA_VIEWING,           // Viewing cached data
    DATA_ENTRY,            // Entering new data
    PHOTO_CAPTURE,         // Capturing photos
    INSTALLATION_WORKFLOW, // Installation workflow progress
    REQUIRES_IMMEDIATE_SYNC // Operations that require immediate sync
}

/**
 * Data for offline operations
 */
data class OfflineOperationData(
    val id: String = generateId(),
    val type: String,
    val data: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: Int = 1
) {
    companion object {
        private var counter = 0
        private fun generateId(): String = "op_${System.currentTimeMillis()}_${counter++}"
    }
}

/**
 * Offline storage statistics
 */
data class OfflineStorageStats(
    val cachedDataSize: Long, // bytes
    val pendingUploads: Int,
    val lastSyncTime: Long,
    val storageAvailable: Boolean
)

/**
 * Sync result for offline operations
 */
data class SyncResult(
    val operationsProcessed: Int,
    val operationsFailed: Int,
    val syncTimeMs: Long
)