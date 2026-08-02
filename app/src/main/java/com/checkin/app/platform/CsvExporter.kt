package com.checkin.app.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.checkin.app.R
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Decimal hours is the CSV's unit, so the divisor is a Double. */
private const val MILLIS_PER_HOUR = 3_600_000.0

/** Outcome of a CSV export — a typed result so no user-facing strings live in the ViewModel. */
sealed interface ExportResult {
    data object Success : ExportResult

    /** The requested range holds no completed day. Not a failure — there is simply nothing to write. */
    data object Nothing : ExportResult
    data class Failure(val message: String?) : ExportResult
}

/** Writes the sessions CSV and hands it to the system share sheet. */
interface CsvExporter {
    suspend fun export(startKey: String, endKey: String, summaries: Map<String, DailyAggregate>): ExportResult
}

class DefaultCsvExporter(private val context: Context) : CsvExporter {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Every failure mode here — file I/O, a FileProvider misconfiguration, no app able to receive
    // the share — is reported to the user the same way, as a Failure carrying the message. Catching
    // each separately would produce identical branches.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun export(
        startKey: String,
        endKey: String,
        summaries: Map<String, DailyAggregate>,
    ): ExportResult = try {
        // Blocking file I/O runs off the main thread; the share Intent stays on Main.
        val csvFile = withContext(Dispatchers.IO) { writeCsv(startKey, endKey, summaries) }
        share(csvFile)
        ExportResult.Success
    } catch (e: Exception) {
        ExportResult.Failure(e.message)
    }

    /**
     * Writes one row per day across the range, gap-filling days with no sessions as zeros.
     *
     * There is deliberately no Status column. Any wording for one would be a verdict on the day, and
     * this is the one artifact that leaves the device — zero hours and zero sessions already say a
     * day had nothing recorded, without grading it.
     */
    private fun writeCsv(startKey: String, endKey: String, summaries: Map<String, DailyAggregate>): File {
        val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(exportDir, "checkin_${startKey}_$endKey.csv")

        FileWriter(file).use { writer ->
            writer.write("Date,First Check In,Last Check Out,Total Hours,Session Count\n")

            var current = LocalDate.parse(startKey, dateFormatter)
            val end = LocalDate.parse(endKey, dateFormatter)
            while (!current.isAfter(end)) {
                val key = current.format(dateFormatter)
                writer.write(row(key, summaries[key]))
                current = current.plusDays(1)
            }
        }
        return file
    }

    private fun row(key: String, summary: DailyAggregate?): String {
        val firstIn = summary?.firstCheckIn?.let { TimeFormat.clock(it) } ?: ""
        val lastOut = summary?.lastCheckOut?.let { TimeFormat.clock(it) } ?: ""
        val totalHrs = summary
            ?.let { String.format(Locale.US, "%.2f", it.totalDurationMs / MILLIS_PER_HOUR) }
            ?: "0.00"
        val count = summary?.sessionCount?.toString() ?: "0"
        return "$key,$firstIn,$lastOut,$totalHrs,$count\n"
    }

    private fun share(csvFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.export_chooser_title))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}
