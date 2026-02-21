package com.checkin.app.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.checkin.app.R
import com.checkin.app.ui.punch.PunchScreen
import com.checkin.app.ui.attendance.AttendanceScreen
import com.checkin.app.ui.reports.ReportsScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    data object Punch : Screen("punch", R.string.nav_punch, Icons.Default.Fingerprint)
    data object Attendance : Screen("attendance", R.string.nav_attendance, Icons.Default.CalendarMonth)
    data object Reports : Screen("reports", R.string.nav_reports, Icons.Default.Assessment)
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Punch,
        Screen.Attendance,
        Screen.Reports
    )
    val bottomBarColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    NavigationBar(
        modifier = Modifier
            .background(bottomBarColor)
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
            ),
        containerColor = bottomBarColor,
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { screen ->
            val title = stringResource(screen.titleRes)
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = title) },
                label = { Text(title) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(
        navController,
        startDestination = Screen.Punch.route,
        enterTransition = {
            fadeIn(animationSpec = tween(durationMillis = 200, easing = LinearEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 200, easing = LinearEasing))
        }
    ) {
        composable(Screen.Punch.route) {
            PunchScreen()
        }
        composable(Screen.Attendance.route) {
            AttendanceScreen()
        }
        composable(Screen.Reports.route) {
            ReportsScreen()
        }
    }
}
