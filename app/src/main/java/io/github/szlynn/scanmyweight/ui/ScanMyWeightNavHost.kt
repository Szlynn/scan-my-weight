package io.github.szlynn.scanmyweight.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.szlynn.scanmyweight.data.SettingsRepository
import io.github.szlynn.scanmyweight.ui.scan.ScanScreen
import io.github.szlynn.scanmyweight.ui.settings.SettingsScreen

private const val ROUTE_SCAN = "scan"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun ScanMyWeightNavHost(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_SCAN,
        modifier = modifier,
    ) {
        composable(ROUTE_SCAN) {
            ScanScreen(
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                repository = settingsRepository,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
