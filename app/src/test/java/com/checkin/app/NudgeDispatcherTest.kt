package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeCatalog
import com.checkin.app.notify.engagement.NudgeDispatcher
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The dispatcher's failure mode is silent: a nudge logged but never posted looks identical in the
 * data to one nobody acted on, so the conversion rate quietly drops with nothing to point at. These
 * pin the invariant that the log only ever records what the platform actually accepted.
 *
 * It became testable at all once copy resolution moved behind [StringResolver] — needing a `Context`
 * for `getString` is what kept the one class in this layer with a silent failure off the JVM suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NudgeDispatcherTest {

    // 11:00 local on a day the user hasn't checked in — the trigger hour for NOT_CHECKED_IN_BY.
    private val today = LocalDate.of(2026, 6, 15)
    private val elevenAm = today.atTime(11, 0)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val time = FixedTime(elevenAm, today)
    private val notifier = FakeNotifier()
    private val log = FakeEngagementLog()
    private val prefs = FakeEngagementSettings(masterEnabled = true)
    private val settings = FakeAttendanceSettings(trackingStart = today.minusDays(10))

    private fun dispatcher(): NudgeDispatcher {
        prefs.setEnabled(Nudge.NOT_CHECKED_IN_BY, true)
        return NudgeDispatcher(
            strings = StringResolver { "copy-$it" },
            repository = CheckInRepository(FakeCheckInSessionDao(), time) { settings.readSchedule() },
            settings = settings,
            prefs = prefs,
            notifier = notifier,
            log = log,
            timeSource = time
        )
    }

    @Test
    fun `an eligible nudge is posted, marked and logged`() = runTest {
        val sent = dispatcher().runOnce()

        assertEquals(Nudge.NOT_CHECKED_IN_BY, sent)
        assertEquals(1, notifier.shown.size)
        assertEquals(1, log.shownCountSince(0L))
        assertNotNull(prefs.lastShownAt()[Nudge.NOT_CHECKED_IN_BY])
    }

    /**
     * POST_NOTIFICATIONS is revocable at any time. A refused post that still logged SHOWN would put
     * an un-actionable event in the denominator and understate every conversion rate — and marking
     * it shown would burn the day's single nudge slot on a notification nobody saw.
     */
    @Test
    fun `a refused post records nothing`() = runTest {
        notifier.refuse = true

        val sent = dispatcher().runOnce()

        assertNull(sent)
        assertEquals(0, log.shownCountSince(0L))
        assertTrue(prefs.lastShownAt().isEmpty())
    }

    @Test
    fun `nothing is posted when no nudge is eligible`() = runTest {
        prefs.masterEnabled = false

        val sent = dispatcher().runOnce()

        assertNull(sent)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(0, log.shownCountSince(0L))
    }

    /** The spec is what the tray and the dismissal receiver both read; a wrong field is invisible. */
    @Test
    fun `the posted spec carries the nudge's own id, channel and dismissal tag`() = runTest {
        dispatcher().runOnce()

        val spec = notifier.shown.single()
        assertEquals(Nudge.NOT_CHECKED_IN_BY.notificationId, spec.id)
        assertEquals(NotificationChannels.ENGAGEMENT, spec.channelId)
        assertEquals(EngagementSource.NUDGE, spec.dismissal?.source)
        assertEquals(Nudge.NOT_CHECKED_IN_BY.name, spec.dismissal?.key)
    }

    /**
     * Bucketing is deterministic per install by design, so without an override the debug harness can
     * only ever preview whichever wording this device landed on.
     */
    @Test
    fun `a forced variant overrides the install's bucket`() = runTest {
        val dispatcher = dispatcher()
        val variantCount = NudgeCatalog.variants(Nudge.NOT_CHECKED_IN_BY).size

        repeat(variantCount) { dispatcher.forceSend(Nudge.NOT_CHECKED_IN_BY, variant = it) }

        val variants = log.events.value
            .filter { it.event == EngagementEventType.SHOWN.name }
            .map { it.variant }
        assertEquals((0 until variantCount).toList(), variants)
    }
}
