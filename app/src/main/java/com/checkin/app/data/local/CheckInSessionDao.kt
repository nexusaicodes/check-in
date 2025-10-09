package com.checkin.app.data.local

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

    @Query("SELECT * FROM check_in_sessions ORDER BY start_timestamp DESC")
    fun getAllSessions(): Flow<List<CheckInSession>>

    @Query("SELECT * FROM check_in_sessions WHERE end_timestamp IS NULL LIMIT 1")
    suspend fun getActiveSession(): CheckInSession?

    @Query("SELECT * FROM check_in_sessions ORDER BY start_timestamp ASC")
    suspend fun getAllSessionsForExport(): List<CheckInSession>
}
