package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.VinylOrbitViewModel
import com.example.ui.screens.AllAppsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.VinylOrbitTheme

const val ROUTE_HOME = "home"
const val ROUTE_ALL_APPS = "all_apps"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VinylOrbitTheme {
                VinylOrbitApp()
            }
        }
    }
}

@Composable
fun VinylOrbitApp(
    viewModel: VinylOrbitViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDeviceState()
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(route = ROUTE_HOME) {
            HomeScreen(
                uiState = uiState,
                onToggleApp = viewModel::toggleAppAllowed,
                onOpenAllApps = { navController.navigate(ROUTE_ALL_APPS) },
                onPlayStopPreviewClick = viewModel::onWidgetPreviewPlayStopClicked,
                onServicePreviewClick = viewModel::onWidgetPreviewServiceClicked,
                onButtonStyleSelected = viewModel::setButtonStyle,
                onEmptyVinylStyleSelected = viewModel::setEmptyVinylStyle,
                onCustomEmptyVinylPicked = viewModel::saveCustomEmptyVinylImage,
                onClearArtWhenStoppedChanged = viewModel::setClearArtWhenStopped,
                loadAppIcon = viewModel::loadAppIcon
            )
        }

        composable(route = ROUTE_ALL_APPS) {
            AllAppsScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onSearchQueryChange = viewModel::updateSearchQuery,
                onFilterChange = viewModel::updateFilter,
                onToggleApp = viewModel::toggleAppAllowed,
                onResetToDefaults = viewModel::resetToDefaultServices,
                loadAppIcon = viewModel::loadAppIcon
            )
        }
    }
}
