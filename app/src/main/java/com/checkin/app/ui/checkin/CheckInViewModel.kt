package com.checkin.app.ui.checkin

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.service.StopwatchService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class CheckInViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CheckInRepository
    private val _elapsedTime = MutableStateFlow(0L) // milliseconds
    private val _isRunning = MutableStateFlow(false)
    private val _currentSessionId = MutableStateFlow<Long?>(null)
    private val _showDescriptionDialog = MutableStateFlow(false)
    private val _sessionDescription = MutableStateFlow<String?>(null)
    private var startTimestamp: Long = 0L
    private var timerJob: Job? = null

    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    val showDescriptionDialog: StateFlow<Boolean> = _showDescriptionDialog.asStateFlow()
    val sessionDescription: StateFlow<String?> = _sessionDescription.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao)
        checkForActiveSession()
    }

    private fun checkForActiveSession() {
        viewModelScope.launch {
            val activeSession = repository.getActiveSession()
            if (activeSession != null) {
                _isRunning.value = true
                _currentSessionId.value = activeSession.id
                startTimestamp = activeSession.startedAt
                // Display the current description (which may be original or already transformed)
                _sessionDescription.value = activeSession.description
                startTimer()
            }
        }
    }

    fun showDescriptionDialog() {
        _showDescriptionDialog.value = true
    }

    fun hideDescriptionDialog() {
        _showDescriptionDialog.value = false
    }

    private suspend fun transformDescriptionAsync(description: String): String {
        // Simulate async transformation with delay
        delay(2000) // 2 second delay to emulate processing
        // Transform: convert to uppercase
        return description.uppercase()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTimestamp
                _elapsedTime.value = elapsed
                delay(100) // Update every 100ms for smooth UI
            }
        }
    }

    fun startStopwatch(description: String) {
        viewModelScope.launch {
            // STEP 1: Store original description immediately to session
            val sessionId = repository.startSession(description)
            _currentSessionId.value = sessionId
            _isRunning.value = true
            startTimestamp = System.currentTimeMillis()
            _elapsedTime.value = 0L
            // Display original description immediately in UI
            _sessionDescription.value = description

            // Start foreground service
            val intent = Intent(getApplication(), StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_START
                putExtra(StopwatchService.EXTRA_SESSION_ID, sessionId)
            }
            ContextCompat.startForegroundService(getApplication(), intent)

            // Start UI timer
            startTimer()

            // STEP 2: Trigger async transformation function
            viewModelScope.launch {
                val transformedDescription = transformDescriptionAsync(description)

                // Update the description in the database
                repository.updateSessionDescription(sessionId, transformedDescription)

                // Update the UI if this session is still active
                if (_currentSessionId.value == sessionId) {
                    _sessionDescription.value = transformedDescription
                }
            }
        }
    }

    fun stopStopwatch() {
        viewModelScope.launch {
            _currentSessionId.value?.let { sessionId ->
                repository.stopSession(sessionId, System.currentTimeMillis())
                _isRunning.value = false
                _elapsedTime.value = 0L
                _currentSessionId.value = null
                _sessionDescription.value = null

                // Stop timer job
                timerJob?.cancel()
                timerJob = null

                // Stop foreground service
                val intent = Intent(getApplication(), StopwatchService::class.java).apply {
                    action = StopwatchService.ACTION_STOP
                }
                getApplication<Application>().startService(intent)
            }
        }
    }

    fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
