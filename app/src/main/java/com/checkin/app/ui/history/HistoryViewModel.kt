package com.checkin.app.ui.history

import android.app.Application
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.repository.CheckInRepository
import com.opencsv.CSVWriter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CheckInRepository
    val sessions: LiveData<List<CheckInSession>>

    init {
        val dao = AppDatabase.getDatabase(application).checkInSessionDao()
        repository = CheckInRepository(dao)
        sessions = repository.allSessions.asLiveData()
    }

    fun exportToCSV() {
        viewModelScope.launch {
            try {
                val allSessions = repository.getAllSessionsForExport()
                val fileName = "checkin_export_${System.currentTimeMillis()}.csv"
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                CSVWriter(FileWriter(file)).use { writer ->
                    // Write header
                    writer.writeNext(arrayOf("id", "start_timestamp", "end_timestamp", "duration_millis"))

                    // Write data
                    allSessions.forEach { session ->
                        writer.writeNext(
                            arrayOf(
                                session.id.toString(),
                                session.startTimestamp.toString(),
                                session.endTimestamp?.toString() ?: "",
                                session.durationMillis?.toString() ?: ""
                            )
                        )
                    }
                }

                // Show success toast
                Toast.makeText(
                    getApplication(),
                    "Exported to Downloads/$fileName",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    getApplication(),
                    "Export failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))

        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }

    // Aggregate sessions by date for chart (returns minutes)
    fun aggregateSessionsByDate(sessions: List<CheckInSession>): Map<String, Float> {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val aggregated = mutableMapOf<String, Float>()

        sessions.forEach { session ->
            session.durationMillis?.let { duration ->
                val date = dateFormat.format(Date(session.startTimestamp))
                val minutes = duration / (1000f * 60) // Convert to minutes instead of hours
                aggregated[date] = (aggregated[date] ?: 0f) + minutes
            }
        }

        return aggregated.toSortedMap()
    }
}
