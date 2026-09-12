package com.share.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.share.app.ui.components.BackgroundMesh
import com.share.app.ui.navigation.KnoticNavGraph
import com.share.app.ui.root.RootIntent
import com.share.app.ui.root.RootViewModel
import com.share.app.ui.theme.KnoticTheme
import org.koin.compose.viewmodel.koinViewModel

/** The shared root composable, hosted by Android, iOS and desktop alike. */
@Composable
fun App() {
    val rootViewModel: RootViewModel = koinViewModel()
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()

    KnoticTheme(preference = rootState.themePreference) {
        Box(Modifier.fillMaxSize().background(KnoticTheme.colors.bg)) {
            BackgroundMesh()
            KnoticNavGraph(
                themePreference = rootState.themePreference,
                onCycleTheme = { rootViewModel.onIntent(RootIntent.CycleTheme) },
            )
        }
    }
}
