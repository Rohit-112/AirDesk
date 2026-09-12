package com.share.app.ui.root

import androidx.lifecycle.viewModelScope
import com.share.app.base.BaseViewModel
import com.share.app.base.UiEffect
import com.share.app.base.UiIntent
import com.share.app.base.UiState
import com.share.app.domain.model.ThemePreference
import com.share.app.domain.usecase.PreferencesUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class RootUiState(val themePreference: ThemePreference = ThemePreference.SYSTEM) : UiState

sealed interface RootIntent : UiIntent {
    data object CycleTheme : RootIntent
}

sealed interface RootEffect : UiEffect

/** App-wide concerns that outlive any one screen: currently the theme. */
class RootViewModel(
    private val preferencesUseCase: PreferencesUseCase,
) : BaseViewModel<RootUiState, RootIntent, RootEffect>(RootUiState()) {

    init {
        preferencesUseCase.themePreference
            .onEach { preference -> updateState { copy(themePreference = preference) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: RootIntent) {
        when (intent) {
            RootIntent.CycleTheme -> viewModelScope.launch { preferencesUseCase.cycleThemePreference() }
        }
    }
}
