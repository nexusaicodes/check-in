package com.checkin.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.di.ExportResult
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.util.TimeFormat
import java.util.Locale

@Composable
fun ReportsScreen(
    innerPadding: PaddingValues,
    viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    // Surface each export outcome once as an auto-dismissing snackbar. The event flow is
    // non-replaying, so a config-change re-collect can't re-show a past result.
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { event ->
            val message = when (event) {
                ExportResult.Success -> context.getString(R.string.export_success)
                is ExportResult.Failure -> context.getString(R.string.export_failed, event.message ?: "")
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overall stats
        item {
            if (uiState.totalDays > 0) {
                OverallStatsCard(
                    startDate = uiState.trackingStartDate.toString(),
                    totalDays = uiState.totalDays,
                    presentDays = uiState.presentDays,
                    totalHours = TimeFormat.durationShort(uiState.totalHoursMs),
                    currentStreak = uiState.currentStreak,
                    bestStreak = uiState.bestStreak,
                    deficit = uiState.deficit
                )
            } else {
                EmptyState(
                    icon = Icons.Default.Insights,
                    title = stringResource(R.string.empty_reports_title),
                    message = stringResource(R.string.empty_reports_message)
                )
            }
        }

        // CSV Export
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.export_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportCsv(ExportRange.THIS_MONTH) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            // Icon is decorative — the button's text label conveys the action.
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.export_this_month))
                        }

                        OutlinedButton(
                            onClick = { viewModel.exportCsv(ExportRange.ALL_TIME) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.export_all_time))
                        }
                    }
                }
            }
        }

        // Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Daily target slider — commits once on release, not on every drag tick.
                    var targetHours by remember(uiState.dailyTargetHours) {
                        mutableFloatStateOf(uiState.dailyTargetHours.toFloat())
                    }
                    Text(
                        text = stringResource(R.string.settings_daily_target, targetHours.toInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = targetHours,
                        onValueChange = { targetHours = it },
                        onValueChangeFinished = { viewModel.updateDailyTarget(targetHours.toInt()) },
                        valueRange = 1f..8f,
                        steps = 6
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_tracking_start, uiState.trackingStartDate.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OverallStatsCard(
    startDate: String,
    totalDays: Int,
    presentDays: Int,
    totalHours: String,
    currentStreak: Int,
    bestStreak: Int,
    deficit: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.overall_stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            StatsRow(stringResource(R.string.stat_tracking_since), startDate)
            StatsRow(stringResource(R.string.stat_total_tracked_days), "$totalDays")
            StatsRow(stringResource(R.string.stat_present_days_count), "$presentDays")
            StatsRow(stringResource(R.string.stat_total_hours_worked), totalHours)
            StatsRow(
                stringResource(R.string.stat_current_streak),
                pluralStringResource(R.plurals.days_count, currentStreak, currentStreak)
            )
            StatsRow(
                stringResource(R.string.stat_best_streak),
                pluralStringResource(R.plurals.days_count, bestStreak, bestStreak)
            )
            StatsRow(
                label = stringResource(R.string.stat_cumulative_deficit),
                value = formatDeficit(deficit),
                isHighlighted = deficit > 0
            )
        }
    }
}

@Composable
private fun StatsRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isHighlighted) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun formatDeficit(deficit: Double): String {
    val whole = deficit.toLong()
    return if (deficit == whole.toDouble()) {
        pluralStringResource(R.plurals.days_count, whole.toInt(), whole)
    } else {
        // Fractional deficits are always plural.
        stringResource(R.string.days_decimal, String.format(Locale.US, "%.1f", deficit))
    }
}
