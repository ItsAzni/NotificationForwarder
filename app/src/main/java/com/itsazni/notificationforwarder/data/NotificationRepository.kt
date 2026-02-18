package com.itsazni.notificationforwarder.data

import android.content.Context
import com.itsazni.notificationforwarder.settings.FilterMode
import com.itsazni.notificationforwarder.settings.SettingsStore

class NotificationRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).queueDao()
    private val settingsStore = SettingsStore(context)

    suspend fun enqueue(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        postedAt: Long,
        notificationKey: String
    ) {
        if (!settingsStore.forwardingEnabled) {
            return
        }

        if (!allowPackage(packageName)) {
            return
        }

        val now = System.currentTimeMillis()
        val item = QueueItem(
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            postedAt = postedAt,
            notificationKey = notificationKey,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(item)
    }

    suspend fun getPending(limit: Int): List<QueueItem> {
        return dao.getPending(System.currentTimeMillis(), limit)
    }

    suspend fun markSending(ids: List<Long>) {
        dao.markSending(ids, System.currentTimeMillis())
    }

    suspend fun markSent(id: Long) {
        dao.markSent(id, System.currentTimeMillis())
    }

    suspend fun markFailure(id: Long, attemptCount: Int, maxRetry: Int, lastError: String) {
        val failed = attemptCount >= maxRetry
        val delayMillis = if (failed) 0L else calculateBackoff(attemptCount)
        dao.updateFailure(
            id = id,
            status = if (failed) QueueStatus.FAILED else QueueStatus.PENDING,
            attemptCount = attemptCount,
            nextRetryAt = System.currentTimeMillis() + delayMillis,
            lastError = lastError,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun observeStats() = dao.observeStats()

    fun observeRecent(limit: Int) = dao.observeRecent(limit)

    suspend fun deleteQueueItem(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearQueue() {
        dao.clearAll()
    }

    private fun calculateBackoff(attemptCount: Int): Long {
        val base = 30_000L
        val exponential = base * (1L shl (attemptCount.coerceAtMost(6)))
        val jitter = (0..4_000).random().toLong()
        return exponential + jitter
    }

    private fun allowPackage(packageName: String): Boolean {
        val list = settingsStore.filterPackages
        return when (settingsStore.filterMode) {
            FilterMode.ALL_APPS -> true
            FilterMode.WHITELIST -> list.contains(packageName)
            FilterMode.BLACKLIST -> !list.contains(packageName)
        }
    }
}
