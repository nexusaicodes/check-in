package com.checkin.app.ui.attendance.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import com.checkin.app.ui.components.charts.DonutChart
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.statusColor
import java.time.LocalDate

/** Month-summary values (all today-excluded). See [computeMonthTiles]. */
data class MonthTiles(
    val present: Int,
    val half: Int,
    val full: Int,
    val totalHoursMs: Long,
    val avgDailyMs: Long
)

/**
 * Tile values for the month card, all excluding [todayKey] (in-progress, uncounted). [full] is derived
 * by subtraction so absent tracked days count as full-day leave; the daily average divides the
 * today-excluded total by [trackedDaysInMonth], keeping every figure consistent about "today".
 */
fun computeMonthTiles(
    summaries: Map<String, DailySummary>,
    todayKey: String,
    trackedDaysInMonth: Int
): MonthTiles {
    val classified = summaries.filterKeys { it != todayKey }.values
    val present = classified.count { it.status == AttendanceStatus.PRESENT }
    val half = classified.count { it.status == AttendanceStatus.HALF_DAY_LEAVE }
    val full = (trackedDaysInMonth - present - half).coerceAtLeast(0)
    val totalHoursMs = classified.sumOf { it.totalDurationMs }
    val avgDailyMs = if (trackedDaysInMonth > 0) totalHoursMs / trackedDaysInMonth else 0L
    return MonthTiles(present, half, full, totalHoursMs, avgDailyMs)
}

/**
 * The month's split as a donut with a counted legend, over the two averages worth comparing: this
 * month against the all-time baseline. Raw totals are deliberately absent — a bare "168h" says
 * nothing without knowing how many days produced it, which the average already answers.
 */
@Composable
fun MonthSummaryCard(
    summaries: Map<String, DailySummary>,
    trackedDaysInMonth: Int,
    allTimeAvgDailyMs: Long,
    today: LocalDate,
    formatDuration: (Long) -> String
) {
    val tiles = computeMonthTiles(summaries, today.toString(), trackedDaysInMonth)
    val presentColor = statusColor(AttendanceStatus.PRESENT)
    val halfColor = statusColor(AttendanceStatus.HALF_DAY_LEAVE)
    val fullColor = statusColor(AttendanceStatus.FULL_DAY_LEAVE)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    values = listOf(tiles.present.toFloat(), tiles.half.toFloat(), tiles.full.toFloat()),
                    colors = listOf(presentColor, halfColor, fullColor),
                    contentDescription = stringResource(
                        R.string.cd_month_split,
                        tiles.present,
                        tiles.half,
                        tiles.full
                    ),
                    emptyColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(112.dp),
                    strokeWidth = 18.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$trackedDaysInMonth",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.stat_days_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegendRow(presentColor, stringResource(R.string.stat_present), tiles.present)
                    LegendRow(halfColor, stringResource(R.string.stat_half_day), tiles.half)
                    LegendRow(fullColor, stringResource(R.string.stat_full_day), tiles.full)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                AverageFigure(
                    label = stringResource(R.string.stat_avg_this_month),
                    value = formatDuration(tiles.avgDailyMs),
                    modifier = Modifier.weight(1f)
                )
                AverageFigure(
                    label = stringResource(R.string.stat_avg_all_time),
                    value = formatDuration(allTimeAvgDailyMs),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AverageFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthSummaryCardPreview() {
    CheckInAppTheme {
        val summaries = mapOf(
            "2026-06-02" to DailySummary("2026-06-02", 8 * 3_600_000L, 1, 0L, 0L, AttendanceStatus.PRESENT),
            "2026-06-03" to DailySummary("2026-06-03", 4 * 3_600_000L, 1, 0L, 0L, AttendanceStatus.HALF_DAY_LEAVE)
        )
        MonthSummaryCard(
            summaries = summaries,
            trackedDaysInMonth = 5,
            allTimeAvgDailyMs = 6 * 3_600_000L,
            today = LocalDate.of(2026, 6, 15),
            formatDuration = { "${it / 3_600_000}h" }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthSummaryCardEmptyPreview() {
    CheckInAppTheme {
        MonthSummaryCard(
            summaries = emptyMap(),
            trackedDaysInMonth = 0,
            allTimeAvgDailyMs = 0L,
            today = LocalDate.of(2026, 6, 1),
            formatDuration = { "0h" }
        )
    }
}
