package com.checkin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.di.AttendanceSettings
import com.checkin.app.di.ServiceController
import com.checkin.app.notify.engagement.EngagementSettings
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeTrigger
import com.checkin.app.notify.log.EngagementEvent
import com.checkin.app.notify.log.EngagementLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUiState(
    val dailyTargetHours: Int = TargetSchedule.DEFAULT_TARGET_HOURS,
    val trackingStartDate: LocalDate? = null,
    val nudgesEnabled: Boolean = false,
    val enabledNudges: Set<Nudge> = emptySet(),
    val presenceCheckEnabled: Boolean = true,
    val presenceCheckPauses: Boolean = true
)

/**
 * Settings are prefs-backed rather than DB-backed, so there is no Room `Flow` to build on — the state
 * is re-read on resume and after each write instead. The engagement event log is the exception: it
 * is a real table, so the debug harness observes it reactively.
 */
class SettingsViewModel(
    private val settings: AttendanceSettings,
    private val engagementPrefs: EngagementSettings,
    private val engagementLog: EngagementLog,
    private val nudgeTrigger: NudgeTrigger,
    private val serviceController: ServiceController
) : ViewModel() {

    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Debug harness only — surfaces what the engagement layer has actually recorded. */
    val recentEvents: StateFlow<List<EngagementEvent>> =
        engagementLog.recent(EVENT_LOG_LIMIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun readState(): SettingsUiState = SettingsUiState(
        dailyTargetHours = settings.dailyTargetHoursToday(),
        trackingStartDate = settings.readTrackingStartOrNull(),
        nudgesEnabled = engagementPrefs.masterEnabled,
        enabledNudges = Nudge.entries.filter { engagementPrefs.isEnabled(it) }.toSet(),
        // From attendance_prefs, not engagement_prefs: these decide how time is counted.
        presenceCheckEnabled = settings.presenceCheckEnabled,
        presenceCheckPauses = settings.presenceCheckPauses
    )

    fun onResumed() {
        _uiState.value = readState()
    }

    /** Records [hours] effective from today; past days keep the target that was in effect then. */
    fun updateDailyTarget(hours: Int) {
        settings.recordTargetChange(hours)
        _uiState.value = readState()
    }

    fun setNudgesEnabled(enabled: Boolean) {
        engagementPrefs.masterEnabled = enabled
        _uiState.value = readState()
    }

    fun setNudgeEnabled(nudge: Nudge, enabled: Boolean) {
        engagementPrefs.setEnabled(nudge, enabled)
        _uiState.value = readState()
    }

    /**
     * Both reach a session that is already running, not just the next one.
     *
     * Prefs alone would leave the current session under the settings it started with, and the case
     * that matters is a check already outstanding: turning it off, or turning off its penalty, has
     * to release the clock it froze. Nothing else can — a pause closes only on a notification tap or
     * the in-app Resume button, and with the check off neither will happen. Already-settled paused
     * time is not revisited; only the open window is.
     */
    fun setPresenceCheckEnabled(enabled: Boolean) {
        settings.presenceCheckEnabled = enabled
        serviceController.presenceSettingsChanged()
        _uiState.value = readState()
    }

    fun setPresenceCheckPauses(pauses: Boolean) {
        settings.presenceCheckPauses = pauses
        serviceController.presenceSettingsChanged()
        _uiState.value = readState()
    }

    // --- Debug harness ---

    /**
     * Sends [nudge] immediately, bypassing eligibility, so copy can be reviewed on demand.
     * [variant] overrides the install's own bucket — without it only one wording is ever reachable
     * on a given device, since bucketing is deterministic per install by design.
     */
    fun debugSend(nudge: Nudge, variant: Int) {
        viewModelScope.launch { nudgeTrigger.forceSend(nudge, variant) }
    }

    /** Runs a real evaluation pass now instead of waiting for the hourly worker. */
    fun debugRunPass() {
        viewModelScope.launch { nudgeTrigger.runOnce() }
    }

    fun debugClearLog() {
        viewModelScope.launch {
            engagementLog.clear()
            engagementPrefs.clearHistory()
        }
    }

    companion object {
        private const val EVENT_LOG_LIMIT = 30

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                SettingsViewModel(
                    container.settings,
                    container.engagementSettings,
                    container.engagementLog,
                    container.nudgeDispatcher,
                    container.serviceController
                )
            }
        }
    }
}
