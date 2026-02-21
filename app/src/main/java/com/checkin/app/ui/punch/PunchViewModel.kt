package com.checkin.app.ui.punch

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.CheckInSession
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
import java.util.Locale

class PunchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CheckInRepository
    private val prefs = application.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
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

    val dailyTargetMs: Long
        get() {
            val hours = prefs.getInt("daily_target_hours", 2)
            return hours * 60 * 60 * 1000L
        }

    val trackingStartDate: LocalDate
        get() {
            val stored = prefs.getString("tracking_start_date", null)
            return if (stored != null) LocalDate.parse(stored, dateFormatter)
            else LocalDate.now()
        }

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao)

        todaySessions = repository.getTodaySessionsFlow(todayDateKey)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        todayTotalDuration = repository.getTodayTotalDurationFlow(todayDateKey)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        checkForActiveSession()
        loadDeficit()

        // Ensure tracking start date is set
        if (prefs.getString("tracking_start_date", null) == null) {
            prefs.edit().putString("tracking_start_date", todayDateKey).apply()
        }
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

    fun onSelfieCaptured(filePath: String) {
        _showSelfieCapture.value = false
        when (_selfieAction.value) {
            SelfieAction.PunchIn -> executePunchIn(filePath)
            SelfieAction.PunchOut -> executePunchOut(filePath)
            SelfieAction.None -> {}
        }
        _selfieAction.value = SelfieAction.None
    }

    private fun executePunchIn(selfiePath: String) {
        viewModelScope.launch {
            val sessionId = repository.punchIn(selfiePath)
            _currentSessionId.value = sessionId
            _isRunning.value = true
            val startTime = System.currentTimeMillis()
            _currentSessionStartTime.value = startTime
            _elapsedTime.value = 0L

            // Start foreground service
            val intent = Intent(getApplication(), StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_START
                putExtra(StopwatchService.EXTRA_SESSION_ID, sessionId)
            }
            ContextCompat.startForegroundService(getApplication(), intent)

            startTimer(startTime)
        }
    }

    private fun executePunchOut(selfiePath: String) {
        viewModelScope.launch {
            _currentSessionId.value?.let { sessionId ->
                repository.punchOut(sessionId, selfiePath)
                _isRunning.value = false
                _elapsedTime.value = 0L
                _currentSessionId.value = null
                _currentSessionStartTime.value = null
                timerJob?.cancel()
                timerJob = null

                // Stop foreground service
                val intent = Intent(getApplication(), StopwatchService::class.java).apply {
                    action = StopwatchService.ACTION_STOP
                }
                getApplication<Application>().startService(intent)

                // Refresh deficit
                loadDeficit()
            }
        }
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

    fun todayStatus(totalMs: Long): AttendanceStatus {
        val twoHours = 2 * 60 * 60 * 1000L
        val oneHour = 1 * 60 * 60 * 1000L
        return when {
            totalMs >= twoHours -> AttendanceStatus.PRESENT
            totalMs >= oneHour -> AttendanceStatus.HALF_DAY_LEAVE
            else -> AttendanceStatus.FULL_DAY_LEAVE
        }
    }

    fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationShort(millis: Long): String {
        val totalMinutes = millis / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
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
