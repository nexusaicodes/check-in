package com.checkin.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.checkin.app.R

sealed class Screen(val route: String, val titleRes: Int) {

    /** A bottom-nav destination. */
    sealed class Tab(route: String, titleRes: Int, val icon: ImageVector) : Screen(route, titleRes)

    /**
     * A destination pushed above [parent]: the top bar trades the centred title for a back arrow and
     * [parent] stays selected below, so a sub-screen still reads as part of its section.
     */
    sealed class Detail(route: String, titleRes: Int, val parent: Tab) : Screen(route, titleRes)

    data object CheckIn : Tab("checkin", R.string.nav_check_in, Icons.Default.Schedule)
    data object Attendance : Tab("attendance", R.string.nav_attendance, Icons.Default.CalendarMonth)
    data object Reports : Tab("reports", R.string.nav_reports, Icons.Default.Assessment)
    data object Settings : Tab("settings", R.string.nav_settings, Icons.Default.Settings)
    data object Licenses : Detail("licenses", R.string.nav_licenses, Settings)
}

internal val tabs = listOf(Screen.CheckIn, Screen.Attendance, Screen.Reports, Screen.Settings)

/**
 * Every destination the title bar can name. Details belong here and not in [tabs] — a route missing
 * from this list falls back to the start destination and would silently mislabel the screen.
 */
internal val titledScreens: List<Screen> = tabs + Screen.Licenses
