package com.checkin.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryUiState(
    val sessions: List<CheckInSession> = emptyList(),
    val isLoading: Boolean = true,
    val displayCount: Int = 10,
    val error: String? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CheckInRepository

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val initialDisplayCount = 10
    private val incrementCount = 10

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao)

        // Observe database changes with Flow - instant updates!
        viewModelScope.launch {
            repository.getCompletedSessionsFlow(limit = 100)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
                .collect { allSessions ->
                    _uiState.value = _uiState.value.copy(
                        sessions = allSessions,
                        isLoading = false,
                        error = null
                    )
                }
        }
    }

    fun loadMore() {
        val currentCount = _uiState.value.displayCount
        _uiState.value = _uiState.value.copy(
            displayCount = currentCount + incrementCount
        )
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDuration(millis: Long?): String? {
        val ms = millis ?: 0
        if (ms == 0L) {
            return null
        }
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))

        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm %ds", minutes, seconds)
            else -> String.format(Locale.getDefault(), "%ds", seconds)
        }
    }
}
