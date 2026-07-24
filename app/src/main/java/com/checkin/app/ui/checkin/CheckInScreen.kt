package com.checkin.app.ui.checkin

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.ui.components.charts.CircularProgressRing
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.statusColor
import com.checkin.app.util.TimeFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The screen is deliberately a fixed, non-scrolling [Column]: the timer and the primary action must
 * both be reachable in one glance and one thumb reach on a phone. Only the today's-sessions section
 * scrolls, and it scrolls inside its own bounded height so nothing above it moves.
 */
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
    val progress = if (dailyTargetMs > 0L) (effectiveTotal.toFloat() / dailyTargetMs).coerceIn(0f, 1f) else 0f
    val completedSessions = uiState.todaySessions.filter { it.stoppedAt != null }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // A weighted Column clips rather than scrolls once content outgrows the viewport, so short
        // viewports (landscape, very small phones, large font scales) fall back to a scrolling
        // layout. Portrait phones — the case the design targets — always take the fixed branch.
        val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val gaugeSize = if (compact) COMPACT_GAUGE else (maxHeight * 0.34f).coerceIn(GAUGE_MIN, GAUGE_MAX)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 8.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatDateHeader(uiState.todayDateKey),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Splits the free space ~0.6 : 1 above and below the gauge, which drops it roughly 15%
            // of the viewport from the top and leaves the action sitting in the thumb arc.
            if (compact) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(0.6f))

            if (uiState.hasEverTracked) {
                TimerGauge(
                    elapsedTotal = effectiveTotal,
                    progress = progress,
                    isPaused = uiState.isPaused,
                    size = gaugeSize
                )
            } else {
                // First-run welcome, shown instead of a gauge that would only ever read 00:00.
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = stringResource(R.string.empty_checkin_title),
                    message = stringResource(R.string.empty_checkin_message)
                )
            }

            if (compact) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))

            // Sessions sit above the button so expanding them grows upward into the spacers. The
            // primary action stays pinned to the bottom and never moves under the user's thumb.
            if (completedSessions.isNotEmpty()) {
                TodaySessions(sessions = completedSessions, scrollable = !compact)
                Spacer(modifier = Modifier.height(12.dp))
            }

            CheckInOutButton(
                isRunning = uiState.isRunning,
                isPaused = uiState.isPaused,
                onCheckIn = { viewModel.requestCheckIn() },
                onCheckOut = { viewModel.requestCheckOut() },
                onResume = { viewModel.requestResume() }
            )
        }
    }
}

private val COMPACT_HEIGHT_THRESHOLD = 560.dp
private val COMPACT_GAUGE = 150.dp
private val GAUGE_MIN = 190.dp
private val GAUGE_MAX = 260.dp

/**
 * The day's total inside a ring that fills toward the target. The ring only ever reads neutral or
 * positive — brand primary while the day is in progress, the "present" accent once the target is
 * met — so an incomplete day is never coloured as a failure.
 */
@Composable
private fun TimerGauge(
    elapsedTotal: Long,
    progress: Float,
    isPaused: Boolean,
    size: Dp = GAUGE_MAX
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    val reached = progress >= 1f
    val ringColor by animateColorAsState(
        targetValue = if (reached) statusColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.primary,
        label = "ringColor"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressRing(
            progress = animatedProgress,
            color = ringColor,
            trackColor = ringColor.copy(alpha = 0.15f),
            modifier = Modifier.size(size)
        ) {
            Text(
                text = TimeFormat.durationShort(elapsedTotal),
                // The readout has to stay inside the ring, which shrinks on short viewports.
                style = if (size < GAUGE_MIN) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // A frozen clock reads as a bug without this; the Resume button alone doesn't explain why.
        if (isPaused) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.checkin_paused_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

    // Checking out is a neutral, expected end to the day — a tonal treatment, never an alarm colour.
    val containerColor by animateColorAsState(
        targetValue = if (isRunning)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.primary,
        label = "checkButtonContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isRunning)
            MaterialTheme.colorScheme.onSecondaryContainer
        else
            MaterialTheme.colorScheme.onPrimary,
        label = "checkButtonContent"
    )

    Button(
        onClick = if (isRunning) onCheckOut else onCheckIn,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
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

/**
 * Collapsed by default so the screen holds one viewport; expanding reveals the day's intervals in a
 * bounded, internally-scrolling list rather than growing the page.
 */
@Composable
private fun TodaySessions(sessions: List<CheckInSession>, scrollable: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val total = sessions.sumOf { it.duration ?: 0L }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.todays_sessions_summary,
                    pluralStringResource(R.plurals.sessions_count, sessions.size, sessions.size),
                    TimeFormat.durationShort(total)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.cd_collapse_sessions else R.string.cd_expand_sessions
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            if (scrollable) {
                // Bounded and internally scrolling, so a long day can't push the layout past the
                // viewport no matter how many intervals it holds.
                LazyColumn(
                    modifier = Modifier.heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        IntervalRow(session)
                    }
                }
            } else {
                // The compact layout already scrolls as a whole; a lazy list nested inside it would
                // be measured with unbounded height and crash.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sessions.forEach { session -> IntervalRow(session) }
                }
            }
        }
    }
}

@Composable
private fun IntervalRow(session: CheckInSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${TimeFormat.clock(session.startedAt)} - ${session.stoppedAt?.let { TimeFormat.clock(it) } ?: ""}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = session.duration?.let { TimeFormat.durationShort(it) } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatDateHeader(dateKey: String): String {
    val date = java.time.LocalDate.parse(dateKey)
    return date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TimerGaugeInProgressPreview() {
    CheckInAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TimerGauge(elapsedTotal = 5 * 3_600_000L, progress = 0.625f, isPaused = false)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TimerGaugeTargetMetPreview() {
    CheckInAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TimerGauge(elapsedTotal = 8 * 3_600_000L, progress = 1f, isPaused = false)
        }
    }
}
