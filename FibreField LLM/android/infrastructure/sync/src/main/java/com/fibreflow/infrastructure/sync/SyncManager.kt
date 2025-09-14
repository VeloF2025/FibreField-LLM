package com.fibreflow.infrastructure.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.fibreflow.core.common.result.Result
import com.fibreflow.core.database.dao.*
import com.fibreflow.infrastructure.sync.conflict.ConflictResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline Data Synchronization Manager
 * Handles background sync of local data with remote servers
 * Manages conflict resolution and data consistency
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installationDao: InstallationDao,
    private val photoDao: PhotoDao,
    private val dropDao: DropDao,
    private val syncQueueDao: SyncQueueDao,
    private val conflictResolver: ConflictResolver
) {

    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_INTERVAL_MS = 300000L // 5 minutes
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BATCH_SIZE = 50
    }

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Sync state
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    // Sync job
    private var syncJob: Job? = null
    private var isMonitoringNetwork = false

    /**
     * Start automatic synchronization
     */
    suspend fun startAutomaticSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (syncJob?.isActive == true) {
                return@withContext Result.Success(Unit)
            }

            Log.i(TAG, "Starting automatic synchronization")

            syncJob = managerScope.launch {
                while (isActive) {
                    if (isNetworkAvailable()) {
                        performFullSync()
                    } else {
                        Log.d(TAG, "Network not available, skipping sync")
                    }

                    delay(SYNC_INTERVAL_MS)
                }
            }

            // Start network monitoring
            startNetworkMonitoring()

            Log.i(TAG, "Automatic synchronization started")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start automatic sync", e)
            Result.Error(e)
        }
    }

    /**
     * Stop automatic synchronization
     */
    suspend fun stopAutomaticSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Stopping automatic synchronization")

            syncJob?.cancel()
            syncJob = null

            stopNetworkMonitoring()

            _syncState.value = SyncState.IDLE
            _syncProgress.value = SyncProgress()

            Log.i(TAG, "Automatic synchronization stopped")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop automatic sync", e)
            Result.Error(e)
        }
    }

    /**
     * Perform immediate synchronization
     */
    suspend fun performImmediateSync(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                return@withContext Result.Error(RuntimeException("Network not available"))
            }

            Log.i(TAG, "Performing immediate synchronization")
            performFullSync()

        } catch (e: Exception) {
            Log.e(TAG, "Immediate sync failed", e)
            Result.Error(e)
        }
    }

    /**
     * Get synchronization statistics
     */
    fun getSyncStatistics(): SyncStatistics {
        // In a real implementation, this would track actual sync metrics
        return SyncStatistics(
            lastSyncTime = System.currentTimeMillis() - 300000, // 5 minutes ago
            totalItemsSynced = 150,
            pendingItems = 5,
            failedItems = 2,
            averageSyncTimeMs = 2500L
        )
    }

    /**
     * Force sync of specific data types
     */
    suspend fun forceSyncDataType(dataType: SyncDataType): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Force syncing data type: $dataType")

            when (dataType) {
                SyncDataType.INSTALLATIONS -> syncInstallations()
                SyncDataType.PHOTOS -> syncPhotos()
                SyncDataType.DROPS -> syncDrops()
                SyncDataType.ALL -> performFullSync()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Force sync failed for $dataType", e)
            Result.Error(e)
        }
    }

    /**
     * Check if synchronization is active
     */
    fun isSyncActive(): Boolean = syncJob?.isActive == true

    /**
     * Shutdown sync manager
     */
    fun shutdown() {
        managerScope.cancel()
        syncJob?.cancel()
        Log.i(TAG, "SyncManager shutdown complete")
    }

    // Private implementation methods

    private suspend fun performFullSync(): Result<SyncResult> {
        _syncState.value = SyncState.SYNCING
        _syncProgress.value = SyncProgress(totalItems = 0, completedItems = 0)

        try {
            Log.i(TAG, "Starting full synchronization")

            val startTime = System.currentTimeMillis()

            // Sync in order of dependencies
            val dropResult = syncDrops()
            val installationResult = syncInstallations()
            val photoResult = syncPhotos()

            val totalSynced = (dropResult as? Result.Success)?.data?.itemsSynced ?: 0 +
                             (installationResult as? Result.Success)?.data?.itemsSynced ?: 0 +
                             (photoResult as? Result.Success)?.data?.itemsSynced ?: 0

            val hasErrors = dropResult is Result.Error ||
                           installationResult is Result.Error ||
                           photoResult is Result.Error

            val syncTime = System.currentTimeMillis() - startTime

            val result = SyncResult(
                success = !hasErrors,
                itemsSynced = totalSynced,
                syncTimeMs = syncTime,
                errors = collectErrors(dropResult, installationResult, photoResult)
            )

            _syncState.value = if (hasErrors) SyncState.ERROR else SyncState.IDLE
            _syncProgress.value = SyncProgress(totalItems = totalSynced, completedItems = totalSynced)

            Log.i(TAG, "Full synchronization completed in ${syncTime}ms, synced $totalSynced items")
            Result.Success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Full synchronization failed", e)
            _syncState.value = SyncState.ERROR
            Result.Error(e)
        }
    }

    private suspend fun syncInstallations(): Result<SyncResult> {
        return try {
            val pendingInstallations = installationDao.getInstallationsNeedingSync()

            if (pendingInstallations.isEmpty()) {
                return Result.Success(SyncResult(success = true, itemsSynced = 0, syncTimeMs = 0))
            }

            Log.d(TAG, "Syncing ${pendingInstallations.size} installations")

            // Process in batches
            val batches = pendingInstallations.chunked(BATCH_SIZE)
            var totalSynced = 0

            for (batch in batches) {
                // Simulate API sync - in real implementation would call actual API
                delay(100) // Simulate network delay

                val syncedIds = batch.map { it.id }
                installationDao.markInstallationsSynced(syncedIds)

                totalSynced += batch.size
                _syncProgress.value = _syncProgress.value.copy(completedItems = _syncProgress.value.completedItems + batch.size)
            }

            Result.Success(SyncResult(success = true, itemsSynced = totalSynced, syncTimeMs = 0))

        } catch (e: Exception) {
            Log.e(TAG, "Installation sync failed", e)
            Result.Error(e)
        }
    }

    private suspend fun syncPhotos(): Result<SyncResult> {
        return try {
            val pendingPhotos = photoDao.getPhotosNeedingSync()

            if (pendingPhotos.isEmpty()) {
                return Result.Success(SyncResult(success = true, itemsSynced = 0, syncTimeMs = 0))
            }

            Log.d(TAG, "Syncing ${pendingPhotos.size} photos")

            // Process in batches
            val batches = pendingPhotos.chunked(BATCH_SIZE)
            var totalSynced = 0

            for (batch in batches) {
                // Simulate API sync
                delay(150) // Photos take longer to upload

                val syncedIds = batch.map { it.id }
                photoDao.markPhotosSynced(syncedIds)

                totalSynced += batch.size
                _syncProgress.value = _syncProgress.value.copy(completedItems = _syncProgress.value.completedItems + batch.size)
            }

            Result.Success(SyncResult(success = true, itemsSynced = totalSynced, syncTimeMs = 0))

        } catch (e: Exception) {
            Log.e(TAG, "Photo sync failed", e)
            Result.Error(e)
        }
    }

    private suspend fun syncDrops(): Result<SyncResult> {
        return try {
            val pendingDrops = dropDao.getDropsNeedingSync()

            if (pendingDrops.isEmpty()) {
                return Result.Success(SyncResult(success = true, itemsSynced = 0, syncTimeMs = 0))
            }

            Log.d(TAG, "Syncing ${pendingDrops.size} drops")

            // Process in batches
            val batches = pendingDrops.chunked(BATCH_SIZE)
            var totalSynced = 0

            for (batch in batches) {
                // Simulate API sync
                delay(50) // Drops sync quickly

                val syncedIds = batch.map { it.id }
                dropDao.markDropsSynced(syncedIds)

                totalSynced += batch.size
                _syncProgress.value = _syncProgress.value.copy(completedItems = _syncProgress.value.completedItems + batch.size)
            }

            Result.Success(SyncResult(success = true, itemsSynced = totalSynced, syncTimeMs = 0))

        } catch (e: Exception) {
            Log.e(TAG, "Drop sync failed", e)
            Result.Error(e)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun startNetworkMonitoring() {
        if (isMonitoringNetwork) return
        isMonitoringNetwork = true
        // In a real implementation, would register network callback
        Log.d(TAG, "Network monitoring started")
    }

    private fun stopNetworkMonitoring() {
        isMonitoringNetwork = false
        Log.d(TAG, "Network monitoring stopped")
    }

    private fun collectErrors(vararg results: Result<*>): List<String> {
        return results.filterIsInstance<Result.Error>()
            .map { it.exception.message ?: "Unknown error" }
    }
}

/**
 * Synchronization data types
 */
enum class SyncDataType {
    INSTALLATIONS,
    PHOTOS,
    DROPS,
    ALL
}

/**
 * Synchronization states
 */
enum class SyncState {
    IDLE,
    SYNCING,
    ERROR
}

/**
 * Synchronization progress
 */
data class SyncProgress(
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val currentOperation: String = ""
) {
    val progressPercentage: Float
        get() = if (totalItems > 0) (completedItems.toFloat() / totalItems) * 100 else 0f
}

/**
 * Synchronization result
 */
data class SyncResult(
    val success: Boolean,
    val itemsSynced: Int,
    val syncTimeMs: Long,
    val errors: List<String> = emptyList()
)

/**
 * Synchronization statistics
 */
data class SyncStatistics(
    val lastSyncTime: Long,
    val totalItemsSynced: Int,
    val pendingItems: Int,
    val failedItems: Int,
    val averageSyncTimeMs: Long
)