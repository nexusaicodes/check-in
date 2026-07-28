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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import com.checkin.app.ui.components.charts.DonutChart
import com.checkin.app.ui.components.charts.DonutChartDefaults
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.statusColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The month's split as a donut with a counted legend, over the two averages worth comparing: the
 * displayed month against the all-time baseline. Raw totals are deliberately absent — a bare "168h"
 * says nothing without knowing how many days produced it, which the average already answers.
 *
 * [month] is the month the user has navigated to, and it names the first average. The card carries no
 * heading of its own — its height is a layout constant the calendar grid is sized against — so that
 * label is the only thing telling the reader which month these figures are for.
 */
@Composable
fun MonthSummaryCard(
    summaries: Map<String, DailySummary>,
    month: YearMonth,
    trackedDaysInMonth: Int,
    allTimeAvgDailyMs: Long,
    today: LocalDate,
    formatDuration: (Long) -> String,
) {
    val tiles = computeMonthTiles(summaries, today.toString(), trackedDaysInMonth)
    val presentColor = statusColor(AttendanceStatus.PRESENT)
    val halfColor = statusColor(AttendanceStatus.HALF_DAY_LEAVE)
    val fullColor = statusColor(AttendanceStatus.FULL_DAY_LEAVE)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
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
                        tiles.full,
                    ),
                    emptyColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(DonutChartDefaults.size()),
                ) {
                    // DonutChart bounds this to the ring's clear middle; the caption wraps to fit it.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$trackedDaysInMonth",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.stat_days_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            // At the largest font scales the ring stops growing, so the caption
                            // gives way rather than being clipped mid-word.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
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
                    // Abbreviated with the year: it stays no longer than the label it replaced, so
                    // it can't wrap and push the card past the height the grid is measured against.
                    label = stringResource(R.string.stat_avg_this_month, monthLabel(month)),
                    value = formatDuration(tiles.avgDailyMs),
                    modifier = Modifier.weight(1f),
                )
                AverageFigure(
                    label = stringResource(R.string.stat_avg_all_time),
                    value = formatDuration(allTimeAvgDailyMs),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** "Jul 2026" — the year is always shown, since the calendar navigates across years too. */
private fun monthLabel(month: YearMonth): String {
    val locale = Locale.getDefault()
    return "${month.month.getDisplayName(TextStyle.SHORT, locale)} ${month.year}"
}

@Composable
private fun LegendRow(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AverageFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
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
            "2026-06-03" to DailySummary("2026-06-03", 4 * 3_600_000L, 1, 0L, 0L, AttendanceStatus.HALF_DAY_LEAVE),
        )
        MonthSummaryCard(
            summaries = summaries,
            month = YearMonth.of(2026, 6),
            trackedDaysInMonth = 5,
            allTimeAvgDailyMs = 6 * 3_600_000L,
            today = LocalDate.of(2026, 6, 15),
            formatDuration = { "${it / 3_600_000}h" },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthSummaryCardEmptyPreview() {
    CheckInAppTheme {
        MonthSummaryCard(
            summaries = emptyMap(),
            month = YearMonth.of(2026, 6),
            trackedDaysInMonth = 0,
            allTimeAvgDailyMs = 0L,
            today = LocalDate.of(2026, 6, 1),
            formatDuration = { "0h" },
        )
    }
}
