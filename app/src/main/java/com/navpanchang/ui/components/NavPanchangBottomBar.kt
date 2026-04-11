package com.navpanchang.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.navpanchang.R
import com.navpanchang.ui.NavDestinations

private data class BottomBarItem(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int
)

private val BottomBarItems = listOf(
    BottomBarItem(NavDestinations.SUBSCRIPTIONS, Icons.Filled.Home, R.string.nav_home),
    BottomBarItem(NavDestinations.CALENDAR, Icons.Filled.CalendarMonth, R.string.nav_calendar),
    BottomBarItem(NavDestinations.SETTINGS, Icons.Filled.Settings, R.string.nav_settings)
)

@Composable
fun NavPanchangBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        BottomBarItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        // Avoid building a giant back stack: pop up to the start destination.
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                label = { Text(stringResource(item.labelRes)) }
            )
        }
    }
}
