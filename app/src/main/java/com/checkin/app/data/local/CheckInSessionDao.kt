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

    @Query("SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    suspend fun getActiveSession(): CheckInSession?

    @Query("SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    fun getActiveSessionFlow(): Flow<CheckInSession?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): CheckInSession?

    @Query("SELECT * FROM sessions WHERE date_key = :dateKey ORDER BY started_at ASC")
    suspend fun getSessionsByDate(dateKey: String): List<CheckInSession>

    @Query("SELECT * FROM sessions WHERE date_key = :dateKey ORDER BY started_at ASC")
    fun getSessionsByDateFlow(dateKey: String): Flow<List<CheckInSession>>

    @Query(
        """
        SELECT date_key AS dateKey,
               COALESCE(SUM(duration), 0) AS totalDurationMs,
               COUNT(*) AS sessionCount,
               MIN(started_at) AS firstCheckIn,
               MAX(stopped_at) AS lastCheckOut
        FROM sessions
        WHERE date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        GROUP BY date_key
        ORDER BY date_key ASC
    """,
    )
    suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate>

    @Query(
        """
        SELECT date_key AS dateKey,
               COALESCE(SUM(duration), 0) AS totalDurationMs,
               COUNT(*) AS sessionCount,
               MIN(started_at) AS firstCheckIn,
               MAX(stopped_at) AS lastCheckOut
        FROM sessions
        WHERE date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        GROUP BY date_key
        ORDER BY date_key ASC
    """,
    )
    fun getDailyAggregatesFlow(startDate: String, endDate: String): Flow<List<DailyAggregate>>

    @Query("SELECT DISTINCT date_key FROM sessions ORDER BY date_key ASC")
    suspend fun getAllDateKeys(): List<String>

    /**
     * The day of the earliest session, or null when there are none — the day tracking began.
     *
     * `date_key` is ISO, so its lexicographic minimum is its chronological one. Deliberately *not*
     * filtered to completed sessions: a first check-in that is still open is still the day tracking
     * began, which is what the preference this replaced recorded.
     */
    @Query("SELECT MIN(date_key) FROM sessions")
    suspend fun getFirstDateKey(): String?

    @Query("SELECT MIN(date_key) FROM sessions")
    fun getFirstDateKeyFlow(): Flow<String?>

    @Query(
        """
        SELECT * FROM sessions
        WHERE date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        ORDER BY date_key ASC, started_at ASC
    """,
    )
    suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession>
}
