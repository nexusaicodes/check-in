package com.checkin.app

import com.checkin.app.data.local.TargetSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EffectiveTargetTest {

    private val june1 = LocalDate.of(2026, 6, 1)
    private val july1 = LocalDate.of(2026, 7, 1)

    @Test
    fun `empty schedule falls back to the default`() {
        assertEquals(
            TargetSchedule.DEFAULT_TARGET_HOURS,
            TargetSchedule.effectiveTargetHours(emptyList(), june1)
        )
    }

    @Test
    fun `parse and serialize round-trip and stay sorted`() {
        val raw = "2026-07-01=4;2026-06-01=2"
        val entries = TargetSchedule.parse(raw)
        assertEquals(listOf(june1, july1), entries.map { it.effectiveFrom })
        assertEquals("2026-06-01=2;2026-07-01=4", TargetSchedule.serialize(entries))
    }

    @Test
    fun `parse tolerates malformed tokens`() {
        val entries = TargetSchedule.parse("garbage;2026-06-01=2;=;2026-13-40=9")
        assertEquals(1, entries.size)
        assertEquals(2, entries[0].targetHours)
    }

    @Test
    fun `effective target uses the latest entry on or before the date`() {
        val entries = TargetSchedule.parse("2026-06-01=2;2026-07-01=4")
        assertEquals(2, TargetSchedule.effectiveTargetHours(entries, june1))
        assertEquals(2, TargetSchedule.effectiveTargetHours(entries, LocalDate.of(2026, 6, 30)))
        assertEquals(4, TargetSchedule.effectiveTargetHours(entries, july1))
        assertEquals(4, TargetSchedule.effectiveTargetHours(entries, LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun `effective target ms derives from hours`() {
        val entries = TargetSchedule.parse("2026-06-01=3")
        assertEquals(3 * 60 * 60 * 1000L, TargetSchedule.effectiveTargetMs(entries, june1))
    }

    @Test
    fun `withChange appends a differing value and replaces same-date entries`() {
        val base = TargetSchedule.parse("2026-06-01=2")
        val changed = TargetSchedule.withChange(base, july1, 4)
        assertEquals(2, TargetSchedule.effectiveTargetHours(changed, june1))
        assertEquals(4, TargetSchedule.effectiveTargetHours(changed, july1))

        val replaced = TargetSchedule.withChange(changed, july1, 6)
        assertEquals(6, TargetSchedule.effectiveTargetHours(replaced, july1))
        assertEquals(2, replaced.count { it.effectiveFrom == june1 || it.effectiveFrom == july1 })
    }

    @Test
    fun `legacy fallback keeps a non-empty parsed log unchanged`() {
        val parsed = TargetSchedule.parse("2026-06-01=5")
        assertEquals(parsed, TargetSchedule.withLegacyFallback(parsed, june1, 9))
    }

    @Test
    fun `legacy fallback synthesizes an entry when the log is empty but tracking has started`() {
        // Pre-log install: empty schedule, tracking started, legacy scalar target = 8.
        val result = TargetSchedule.withLegacyFallback(emptyList(), june1, 8)
        assertEquals(listOf(TargetSchedule.Entry(june1, 8)), result)
        assertEquals(8, TargetSchedule.effectiveTargetHours(result, june1))
    }

    @Test
    fun `legacy fallback stays empty when tracking has not started`() {
        // Fresh install: no schedule and no tracking start → default target applies downstream.
        assertEquals(emptyList<TargetSchedule.Entry>(), TargetSchedule.withLegacyFallback(emptyList(), null, 8))
    }

    @Test
    fun `withChange drops a no-op that matches the value already in effect`() {
        val base = TargetSchedule.parse("2026-06-01=2")
        // Setting 2 again for a later date changes nothing effective, so no new entry is kept.
        val unchanged = TargetSchedule.withChange(base, july1, 2)
        assertEquals(1, unchanged.size)
        assertEquals(june1, unchanged[0].effectiveFrom)
    }
}
