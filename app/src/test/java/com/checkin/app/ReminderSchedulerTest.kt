package com.checkin.app

import com.checkin.app.service.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReminderSchedulerTest {

    private val start = 1_000_000L
    private val present = 60 * 60 * 1000L // 1h present mark

    @Test
    fun `reminder falls within the 50 to 100 percent window`() {
        val halfMark = present / 2
        val lower = start + halfMark
        val upper = start + present
        // Many seeds — the reminder must always land inside the window.
        for (seed in 0 until 500) {
            val at = ReminderScheduler.computeReminderAt(start, present, Random(seed))
            assertTrue("seed=$seed at=$at below window", at >= lower)
            assertTrue("seed=$seed at=$at above window", at <= upper)
        }
    }

    @Test
    fun `zero present mark schedules immediately`() {
        assertEquals(start, ReminderScheduler.computeReminderAt(start, 0L, Random(1)))
    }

    @Test
    fun `is deterministic for a given seed`() {
        val a = ReminderScheduler.computeReminderAt(start, present, Random(42))
        val b = ReminderScheduler.computeReminderAt(start, present, Random(42))
        assertEquals(a, b)
    }
}
