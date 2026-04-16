package com.itsazni.notificationforwarder.service

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.itsazni.notificationforwarder.data.NotificationRepository
import com.itsazni.notificationforwarder.worker.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class RecentEvent(
        val contentHash: Int,
        val postedAt: Long,
        val seenAt: Long
    )

    private val dedupLock = Any()
    private val recentEvents = LinkedHashMap<String, RecentEvent>(MAX_RECENT_EVENTS, 0.75f, true)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val item = sbn ?: return
        if (item.packageName == packageName) {
            return
        }

        val notification: Notification = item.notification
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        if (shouldSkip(item, notification, title, text, bigText)) {
            return
        }

        serviceScope.launch {
            val repository = NotificationRepository(applicationContext)
            repository.enqueue(
                packageName = item.packageName,
                appName = resolveAppName(item.packageName),
                title = title,
                text = text,
                postedAt = item.postTime,
                notificationKey = item.key
            )
            WorkerScheduler.enqueueImmediate(applicationContext)
        }
    }

    private fun resolveAppName(pkg: String): String {
        return runCatching {
            val pm = packageManager
            val applicationInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(pkg)
    }

    private fun shouldSkip(
        sbn: StatusBarNotification,
        notification: Notification,
        title: String,
        text: String,
        bigText: String
    ): Boolean {
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            return true
        }

        val stableKey = buildStableKey(sbn)
        val contentHash = listOf(title, text, bigText).joinToString("\u001f").hashCode()
        val now = System.currentTimeMillis()

        synchronized(dedupLock) {
            val previous = recentEvents[stableKey]
            if (previous != null) {
                val sameContent = previous.contentHash == contentHash
                val samePostTime = previous.postedAt == sbn.postTime
                val burstUpdate = now - previous.seenAt <= DUPLICATE_WINDOW_MS
                if (sameContent && (samePostTime || burstUpdate)) {
                    return true
                }
            }

            recentEvents[stableKey] = RecentEvent(
                contentHash = contentHash,
                postedAt = sbn.postTime,
                seenAt = now
            )
            trimRecentEvents()
        }

        return false
    }

    private fun buildStableKey(sbn: StatusBarNotification): String {
        val fallback = buildString {
            append(sbn.packageName)
            append('|')
            append(sbn.id)
            append('|')
            append(sbn.tag ?: "")
            append('|')
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                append(sbn.user.hashCode())
            } else {
                append("legacy-user")
            }
        }
        return sbn.key.ifBlank { fallback }
    }

    private fun trimRecentEvents() {
        while (recentEvents.size > MAX_RECENT_EVENTS) {
            val firstKey = recentEvents.entries.firstOrNull()?.key ?: return
            recentEvents.remove(firstKey)
        }
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 500L
        private const val MAX_RECENT_EVENTS = 512
    }
}
