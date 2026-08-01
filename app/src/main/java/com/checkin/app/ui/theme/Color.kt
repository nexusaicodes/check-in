package com.checkin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// The one hue a recorded day is drawn in, tuned per theme so it keeps adequate contrast in light and
// dark. There is deliberately no second or third colour: days are no longer classified, so there is
// no "worse" shade for one to mean.
private val PresentLight = Color(0xFF2E7D32)
private val PresentDark = Color(0xFF81C784)

// Kept only for the stop half of the start/stop control below. Not a status colour — no day is ever
// drawn in it.
private val StopLight = Color(0xFFC62828)
private val StopDark = Color(0xFFE57373)

// Label colors for the filled action buttons. Light theme fills with the deep hue and writes in
// white; dark theme fills with the pale hue and writes in near-black, which is how Material 3 keeps
// a colored container legible on a dark surface. Every pair clears WCAG AA (4.5:1) for body text.
private val OnStartDark = Color(0xFF0A2E12)
private val OnStopDark = Color(0xFF3B0A0A)

/**
 * Theme-aware colour for a day, at [fraction] of full strength.
 *
 * Strength is carried in the alpha channel rather than by interpolating toward the surface, so a
 * cell composites correctly over whatever it is drawn on in either theme. A [fraction] of zero is
 * fully transparent — a day with nothing recorded is an empty cell, not a coloured one.
 */
@Composable
@ReadOnlyComposable
fun dayColor(fraction: Float): Color {
    val base = if (isSystemInDarkTheme()) PresentDark else PresentLight
    return base.copy(alpha = fraction.coerceIn(0f, 1f))
}

/** Full-strength day colour, for legends and any mark that isn't standing for a quantity. */
@Composable
@ReadOnlyComposable
fun dayColor(): Color = if (isSystemInDarkTheme()) PresentDark else PresentLight

/** A filled button's container and the label on top of it. */
data class ActionColors(val container: Color, val content: Color)

/**
 * Colors for starting the clock.
 *
 * Deliberately the same green the calendar draws a recorded day in rather than a second, slightly
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
    ActionColors(StopDark, OnStopDark)
} else {
    ActionColors(StopLight, Color.White)
}
