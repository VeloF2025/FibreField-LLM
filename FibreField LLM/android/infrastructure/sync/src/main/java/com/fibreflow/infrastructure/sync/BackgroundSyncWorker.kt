package com.fibreflow.infrastructure.sync

import android.content.Context
import androidx.work.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Background worker for data synchronization
 * Runs periodic sync operations in the background
 */
class BackgroundSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var syncManager: SyncManager

    override suspend fun doWork(): Result {
        return try {
            Timber.d("BackgroundSyncWorker: Starting background sync")

            // Perform the sync operation
            val syncResult = syncManager.performImmediateSync()

            when (syncResult) {
                is com.fibreflow.core.common.result.Result.Success -> {
                    Timber.d("BackgroundSyncWorker: Sync completed successfully")
                    Result.success()
                }
                is com.fibreflow.core.common.result.Result.Error -> {
                    Timber.e(syncResult.exception, "BackgroundSyncWorker: Sync failed")
                    // Return retry for transient failures
                    if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "BackgroundSyncWorker: Unexpected error")
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        const val WORK_NAME = "background_sync_work"

        /**
         * Schedule periodic background sync
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncWorkRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                SYNC_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_DELAY_MINUTES,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncWorkRequest
            )

            Timber.d("BackgroundSyncWorker: Scheduled periodic sync every $SYNC_INTERVAL_HOURS hours")
        }

        /**
         * Schedule one-time immediate sync
         */
        fun scheduleImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWorkRequest = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_DELAY_MINUTES,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                syncWorkRequest
            )

            Timber.d("BackgroundSyncWorker: Scheduled immediate sync")
        }

        /**
         * Cancel all background sync work
         */
        fun cancelAllSyncWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
            Timber.d("BackgroundSyncWorker: Cancelled all sync work")
        }

        /**
         * Get work info for monitoring
         */
        fun getWorkInfo(context: Context): LiveData<List<WorkInfo>> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(WORK_NAME)
        }

        // Configuration constants
        private const val SYNC_INTERVAL_HOURS = 2L // Sync every 2 hours
        private const val MIN_BACKOFF_DELAY_MINUTES = 15L // Minimum 15 minutes between retries
    }
}

/**
 * Sync worker factory for dependency injection
 */
class SyncWorkerFactory @Inject constructor(
    private val syncManager: SyncManager
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            BackgroundSyncWorker::class.java.name -> {
                val worker = BackgroundSyncWorker(appContext, workerParameters)
                // Inject dependencies (in a real app, use Dagger or Hilt)
                worker.syncManager = syncManager
                worker
            }
            else -> null
        }
    }
}