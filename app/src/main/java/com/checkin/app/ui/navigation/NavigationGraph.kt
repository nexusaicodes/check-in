package com.checkin.app.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.checkin.app.ui.about.LicensesScreen
import com.checkin.app.ui.attendance.AttendanceScreen
import com.checkin.app.ui.checkin.CheckInScreen
import com.checkin.app.ui.checkin.CheckInViewModel
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.reports.ReportsScreen
import com.checkin.app.ui.settings.SettingsScreen

@Composable
internal fun NavigationGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
    checkInViewModel: CheckInViewModel,
) {
    NavHost(
        navController,
        startDestination = Screen.CheckIn.route,
        enterTransition = {
            fadeIn(animationSpec = tween(durationMillis = 200, easing = LinearEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 200, easing = LinearEasing))
        },
    ) {
        composable(Screen.CheckIn.route) {
            ConstrainedContent { CheckInScreen(innerPadding = innerPadding, viewModel = checkInViewModel) }
        }
        composable(Screen.Attendance.route) {
            // Attendance manages its own width (two-pane on expanded), so it is not width-capped here.
            AttendanceScreen(innerPadding = innerPadding)
        }
        composable(Screen.Reports.route) {
            ConstrainedContent { ReportsScreen(innerPadding = innerPadding) }
        }
        composable(Screen.Settings.route) {
            ConstrainedContent {
                SettingsScreen(
                    innerPadding = innerPadding,
                    // launchSingleTop: a double tap on the row would otherwise push two identical
                    // Licenses entries, so the first back press appears to do nothing.
                    onOpenLicenses = {
                        navController.navigate(Screen.Licenses.route) { launchSingleTop = true }
                    },
                )
            }
        }
        composable(Screen.Licenses.route) {
            ConstrainedContent { LicensesScreen(innerPadding = innerPadding) }
        }
    }
}
