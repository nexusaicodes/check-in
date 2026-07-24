package com.checkin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.di.AttendanceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class SettingsUiState(
    val dailyTargetHours: Int = TargetSchedule.DEFAULT_TARGET_HOURS,
    val trackingStartDate: LocalDate? = null
)

/**
 * Settings are prefs-backed rather than DB-backed, so there is no Room `Flow` to build on — the state
 * is re-read on resume and after each write instead.
 */
class SettingsViewModel(
    private val settings: AttendanceSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun readState() = SettingsUiState(
        dailyTargetHours = settings.dailyTargetHoursToday(),
        trackingStartDate = settings.readTrackingStartOrNull()
    )

    fun onResumed() {
        _uiState.value = readState()
    }

    /** Records [hours] effective from today; past days keep the target that was in effect then. */
    fun updateDailyTarget(hours: Int) {
        settings.recordTargetChange(hours)
        _uiState.value = readState()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                SettingsViewModel(container.settings)
            }
        }
    }
}
