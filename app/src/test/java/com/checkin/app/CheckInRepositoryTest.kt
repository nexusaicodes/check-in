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

        override suspend fun getActiveSession(): CheckInSession? = sessions.firstOrNull { it.stoppedAt == null }

        override fun getActiveSessionFlow(): Flow<CheckInSession?> =
            flowOf(sessions.firstOrNull { it.stoppedAt == null })

        override suspend fun getSessionById(sessionId: Long): CheckInSession? =
            sessions.firstOrNull { it.id == sessionId }

        override suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> =
            sessions.filter { it.dateKey == dateKey }

        override fun getSessionsByDateFlow(dateKey: String): Flow<List<CheckInSession>> =
            flowOf(sessions.filter { it.dateKey == dateKey })

        override suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> = emptyList()

        override fun getDailyAggregatesFlow(startDate: String, endDate: String): Flow<List<DailyAggregate>> =
            flowOf(emptyList())

        override suspend fun getAllDateKeys(): List<String> = sessions.map { it.dateKey }.distinct()

        override suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession> = sessions
    }

    private class FixedTime(private val now: Long, private val date: LocalDate) : TimeSource {
        override fun nowMillis(): Long = now
        override fun today(): LocalDate = date
        override fun currentDay(): Flow<LocalDate> = flowOf(date)
    }

    @Test
    fun `checkIn attributes the session to the check-in local date`() = runBlocking {
        val dao = FakeDao()
        val repo = CheckInRepository(dao, FixedTime(1_700_000_000_000L, LocalDate.of(2026, 6, 15)))

        val id = repo.checkIn().id
        val session = dao.getSessionById(id)!!

        assertEquals("2026-06-15", session.dateKey)
        assertEquals(1_700_000_000_000L, session.startedAt)
        assertNull(session.stoppedAt)
    }

    /**
     * Two gated paths can reach a check-in — the Check-In screen and a nudge tap — and either can
     * resolve while the other's gate is still up. A second open row would never be closed and its
     * hours would never reach `duration`, so the invariant is enforced in the one writer.
     */
    @Test
    fun `checkIn returns the open session rather than opening a second`() = runBlocking {
        val dao = FakeDao()
        val repo = CheckInRepository(dao, FixedTime(1000L, LocalDate.of(2026, 6, 15)))

        val first = repo.checkIn()
        val second = repo.checkIn()

        assertEquals(first.id, second.id)
        assertEquals(1, dao.sessions.size)
    }

    @Test
    fun `checkIn opens a new session once the previous one is closed`() = runBlocking {
        val dao = FakeDao()
        val repo = CheckInRepository(dao, FixedTime(1000L, LocalDate.of(2026, 6, 15)))

        val first = repo.checkIn()
        repo.checkOut(first.id)
        val second = repo.checkIn()

        assertEquals(2, dao.sessions.size)
        assertEquals(first.id + 1, second.id)
    }

    @Test
    fun `checkOut records duration but keeps the check-in day even across midnight`() = runBlocking {
        val dao = FakeDao()
        val checkInDay = LocalDate.of(2026, 6, 15)
        val id = CheckInRepository(dao, FixedTime(1000L, checkInDay)).checkIn().id

        // Check out the next calendar day: attribution stays on the check-in day (immutable).
        CheckInRepository(dao, FixedTime(6000L, checkInDay.plusDays(1))).checkOut(id)
        val session = dao.getSessionById(id)!!

        assertEquals(5000L, session.duration)
        assertEquals("2026-06-15", session.dateKey)
    }

    /**
     * The day-boundary close stamps the instant the day ended, not the instant its (inexact) alarm
     * landed. An alarm delivered hours late must not hand a forgotten session hours on a day it does
     * not belong to.
     */
    @Test
    fun `checkOutAt stamps the given instant rather than now`() = runBlocking {
        val dao = FakeDao()
        val day = LocalDate.of(2026, 6, 15)
        val id = CheckInRepository(dao, FixedTime(1000L, day)).checkIn().id

        // "Now" is 60_000 — the alarm landed very late; the boundary it closes at is 9000.
        CheckInRepository(dao, FixedTime(60_000L, day)).checkOutAt(id, 9000L)
        val session = dao.getSessionById(id)!!

        assertEquals(9000L, session.stoppedAt)
        assertEquals(8000L, session.duration)
    }

    /**
     * A stop before the start is a changed system clock or a corrupt row. Flooring at zero keeps one
     * bad row from poisoning every total that sums durations.
     */
    @Test
    fun `checkOutAt floors a backwards duration at zero`() = runBlocking {
        val dao = FakeDao()
        val day = LocalDate.of(2026, 6, 15)
        val id = CheckInRepository(dao, FixedTime(5000L, day)).checkIn().id

        CheckInRepository(dao, FixedTime(5000L, day)).checkOutAt(id, 1000L)

        assertEquals(0L, dao.getSessionById(id)!!.duration)
    }
}
