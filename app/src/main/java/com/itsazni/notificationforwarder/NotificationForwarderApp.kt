package com.itsazni.notificationforwarder

import android.app.Application
import com.itsazni.notificationforwarder.worker.WorkerScheduler

class NotificationForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkerScheduler.ensurePeriodic(this)
    }
}
