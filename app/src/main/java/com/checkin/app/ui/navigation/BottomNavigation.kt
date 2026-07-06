package com.checkin.app.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.checkin.app.R
import com.checkin.app.ui.attendance.AttendanceScreen
import com.checkin.app.ui.camera.SelfieCaptureScreen
import com.checkin.app.ui.checkin.CheckInScreen
import com.checkin.app.ui.checkin.CheckInViewModel
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.reports.ReportsScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    data object CheckIn : Screen("checkin", R.string.nav_check_in, Icons.Default.Schedule)
    data object Attendance : Screen("attendance", R.string.nav_attendance, Icons.Default.CalendarMonth)
    data object Reports : Screen("reports", R.string.nav_reports, Icons.Default.Assessment)
}

private val screens = listOf(Screen.CheckIn, Screen.Attendance, Screen.Reports)

/**
 * Top-level chrome: the bottom nav around the nav host. Each screen's section is identified by the
 * bottom nav, so there is no title bar — screens draw directly under the status-bar inset.
 */
@Composable
fun AppNavScaffold(navController: NavHostController) {
    // Hoisted here (shared with the Check-In tab) so its presence gate can render full-screen above
    // the chrome — the camera and capture button must not be covered by the bottom nav.
    val checkInViewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory)
    val checkInState by checkInViewModel.uiState.collectAsStateWithLifecycle()

    if (checkInState.showSelfieCapture) {
        // Full-screen modal gate: the Scaffold is not composed underneath (gate XOR chrome), so the
        // nav bar can't overlap the capture button. Back dismisses the gate.
        BackHandler { checkInViewModel.dismissSelfieCapture() }
        SelfieCaptureScreen(
            onAuthSuccess = { checkInViewModel.onAuthSuccess() },
            onDismiss = { checkInViewModel.dismissSelfieCapture() }
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavigationGraph(navController, innerPadding, checkInViewModel)
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        screens.forEach { screen ->
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
fun NavigationGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
    checkInViewModel: CheckInViewModel
) {
    NavHost(
        navController,
        startDestination = Screen.CheckIn.route,
        enterTransition = {
            fadeIn(animationSpec = tween(durationMillis = 200, easing = LinearEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 200, easing = LinearEasing))
        }
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
    }
}
