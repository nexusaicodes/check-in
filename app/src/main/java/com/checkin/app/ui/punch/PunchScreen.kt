package com.checkin.app.ui.punch

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.data.local.AttendanceRules
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.ui.camera.SelfieCaptureScreen
import com.checkin.app.ui.theme.statusColor
import com.checkin.app.util.TimeFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PunchScreen(viewModel: PunchViewModel = viewModel()) {
    val isRunning by viewModel.isRunning.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val todaySessions by viewModel.todaySessions.collectAsState()
    val todayTotal by viewModel.todayTotalDuration.collectAsState()
    val deficit by viewModel.deficit.collectAsState()
    val showSelfie by viewModel.showSelfieCapture.collectAsState()
    val currentStartTime by viewModel.currentSessionStartTime.collectAsState()

    if (showSelfie) {
        SelfieCaptureScreen(
            onAuthSuccess = { viewModel.onAuthSuccess() },
            onDismiss = { viewModel.dismissSelfieCapture() }
        )
        return
    }

    // Read the (prefs-backed) target once, then reuse it for status and progress this frame.
    val dailyTargetMs = viewModel.dailyTargetMs
    // Effective total = completed sessions + current running interval
    val effectiveTotal = todayTotal + if (isRunning) elapsedTime else 0L
    val status = AttendanceRules.classify(effectiveTotal, dailyTargetMs)
    val progress = (effectiveTotal.toFloat() / dailyTargetMs).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = formatDateHeader(viewModel.todayDateKey),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Status card
        item {
            StatusCard(
                effectiveTotal = effectiveTotal,
                dailyTargetMs = dailyTargetMs,
                progress = progress,
                status = status,
                deficit = deficit,
                formatDuration = TimeFormat::durationShort
            )
        }

        // Current session card
        if (isRunning) {
            item {
                CurrentSessionCard(
                    startTime = currentStartTime,
                    elapsed = elapsedTime,
                    formatTime = TimeFormat::hms
                )
            }
        }

        // Punch button
        item {
            PunchButton(
                isRunning = isRunning,
                onPunchIn = { viewModel.requestPunchIn() },
                onPunchOut = { viewModel.requestPunchOut() }
            )
        }

        // Today's intervals
        val completedSessions = todaySessions.filter { it.stoppedAt != null }
        if (completedSessions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.todays_intervals),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(completedSessions, key = { it.id }) { session ->
                IntervalRow(session, TimeFormat::durationShort)
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatusCard(
    effectiveTotal: Long,
    dailyTargetMs: Long,
    progress: Float,
    status: AttendanceStatus,
    deficit: Double,
    formatDuration: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Hero time
            Text(
                text = "${formatDuration(effectiveTotal)} / ${formatDuration(dailyTargetMs)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = statusColor(status),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status badge + deficit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status)

                if (deficit > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null, // decorative — adjacent deficit text conveys it
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.deficit_days, formatDeficit(deficit)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: AttendanceStatus) {
    val color = statusColor(status)
    val label = when (status) {
        AttendanceStatus.PRESENT -> stringResource(R.string.status_present)
        AttendanceStatus.HALF_DAY_LEAVE -> stringResource(R.string.status_half_day)
        AttendanceStatus.FULL_DAY_LEAVE -> stringResource(R.string.status_full_day)
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CurrentSessionCard(
    startTime: Long?,
    elapsed: Long,
    formatTime: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.current_session),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = startTime?.let { TimeFormat.clock(it) } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = formatTime(elapsed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PunchButton(
    isRunning: Boolean,
    onPunchIn: () -> Unit,
    onPunchOut: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.primary,
        label = "punchButtonColor"
    )

    Button(
        onClick = if (isRunning) onPunchOut else onPunchIn,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null, // decorative — the button's text label conveys the action
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isRunning)
                stringResource(R.string.punch_out)
            else
                stringResource(R.string.punch_in),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IntervalRow(
    session: CheckInSession,
    formatDuration: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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

private fun formatDateHeader(dateKey: String): String {
    val date = java.time.LocalDate.parse(dateKey)
    return date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))
}

private fun formatDeficit(deficit: Double): String {
    return if (deficit == deficit.toLong().toDouble()) {
        deficit.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", deficit)
    }
}
