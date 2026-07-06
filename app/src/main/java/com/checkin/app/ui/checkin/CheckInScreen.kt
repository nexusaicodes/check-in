package com.checkin.app.ui.checkin

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.data.local.AttendanceRules
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.statusColor
import com.checkin.app.util.TimeFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun CheckInScreen(
    innerPadding: PaddingValues,
    viewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh prefs-backed inputs and roll the date window forward when the screen resumes.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    // The presence gate (showSelfieCapture) is rendered full-screen by AppNavScaffold, above the
    // chrome — not here — so the camera and its capture button aren't covered by the bottom nav.

    // Elapsed ticker is screen-driven, so it only runs while this screen is composed. It nets out
    // paused time; while a pause is open the value is frozen (the open-pause term cancels the tick).
    val startTime = uiState.currentSessionStartTime
    val pausedMs = uiState.currentSessionPausedMs
    val pauseStartedAt = uiState.currentSessionPauseStartedAt
    var elapsed by remember(startTime, pausedMs, pauseStartedAt) { mutableStateOf(0L) }
    LaunchedEffect(uiState.isRunning, startTime, pausedMs, pauseStartedAt) {
        if (uiState.isRunning && startTime != null) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val openPause = pauseStartedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
                elapsed = (now - startTime - pausedMs - openPause).coerceAtLeast(0L)
                if (pauseStartedAt != null) break // frozen — nothing to tick until presence is re-verified
                delay(1000)
            }
        } else {
            elapsed = 0L
        }
    }

    val dailyTargetMs = uiState.dailyTargetMs
    // Effective total = completed sessions + current running interval.
    val effectiveTotal = uiState.todayTotalDuration + if (uiState.isRunning) elapsed else 0L
    val status = AttendanceRules.classify(effectiveTotal, dailyTargetMs)
    val progress = if (dailyTargetMs > 0L) (effectiveTotal.toFloat() / dailyTargetMs).coerceIn(0f, 1f) else 0f

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
        if (uiState.hasEverTracked) {
            // Date header
            item {
                Text(
                    text = formatDateHeader(uiState.todayDateKey),
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
                    deficit = uiState.deficit,
                    formatDuration = TimeFormat::durationShort
                )
            }

            // Current session card
            if (uiState.isRunning) {
                item {
                    CurrentSessionCard(
                        startTime = startTime,
                        elapsed = elapsed,
                        isPaused = uiState.isPaused,
                        formatTime = TimeFormat::hms
                    )
                }
            }
        } else {
            // First-run welcome, shown instead of today's (empty) status.
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = stringResource(R.string.empty_checkin_title),
                    message = stringResource(R.string.empty_checkin_message)
                )
            }
        }

        // Check-in / check-out button (plus Resume while a presence check is pending)
        item {
            CheckInOutButton(
                isRunning = uiState.isRunning,
                isPaused = uiState.isPaused,
                onCheckIn = { viewModel.requestCheckIn() },
                onCheckOut = { viewModel.requestCheckOut() },
                onResume = { viewModel.requestResume() }
            )
        }

        // Today's intervals
        val completedSessions = uiState.todaySessions.filter { it.stoppedAt != null }
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
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp).animateContentSize()) {
            // Hero time
            Text(
                text = "${formatDuration(effectiveTotal)} / ${formatDuration(dailyTargetMs)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
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
    isPaused: Boolean,
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
                text = stringResource(
                    if (isPaused) R.string.current_session_paused else R.string.current_session
                ),
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
private fun CheckInOutButton(
    isRunning: Boolean,
    isPaused: Boolean,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onResume: () -> Unit
) {
    // While paused, re-verifying presence is the primary action; checking out stays available below.
    if (isPaused) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null, // decorative — the button's text label conveys the action
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.resume_session),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onCheckOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null, // decorative — the button's text label conveys the action
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.check_out),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    val buttonColor by animateColorAsState(
        targetValue = if (isRunning)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.primary,
        label = "checkButtonColor"
    )

    Button(
        onClick = if (isRunning) onCheckOut else onCheckIn,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            if (isRunning) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
            contentDescription = null, // decorative — the button's text label conveys the action
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isRunning)
                stringResource(R.string.check_out)
            else
                stringResource(R.string.check_in),
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusCardPreview() {
    CheckInAppTheme {
        StatusCard(
            effectiveTotal = 5 * 3_600_000L,
            dailyTargetMs = 8 * 3_600_000L,
            progress = 0.625f,
            status = AttendanceStatus.HALF_DAY_LEAVE,
            deficit = 2.0,
            formatDuration = { "${it / 3_600_000}h" }
        )
    }
}
