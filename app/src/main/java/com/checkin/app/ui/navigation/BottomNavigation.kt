package com.checkin.app.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.checkin.app.ui.about.LicensesScreen
import com.checkin.app.ui.attendance.AttendanceScreen
import com.checkin.app.ui.camera.SelfieCaptureScreen
import com.checkin.app.ui.checkin.CheckInScreen
import com.checkin.app.ui.checkin.CheckInViewModel
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.reports.ReportsScreen
import com.checkin.app.ui.settings.SettingsScreen

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

private val tabs = listOf(Screen.CheckIn, Screen.Attendance, Screen.Reports, Screen.Settings)

/**
 * Every destination the title bar can name. Details belong here and not in [tabs] — a route missing
 * from this list falls back to the start destination and would silently mislabel the screen.
 */
private val titledScreens: List<Screen> = tabs + Screen.Licenses

/**
 * Top-level chrome: a centered title bar and the bottom nav around the nav host. The title names the
 * active section, so a screen never has to draw its own heading; screens receive the combined inset
 * through the Scaffold's padding.
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

    // One back-stack subscription drives both bars: the title and the selected nav item can never
    // disagree about which section is showing.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = titledScreens.firstOrNull { it.route == currentRoute } ?: Screen.CheckIn
    // A detail screen keeps its parent tab lit rather than leaving the bar with nothing selected.
    val selectedTab = when (currentScreen) {
        is Screen.Detail -> currentScreen.parent
        is Screen.Tab -> currentScreen
    }

    // Only a detail screen gets a back arrow; the tabs keep the bare centred title.
    val onBack: (() -> Unit)? = if (currentScreen is Screen.Detail) {
        { navController.popBackStack() }
    } else {
        null
    }

    Scaffold(
        topBar = { AppTopBar(currentScreen = currentScreen, onBack = onBack) },
        bottomBar = { BottomNavigationBar(navController, currentScreen, selectedTab) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavigationGraph(navController, innerPadding, checkInViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(currentScreen: Screen, onBack: (() -> Unit)?) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(currentScreen.titleRes)) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back)
                    )
                }
            }
        }
    )
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    currentScreen: Screen,
    selectedTab: Screen.Tab
) {
    NavigationBar {
        tabs.forEach { screen ->
            val title = stringResource(screen.titleRes)
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = title) },
                label = { Text(title) },
                selected = selectedTab == screen,
                onClick = {
                    // A detail is popped first. `saveState` would otherwise store it as part of its
                    // parent tab's stack and `restoreState` would put the user back on the detail
                    // next time they tap that tab — so tapping the lit Settings tab from Licenses
                    // would land on Licenses again and read as a dead tab.
                    val detail = currentScreen as? Screen.Detail
                    // False when the parent isn't on the stack and nothing popped; fall through to a
                    // normal navigate then, rather than leaving the tap doing nothing at all.
                    val popped = detail != null &&
                        navController.popBackStack(detail.parent.route, inclusive = false)
                    // Popping already landed on the tapped tab; navigating again would re-save it.
                    if (popped && detail?.parent == screen) return@NavigationBarItem

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
        composable(Screen.Settings.route) {
            ConstrainedContent {
                SettingsScreen(
                    innerPadding = innerPadding,
                    // launchSingleTop: a double tap on the row would otherwise push two identical
                    // Licenses entries, so the first back press appears to do nothing.
                    onOpenLicenses = {
                        navController.navigate(Screen.Licenses.route) { launchSingleTop = true }
                    }
                )
            }
        }
        composable(Screen.Licenses.route) {
            ConstrainedContent { LicensesScreen(innerPadding = innerPadding) }
        }
    }
}
