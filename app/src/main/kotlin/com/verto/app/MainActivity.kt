package com.verto.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.verto.app.notifications.InternalNavigationMailbox
import com.verto.app.startup.DeferredStartupCoordinator
import com.verto.app.ui.navigation.AppNavigation
import com.verto.app.ui.navigation.Screen
import com.verto.app.ui.theme.VertoTheme
import com.verto.app.utils.AppFontSize
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: PreferencesManager
    @Inject lateinit var deferredStartup: DeferredStartupCoordinator

    /**
     * External/internal navigation requests are queued until authentication admission succeeds.
     * No intent is allowed to bypass the splash/session authorization boundary.
     */
    private val pendingNavRoutes = Channel<String>(Channel.BUFFERED)
    private val pendingNavRouteFlow = pendingNavRoutes.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        enqueueNavigationRequest(intent)
        InternalNavigationMailbox.consume()?.let { route -> pendingNavRoutes.trySend(route) }

        setContent {
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.AUTO)
            val appFontSize by prefs.appFontSize.collectAsStateWithLifecycle(initialValue = AppFontSize.MEDIUM)

            VertoTheme(
                themeMode = themeMode,
                appFontSize = appFontSize,
            ) {
                val navController = rememberNavController()
                var admissionGranted by remember { mutableStateOf(false) }

                // HOME is the first route reached only after a successful auth decision/login.
                // Returning to any auth route revokes admission for subsequent external intents.
                LaunchedEffect(navController) {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val route = entry.destination.route
                        when {
                            route == Screen.Home.route -> {
                                if (!admissionGranted) {
                                    admissionGranted = true
                                    deferredStartup.start()
                                }
                            }
                            route in authenticationRoutes -> admissionGranted = false
                        }
                    }
                }

                LaunchedEffect(navController) {
                    pendingNavRouteFlow.collect { route ->
                        snapshotFlow { admissionGranted }.first { it }
                        if (navController.currentDestination?.route != route) {
                            navController.navigate(route)
                        }
                    }
                }

                AppNavigation(
                    navController = navController,
                    startDestination = "splash",
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueNavigationRequest(intent)
        InternalNavigationMailbox.consume()?.let { route -> pendingNavRoutes.trySend(route) }
    }

    private fun enqueueNavigationRequest(intent: Intent?) {
        intent?.getStringExtra("startRoute")
            ?.takeIf { it == Screen.Home.route || it == Screen.Inventory.route }
            ?.let { route -> pendingNavRoutes.trySend(route) }
    }

    companion object {
        private val authenticationRoutes = setOf(
            "splash",
            Screen.Login.route,
            Screen.Register.route,
            Screen.JoinOrg.route,
            Screen.PasswordResetSent.route,
            Screen.ResetPassword.route,
        )
    }
}
