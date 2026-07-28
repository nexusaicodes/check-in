package com.checkin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.checkin.app.data.local.AttendanceStatus

// Attendance status palette, tuned per theme so it keeps adequate contrast in light and dark.
private val PresentLight = Color(0xFF2E7D32)
private val PresentDark = Color(0xFF81C784)
private val HalfDayLight = Color(0xFFEF6C00)
private val HalfDayDark = Color(0xFFFFB74D)
private val FullDayLight = Color(0xFFC62828)
private val FullDayDark = Color(0xFFE57373)

// Label colors for the filled action buttons. Light theme fills with the deep hue and writes in
// white; dark theme fills with the pale hue and writes in near-black, which is how Material 3 keeps
// a colored container legible on a dark surface. Every pair clears WCAG AA (4.5:1) for body text.
private val OnStartDark = Color(0xFF0A2E12)
private val OnStopDark = Color(0xFF3B0A0A)

/** Theme-aware color for an attendance status. */
@Composable
@ReadOnlyComposable
fun statusColor(status: AttendanceStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (status) {
        AttendanceStatus.PRESENT -> if (dark) PresentDark else PresentLight
        AttendanceStatus.HALF_DAY_LEAVE -> if (dark) HalfDayDark else HalfDayLight
        AttendanceStatus.FULL_DAY_LEAVE -> if (dark) FullDayDark else FullDayLight
    }
}

/** A filled button's container and the label on top of it. */
data class ActionColors(val container: Color, val content: Color)

/**
 * Colors for starting the clock — checking in, and resuming a paused session.
 *
 * Deliberately the same green the calendar uses for a present day rather than a second, slightly
 * different one: the app should own one green and one red, not four near-misses.
 */
@Composable
@ReadOnlyComposable
fun startActionColors(): ActionColors = if (isSystemInDarkTheme()) {
    ActionColors(PresentDark, OnStartDark)
} else {
    ActionColors(PresentLight, Color.White)
}

/**
 * Colors for stopping the clock. Red here is the stop half of a start/stop control, not an error
 * state — nothing about checking out is a failure, and no copy on the screen says otherwise.
 */
@Composable
@ReadOnlyComposable
fun stopActionColors(): ActionColors = if (isSystemInDarkTheme()) {
    ActionColors(FullDayDark, OnStopDark)
} else {
    ActionColors(FullDayLight, Color.White)
}
