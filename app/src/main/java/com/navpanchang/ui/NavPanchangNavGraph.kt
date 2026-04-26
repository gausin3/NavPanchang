package com.navpanchang.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.navpanchang.ui.calendar.CalendarScreen
import com.navpanchang.ui.settings.AboutScreen
import com.navpanchang.ui.settings.DebugMenuScreen
import com.navpanchang.ui.settings.HomeCityPickerScreen
import com.navpanchang.ui.settings.SettingsScreen
import com.navpanchang.ui.subscriptions.EventDetailScreen
import com.navpanchang.ui.subscriptions.EventDetailViewModel
import com.navpanchang.ui.subscriptions.SubscriptionsScreen

/**
 * Top-level navigation destinations.
 *
 *  * [SUBSCRIPTIONS] / [CALENDAR] / [SETTINGS] — bottom-bar destinations.
 *  * [EVENT_DETAIL] — per-event drill-down reached by tapping a subscription row.
 *  * [HOME_CITY_PICKER] — searchable city picker, reached from Settings.
 *  * [ABOUT] — AGPL attribution + source link, reached from Settings.
 */
object NavDestinations {
    const val SUBSCRIPTIONS = "subscriptions"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"

    const val EVENT_DETAIL = "event/{${EventDetailViewModel.ARG_EVENT_ID}}"
    const val HOME_CITY_PICKER = "home_city_picker"
    const val ABOUT = "about"
    const val DEBUG_MENU = "debug_menu"

    fun eventDetailRoute(eventId: String): String = "event/$eventId"
}

@Composable
fun NavPanchangNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavDestinations.SUBSCRIPTIONS,
        modifier = modifier
    ) {
        composable(NavDestinations.SUBSCRIPTIONS) {
            SubscriptionsScreen(
                onEventClick = { eventId ->
                    navController.navigate(NavDestinations.eventDetailRoute(eventId))
                },
                onPickHomeCity = { navController.navigate(NavDestinations.HOME_CITY_PICKER) }
            )
        }

        composable(NavDestinations.CALENDAR) {
            CalendarScreen(
                onPickHomeCity = { navController.navigate(NavDestinations.HOME_CITY_PICKER) }
            )
        }

        composable(NavDestinations.SETTINGS) {
            SettingsScreen(
                onHomeCityClick = { navController.navigate(NavDestinations.HOME_CITY_PICKER) },
                onAboutClick = { navController.navigate(NavDestinations.ABOUT) }
            )
        }

        composable(
            route = NavDestinations.EVENT_DETAIL,
            arguments = listOf(
                navArgument(EventDetailViewModel.ARG_EVENT_ID) { type = NavType.StringType }
            )
        ) {
            EventDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(NavDestinations.HOME_CITY_PICKER) {
            HomeCityPickerScreen(
                onCitySelected = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onUnlockDebugMenu = { navController.navigate(NavDestinations.DEBUG_MENU) }
            )
        }

        composable(NavDestinations.DEBUG_MENU) {
            DebugMenuScreen(onBack = { navController.popBackStack() })
        }
    }
}
