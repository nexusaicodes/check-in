package com.checkin.app

import com.checkin.app.ui.settings.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SettingsViewModelTest {

    @Test
    fun `initial state reflects the settings seam`() {
        val settings = FakeAttendanceSettings(
            trackingStart = LocalDate.of(2026, 6, 1),
            targetHoursToday = 6
        )
        val viewModel = SettingsViewModel(settings)

        assertEquals(6, viewModel.uiState.value.dailyTargetHours)
        assertEquals(LocalDate.of(2026, 6, 1), viewModel.uiState.value.trackingStartDate)
    }

    /** Before the first check-in there is no tracking start; the screen shows the not-started copy. */
    @Test
    fun `tracking start is null until tracking has begun`() {
        val viewModel = SettingsViewModel(FakeAttendanceSettings(trackingStart = null))

        assertNull(viewModel.uiState.value.trackingStartDate)
    }

    @Test
    fun `updating the target writes through and re-reads`() {
        val settings = FakeAttendanceSettings(targetHoursToday = 8)
        val viewModel = SettingsViewModel(settings)

        viewModel.updateDailyTarget(4)

        assertEquals(4, settings.recordedTarget)
        assertEquals(4, viewModel.uiState.value.dailyTargetHours)
    }

    /** Prefs can change while another screen is showing, so resume re-reads rather than trusting cache. */
    @Test
    fun `resume picks up a change made elsewhere`() {
        val settings = FakeAttendanceSettings(targetHoursToday = 8)
        val viewModel = SettingsViewModel(settings)

        settings.targetHoursToday = 2
        settings.trackingStart = LocalDate.of(2026, 7, 1)
        viewModel.onResumed()

        assertEquals(2, viewModel.uiState.value.dailyTargetHours)
        assertEquals(LocalDate.of(2026, 7, 1), viewModel.uiState.value.trackingStartDate)
    }
}
