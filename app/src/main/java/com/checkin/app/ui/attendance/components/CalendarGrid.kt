package com.checkin.app.ui.attendance.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
    val firstDayOfMonth = yearMonth.atDay(1)
    val startOffset = (firstDayOfMonth.dayOfWeek.value % 7) // Sun=0

    Column(modifier = Modifier.fillMaxWidth()) {
        // Day of week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
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
                        val isTracked = !date.isBefore(trackingStartDate) && !date.isAfter(today)

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
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
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
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        !isTracked -> Color.Transparent
        summary == null -> Color(0xFFF44336).copy(alpha = 0.15f) // no data = full day leave
        summary.status == AttendanceStatus.PRESENT -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        summary.status == AttendanceStatus.HALF_DAY_LEAVE -> Color(0xFFFF9800).copy(alpha = 0.15f)
        summary.status == AttendanceStatus.FULL_DAY_LEAVE -> Color(0xFFF44336).copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
