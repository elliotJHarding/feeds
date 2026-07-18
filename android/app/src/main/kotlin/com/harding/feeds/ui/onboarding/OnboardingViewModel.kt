package com.harding.feeds.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harding.feeds.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Group + baby onboarding. The visible step is derived from cached state (no group ->
 * choose; group but no baby -> baby setup) plus one local flag: the invite code shown
 * right after creating a group. The root phase machine flips to Ready by itself once a
 * baby lands in Room, which ends the flow.
 */
class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    val group = container.groupRepository.group()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var inviteCode by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun createGroup() = op {
        container.groupRepository.createGroup()
        inviteCode = container.groupRepository.inviteCode()
    }

    fun dismissInviteCode() {
        inviteCode = null
    }

    fun joinGroup(code: String) = op {
        container.groupRepository.joinGroup(code.trim())
        // Pull babies + feeds now so the root gate lands on Ready (or baby setup) with data.
        container.syncEngine.sync()
    }

    fun createBaby(name: String, dateOfBirth: LocalDate?) = op {
        container.babyRepository.createBaby(name.trim(), dateOfBirth)
    }

    private fun op(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            runCatching { block() }.onFailure { error = it.message ?: "Something went wrong" }
            busy = false
        }
    }
}
