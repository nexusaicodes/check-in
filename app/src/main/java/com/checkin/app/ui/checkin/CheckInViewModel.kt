package com.checkin.app.ui.checkin

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.service.StopwatchService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CheckInViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CheckInRepository
    private val _elapsedTime = MutableLiveData(0L) // milliseconds
    private val _isRunning = MutableLiveData(false)
    private val _currentSessionId = MutableLiveData<Long?>(null)
    private var startTimestamp: Long = 0L
    private var timerJob: Job? = null

    val elapsedTime: LiveData<Long> = _elapsedTime
    val isRunning: LiveData<Boolean> = _isRunning

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
                startTimestamp = activeSession.startTimestamp
                // Start the UI timer
                startTimer()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTimestamp
                _elapsedTime.postValue(elapsed)
                delay(100) // Update every 100ms for smooth UI
            }
        }
    }

    fun startStopwatch() {
        viewModelScope.launch {
            val sessionId = repository.startSession()
            _currentSessionId.value = sessionId
            _isRunning.value = true
            startTimestamp = System.currentTimeMillis()
            _elapsedTime.value = 0L

            // Start foreground service
            val intent = Intent(getApplication(), StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_START
                putExtra(StopwatchService.EXTRA_SESSION_ID, sessionId)
            }
            ContextCompat.startForegroundService(getApplication(), intent)

            // Start UI timer
            startTimer()
        }
    }

    fun stopStopwatch() {
        viewModelScope.launch {
            _currentSessionId.value?.let { sessionId ->
                repository.stopSession(sessionId, System.currentTimeMillis())
                _isRunning.value = false
                _elapsedTime.value = 0L
                _currentSessionId.value = null

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
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
