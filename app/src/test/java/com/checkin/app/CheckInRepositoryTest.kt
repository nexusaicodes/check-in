package com.checkin.app

import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CheckInRepositoryTest {

    private class FakeDao : CheckInSessionDao {
        val sessions = mutableListOf<CheckInSession>()
        private var nextId = 1L

        override suspend fun insertSession(session: CheckInSession): Long {
            val stored = session.copy(id = nextId++)
            sessions.add(stored)
            return stored.id
        }

        override suspend fun updateSession(session: CheckInSession) {
            val index = sessions.indexOfFirst { it.id == session.id }
            if (index >= 0) sessions[index] = session
        }

        override suspend fun getActiveSession(): CheckInSession? =
            sessions.firstOrNull { it.stoppedAt == null }

        override suspend fun getSessionById(sessionId: Long): CheckInSession? =
            sessions.firstOrNull { it.id == sessionId }

        override suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> =
            sessions.filter { it.dateKey == dateKey }

        override fun getSessionsByDateFlow(dateKey: String): Flow<List<CheckInSession>> =
            flowOf(sessions.filter { it.dateKey == dateKey })

        override suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> =
            emptyList()

        override fun getTotalDurationForDateFlow(dateKey: String): Flow<Long> = flowOf(0L)

        override suspend fun getAllDateKeys(): List<String> = sessions.map { it.dateKey }.distinct()

        override suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession> =
            sessions
    }

    private class FixedTime(private val now: Long, private val date: LocalDate) : TimeSource {
        override fun nowMillis(): Long = now
        override fun today(): LocalDate = date
    }

    @Test
    fun `punchIn attributes the session to the punch-in local date`() = runBlocking {
        val dao = FakeDao()
        val repo = CheckInRepository(dao, FixedTime(1_700_000_000_000L, LocalDate.of(2026, 6, 15)))

        val id = repo.punchIn()
        val session = dao.getSessionById(id)!!

        assertEquals("2026-06-15", session.dateKey)
        assertEquals(1_700_000_000_000L, session.startedAt)
        assertNull(session.stoppedAt)
    }

    @Test
    fun `punchOut records duration but keeps the punch-in day even across midnight`() = runBlocking {
        val dao = FakeDao()
        val punchInDay = LocalDate.of(2026, 6, 15)
        val id = CheckInRepository(dao, FixedTime(1000L, punchInDay)).punchIn()

        // Punch out the next calendar day: attribution stays on the punch-in day (immutable).
        CheckInRepository(dao, FixedTime(6000L, punchInDay.plusDays(1))).punchOut(id)
        val session = dao.getSessionById(id)!!

        assertEquals(5000L, session.duration)
        assertEquals("2026-06-15", session.dateKey)
    }
}
