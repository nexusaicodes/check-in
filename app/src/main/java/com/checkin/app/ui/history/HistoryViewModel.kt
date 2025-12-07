package com.checkin.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CheckInRepository
    val sessions: LiveData<List<CheckInSession>>
    val pagedSessions: Flow<PagingData<CheckInSession>>

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao)
        sessions = repository.allSessions.asLiveData()
        pagedSessions = repository.getCompletedSessionsPaged()
            .cachedIn(viewModelScope)
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDuration(millis: Long?): String? {
        val ms = millis ?: 0
        if (ms.toInt() == 0) {
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
