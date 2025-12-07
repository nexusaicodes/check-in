package com.checkin.app.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInSessionDao {
    @Insert
    suspend fun insertSession(session: CheckInSession): Long

    @Update
    suspend fun updateSession(session: CheckInSession)

    @Query("SELECT * FROM sessions ORDER BY started_at DESC")
    fun getAllSessions(): Flow<List<CheckInSession>>

    @Query("SELECT * FROM sessions WHERE stopped_at IS NOT NULL ORDER BY started_at DESC")
    fun getCompletedSessionsPaged(): PagingSource<Int, CheckInSession>

    @Query("SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    suspend fun getActiveSession(): CheckInSession?

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): CheckInSession?
}
