package com.checkin.app.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.tabularFigures
import com.checkin.app.util.TimeFormat
import kotlinx.coroutines.delay

/**
 * The moment after a check-out: what was just recorded, held on screen briefly and then dismissed.
 *
 * **It celebrates showing up, never how long for.** The copy, the emphasis and the icon are
 * identical at twenty minutes and at four hours — a message that warmed as the number grew would be
 * the deleted daily target returning as a congratulation threshold, which is the one thing this
 * screen must not become. The duration is stated because it is a quantity worth seeing, exactly as
 * it is everywhere else in the app, and it is never graded.
 *
 * Rendered above the nav host rather than inside the Check-In screen because a check-out can be
 * written from the notification while any tab is open — see [CheckOutSignal].
 */
@Composable
fun CheckOutCelebration(completed: CheckOutSignal.Completed, onDismiss: () -> Unit) {
    // Re-armed per completion, so a second check-out gets its own full dwell rather than inheriting
    // whatever was left of the first one's.
    LaunchedEffect(completed) {
        delay(DWELL_MS)
        onDismiss()
    }

    val titleText = stringResource(R.string.checkout_celebration_title)
    val sessionText = TimeFormat.durationShort(completed.sessionMs)
    val dayText = stringResource(
        R.string.todays_sessions_summary,
        pluralStringResource(
            R.plurals.sessions_count,
            completed.daySessionCount,
            completed.daySessionCount,
        ),
        TimeFormat.durationShort(completed.dayTotalMs),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // No ripple and no role: the whole surface is a dismiss target rather than a control,
            // and an indication spreading from the tap point would read as a button that isn't there.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            // One announcement for the whole surface. Read as separate nodes it arrives as four
            // disconnected fragments, and the dismiss hint is the least useful of them.
            .clearAndSetSemantics {
                contentDescription = "$titleText. $sessionText. $dayText"
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stat_checkin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICON_SIZE),
            )
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = sessionText,
                style = MaterialTheme.typography.displaySmall.tabularFigures(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = dayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = stringResource(R.string.checkout_celebration_dismiss),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}

/**
 * How long the celebration holds before retiring itself.
 *
 * Long enough to read three figures, short enough that it never becomes something to dismiss — the
 * tap is an escape hatch for the impatient, not the way out.
 */
private const val DWELL_MS = 2500L

private val ICON_SIZE = 72.dp

@Preview(showBackground = true)
@Composable
private fun CheckOutCelebrationPreview() {
    CheckInAppTheme {
        CheckOutCelebration(
            completed = CheckOutSignal.Completed(
                sessionMs = 2 * 3_600_000L + 14 * 60_000L,
                dayTotalMs = 6 * 3_600_000L + 12 * 60_000L,
                daySessionCount = 2,
            ),
            onDismiss = {},
        )
    }
}

/** A short session gets exactly the same treatment, which is the point. */
@Preview(showBackground = true)
@Composable
private fun CheckOutCelebrationShortPreview() {
    CheckInAppTheme {
        CheckOutCelebration(
            completed = CheckOutSignal.Completed(
                sessionMs = 20 * 60_000L,
                dayTotalMs = 20 * 60_000L,
                daySessionCount = 1,
            ),
            onDismiss = {},
        )
    }
}
