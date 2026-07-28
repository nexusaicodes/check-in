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
        service: FakeServiceController = FakeServiceController(),
    ) = SettingsViewModel(settings, engagement, log, trigger, service)

    @Test
    fun `initial state reflects the settings seam`() {
        val settings = FakeAttendanceSettings(targetHoursToday = 6)
        val viewModel = buildViewModel(settings)

        assertEquals(6, viewModel.uiState.value.dailyTargetHours)
    }

    @Test
    fun `updating the target writes through and re-reads`() {
        val settings = FakeAttendanceSettings(targetHoursToday = 8)
        val viewModel = buildViewModel(settings)

        viewModel.updateDailyTarget(4)

        assertEquals(4, settings.recordedTarget)
        assertEquals(4, viewModel.uiState.value.dailyTargetHours)
    }

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
        val settings = FakeAttendanceSettings(targetHoursToday = 8)
        val viewModel = buildViewModel(settings)

        settings.targetHoursToday = 2
        viewModel.onResumed()

        assertEquals(2, viewModel.uiState.value.dailyTargetHours)
    }

    // --- Presence check ---

    /** Both default on, so an upgrade behaves exactly as the install did before they were settings. */
    @Test
    fun `the presence check and its pause both default on`() {
        val state = buildViewModel(FakeAttendanceSettings()).uiState.value

        assertTrue(state.presenceCheckEnabled)
        assertTrue(state.presenceCheckPauses)
    }

    @Test
    fun `presence check toggles write through and re-read`() {
        val settings = FakeAttendanceSettings()
        val viewModel = buildViewModel(settings)

        viewModel.setPresenceCheckPauses(false)
        assertFalse(settings.presenceCheckPauses)
        assertFalse(viewModel.uiState.value.presenceCheckPauses)

        viewModel.setPresenceCheckEnabled(false)
        assertFalse(settings.presenceCheckEnabled)
        assertFalse(viewModel.uiState.value.presenceCheckEnabled)
    }

    /**
     * A session already running has to hear about both toggles. Writing only the pref would leave it
     * under the settings it started with — and a check already outstanding would keep the clock
     * frozen with no path left to release it, since a pause closes only on a notification tap or the
     * in-app Resume button.
     */
    @Test
    fun `both presence toggles reach the running service`() {
        val service = FakeServiceController()
        val viewModel = buildViewModel(FakeAttendanceSettings(), service = service)

        viewModel.setPresenceCheckEnabled(false)
        assertEquals(1, service.presenceSettingsChangedCount)

        viewModel.setPresenceCheckPauses(false)
        assertEquals(2, service.presenceSettingsChangedCount)
    }

    /**
     * The presence check is attendance accounting, not encouragement. Turning nudges off must not
     * quietly change whether a user's clock stops.
     */
    @Test
    fun `the nudge master switch does not touch the presence check`() {
        val settings = FakeAttendanceSettings()
        val viewModel = buildViewModel(settings, FakeEngagementSettings())

        viewModel.setNudgesEnabled(false)

        assertTrue(settings.presenceCheckEnabled)
        assertTrue(viewModel.uiState.value.presenceCheckEnabled)
        assertTrue(viewModel.uiState.value.presenceCheckPauses)
    }
}
