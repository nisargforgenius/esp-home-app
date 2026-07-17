package com.example.iotcontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.iotcontroller.ui.screens.ConnectScreen
import com.example.iotcontroller.ui.screens.DashboardScreen
import com.example.iotcontroller.ui.screens.HomeScreen
import com.example.iotcontroller.ui.screens.ScheduleScreen
import com.example.iotcontroller.ui.screens.SettingsScreen
import com.example.iotcontroller.ui.theme.IoTControllerTheme
import com.example.iotcontroller.viewmodel.EspViewModel
import com.example.iotcontroller.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {

    // On Android 10+, WifiManager.connectionInfo (used to figure out which
    // /24 subnet to scan) silently returns 0.0.0.0 without a location
    // permission granted -- this breaks the network scan feature entirely
    // with no visible error, which is exactly the "scan does nothing" bug
    // seen on newer OS versions. We request it once on launch; the app still
    // works without it (manual IP entry etc.), but scanning won't find
    // anything until it's granted.
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op -- HomeScreen/ConnectScreen just won't find scan results if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val repository = (application as EspApp).repository
        val viewModel = EspViewModel(repository)
        val homeViewModel = HomeViewModel(repository)

        setContent {
            // homeViewModel.uiState.appTheme is loaded from DataStore on
            // launch and updated live whenever the user picks a new theme in
            // Settings, so this recomposes the whole app tree on change.
            IoTControllerTheme(appTheme = homeViewModel.uiState.appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel, homeViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: EspViewModel, homeViewModel: HomeViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                homeViewModel = homeViewModel,
                espViewModel = viewModel,
                onOpenDevice = { device ->
                    // Point the dashboard's ViewModel at this specific device
                    // before navigating, since EspViewModel tracks "the
                    // currently active device" for the dashboard screen.
                    viewModel.connectToDevice(device)
                    navController.navigate("dashboard")
                },
                onStartGuidedSetup = { navController.navigate("connect") },
                onEnterIpManually = { navController.navigate("connect") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                homeViewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("connect") {
            ConnectScreen(
                viewModel = viewModel,
                onNavigateToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("home")
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onOpenSchedules = { navController.navigate("schedule") }
            )
        }
        composable("schedule") {
            ScheduleScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
