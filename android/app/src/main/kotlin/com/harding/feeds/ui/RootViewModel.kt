package com.harding.feeds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harding.feeds.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Decides which top-level surface is showing. The app is Ready only once a group AND a baby
 * exist locally; anything less (fresh sign-in, group created but baby not set up yet) routes
 * through onboarding. A cached group+baby short-circuits to Ready so a normal app open never
 * waits on the network.
 */
class RootViewModel(private val container: AppContainer) : ViewModel() {

    sealed interface Phase {
        data object Loading : Phase
        data object SignedOut : Phase
        data object Onboarding : Phase
        data object Ready : Phase
    }

    private val signedIn = MutableStateFlow(container.authRepository.isLoggedIn)
    private val serverChecked = MutableStateFlow(false)

    val phase: StateFlow<Phase> = combine(
        signedIn,
        serverChecked,
        container.groupRepository.group(),
        container.babyRepository.babies(),
    ) { isSignedIn, checked, group, babies ->
        when {
            !isSignedIn -> Phase.SignedOut
            group != null && babies.isNotEmpty() -> Phase.Ready
            !checked -> Phase.Loading
            else -> Phase.Onboarding
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Phase.Loading)

    init {
        if (signedIn.value) refreshFromServer()
    }

    fun onSignedIn() {
        signedIn.value = true
        refreshFromServer()
    }

    /**
     * Pulls group membership (a device may be signing in to an account that already has a
     * group) and, when a group exists, runs a full sync so babies and feeds are present
     * before Ready is decided. Failures (offline) still mark the check done - cached state
     * then decides the phase.
     */
    private fun refreshFromServer() {
        viewModelScope.launch {
            runCatching {
                if (container.groupRepository.refreshGroup() != null) {
                    container.syncEngine.sync()
                }
            }
            serverChecked.value = true
        }
    }
}
