package com.checkin.app.ui.reports

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.AttendancePrefs
import com.checkin.app.data.AttendanceStats
import com.checkin.app.data.DeficitCalculator
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CheckInRepository
    private val prefs = application.getSharedPreferences(AttendancePrefs.NAME, Context.MODE_PRIVATE)
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

    private val _dailyTargetHours = MutableStateFlow(TargetSchedule.DEFAULT_TARGET_HOURS)
    val dailyTargetHours: StateFlow<Int> = _dailyTargetHours.asStateFlow()

    val trackingStartDate: LocalDate
        get() = AttendancePrefs.readTrackingStart(prefs)

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao, targetSchedule = { AttendancePrefs.readSchedule(prefs) })
        _dailyTargetHours.value = prefs.getInt(
            AttendancePrefs.KEY_DAILY_TARGET_HOURS,
            TargetSchedule.DEFAULT_TARGET_HOURS
        )
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val startDate = trackingStartDate
            val yesterday = LocalDate.now().minusDays(1)

            if (startDate.isAfter(yesterday)) {
                _totalDays.value = 0
                _presentDays.value = 0
                _totalHoursMs.value = 0L
                _deficit.value = 0.0
                _currentStreak.value = 0
                _bestStreak.value = 0
                return@launch
            }

            val summaries = repository.getDailySummaries(
                startDate.format(dateFormatter),
                yesterday.format(dateFormatter)
            )

            _totalDays.value = (yesterday.toEpochDay() - startDate.toEpochDay() + 1).toInt()
            _presentDays.value = AttendanceStats.presentDays(summaries)
            _totalHoursMs.value = AttendanceStats.totalWorkedMs(summaries)
            _deficit.value = DeficitCalculator.computeDeficit(summaries, startDate, yesterday)
            _currentStreak.value = AttendanceStats.currentStreak(summaries, startDate, yesterday)
            _bestStreak.value = AttendanceStats.bestStreak(summaries, startDate, yesterday)
        }
    }

    fun exportCsv(rangeType: ExportRange) {
        viewModelScope.launch {
            try {
                val (startStr, endStr) = when (rangeType) {
                    ExportRange.THIS_MONTH -> {
                        val month = YearMonth.now()
                        Pair(
                            month.atDay(1).format(dateFormatter),
                            month.atEndOfMonth().format(dateFormatter)
                        )
                    }
                    ExportRange.ALL_TIME -> Pair(
                        trackingStartDate.format(dateFormatter),
                        LocalDate.now().format(dateFormatter)
                    )
                }

                val summaries = repository.getDailySummaries(startStr, endStr)
                val app = getApplication<Application>()

                // Blocking file I/O runs off the main thread; the share Intent below stays on Main.
                val csvFile = withContext(Dispatchers.IO) {
                    val exportDir = File(app.cacheDir, "exports").also { it.mkdirs() }
                    val file = File(exportDir, "attendance_${startStr}_${endStr}.csv")

                    FileWriter(file).use { writer ->
                        writer.write("Date,First Punch In,Last Punch Out,Total Hours,Session Count,Status\n")

                        var current = LocalDate.parse(startStr, dateFormatter)
                        val end = LocalDate.parse(endStr, dateFormatter)

                        while (!current.isAfter(end)) {
                            val key = current.format(dateFormatter)
                            val summary = summaries[key]

                            val firstIn = summary?.firstPunchIn?.let { TimeFormat.clock(it) } ?: ""
                            val lastOut = summary?.lastPunchOut?.let { TimeFormat.clock(it) } ?: ""
                            val totalHrs = summary?.let {
                                String.format(Locale.US, "%.2f", it.totalDurationMs / 3600000.0)
                            } ?: "0.00"
                            val count = summary?.sessionCount?.toString() ?: "0"
                            val status = summary?.status?.name ?: "FULL_DAY_LEAVE"

                            writer.write("$key,$firstIn,$lastOut,$totalHrs,$count,$status\n")
                            current = current.plusDays(1)
                        }
                    }
                    file
                }

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

    /** Records [hours] effective from today; past days keep the target that was in effect then. */
    fun updateDailyTarget(hours: Int) {
        _dailyTargetHours.value = hours
        val updated = TargetSchedule.withChange(AttendancePrefs.readSchedule(prefs), LocalDate.now(), hours)
        prefs.edit()
            .putInt(AttendancePrefs.KEY_DAILY_TARGET_HOURS, hours)
            .putString(AttendancePrefs.KEY_TARGET_SCHEDULE, TargetSchedule.serialize(updated))
            .apply()
        loadStats()
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }
}

enum class ExportRange {
    THIS_MONTH,
    ALL_TIME
}
