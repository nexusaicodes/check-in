package com.checkin.app.ui.reports

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CheckInRepository
    private val prefs = application.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val _deficit = MutableStateFlow(0.0)
    val deficit: StateFlow<Double> = _deficit.asStateFlow()

    private val _totalDays = MutableStateFlow(0)
    val totalDays: StateFlow<Int> = _totalDays.asStateFlow()

    private val _presentDays = MutableStateFlow(0)
    val presentDays: StateFlow<Int> = _presentDays.asStateFlow()

    private val _totalHoursMs = MutableStateFlow(0L)
    val totalHoursMs: StateFlow<Long> = _totalHoursMs.asStateFlow()

    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _bestStreak = MutableStateFlow(0)
    val bestStreak: StateFlow<Int> = _bestStreak.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private val _dailyTargetHours = MutableStateFlow(2)
    val dailyTargetHours: StateFlow<Int> = _dailyTargetHours.asStateFlow()

    val trackingStartDate: LocalDate
        get() {
            val stored = prefs.getString("tracking_start_date", null)
            return if (stored != null) LocalDate.parse(stored, dateFormatter)
            else LocalDate.now()
        }

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao)
        _dailyTargetHours.value = prefs.getInt("daily_target_hours", 2)
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val startDate = trackingStartDate
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)

            if (startDate.isAfter(yesterday)) {
                _totalDays.value = 0
                _deficit.value = 0.0
                return@launch
            }

            val startStr = startDate.format(dateFormatter)
            val endStr = yesterday.format(dateFormatter)
            val summaries = repository.getDailySummaries(startStr, endStr)

            val dayCount = (yesterday.toEpochDay() - startDate.toEpochDay() + 1).toInt()
            _totalDays.value = dayCount
            _presentDays.value = summaries.values.count { it.status == AttendanceStatus.PRESENT }
            _totalHoursMs.value = summaries.values.sumOf { it.totalDurationMs }
            _deficit.value = repository.calculateDeficit(startDate)

            // Calculate streaks
            var current = 0
            var best = 0
            var tempStreak = 0
            var d = yesterday
            // Current streak: count from yesterday backwards
            while (!d.isBefore(startDate)) {
                val key = d.format(dateFormatter)
                val summary = summaries[key]
                if (summary != null && summary.status == AttendanceStatus.PRESENT) {
                    current++
                    d = d.minusDays(1)
                } else break
            }
            // Best streak: iterate all days
            d = startDate
            while (!d.isAfter(yesterday)) {
                val key = d.format(dateFormatter)
                val summary = summaries[key]
                if (summary != null && summary.status == AttendanceStatus.PRESENT) {
                    tempStreak++
                    if (tempStreak > best) best = tempStreak
                } else {
                    tempStreak = 0
                }
                d = d.plusDays(1)
            }
            _currentStreak.value = current
            _bestStreak.value = best
        }
    }

    fun exportCsv(rangeType: ExportRange) {
        viewModelScope.launch {
            try {
                val (startStr, endStr) = when (rangeType) {
                    ExportRange.THIS_MONTH -> {
                        val month = YearMonth.now()
                        Pair(month.atDay(1).format(dateFormatter), month.atEndOfMonth().format(dateFormatter))
                    }
                    ExportRange.ALL_TIME -> {
                        Pair(trackingStartDate.format(dateFormatter), LocalDate.now().format(dateFormatter))
                    }
                }

                val summaries = repository.getDailySummaries(startStr, endStr)
                val app = getApplication<Application>()
                val exportDir = File(app.cacheDir, "exports").also { it.mkdirs() }
                val csvFile = File(exportDir, "attendance_${startStr}_${endStr}.csv")

                FileWriter(csvFile).use { writer ->
                    writer.write("Date,First Punch In,Last Punch Out,Total Hours,Session Count,Status\n")

                    var current = LocalDate.parse(startStr, dateFormatter)
                    val end = LocalDate.parse(endStr, dateFormatter)
                    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

                    while (!current.isAfter(end)) {
                        val key = current.format(dateFormatter)
                        val summary = summaries[key]

                        val firstIn = summary?.firstPunchIn?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
                        } ?: ""
                        val lastOut = summary?.lastPunchOut?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
                        } ?: ""
                        val totalHrs = summary?.let {
                            String.format(Locale.US, "%.2f", it.totalDurationMs / 3600000.0)
                        } ?: "0.00"
                        val count = summary?.sessionCount?.toString() ?: "0"
                        val status = summary?.status?.name ?: "FULL_DAY_LEAVE"

                        writer.write("$key,$firstIn,$lastOut,$totalHrs,$count,$status\n")
                        current = current.plusDays(1)
                    }
                }

                // Share via intent
                val uri = FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.fileprovider",
                    csvFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(Intent.createChooser(shareIntent, "Export Attendance").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })

                _exportStatus.value = "Exported successfully"
            } catch (e: Exception) {
                _exportStatus.value = "Export failed: ${e.message}"
            }
        }
    }

    fun updateDailyTarget(hours: Int) {
        _dailyTargetHours.value = hours
        prefs.edit().putInt("daily_target_hours", hours).apply()
    }

    fun updateTrackingStartDate(date: LocalDate) {
        prefs.edit().putString("tracking_start_date", date.format(dateFormatter)).apply()
        loadStats()
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }

    fun formatDurationShort(millis: Long): String {
        val totalMinutes = millis / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
    }
}

enum class ExportRange {
    THIS_MONTH,
    ALL_TIME
}
