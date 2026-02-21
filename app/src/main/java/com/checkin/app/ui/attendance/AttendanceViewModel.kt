package com.checkin.app.ui.attendance

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.DailySummary
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CheckInRepository
    private val prefs = application.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _dailySummaries = MutableStateFlow<Map<String, DailySummary>>(emptyMap())
    val dailySummaries: StateFlow<Map<String, DailySummary>> = _dailySummaries.asStateFlow()

    private val _selectedDaySessions = MutableStateFlow<List<CheckInSession>>(emptyList())
    val selectedDaySessions: StateFlow<List<CheckInSession>> = _selectedDaySessions.asStateFlow()

    private val _selectedDateKey = MutableStateFlow<String?>(null)
    val selectedDateKey: StateFlow<String?> = _selectedDateKey.asStateFlow()

    private val _deficit = MutableStateFlow(0.0)
    val deficit: StateFlow<Double> = _deficit.asStateFlow()

    val trackingStartDate: LocalDate
        get() {
            val stored = prefs.getString("tracking_start_date", null)
            return if (stored != null) LocalDate.parse(stored, dateFormatter)
            else LocalDate.now()
        }

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao)
        loadMonth()
        loadDeficit()
    }

    fun previousMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
        _selectedDateKey.value = null
        _selectedDaySessions.value = emptyList()
        loadMonth()
    }

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
        _selectedDateKey.value = null
        _selectedDaySessions.value = emptyList()
        loadMonth()
    }

    fun selectDay(dateKey: String) {
        if (_selectedDateKey.value == dateKey) {
            _selectedDateKey.value = null
            _selectedDaySessions.value = emptyList()
        } else {
            _selectedDateKey.value = dateKey
            viewModelScope.launch {
                _selectedDaySessions.value = repository.getSessionsByDate(dateKey)
            }
        }
    }

    private fun loadMonth() {
        viewModelScope.launch {
            val month = _currentMonth.value
            val startDate = month.atDay(1).format(dateFormatter)
            val endDate = month.atEndOfMonth().format(dateFormatter)
            _dailySummaries.value = repository.getDailySummaries(startDate, endDate)
        }
    }

    private fun loadDeficit() {
        viewModelScope.launch {
            _deficit.value = repository.calculateDeficit(trackingStartDate)
        }
    }

    fun formatDurationShort(millis: Long): String {
        val totalMinutes = millis / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
    }
}
