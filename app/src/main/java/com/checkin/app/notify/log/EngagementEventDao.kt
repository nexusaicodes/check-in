package com.checkin.app.notify.log

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EngagementEventDao {

    @Insert
    suspend fun insert(event: EngagementEvent): Long

    @Query("SELECT * FROM engagement_events ORDER BY at DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<EngagementEvent>>

    @Query(
        "SELECT * FROM engagement_events WHERE event = :event AND at >= :since " +
            "ORDER BY at DESC, id DESC LIMIT 1"
    )
    suspend fun latestOfType(event: String, since: Long): EngagementEvent?

    @Query("SELECT COUNT(*) FROM engagement_events WHERE event = :event AND at >= :since")
    suspend fun countOfTypeSince(event: String, since: Long): Int

    @Query("DELETE FROM engagement_events WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM engagement_events")
    suspend fun clear()
}
