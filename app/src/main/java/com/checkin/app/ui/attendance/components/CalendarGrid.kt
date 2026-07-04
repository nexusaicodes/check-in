package com.checkin.app.ui.attendance.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.statusColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    summaries: Map<String, DailySummary>,
    selectedDateKey: String?,
    trackingStartDate: LocalDate,
    onDayClick: (String) -> Unit
) {
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val firstDayOfMonth = yearMonth.atDay(1)
    val startOffset = (firstDayOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val weekDays = (0L..6L).map { firstDayOfWeek.plus(it) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Day-of-week headers, localized and ordered from the locale's first day of week.
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Calendar cells
        val totalDays = yearMonth.lengthOfMonth()
        val totalCells = startOffset + totalDays
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startOffset + 1

                    if (dayNum in 1..totalDays) {
                        val date = yearMonth.atDay(dayNum)
                        val key = date.format(dateFormatter)
                        val summary = summaries[key]
                        val isSelected = key == selectedDateKey
                        val isToday = date == today
                        // Today is excluded from classification (in-progress); only past tracked
                        // days with no sessions are shown as leave.
                        val isTracked = !date.isBefore(trackingStartDate) && date.isBefore(today)

                        DayCell(
                            day = dayNum,
                            summary = summary,
                            isSelected = isSelected,
                            isToday = isToday,
                            isTracked = isTracked,
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(key) }
                        )
                    } else {
                        // Empty cell
                        Box(modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    summary: DailySummary?,
    isSelected: Boolean,
    isToday: Boolean,
    isTracked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // A tracked day with no sessions is treated as a full day of leave.
    val effectiveStatus = when {
        !isTracked -> null
        summary == null -> AttendanceStatus.FULL_DAY_LEAVE
        else -> summary.status
    }

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        effectiveStatus != null -> statusColor(effectiveStatus).copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Status is color-coded; announce it so it is not conveyed by color alone.
    val statusLabel = when (effectiveStatus) {
        AttendanceStatus.PRESENT -> stringResource(R.string.status_present)
        AttendanceStatus.HALF_DAY_LEAVE -> stringResource(R.string.status_half_day)
        AttendanceStatus.FULL_DAY_LEAVE -> stringResource(R.string.status_full_day)
        null -> null
    }
    val cellDescription = if (statusLabel != null) {
        stringResource(R.string.cd_day_status, day, statusLabel)
    } else {
        day.toString()
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cellDescription },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics { } // parent's contentDescription conveys the cell
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            // Small dot indicator for today
            if (isToday) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(2.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarGridPreview() {
    CheckInAppTheme {
        val month = YearMonth.of(2026, 6)
        val summaries = mapOf(
            "2026-06-02" to DailySummary("2026-06-02", 8 * 3_600_000L, 1, 0L, 0L, AttendanceStatus.PRESENT),
            "2026-06-04" to DailySummary("2026-06-04", 4 * 3_600_000L, 1, 0L, 0L, AttendanceStatus.HALF_DAY_LEAVE),
            "2026-06-05" to DailySummary("2026-06-05", 3_600_000L, 1, 0L, 0L, AttendanceStatus.FULL_DAY_LEAVE)
        )
        CalendarGrid(
            yearMonth = month,
            summaries = summaries,
            selectedDateKey = "2026-06-04",
            trackingStartDate = month.atDay(1),
            onDayClick = {}
        )
    }
}
