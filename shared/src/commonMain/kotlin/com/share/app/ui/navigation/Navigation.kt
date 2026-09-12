package com.share.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.share.app.domain.model.ThemePreference
import com.share.app.ui.about.AboutScreen
import com.share.app.ui.home.HomeScreen
import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Home : Screen

    /** How it works, privacy and FAQ. */
    @Serializable
    data object About : Screen
}

/**
 * The session engine lives above this graph, so moving between screens never
 * tears down or duplicates the live session.
 */
@Composable
fun KnoticNavGraph(themePreference: ThemePreference, onCycleTheme: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home) {
        homeScreen(
            themePreference = themePreference,
            onCycleTheme = onCycleTheme,
            onNavigateToAbout = { navController.navigate(Screen.About) { launchSingleTop = true } },
        )
        aboutScreen(onBack = { navController.navigateUp() })
    }
}

private fun NavGraphBuilder.homeScreen(
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    composable<Screen.Home> {
        HomeScreen(
            themePreference = themePreference,
            onCycleTheme = onCycleTheme,
            onNavigateToAbout = onNavigateToAbout,
        )
    }
}

private fun NavGraphBuilder.aboutScreen(onBack: () -> Unit) {
    composable<Screen.About> {
        AboutScreen(onBack = onBack)
    }
}
