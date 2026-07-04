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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.checkin.app.R
import com.checkin.app.ui.attendance.AttendanceScreen
import com.checkin.app.ui.checkin.CheckInScreen
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.reports.ReportsScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    data object CheckIn : Screen("checkin", R.string.nav_check_in, Icons.Default.Schedule)
    data object Attendance : Screen("attendance", R.string.nav_attendance, Icons.Default.CalendarMonth)
    data object Reports : Screen("reports", R.string.nav_reports, Icons.Default.Assessment)
}

private val screens = listOf(Screen.CheckIn, Screen.Attendance, Screen.Reports)

/** Top-level chrome: a title bar (with scroll elevation) and the bottom nav, around the nav host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavScaffold(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = screens.firstOrNull { it.route == navBackStackEntry?.destination?.route }
        ?: Screen.CheckIn
    // The Check-In tab surfaces the app name; the others show their section title.
    val titleRes = if (currentScreen == Screen.CheckIn) R.string.app_name else currentScreen.titleRes
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavigationGraph(navController, innerPadding)
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
fun NavigationGraph(navController: NavHostController, innerPadding: PaddingValues) {
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
            ConstrainedContent { CheckInScreen(innerPadding = innerPadding) }
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
