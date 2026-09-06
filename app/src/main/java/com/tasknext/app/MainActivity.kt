package com.tasknext.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tasknext.app.ui.HistoryScreen
import com.tasknext.app.ui.HomeScreen
import com.tasknext.app.ui.SettingsScreen
import com.tasknext.app.ui.TaskListScreen
import com.tasknext.app.ui.TomorrowScreen
import com.tasknext.app.ui.theme.TaskNextTheme
import com.tasknext.app.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* user's choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.settings.notificationsEnabled) {
            requestNotificationPermissionIfNeeded()
        }

        setContent {
            var darkTheme by remember { mutableStateOf(viewModel.settings.darkTheme) }

            TaskNextTheme(darkTheme = darkTheme) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                val navController = rememberNavController()
                fun goHome() {
                    if (!navController.popBackStack("home", inclusive = false)) {
                        navController.navigate("home") { launchSingleTop = true }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } }
                        )
                    }
                    composable("list") {
                        TaskListScreen(
                            viewModel = viewModel,
                            onBack = { goHome() }
                        )
                    }
                    composable("tomorrow") {
                        TomorrowScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenHistory = { navController.navigate("history") },
                            onDarkThemeChanged = { darkTheme = it },
                            onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() }
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            viewModel = viewModel,
                            onBack = { goHome() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
