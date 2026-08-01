package com.checkin.app

import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun buildViewModel(
        settings: FakeAttendanceSettings,
        engagement: FakeEngagementSettings = FakeEngagementSettings(),
        log: FakeEngagementLog = FakeEngagementLog(),
        trigger: FakeNudgeTrigger = FakeNudgeTrigger(),
    ) = SettingsViewModel(settings, engagement, log, trigger)

    /** Nudges must be opt-in — shipping the feature can't start messaging existing users. */
    @Test
    fun `nudges are off by default`() {
        val viewModel = buildViewModel(FakeAttendanceSettings())

        assertFalse(viewModel.uiState.value.nudgesEnabled)
        assertTrue(viewModel.uiState.value.enabledNudges.isEmpty())
    }

    @Test
    fun `toggling the master switch writes through and re-reads`() {
        val engagement = FakeEngagementSettings()
        val viewModel = buildViewModel(FakeAttendanceSettings(), engagement)

        viewModel.setNudgesEnabled(true)

        assertTrue(engagement.masterEnabled)
        assertTrue(viewModel.uiState.value.nudgesEnabled)
    }

    /**
     * The per-nudge switch stays as the user set it even while the master switch is off, so turning
     * the master back on restores their selection rather than silently re-enabling everything.
     */
    @Test
    fun `an individual nudge keeps its own state independent of the master switch`() {
        val engagement = FakeEngagementSettings()
        val viewModel = buildViewModel(FakeAttendanceSettings(), engagement)

        viewModel.setNudgeEnabled(Nudge.NOT_CHECKED_IN_BY, true)
        assertTrue(Nudge.NOT_CHECKED_IN_BY in viewModel.uiState.value.enabledNudges)
        // Master is still off, so nothing is actually eligible to send.
        assertTrue(engagement.enabledNudges().isEmpty())

        viewModel.setNudgesEnabled(true)
        assertEquals(setOf(Nudge.NOT_CHECKED_IN_BY), engagement.enabledNudges())
    }

    @Test
    fun `the debug harness forces a send and runs a pass`() = runTest {
        val trigger = FakeNudgeTrigger()
        val viewModel = buildViewModel(FakeAttendanceSettings(), trigger = trigger)

        viewModel.debugSend(Nudge.NOT_CHECKED_IN_BY, variant = 1)
        viewModel.debugRunPass()
        advanceUntilIdle()

        // The variant reaches the dispatcher: the harness exists to preview copy, and the install's
        // own bucket is fixed, so a dropped override would make every other wording unreachable.
        assertEquals(listOf(Nudge.NOT_CHECKED_IN_BY to 1), trigger.forced)
        assertEquals(1, trigger.runOnceCount)
    }

    /** Clearing has to wipe the send history too, or cooldowns would outlive the log they came from. */
    @Test
    fun `clearing the log also clears send history`() = runTest {
        val engagement = FakeEngagementSettings()
        val log = FakeEngagementLog()
        val viewModel = buildViewModel(FakeAttendanceSettings(), engagement, log)
        engagement.markShown(Nudge.NOT_CHECKED_IN_BY, 1_000L)

        viewModel.debugClearLog()
        advanceUntilIdle()

        assertEquals(1, log.clearCount)
        assertTrue(engagement.lastShownAt().isEmpty())
    }

    /** Prefs can change while another screen is showing, so resume re-reads rather than trusting cache. */
    @Test
    fun `resume picks up a change made elsewhere`() {
        val engagement = FakeEngagementSettings()
        val viewModel = buildViewModel(FakeAttendanceSettings(), engagement)
        assertFalse(viewModel.uiState.value.nudgesEnabled)

        engagement.masterEnabled = true
        viewModel.onResumed()

        assertTrue(viewModel.uiState.value.nudgesEnabled)
    }
}
