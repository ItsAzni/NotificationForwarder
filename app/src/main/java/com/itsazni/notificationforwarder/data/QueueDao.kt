package com.itsazni.notificationforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: QueueItem): Long

    @Query(
        """
        SELECT * FROM notification_queue
        WHERE status = 'PENDING' AND nextRetryAt <= :now
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun getPending(now: Long, limit: Int): List<QueueItem>

    @Query("UPDATE notification_queue SET status = 'SENDING', updatedAt = :now WHERE id IN (:ids)")
    suspend fun markSending(ids: List<Long>, now: Long)

    @Query(
        """
        UPDATE notification_queue
        SET status = 'SENT', lastError = NULL, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun markSent(id: Long, now: Long)

    @Query(
        """
        UPDATE notification_queue
        SET status = :status,
            attemptCount = :attemptCount,
            nextRetryAt = :nextRetryAt,
            lastError = :lastError,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateFailure(
        id: Long,
        status: QueueStatus,
        attemptCount: Int,
        nextRetryAt: Long,
        lastError: String,
        updatedAt: Long
    )

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pendingCount,
            COALESCE(SUM(CASE WHEN status = 'SENDING' THEN 1 ELSE 0 END), 0) AS sendingCount,
            COALESCE(SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END), 0) AS sentCount,
            COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failedCount
        FROM notification_queue
        """
    )
    fun observeStats(): Flow<QueueStats>

    @Query("SELECT * FROM notification_queue ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<QueueItem>>

    @Query("DELETE FROM notification_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notification_queue")
    suspend fun clearAll()
}
