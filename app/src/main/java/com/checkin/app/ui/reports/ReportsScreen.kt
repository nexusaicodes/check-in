package com.checkin.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import java.util.Locale

@Composable
fun ReportsScreen(viewModel: ReportsViewModel = viewModel()) {
    val deficit by viewModel.deficit.collectAsState()
    val totalDays by viewModel.totalDays.collectAsState()
    val presentDays by viewModel.presentDays.collectAsState()
    val totalHoursMs by viewModel.totalHoursMs.collectAsState()
    val currentStreak by viewModel.currentStreak.collectAsState()
    val bestStreak by viewModel.bestStreak.collectAsState()
    val dailyTargetHours by viewModel.dailyTargetHours.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.reports_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Overall stats
        item {
            OverallStatsCard(
                startDate = viewModel.trackingStartDate.toString(),
                totalDays = totalDays,
                presentDays = presentDays,
                totalHours = viewModel.formatDurationShort(totalHoursMs),
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                deficit = deficit
            )
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

                    exportStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                    // Daily target slider
                    Text(
                        text = stringResource(R.string.settings_daily_target, dailyTargetHours),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = dailyTargetHours.toFloat(),
                        onValueChange = { viewModel.updateDailyTarget(it.toInt()) },
                        valueRange = 1f..8f,
                        steps = 6
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_tracking_start, viewModel.trackingStartDate.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
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
            StatsRow(stringResource(R.string.stat_current_streak), "$currentStreak days")
            StatsRow(stringResource(R.string.stat_best_streak), "$bestStreak days")
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

private fun formatDeficit(deficit: Double): String {
    return if (deficit == deficit.toLong().toDouble()) {
        "${deficit.toLong()} days"
    } else {
        String.format(Locale.US, "%.1f days", deficit)
    }
}
