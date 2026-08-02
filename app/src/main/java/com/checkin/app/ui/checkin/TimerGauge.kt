package com.checkin.app.ui.checkin

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.charts.CircularProgressRing
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.tabularFigures
import com.checkin.app.util.TimeFormat

/** The sweep's period: the ring completes one turn per hour of the day's total. */
private const val MILLIS_PER_HOUR = 60 * 60 * 1000f

internal val COMPACT_GAUGE = 150.dp
internal val GAUGE_MIN = 190.dp
internal val GAUGE_MAX = 260.dp

/**
 * The day's total inside a ring that sweeps once an hour.
 *
 * The sweep is motion, not measurement: there is no target, so the ring is a fraction of nothing and
 * simply marks the passing hour before starting again. It follows that **the description must state
 * the elapsed time, never a percentage** — the sweep position means nothing, and announcing it as
 * progress would invent a goal the app does not have.
 */
@Composable
internal fun TimerGauge(elapsedTotal: Long, size: Dp = GAUGE_MAX) {
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
