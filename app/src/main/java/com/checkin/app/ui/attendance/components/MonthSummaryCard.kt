package com.checkin.app.ui.attendance.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.DailySummary
import java.util.Locale

@Composable
fun MonthSummaryCard(
    summaries: Map<String, DailySummary>,
    trackedDaysInMonth: Int,
    deficit: Double,
    formatDuration: (Long) -> String
) {
    val presentDays = summaries.values.count { it.status == AttendanceStatus.PRESENT }
    val halfDays = summaries.values.count { it.status == AttendanceStatus.HALF_DAY_LEAVE }
    val fullDays = trackedDaysInMonth - presentDays - halfDays
    val totalHours = summaries.values.sumOf { it.totalDurationMs }
    val avgDaily = if (summaries.isNotEmpty()) totalHours / summaries.size else 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.monthly_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(stringResource(R.string.stat_present), "$presentDays", Color(0xFF4CAF50))
                StatItem(stringResource(R.string.stat_half_day), "$halfDays", Color(0xFFFF9800))
                StatItem(stringResource(R.string.stat_full_day), "${fullDays.coerceAtLeast(0)}", Color(0xFFF44336))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.stat_total_hours),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(totalHours),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.stat_avg_daily),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(avgDaily),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.stat_deficit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDeficit(deficit),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (deficit > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
