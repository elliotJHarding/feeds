package com.harding.feeds.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.harding.feeds.di.AppContainer
import com.harding.feeds.ui.charts.ChartsScreen
import com.harding.feeds.ui.charts.ChartsViewModel
import com.harding.feeds.ui.home.HomeScreen
import com.harding.feeds.ui.home.HomeViewModel
import com.harding.feeds.ui.onboarding.OnboardingScreen
import com.harding.feeds.ui.onboarding.OnboardingViewModel
import com.harding.feeds.ui.signin.SignInScreen

/**
 * Top level: the phase gate (signed out -> onboarding -> ready) sits above a two-route
 * NavHost (home, charts). History and feed editing live inside home, so the whole app is
 * reachable one gesture from the entry surface.
 */
@Composable
fun FeedsApp(container: AppContainer) {
    val rootViewModel: RootViewModel = viewModel { RootViewModel(container) }
    val phase by rootViewModel.phase.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        when (phase) {
            RootViewModel.Phase.Loading -> LoadingScreen()
            RootViewModel.Phase.SignedOut ->
                SignInScreen(container, onSignedIn = rootViewModel::onSignedIn)
            RootViewModel.Phase.Onboarding ->
                OnboardingScreen(viewModel { OnboardingViewModel(container) })
            RootViewModel.Phase.Ready -> ReadyNavHost(container)
        }
    }
}

@Composable
private fun ReadyNavHost(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = viewModel { HomeViewModel(container) },
                onOpenCharts = { navController.navigate("charts") },
            )
        }
        composable("charts") {
            ChartsScreen(
                vm = viewModel { ChartsViewModel(container) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
