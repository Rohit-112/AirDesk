package com.share.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.share.app.domain.analytics.AnalyticsEvent
import com.share.app.domain.analytics.AnalyticsLogger
import com.share.app.domain.model.ThemePreference
import com.share.app.ui.about.AboutScreen
import com.share.app.ui.convert.ConvertScreen
import com.share.app.ui.home.HomeScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

sealed interface Screen {
    @Serializable
    data object Home : Screen

    /** How it works, privacy and FAQ. */
    @Serializable
    data object About : Screen

    /** Convert an image on this device, with no pairing at all. */
    @Serializable
    data object Convert : Screen
}

/**
 * The session engine lives above this graph, so moving between screens never
 * tears down or duplicates the live session.
 */
@Composable
fun KnoticNavGraph(themePreference: ThemePreference, onCycleTheme: () -> Unit) {
    val navController = rememberNavController()
    ReportScreenViews(navController.currentBackStackEntryAsState().value?.destination?.route)

    NavHost(navController = navController, startDestination = Screen.Home) {
        homeScreen(
            themePreference = themePreference,
            onCycleTheme = onCycleTheme,
            onNavigateToAbout = { navController.navigate(Screen.About) { launchSingleTop = true } },
            onNavigateToConvert = { navController.navigate(Screen.Convert) { launchSingleTop = true } },
        )
        aboutScreen(onBack = { navController.navigateUp() })
        convertScreen(onBack = { navController.navigateUp() })
    }
}

/**
 * One screen view per destination. The route of a type-safe destination is its
 * serial name, so the last segment is the screen - a small, fixed set, which is
 * what Firebase expects of this dimension.
 */
@Composable
private fun ReportScreenViews(route: String?) {
    val analytics = koinInject<AnalyticsLogger>()
    val screen = route?.substringBefore('?')?.substringBefore('/')?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
    LaunchedEffect(screen) {
        if (screen != null) analytics.log(AnalyticsEvent.ScreenView(screen))
    }
}

private fun NavGraphBuilder.homeScreen(
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToConvert: () -> Unit,
) {
    composable<Screen.Home> {
        HomeScreen(
            themePreference = themePreference,
            onCycleTheme = onCycleTheme,
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToConvert = onNavigateToConvert,
        )
    }
}

private fun NavGraphBuilder.aboutScreen(onBack: () -> Unit) {
    composable<Screen.About> {
        AboutScreen(onBack = onBack)
    }
}

private fun NavGraphBuilder.convertScreen(onBack: () -> Unit) {
    composable<Screen.Convert> {
        ConvertScreen(onBack = onBack)
    }
}
