package com.checkin.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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
 * Both are variable fonts, so one file per family covers every weight (fine at minSdk 34). The
 * weights declared are the ones the app actually asks for: Normal, Medium, SemiBold and Bold.
 */
@OptIn(ExperimentalTextApi::class) // FontVariation.Settings; the overload is @RequiresApi(26), min is 34.
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId,
    weight,
    // Pins the wght axis to the weight being asked for. Without it the file loads at its default
    // instance and the platform fakes the heavier weights by smearing the glyphs.
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Display, headline and title sizes — the app's voice. */
private val Display = FontFamily(
    variable(R.font.outfit_variable, FontWeight.Normal),
    variable(R.font.outfit_variable, FontWeight.Medium),
    variable(R.font.outfit_variable, FontWeight.SemiBold),
    variable(R.font.outfit_variable, FontWeight.Bold),
)

/** Body and label sizes — everything read at length or at 14sp and below. */
private val Text = FontFamily(
    variable(R.font.manrope_variable, FontWeight.Normal),
    variable(R.font.manrope_variable, FontWeight.Medium),
    variable(R.font.manrope_variable, FontWeight.SemiBold),
    variable(R.font.manrope_variable, FontWeight.Bold),
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
