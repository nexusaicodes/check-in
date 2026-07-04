package com.checkin.app.ui.punch

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.AttendancePrefs
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.service.StopwatchService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PunchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CheckInRepository
    private val prefs = application.getSharedPreferences(AttendancePrefs.NAME, Context.MODE_PRIVATE)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private val _currentSessionStartTime = MutableStateFlow<Long?>(null)
    val currentSessionStartTime: StateFlow<Long?> = _currentSessionStartTime.asStateFlow()

    private val _deficit = MutableStateFlow(0.0)
    val deficit: StateFlow<Double> = _deficit.asStateFlow()

    private val _showSelfieCapture = MutableStateFlow(false)
    val showSelfieCapture: StateFlow<Boolean> = _showSelfieCapture.asStateFlow()

    private val _selfieAction = MutableStateFlow<SelfieAction>(SelfieAction.None)
    val selfieAction: StateFlow<SelfieAction> = _selfieAction.asStateFlow()

    private var timerJob: Job? = null

    val todayDateKey: String = LocalDate.now().format(dateFormatter)

    val todaySessions: StateFlow<List<CheckInSession>>
    val todayTotalDuration: StateFlow<Long>

    /** Today's target ("present" mark) from the effective-target schedule. */
    val dailyTargetMs: Long
        get() = TargetSchedule.effectiveTargetMs(AttendancePrefs.readSchedule(prefs), LocalDate.now())

    val trackingStartDate: LocalDate
        get() = AttendancePrefs.readTrackingStart(prefs)

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao, targetSchedule = { AttendancePrefs.readSchedule(prefs) })

        todaySessions = repository.getTodaySessionsFlow(todayDateKey)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        todayTotalDuration = repository.getTodayTotalDurationFlow(todayDateKey)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        checkForActiveSession()
        loadDeficit()
    }

    private fun checkForActiveSession() {
        viewModelScope.launch {
            val activeSession = repository.getActiveSession()
            if (activeSession != null) {
                _isRunning.value = true
                _currentSessionId.value = activeSession.id
                _currentSessionStartTime.value = activeSession.startedAt
                startTimer(activeSession.startedAt)
            }
        }
    }

    private fun loadDeficit() {
        viewModelScope.launch {
            _deficit.value = repository.calculateDeficit(trackingStartDate)
        }
    }

    fun requestPunchIn() {
        _selfieAction.value = SelfieAction.PunchIn
        _showSelfieCapture.value = true
    }

    fun requestPunchOut() {
        _selfieAction.value = SelfieAction.PunchOut
        _showSelfieCapture.value = true
    }

    fun dismissSelfieCapture() {
        _showSelfieCapture.value = false
        _selfieAction.value = SelfieAction.None
    }

    /** Called once the auth gate (face detection or biometric fallback) has passed. */
    fun onAuthSuccess() {
        _showSelfieCapture.value = false
        when (_selfieAction.value) {
            SelfieAction.PunchIn -> executePunchIn()
            SelfieAction.PunchOut -> executePunchOut()
            SelfieAction.None -> {}
        }
        _selfieAction.value = SelfieAction.None
    }

    private fun executePunchIn() {
        viewModelScope.launch {
            seedTrackingStartIfNeeded()
            val sessionId = repository.punchIn()
            _currentSessionId.value = sessionId
            _isRunning.value = true
            val startTime = System.currentTimeMillis()
            _currentSessionStartTime.value = startTime
            _elapsedTime.value = 0L

            val intent = Intent(getApplication(), StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_START
                putExtra(StopwatchService.EXTRA_SESSION_ID, sessionId)
            }
            ContextCompat.startForegroundService(getApplication(), intent)

            startTimer(startTime)
        }
    }

    private fun executePunchOut() {
        viewModelScope.launch {
            _currentSessionId.value?.let { sessionId ->
                repository.punchOut(sessionId)
                _isRunning.value = false
                _elapsedTime.value = 0L
                _currentSessionId.value = null
                _currentSessionStartTime.value = null
                timerJob?.cancel()
                timerJob = null

                val intent = Intent(getApplication(), StopwatchService::class.java).apply {
                    action = StopwatchService.ACTION_STOP
                }
                getApplication<Application>().startService(intent)

                loadDeficit()
            }
        }
    }

    /** Tracking begins at the first authenticated punch-in, anchoring the target schedule there. */
    private fun seedTrackingStartIfNeeded() {
        if (prefs.getString(AttendancePrefs.KEY_TRACKING_START_DATE, null) != null) return
        val today = LocalDate.now()
        val targetHours = prefs.getInt(
            AttendancePrefs.KEY_DAILY_TARGET_HOURS,
            TargetSchedule.DEFAULT_TARGET_HOURS
        )
        val seeded = listOf(TargetSchedule.Entry(today, targetHours))
        prefs.edit()
            .putString(AttendancePrefs.KEY_TRACKING_START_DATE, today.format(dateFormatter))
            .putString(AttendancePrefs.KEY_TARGET_SCHEDULE, TargetSchedule.serialize(seeded))
            .apply()
    }

    private fun startTimer(startTimestamp: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                _elapsedTime.value = System.currentTimeMillis() - startTimestamp
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

sealed class SelfieAction {
    data object None : SelfieAction()
    data object PunchIn : SelfieAction()
    data object PunchOut : SelfieAction()
}
