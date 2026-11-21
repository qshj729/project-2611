package app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ui.browser.BrowserRoute
import app.ui.dashboard.DashboardRoute
import app.ui.service.ServiceDetailRoute
import app.feature.settings.SettingsScreen
import app.feature.tailscale.TailscaleScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val TAILSCALE = "tailscale"
    const val BROWSER = "browser"
    const val SERVICE_DETAIL = "service/{serviceId}"
}

@Composable
fun MyAppsApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardRoute(
                onOpenService = { serviceId -> navController.navigate("service/$serviceId") },
                onAddService = {},
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenTailscale = { navController.navigate(Routes.TAILSCALE) },
                onOpenBrowser = { navController.navigate(Routes.BROWSER) },
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

        composable(Routes.BROWSER) {
            BrowserRoute(onBack = { navController.popBackStack() })
        }
    }
}
