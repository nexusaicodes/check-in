package com.checkin.app.ui.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.ui.attendance.components.CalendarGrid
import com.checkin.app.ui.attendance.components.MonthSummaryCard
import com.checkin.app.util.TimeFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AttendanceScreen(viewModel: AttendanceViewModel = viewModel()) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val summaries by viewModel.dailySummaries.collectAsState()
    val selectedDateKey by viewModel.selectedDateKey.collectAsState()
    val selectedDaySessions by viewModel.selectedDaySessions.collectAsState()
    val deficit by viewModel.deficit.collectAsState()

    val today = LocalDate.now()
    val trackingStart = viewModel.trackingStartDate
    val monthStart = currentMonth.atDay(1)
    val monthEnd = currentMonth.atEndOfMonth()
    val effectiveStart = if (trackingStart.isAfter(monthStart)) trackingStart else monthStart
    // Exclude today — the in-progress day never counts, matching the deficit/stats convention.
    val effectiveEnd = if (today.isBefore(monthEnd)) today.minusDays(1) else monthEnd
    val trackedDays = if (!effectiveStart.isAfter(effectiveEnd)) {
        (effectiveEnd.toEpochDay() - effectiveStart.toEpochDay() + 1).toInt()
    } else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month selector
        item {
            Spacer(modifier = Modifier.height(16.dp))
            MonthSelector(
                currentMonth = currentMonth,
                onPrevious = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() }
            )
        }

        // Calendar grid
        item {
            CalendarGrid(
                yearMonth = currentMonth,
                summaries = summaries,
                selectedDateKey = selectedDateKey,
                trackingStartDate = trackingStart,
                onDayClick = { viewModel.selectDay(it) }
            )
        }

        // Day detail (if a day is selected)
        if (selectedDateKey != null && selectedDaySessions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.day_detail_title, selectedDateKey ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(selectedDaySessions.filter { it.stoppedAt != null }, key = { it.id }) { session ->
                DayDetailRow(session, TimeFormat::durationShort)
            }
        }

        // Monthly summary card
        item {
            MonthSummaryCard(
                summaries = summaries,
                trackedDaysInMonth = trackedDays,
                deficit = deficit,
                formatDuration = TimeFormat::durationShort
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun MonthSelector(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month)
            )
        }
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.US)} ${currentMonth.year}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month)
            )
        }
    }
}

@Composable
private fun DayDetailRow(
    session: CheckInSession,
    formatDuration: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${TimeFormat.clock(session.startedAt)} - ${session.stoppedAt?.let { TimeFormat.clock(it) } ?: ""}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = session.duration?.let { formatDuration(it) } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
