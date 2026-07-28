package com.checkin.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.checkin.app.R

/**
 * Two families, split by the size they are read at.
 *
 * Outfit's x-height is 0.460 em against Manrope's 0.540 — a difference that decides the split rather
 * than following from it. Outfit reads as elegant on the 45sp gauge and would be thin in a 12sp
 * settings row; Manrope's larger x-height is what makes it hold up there. Using them the other way
 * round is the mistake this arrangement exists to avoid.
 *
 * Each weight is a **static instance** cut from the upstream variable font, not the variable file
 * itself. A variable font would be 331 KB smaller across the two families, but selecting a weight
 * from one needs `FontVariation.Settings`, which is `@ExperimentalTextApi`; without pinning the axis
 * the file loads at its default instance and the platform fakes the heavier weights by smearing the
 * glyphs. Four real cuts per family buys a stable API and genuine weights for that 331 KB.
 *
 * The four are the ones the app actually asks for: Normal, Medium, SemiBold and Bold. Instancing
 * preserves `tnum` and the embedded copyright notice, both of which are load-bearing here.
 */

/** Display, headline and title sizes — the app's voice. */
private val Display = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

/** Body and label sizes — everything read at length or at 14sp and below. */
private val Text = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

// Only the family is swapped: every size, line height and letter spacing stays as Material 3 tuned
// it. Styles the app doesn't use yet are mapped too, so a future call site inherits the right family
// rather than silently falling back to the system font.
private val Default = Typography()

val Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = Display),
    displayMedium = Default.displayMedium.copy(fontFamily = Display),
    displaySmall = Default.displaySmall.copy(fontFamily = Display),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Display),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Display),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Display),
    titleLarge = Default.titleLarge.copy(fontFamily = Display),
    titleMedium = Default.titleMedium.copy(fontFamily = Display),
    titleSmall = Default.titleSmall.copy(fontFamily = Display),
    bodyLarge = Default.bodyLarge.copy(fontFamily = Text),
    bodyMedium = Default.bodyMedium.copy(fontFamily = Text),
    bodySmall = Default.bodySmall.copy(fontFamily = Text),
    labelLarge = Default.labelLarge.copy(fontFamily = Text),
    labelMedium = Default.labelMedium.copy(fontFamily = Text),
    labelSmall = Default.labelSmall.copy(fontFamily = Text),
)

/**
 * The same style with tabular figures — every digit set to one width.
 *
 * For a clock that ticks, and load-bearing now rather than a nicety: Outfit's default `1` is 321
 * units against `0` at 659, a 2.05x spread that would make the gauge lurch sideways every time a `1`
 * entered or left the readout. `tnum` is an OpenType feature, independent of which family the style
 * carries, and both families here resolve it to genuinely uniform widths (Outfit 590, Manrope 1240).
 * A face without the feature ignores it and renders as before.
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")
