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
