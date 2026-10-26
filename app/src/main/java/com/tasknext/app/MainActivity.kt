package com.tasknext.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.createGraph
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.Locale
import com.tasknext.app.data.AppLocale
import com.tasknext.app.data.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import com.tasknext.app.ui.HistoryScreen
import com.tasknext.app.ui.HomeScreen
import com.tasknext.app.ui.SettingsScreen
import com.tasknext.app.ui.TaskListScreen
import com.tasknext.app.ui.TomorrowScreen
import com.tasknext.app.ui.theme.TaskNextTheme
import com.tasknext.app.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        AppLocale.initialize(newBase)
        Locale.setDefault(Locale.forLanguageTag(AppLocale.languageCode(newBase)))
        super.attachBaseContext(AppLocale.localizedContext(newBase))
    }

    private var openRequest by mutableStateOf<TaskOpenRequest?>(null)

    private val viewModel: TaskViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* user's choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openRequest = if (savedInstanceState?.containsKey("pendingTaskDestination") == true) {
            TaskOpenRequest(savedInstanceState.getLong("pendingTaskDestination").takeIf { it > 0 })
        } else if (savedInstanceState == null) TaskDestination.request(intent) else null

        setContent {
            LaunchedEffect(viewModel) {
                viewModel.notificationPermissionRequests.collect { requestNotificationPermissionIfNeeded() }
            }
            var themeMode by remember { mutableStateOf(viewModel.settings.themeMode) }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            TaskNextTheme(darkTheme = darkTheme) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                val navController = rememberNavController()
                fun goHome() {
                    if (navController.currentDestination?.route == "home") return
                    if (!navController.popBackStack("home", inclusive = false)) {
                        navController.navigate("home") { launchSingleTop = true }
                    }
                }

                LaunchedEffect(openRequest) {
                    if (openRequest != null) goHome()
                }

                // Keep destination identities stable when a new Intent or theme change
                // recomposes this scope, including while a pop transition is running.
                val graph = remember(navController) {
                    navController.createGraph(startDestination = "home") {
                        composable("home") { entry ->
                            val homeLifecycle by entry.lifecycle.currentStateFlow.collectAsState()
                            HomeScreen(
                                viewModel = viewModel,
                                // Consume only after navigation has resumed Home. An outgoing
                                // or transitioning entry must not display/consume the request.
                                openRequest = openRequest.takeIf { homeLifecycle == Lifecycle.State.RESUMED },
                                onOpenRequestHandled = { openRequest = null },
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
                                onThemeChanged = { themeMode = it },
                                onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() },
                                onLanguageChanged = {
                                    if (Build.VERSION.SDK_INT < 33) recreate()
                                }
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
                NavHost(navController = navController, graph = graph)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest = TaskDestination.request(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        openRequest?.let { outState.putLong("pendingTaskDestination", it.taskId ?: -1L) }
        super.onSaveInstanceState(outState)
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
