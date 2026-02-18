package com.itsazni.notificationforwarder.service

import android.app.Notification
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
}
