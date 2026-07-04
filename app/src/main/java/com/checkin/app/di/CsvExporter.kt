package com.checkin.app.di

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.checkin.app.R
import com.checkin.app.data.local.DailySummary
import com.checkin.app.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Outcome of a CSV export — a typed result so no user-facing strings live in the ViewModel. */
sealed interface ExportResult {
    data object Success : ExportResult
    data class Failure(val message: String?) : ExportResult
}

/** Writes the attendance CSV and hands it to the system share sheet. */
interface CsvExporter {
    suspend fun export(
        startKey: String,
        endKey: String,
        summaries: Map<String, DailySummary>
    ): ExportResult
}

class DefaultCsvExporter(private val context: Context) : CsvExporter {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override suspend fun export(
        startKey: String,
        endKey: String,
        summaries: Map<String, DailySummary>
    ): ExportResult = try {
        // Blocking file I/O runs off the main thread; the share Intent below stays on Main.
        val csvFile = withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val file = File(exportDir, "attendance_${startKey}_${endKey}.csv")

            FileWriter(file).use { writer ->
                writer.write("Date,First Check In,Last Check Out,Total Hours,Session Count,Status\n")

                var current = LocalDate.parse(startKey, dateFormatter)
                val end = LocalDate.parse(endKey, dateFormatter)

                while (!current.isAfter(end)) {
                    val key = current.format(dateFormatter)
                    val summary = summaries[key]

                    val firstIn = summary?.firstCheckIn?.let { TimeFormat.clock(it) } ?: ""
                    val lastOut = summary?.lastCheckOut?.let { TimeFormat.clock(it) } ?: ""
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

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.export_chooser_title))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        ExportResult.Success
    } catch (e: Exception) {
        ExportResult.Failure(e.message)
    }
}
