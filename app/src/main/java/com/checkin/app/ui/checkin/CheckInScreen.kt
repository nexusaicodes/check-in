package com.checkin.app.ui.checkin

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.service.SessionClock
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.ui.components.charts.CircularProgressRing
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.startActionColors
import com.checkin.app.ui.theme.stopActionColors
import com.checkin.app.ui.theme.tabularFigures
import com.checkin.app.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The screen is deliberately a fixed, non-scrolling [Column]: the timer and the primary action must
 * both be reachable in one glance and one thumb reach on a phone. Where there is room, the day's
 * intervals expand into a bounded list that scrolls inside itself so nothing around it moves; where
 * there isn't, the whole screen scrolls instead. What it never does is let the list grow into the
 * primary action's space.
 */
@Composable
fun CheckInScreen(
    innerPadding: PaddingValues,
    viewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh prefs-backed inputs and roll the date window forward when the screen resumes.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    // The presence gate (showSelfieCapture) is rendered full-screen by AppNavScaffold, above the
    // chrome — not here — so the camera and its capture button aren't covered by the bottom nav.

    // Elapsed ticker is screen-driven, so it only runs while this screen is composed.
    val startTime = uiState.currentSessionStartTime
    var elapsed by remember(startTime) { mutableStateOf(0L) }
    LaunchedEffect(uiState.isRunning, startTime) {
        if (uiState.isRunning && startTime != null) {
            while (isActive) {
                elapsed = SessionClock.elapsedMs(System.currentTimeMillis(), startTime)
                delay(1000)
            }
        } else {
            elapsed = 0L
        }
    }

    // Effective total = completed sessions + current running interval.
    val effectiveTotal = uiState.todayTotalDuration + if (uiState.isRunning) elapsed else 0L

    // The list shows the running interval alongside the closed ones, so its total can agree with the
    // gauge above it. An interval opened on a previous day isn't in today's list, so it contributes
    // to neither — the ticker still runs off it, but today's figures stay today's.
    val runningElapsed = elapsed.takeIf {
        uiState.isRunning && uiState.todaySessions.any { session -> session.stoppedAt == null }
    }
    val sessionsTotal = uiState.todaySessions.sumOf { it.duration ?: 0L } + (runningElapsed ?: 0L)

    // Owned here rather than by TodaySessions: whether the day's intervals are open decides how much
    // room the layout has left, and therefore which branch below can hold it.
    var sessionsExpanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val shortViewport = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val gaugeSize = if (shortViewport) COMPACT_GAUGE else (maxHeight * 0.34f).coerceIn(GAUGE_MIN, GAUGE_MAX)

        // What an expanded list may claim: whatever is left once the chrome, the gauge and the
        // primary action are paid for. A Column measures non-weighted children in declaration order,
        // so an unbounded list declared above the button would take the button's space and coerce it
        // to zero height — the action would silently vanish rather than the list being capped.
        val chrome = innerPadding.calculateTopPadding() + innerPadding.calculateBottomPadding() + ACTION_BOTTOM_GAP
        // The text rows in that estimate grow with the user's font scale while the button does not,
        // so the allowance has to shrink by the same amount or the guarantee only holds at 1.0.
        val textGrowth = TEXT_CONTENT_HEIGHT * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
        val listMax = (maxHeight - chrome - gaugeSize - FIXED_CONTENT_HEIGHT - textGrowth)
            .coerceIn(0.dp, SESSION_LIST_MAX)

        // A weighted Column clips rather than scrolls once content outgrows the viewport. Short
        // viewports (landscape, very small phones, large font scales) fall back to a scrolling
        // layout, as does an expanded list with too little room left to be worth bounding.
        val scrolls = shortViewport || (sessionsExpanded && listMax < SESSION_LIST_MIN)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + ACTION_BOTTOM_GAP,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatDateHeader(uiState.todayDateKey),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Splits the free space ~0.6 : 1 above and below the gauge, which drops it roughly 15%
            // of the viewport from the top and leaves the action sitting in the thumb arc.
            if (scrolls) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(0.6f))

            when {
                // Whether this is a first run is a DB read away, so the slot is held at the gauge's
                // size until the answer arrives. Rendering the welcome meanwhile would flash "get
                // started" at a user with months of history, every time they open the app.
                uiState.loading -> Spacer(Modifier.size(gaugeSize))

                uiState.hasEverTracked -> TimerGauge(
                    elapsedTotal = effectiveTotal,
                    size = gaugeSize,
                )

                // First-run welcome, shown instead of a gauge that would only ever read 00:00. The
                // brand mark rather than an action icon, and a title with no message: this is the
                // one moment on the screen that introduces the app instead of asking for something,
                // and the button below already states the action.
                else -> EmptyState(
                    icon = painterResource(R.drawable.ic_stat_checkin),
                    title = stringResource(R.string.empty_checkin_title),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            if (scrolls) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))

            // Sessions sit above the button so expanding them grows upward into the spacers. The
            // primary action stays pinned to the bottom and never moves under the user's thumb.
            if (uiState.todaySessions.isNotEmpty()) {
                TodaySessions(
                    sessions = uiState.todaySessions,
                    total = sessionsTotal,
                    runningElapsed = runningElapsed,
                    expanded = sessionsExpanded,
                    onToggle = { sessionsExpanded = !sessionsExpanded },
                    // The outer Column already scrolls in that branch; a lazy list nested inside it
                    // would be measured with unbounded height and crash.
                    listMaxHeight = listMax.takeUnless { scrolls },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            CheckInOutButton(
                isRunning = uiState.isRunning,
                onCheckIn = { viewModel.requestCheckIn() },
                onCheckOut = { viewModel.requestCheckOut() },
            )
        }
    }
}

/** The sweep's period: the ring completes one turn per hour of the day's total. */
private const val MILLIS_PER_HOUR = 60 * 60 * 1000f

private val COMPACT_HEIGHT_THRESHOLD = 560.dp
private val COMPACT_GAUGE = 150.dp
private val GAUGE_MIN = 190.dp
private val GAUGE_MAX = 260.dp

/**
 * Lifts the primary action clear of the navigation bar, above the system inset. Counted into the fit
 * budget as well as applied as padding, or an expanded session list reclaims exactly this much and
 * the gap closes again on the one layout that needs it most.
 */
private val ACTION_BOTTOM_GAP = 16.dp

/** Date row + collapsed sessions row + its spacer + the 64.dp action, plus breathing room. */
private val FIXED_CONTENT_HEIGHT = 164.dp

/** The part of that which is text, and so scales with the user's font-size setting. */
private val TEXT_CONTENT_HEIGHT = 64.dp
private val SESSION_LIST_MAX = 180.dp

/** Below this an expanded list shows barely a row, so the whole screen scrolls instead. */
private val SESSION_LIST_MIN = 96.dp

/**
 * The day's total inside a ring that sweeps once an hour.
 *
 * The sweep is motion, not measurement: there is no target, so the ring is a fraction of nothing and
 * simply marks the passing hour before starting again. It follows that **the description must state
 * the elapsed time, never a percentage** — the sweep position means nothing, and announcing it as
 * progress would invent a goal the app does not have.
 */
@Composable
private fun TimerGauge(elapsedTotal: Long, size: Dp = GAUGE_MAX) {
    // Modulo the hour, so the ring completes a turn every hour of the day's total. Not animated
    // across the wrap: springing back from full to empty would read as progress being lost.
    val sweep = (elapsedTotal % MILLIS_PER_HOUR).toFloat() / MILLIS_PER_HOUR
    val ringColor = MaterialTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressRing(
            progress = sweep,
            color = ringColor,
            trackColor = ringColor.copy(alpha = 0.15f),
            contentDescription = stringResource(
                R.string.cd_timer_gauge,
                TimeFormat.durationShort(elapsedTotal),
            ),
            modifier = Modifier.size(size),
        ) {
            Text(
                text = TimeFormat.durationLive(elapsedTotal),
                // The readout has to stay inside the ring, which shrinks on short viewports.
                style = if (size < GAUGE_MIN) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.displayMedium
                }.tabularFigures(),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CheckInOutButton(isRunning: Boolean, onCheckIn: () -> Unit, onCheckOut: () -> Unit) {
    val start = startActionColors()
    val stop = stopActionColors()

    // Start/stop, read at a glance and without the label: green to begin the day, red to end it. The
    // colour animates across the switch so the change reads as one control changing state rather
    // than two buttons swapping places.
    val containerColor by animateColorAsState(
        targetValue = if (isRunning) stop.container else start.container,
        label = "checkButtonContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isRunning) stop.content else start.content,
        label = "checkButtonContent",
    )

    Button(
        onClick = if (isRunning) onCheckOut else onCheckIn,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(
            if (isRunning) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
            contentDescription = null, // decorative — the button's text label conveys the action
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isRunning) {
                stringResource(R.string.check_out)
            } else {
                stringResource(R.string.check_in)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Collapsed by default so the screen holds one viewport; expanding reveals the day's intervals in a
 * bounded, internally-scrolling list rather than growing the page.
 *
 * A non-null [listMaxHeight] bounds the expanded list and lets it scroll inside itself. Null means
 * the caller's layout scrolls as a whole, so the list renders in full and must not be lazy.
 */
@Composable
private fun TodaySessions(
    sessions: List<CheckInSession>,
    total: Long,
    runningElapsed: Long?,
    expanded: Boolean,
    onToggle: () -> Unit,
    listMaxHeight: Dp?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.todays_sessions_summary,
                    pluralStringResource(R.plurals.sessions_count, sessions.size, sessions.size),
                    TimeFormat.durationShort(total),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.cd_collapse_sessions else R.string.cd_expand_sessions,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            if (listMaxHeight != null) {
                // Bounded and internally scrolling, so a long day can't push the layout past the
                // viewport no matter how many intervals it holds.
                LazyColumn(
                    modifier = Modifier.heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
                        IntervalRow(session, runningElapsed)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sessions.forEach { session -> IntervalRow(session, runningElapsed) }
                }
            }
        }
    }
}

/**
 * The open interval is listed like any other, with its live elapsed in place of a settled duration,
 * so the section's total agrees with the gauge and the day's start time is visible somewhere.
 */
@Composable
private fun IntervalRow(session: CheckInSession, runningElapsed: Long?) {
    val running = session.stoppedAt == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (running) {
                "${TimeFormat.clock(session.startedAt)} - ${stringResource(R.string.session_in_progress)}"
            } else {
                "${TimeFormat.clock(session.startedAt)} - ${session.stoppedAt?.let { TimeFormat.clock(it) } ?: ""}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (running) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            // The open interval ticks in the same units as the gauge; settled ones stay coarse.
            text = if (running) {
                runningElapsed?.let { TimeFormat.durationLive(it) } ?: ""
            } else {
                session.duration?.let { TimeFormat.durationShort(it) } ?: ""
            },
            // Tabular for the whole column, not just the ticking row: it stops the open interval
            // jittering, and it right-aligns the settled durations under it into a readable column.
            style = MaterialTheme.typography.bodyMedium.tabularFigures(),
            fontWeight = FontWeight.SemiBold,
            color = if (running) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun formatDateHeader(dateKey: String): String = TimeFormat.dateKeyWithWeekday(dateKey).orEmpty()

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TimerGaugePartHourPreview() {
    CheckInAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TimerGauge(elapsedTotal = 5 * 3_600_000L + 45 * 60_000L)
        }
    }
}

/** On the hour the sweep is back at the start — the ring turns over rather than staying full. */
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TimerGaugeOnTheHourPreview() {
    CheckInAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TimerGauge(elapsedTotal = 8 * 3_600_000L)
        }
    }
}
