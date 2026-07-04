package com.checkin.app.ui.attendance

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.ui.attendance.components.CalendarGrid
import com.checkin.app.ui.attendance.components.MonthSummaryCard
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.util.TimeFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AttendanceScreen(
    innerPadding: PaddingValues,
    viewModel: AttendanceViewModel = viewModel(factory = AttendanceViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Roll the date window / re-read tracking start when the screen resumes.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    val widthSizeClass = calculateWindowSizeClass(LocalContext.current as Activity).widthSizeClass
    val topPad = innerPadding.calculateTopPadding() + 8.dp
    val bottomPad = innerPadding.calculateBottomPadding() + 8.dp
    val hasDetail = uiState.selectedDateKey != null &&
        uiState.selectedDaySessions.any { it.stoppedAt != null }

    if (widthSizeClass == WindowWidthSizeClass.Expanded) {
        // Two-pane: calendar + summary on the left, the selected day's detail on the right.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                calendarItems(uiState, viewModel)
                monthSummaryItem(uiState)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasDetail) {
                    dayDetailItems(uiState)
                } else {
                    item {
                        EmptyState(
                            icon = Icons.Default.Event,
                            title = stringResource(R.string.empty_day_detail_title),
                            message = stringResource(R.string.empty_day_detail_message)
                        )
                    }
                }
            }
        }
    } else {
        ConstrainedContent {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                calendarItems(uiState, viewModel)
                if (hasDetail) {
                    dayDetailItems(uiState)
                }
                monthSummaryItem(uiState)
            }
        }
    }
}

private fun LazyListScope.calendarItems(uiState: AttendanceUiState, viewModel: AttendanceViewModel) {
    item {
        MonthSelector(
            currentMonth = uiState.currentMonth,
            onPrevious = { viewModel.previousMonth() },
            onNext = { viewModel.nextMonth() }
        )
    }
    item {
        CalendarGrid(
            yearMonth = uiState.currentMonth,
            summaries = uiState.summaries,
            selectedDateKey = uiState.selectedDateKey,
            trackingStartDate = uiState.trackingStartDate,
            onDayClick = { viewModel.selectDay(it) }
        )
    }
}

private fun LazyListScope.monthSummaryItem(uiState: AttendanceUiState) {
    item {
        MonthSummaryCard(
            summaries = uiState.summaries,
            trackedDaysInMonth = uiState.trackedDaysInMonth,
            deficit = uiState.deficit,
            formatDuration = TimeFormat::durationShort
        )
    }
}

private fun LazyListScope.dayDetailItems(uiState: AttendanceUiState) {
    item {
        Text(
            text = stringResource(R.string.day_detail_title, uiState.selectedDateKey ?: ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
    items(uiState.selectedDaySessions.filter { it.stoppedAt != null }, key = { it.id }) { session ->
        DayDetailRow(session, TimeFormat::durationShort)
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
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
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
