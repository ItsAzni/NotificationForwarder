package com.itsazni.notificationforwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class QueueStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}

@Entity(
    tableName = "notification_queue",
    indices = [Index(value = ["notificationKey"], unique = true)]
)
data class QueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val notificationKey: String,
    val status: QueueStatus = QueueStatus.PENDING,
    val attemptCount: Int = 0,
    val nextRetryAt: Long = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class QueueStats(
    val pendingCount: Int,
    val sendingCount: Int,
    val sentCount: Int,
    val failedCount: Int
)
