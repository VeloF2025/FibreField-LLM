package com.fibreflow.infrastructure.sync

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

/**
 * Offline queue for managing sync operations when network is unavailable
 * Stores sync data and manages retry logic
 */
@Singleton
class OfflineQueue {

    // In-memory storage for queued items - in production, this would use database
    private val queue = ConcurrentHashMap<String, QueuedSyncItem>()

    /**
     * Add item to sync queue
     */
    fun addToQueue(item: Any) {
        try {
            val queueId = generateQueueId()
            val queuedItem = QueuedSyncItem(
                id = queueId,
                data = item,
                timestamp = System.currentTimeMillis(),
                retryCount = 0,
                sessionId = when (item) {
                    is InstallationCompletionData -> item.sessionId
                    is InstallationFailureData -> item.sessionId
                    else -> null
                }
            )

            queue[queueId] = queuedItem
            Timber.d("Added item to offline queue: $queueId")
        } catch (e: Exception) {
            Timber.e(e, "Error adding item to offline queue")
        }
    }

    /**
     * Remove item from sync queue
     */
    fun removeFromQueue(item: Any) {
        try {
            val itemToRemove = queue.values.find { it.data == item }
            if (itemToRemove != null) {
                queue.remove(itemToRemove.id)
                Timber.d("Removed item from offline queue: ${itemToRemove.id}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error removing item from offline queue")
        }
    }

    /**
     * Get queued items for a specific session
     */
    fun getQueuedItems(sessionId: String): List<Any> {
        return try {
            queue.values
                .filter { it.sessionId == sessionId }
                .map { it.data }
        } catch (e: Exception) {
            Timber.e(e, "Error getting queued items for session: $sessionId")
            emptyList()
        }
    }

    /**
     * Get all queued items
     */
    fun getAllQueuedItems(): List<Any> {
        return try {
            queue.values.map { it.data }
        } catch (e: Exception) {
            Timber.e(e, "Error getting all queued items")
            emptyList()
        }
    }

    /**
     * Update retry count for an item
     */
    fun updateRetryCount(item: Any, newRetryCount: Int) {
        try {
            val queuedItem = queue.values.find { it.data == item }
            if (queuedItem != null) {
                val updatedItem = queuedItem.copy(retryCount = newRetryCount)
                queue[queuedItem.id] = updatedItem
                Timber.d("Updated retry count for item ${queuedItem.id} to $newRetryCount")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating retry count")
        }
    }

    /**
     * Clean up old items from the queue
     */
    fun cleanupOldItems(cutoffTime: Long): Int {
        return try {
            val itemsToRemove = queue.values.filter { it.timestamp < cutoffTime }
            itemsToRemove.forEach { queue.remove(it.id) }
            Timber.d("Cleaned up ${itemsToRemove.size} old queue items")
            itemsToRemove.size
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old queue items")
            0
        }
    }

    /**
     * Get queue size
     */
    fun getQueueSize(): Int {
        return queue.size
    }

    /**
     * Clear all items from queue
     */
    fun clearQueue() {
        try {
            val size = queue.size
            queue.clear()
            Timber.d("Cleared $size items from offline queue")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing offline queue")
        }
    }

    /**
     * Check if queue contains specific item
     */
    fun containsItem(item: Any): Boolean {
        return try {
            queue.values.any { it.data == item }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get queue statistics
     */
    fun getQueueStatistics(): QueueStatistics {
        return try {
            val totalItems = queue.size
            val itemsByType = queue.values.groupBy { it.data::class.simpleName }
            val oldestItem = queue.values.minByOrNull { it.timestamp }
            val newestItem = queue.values.maxByOrNull { it.timestamp }

            QueueStatistics(
                totalItems = totalItems,
                itemsByType = itemsByType.mapValues { it.value.size },
                oldestItemTimestamp = oldestItem?.timestamp,
                newestItemTimestamp = newestItem?.timestamp
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting queue statistics")
            QueueStatistics(0, emptyMap(), null, null)
        }
    }

    /**
     * Generate unique queue ID
     */
    private fun generateQueueId(): String {
        return "queue_${System.currentTimeMillis()}_${queue.size}"
    }
}

/**
 * Data class for queued sync items
 */
data class QueuedSyncItem(
    val id: String,
    val data: Any,
    val timestamp: Long,
    val retryCount: Int,
    val sessionId: String?
)

/**
 * Statistics for the offline queue
 */
data class QueueStatistics(
    val totalItems: Int,
    val itemsByType: Map<String?, Int>,
    val oldestItemTimestamp: Long?,
    val newestItemTimestamp: Long?
)