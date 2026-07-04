package com.checkin.app.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.DeficitCalculator
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.di.AttendanceSettings
import com.checkin.app.di.ServiceController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/** Immutable snapshot the Check-In screen renders. Elapsed time is screen-driven, not held here. */
data class CheckInUiState(
    val loading: Boolean = true,
    val isRunning: Boolean = false,
    val currentSessionStartTime: Long? = null,
    val todayDateKey: String = "",
    val todaySessions: List<CheckInSession> = emptyList(),
    val todayTotalDuration: Long = 0L,
    val dailyTargetMs: Long = 0L,
    val deficit: Double = 0.0,
    val hasEverTracked: Boolean = false,
    val showSelfieCapture: Boolean = false,
    val selfieAction: SelfieAction = SelfieAction.None
)

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModel(
    private val repository: CheckInRepository,
    private val settings: AttendanceSettings,
    private val timeSource: TimeSource,
    private val serviceController: ServiceController
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Re-reads the prefs-backed inputs (target, tracking start) and rolls the date window forward.
    private val refresh = MutableStateFlow(0)
    private val showSelfie = MutableStateFlow(false)
    private val selfieAction = MutableStateFlow<SelfieAction>(SelfieAction.None)

    val uiState: StateFlow<CheckInUiState> = refresh.flatMapLatest {
        val today = timeSource.today()
        val todayKey = today.format(dateFormatter)
        val trackingStart = settings.readTrackingStartOrNull()
        val yesterday = today.minusDays(1)
        val targetMs = TargetSchedule.effectiveTargetMs(settings.readSchedule(), today)

        val deficitFlow = if (trackingStart == null || trackingStart.isAfter(yesterday)) {
            flowOf(0.0)
        } else {
            repository.dailyAggregatesFlow(trackingStart.format(dateFormatter), yesterday.format(dateFormatter))
                .map { DeficitCalculator.computeDeficit(repository.summariesFrom(it), trackingStart, yesterday) }
        }

        combine(
            repository.activeSessionFlow(),
            repository.sessionsForDateFlow(todayKey),
            repository.getTodayTotalDurationFlow(todayKey),
            deficitFlow,
            combine(showSelfie, selfieAction) { show, action -> show to action }
        ) { active, sessions, total, deficit, selfie ->
            CheckInUiState(
                loading = false,
                isRunning = active != null,
                currentSessionStartTime = active?.startedAt,
                todayDateKey = todayKey,
                todaySessions = sessions,
                todayTotalDuration = total,
                dailyTargetMs = targetMs,
                deficit = deficit,
                hasEverTracked = trackingStart != null,
                showSelfieCapture = selfie.first,
                selfieAction = selfie.second
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CheckInUiState(todayDateKey = timeSource.today().format(dateFormatter))
    )

    /** Re-reads prefs-backed inputs and advances the date window (call on screen resume). */
    fun onResumed() {
        refresh.value++
    }

    fun requestCheckIn() {
        selfieAction.value = SelfieAction.CheckIn
        showSelfie.value = true
    }

    fun requestCheckOut() {
        selfieAction.value = SelfieAction.CheckOut
        showSelfie.value = true
    }

    fun dismissSelfieCapture() {
        showSelfie.value = false
        selfieAction.value = SelfieAction.None
    }

    /** Called once the auth gate (face detection or biometric fallback) has passed. */
    fun onAuthSuccess() {
        showSelfie.value = false
        when (selfieAction.value) {
            SelfieAction.CheckIn -> executeCheckIn()
            SelfieAction.CheckOut -> executeCheckOut()
            SelfieAction.None -> {}
        }
        selfieAction.value = SelfieAction.None
    }

    private fun executeCheckIn() {
        viewModelScope.launch {
            settings.seedTrackingStartIfNeeded()
            val sessionId = repository.checkIn()
            serviceController.startTimer(sessionId)
            // Tracking start may have just been seeded — refresh so hasEverTracked/target reflect it.
            refresh.value++
        }
    }

    private fun executeCheckOut() {
        viewModelScope.launch {
            val active = repository.getActiveSession() ?: return@launch
            repository.checkOut(active.id)
            serviceController.stop()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                CheckInViewModel(
                    container.repository,
                    container.settings,
                    container.timeSource,
                    container.serviceController
                )
            }
        }
    }
}

sealed class SelfieAction {
    data object None : SelfieAction()
    data object CheckIn : SelfieAction()
    data object CheckOut : SelfieAction()
}
