package com.checkin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.engagement.EngagementSettings
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeTrigger
import com.checkin.app.notify.log.EngagementEvent
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.platform.PromptSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val snapshotReader: DebugSnapshotReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Backs the debug diagnostics card — what the notification and service layers have actually
     * recorded. `WhileSubscribed`, so the query is live only while the card is open.
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

    /** The live session/service/alarm state, read fresh. See [DebugSnapshotReader]. */
    suspend fun readSnapshot(channels: List<ChannelState>): DebugSnapshot = snapshotReader.read(channels)

    /**
     * A one-shot read of the log, for the clipboard report.
     *
     * Deliberately **not** [recentEvents]`.value`. That flow is `WhileSubscribed`, so it holds its
     * `emptyList()` seed whenever the log section is collapsed — which is its default state, and
     * therefore the state the report would usually be copied in. Reading the flow's first emission
     * asks Room directly and does not care whether anything is currently observing it.
     */
    suspend fun readLog(): List<EngagementEvent> = engagementLog.recent(EVENT_LOG_LIMIT).first()

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
        /**
         * Debug-only reader, so this is sized for reading a session's whole history rather than for
         * a glance: service lifecycle, alarm and nudge rows all interleave, and one overnight session
         * with its two-hourly reminders fills a couple of dozen on its own.
         */
        private const val EVENT_LOG_LIMIT = 100

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                SettingsViewModel(
                    container.settings,
                    container.engagementSettings,
                    container.engagementLog,
                    container.nudgeDispatcher,
                    DebugSnapshotReader(container.repository, container.sessionAlarms, container.timeSource),
                )
            }
        }
    }
}
