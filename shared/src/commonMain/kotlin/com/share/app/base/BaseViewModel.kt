package com.share.app.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything a screen renders, as one immutable value. */
interface UiState

/** Something the user did, or the platform reported, that the screen reacts to. */
interface UiIntent

/** A one-shot instruction for the screen: navigate, show a message. Never replayed. */
interface UiEffect

/**
 * MVVM with a unidirectional MVI loop.
 *
 * The screen renders [uiState], forwards every interaction through [onIntent],
 * and collects [effects] once for things that must not survive a
 * recomposition. State only ever changes through [updateState].
 */
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _uiState.value

    abstract fun onIntent(intent: I)

    protected fun updateState(reducer: S.() -> S) {
        _uiState.update { it.reducer() }
    }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
