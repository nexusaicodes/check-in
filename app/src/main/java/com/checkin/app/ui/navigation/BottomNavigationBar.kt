package com.checkin.app.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController

@Composable
internal fun BottomNavigationBar(navController: NavController, currentScreen: Screen, selectedTab: Screen.Tab) {
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
                },
            )
        }
    }
}
