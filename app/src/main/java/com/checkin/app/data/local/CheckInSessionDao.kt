package com.checkin.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface CheckInSessionDao {
    @Insert
    suspend fun insertSession(session: CheckInSession): Long

    @Update
    suspend fun updateSession(session: CheckInSession)

    @Query("SELECT * FROM sessions WHERE stopped_at IS NOT NULL ORDER BY started_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getCompletedSessions(limit: Int, offset: Int): List<CheckInSession>

    @Query("SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    suspend fun getActiveSession(): CheckInSession?

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): CheckInSession?
}
