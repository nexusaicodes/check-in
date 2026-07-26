package com.checkin.app

import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.engagement.DefaultEngagementReporter
import com.checkin.app.notify.engagement.Nudge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngagementReporterTest {

    /**
     * Notifications outlive an app update. A nudge posted by the release that shared one id across
     * every kind is still in the tray under that id, and cancelling only the current ids would leave
     * it there for good — tapping it later runs the whole presence gate and then resolves to nothing,
     * because a session is already open.
     */
    @Test
    fun `retiring nudges also clears the id the previous release shared`() = runTest {
        val notifier = FakeNotifier()
        val reporter = DefaultEngagementReporter(notifier, FakeEngagementLog())

        reporter.onCheckedIn(atMillis = 1_000L)

        assertTrue(NotificationIds.RETIRED_SHARED_NUDGE in notifier.cancelled)
        assertTrue(Nudge.entries.all { it.notificationId in notifier.cancelled })
    }

    @Test
    fun `a tapped nudge is retired straight away, not only once the check-in lands`() = runTest {
        val notifier = FakeNotifier()
        val reporter = DefaultEngagementReporter(notifier, FakeEngagementLog())

        reporter.onNudgeOpened(atMillis = 1_000L)

        assertTrue(Nudge.NOT_CHECKED_IN_BY.notificationId in notifier.cancelled)
    }
}
