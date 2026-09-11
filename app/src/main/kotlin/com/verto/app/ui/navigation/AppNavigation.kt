package com.verto.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.verto.app.feature.sync.presentation.SyncViewModel
import com.verto.app.ui.components.VertoNavigationDrawerContent
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = "splash",
) {
    val syncViewModel: SyncViewModel = hiltViewModel()
    val drawerViewModel: DrawerNavigationViewModel = hiltViewModel()
    val drawerUiState by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val drawerSyncUiState by syncViewModel.drawerSyncState.collectAsStateWithLifecycle()
    val conflictReviewUiState by syncViewModel.conflictReviewState.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "splash"
    val currentArguments = buildMap {
        navBackStackEntry?.arguments?.getString("category")?.let { put("category", it) }
        navBackStackEntry?.arguments?.getString("supplierScope")?.let { put("supplierScope", it) }
        navBackStackEntry?.arguments?.getString("purchaseScope")?.let { put("purchaseScope", it) }
        navBackStackEntry?.arguments?.getBoolean("isSupplier")?.let { put("isSupplier", it.toString()) }
    }
    val selectedDestination = DrawerDestinationRegistry.selectedDestination(
        currentRoutePattern = currentRoute,
        currentArguments = currentArguments,
    )
    val selectedSection = DrawerDestinationRegistry.selectedSection(currentRoute, currentArguments)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val originalLayoutDirection = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    val drawerWidth = minOf(configuration.screenWidthDp * 0.85f, 400f).dp
    val drawerEnabled = currentRoute !in AUTHENTICATION_ROUTES

    LaunchedEffect(drawerEnabled) {
        if (!drawerEnabled && drawerState.isOpen) drawerState.close()
    }

    LaunchedEffect(drawerState.currentValue, currentRoute) {
        if (drawerState.isOpen) drawerViewModel.alignToCurrentRoute(selectedSection)
    }

    LaunchedEffect(currentRoute, selectedDestination, drawerUiState.isReady) {
        val destination = selectedDestination ?: return@LaunchedEffect
        if (drawerUiState.isReady && !DrawerDestinationRegistry.canAccess(
                destination = destination,
                permissions = drawerUiState.permissions,
                role = drawerUiState.role,
            )
        ) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }


    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    fun navigateFromDrawer(destinationId: String) {
        val destination = DrawerDestinationRegistry.destinationById(destinationId) ?: return
        if (!DrawerDestinationRegistry.canAccess(
                destination = destination,
                permissions = drawerUiState.permissions,
                role = drawerUiState.role,
            )
        ) return

        scope.launch {
            drawerState.close()
            if (selectedDestination?.id == destination.id) return@launch
            navController.navigate(destination.route) {
                popUpTo(Screen.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun navigateHome() {
        if (drawerUiState.role.isNullOrBlank()) return
        scope.launch {
            drawerState.close()
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerEnabled,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(drawerWidth),
                ) {
                    VertoNavigationDrawerContent(
                        uiState = drawerUiState,
                        syncState = drawerSyncUiState,
                        conflictReviewState = conflictReviewUiState,
                        selectedDestinationId = selectedDestination?.id,
                        homeSelected = currentRoute == Screen.Home.route,
                        onHomeClick = ::navigateHome,
                        onDestinationClick = ::navigateFromDrawer,
                        onSectionToggle = drawerViewModel::toggleSection,
                        onSyncNow = syncViewModel::push,
                        onOpenConflictReview = syncViewModel::refreshConflictReviews,
                        onAcceptServerConflict = syncViewModel::acceptServerConflict,
                        onResendLocalConflict = syncViewModel::resendLocalConflict,
                        onClose = { scope.launch { drawerState.close() } },
                    )
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides originalLayoutDirection) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                ) {
                    authGraph(navController, syncViewModel)
                    homeClientsGraph(
                        navController = navController,
                        onOpenDrawer = {
                            if (drawerEnabled) scope.launch {
                                drawerViewModel.alignToCurrentRoute(selectedSection)
                                drawerState.open()
                            }
                        },
                    )
                    invoiceGraph(navController)
                    adminGraph(navController)
                    reportsGraph(navController)
                    settingsGraph(navController)
                    inventoryShipmentsGraph(navController)
                }
            }
        }
    }
}

private val AUTHENTICATION_ROUTES = setOf(
    "splash",
    Screen.Login.route,
    Screen.Register.route,
    Screen.JoinOrg.route,
    Screen.ResetPassword.route,
    Screen.PasswordResetSent.route,
)
