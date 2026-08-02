package com.checkin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.di.PromptSettings
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

data class SettingsUiState(val nudgesEnabled: Boolean = false, val enabledNudges: Set<Nudge> = emptySet())

/**
 * Settings are prefs-backed rather than DB-backed, so there is no Room `Flow` to build on — the state
 * is re-read on resume and after each write instead. The engagement event log is the exception: it is
 * a real table, so the diagnostics card observes it reactively.
 */
class SettingsViewModel(
    private val settings: PromptSettings,
    private val engagementPrefs: EngagementSettings,
    private val engagementLog: EngagementLog,
    private val nudgeTrigger: NudgeTrigger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Backs the diagnostics card, which ships in release — what the notification and service layers
     * have actually recorded. `WhileSubscribed`, so the query is live only while the card is open.
     */
    val recentEvents: StateFlow<List<EngagementEvent>> =
        engagementLog.recent(EVENT_LOG_LIMIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun readState(): SettingsUiState = SettingsUiState(
        nudgesEnabled = engagementPrefs.masterEnabled,
        enabledNudges = Nudge.entries.filter { engagementPrefs.isEnabled(it) }.toSet(),
    )

    fun onResumed() {
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
        viewModelScope.launch { engagementLog.clear() }
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
                )
            }
        }
    }
}
