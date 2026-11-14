package com.hubdeck.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hubdeck.app.ui.dashboard.DashboardRoute
import com.hubdeck.app.ui.service.ServiceDetailRoute
import com.hubdeck.feature.settings.SettingsScreen
import com.hubdeck.feature.tailscale.TailscaleScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val TAILSCALE = "tailscale"
    const val SERVICE_DETAIL = "service/{serviceId}"
}

@Composable
fun HubDeckApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardRoute(
                onOpenService = { serviceId -> navController.navigate("service/$serviceId") },
                onAddService = {},
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenTailscale = { navController.navigate(Routes.TAILSCALE) },
            )
        }

        composable(
            route = Routes.SERVICE_DETAIL,
            arguments = listOf(navArgument("serviceId") { type = NavType.StringType }),
        ) { entry ->
            val serviceId = entry.arguments?.getString("serviceId").orEmpty()
            val context = LocalContext.current
            ServiceDetailRoute(
                serviceId = serviceId,
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    ServiceOpener.open(context, url)
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TAILSCALE) {
            TailscaleScreen(onBack = { navController.popBackStack() })
        }
    }
}
