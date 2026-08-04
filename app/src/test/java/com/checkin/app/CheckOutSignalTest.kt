package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.ui.checkin.CheckOutSignal
import com.checkin.app.ui.checkin.raiseCheckOutCelebration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * The celebration is process-global state shared by two writers, so every test here clears it either
 * side of itself — a leaked `Completed` would otherwise congratulate the next test.
 */
class CheckOutSignalTest {

    @Before
    fun reset() = CheckOutSignal.clear()

    @After
    fun tearDown() = CheckOutSignal.clear()

    @Test
    fun `nothing is celebrated until a session closes`() {
        assertNull(CheckOutSignal.completed.value)
    }

    @Test
    fun `clearing retires the celebration`() {
        CheckOutSignal.raise(sessionMs = 1000L, dayTotalMs = 1000L, daySessionCount = 1)
        assertEquals(1000L, CheckOutSignal.completed.value?.sessionMs)

        CheckOutSignal.clear()

        assertNull(CheckOutSignal.completed.value)
    }

    /** The stored duration, not a recomputed one, so it cannot disagree with the row it describes. */
    @Test
    fun `the raised figures come off the closed row and its own day`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val day = LocalDate.of(2026, 6, 15)
        val hour = 3_600_000L
        dao.seedCompleted(day.toString(), startedAt = 0L, durationMs = hour)
        val repo = CheckInRepository(dao, FixedTime(5 * hour, day))
        val session = repo.checkIn()
        val closed = repo.checkOutAt(session.id, 5 * hour + 2 * hour)

        raiseCheckOutCelebration(repo, closed!!)

        val completed = CheckOutSignal.completed.value!!
        assertEquals(2 * hour, completed.sessionMs)
        // Both sessions on that day, the just-closed one included.
        assertEquals(3 * hour, completed.dayTotalMs)
        assertEquals(2, completed.daySessionCount)
    }

    /**
     * A session belongs wholly to the day it began on. One opened before midnight and closed after
     * it must report that day, not the empty one it happened to end in — reading "today" instead
     * would congratulate the user against a day holding none of the work.
     */
    @Test
    fun `a session that ends after midnight reports the day it began on`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val startDay = LocalDate.of(2026, 6, 15)
        val hour = 3_600_000L
        val repo = CheckInRepository(dao, FixedTime(0L, startDay))
        val session = repo.checkIn()
        // Closed on the following day, while the row still carries 06-15 as its date_key.
        val closed = CheckInRepository(dao, FixedTime(2 * hour, startDay.plusDays(1)))
            .checkOut(session.id)

        raiseCheckOutCelebration(repo, closed!!)

        val completed = CheckOutSignal.completed.value!!
        assertEquals(2 * hour, completed.sessionMs)
        assertEquals(2 * hour, completed.dayTotalMs)
        assertEquals(1, completed.daySessionCount)
    }
}
