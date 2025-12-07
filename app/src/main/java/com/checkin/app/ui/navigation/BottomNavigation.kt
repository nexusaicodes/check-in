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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
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
import com.checkin.app.ui.checkin.CheckInScreen
import com.checkin.app.ui.history.HistoryScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    object CheckIn : Screen("checkin", R.string.nav_checkin, Icons.Default.Timer)
    object History : Screen("history", R.string.nav_history, Icons.Default.History)
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.CheckIn,
        Screen.History
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
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when re-selecting a previously selected item
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
        startDestination = Screen.CheckIn.route,
        enterTransition = {
            fadeIn(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = LinearEasing
                )

            )
        },
        exitTransition = {
            fadeOut(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = LinearEasing
                )
            )
        }
    ) {
        composable(Screen.CheckIn.route) {
            CheckInScreen()
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
    }
}
