package com.example.xrayclient.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.xrayclient.ui.MainViewModel
import com.example.xrayclient.ui.components.EngineDiagnosticsDialog
import com.example.xrayclient.ui.screens.HomeScreen
import com.example.xrayclient.ui.screens.ImportScreen
import com.example.xrayclient.ui.screens.ServersScreen
import com.example.xrayclient.ui.screens.SettingsScreen
import com.example.xrayclient.ui.screens.SubscriptionScreen
import com.example.xrayclient.ui.theme.XrayClientTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object ServersRoute
@Serializable data object ImportRoute
@Serializable data object SubscriptionsRoute
@Serializable data object SettingsRoute

private data class NavTabItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun XrayClientApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vpnSnapshot by viewModel.vpnSnapshot.collectAsStateWithLifecycle()
    val engineAvailability by viewModel.engineAvailability.collectAsStateWithLifecycle()
    val engineLogs by viewModel.engineLogs.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val cl2IsRunning by viewModel.cl2IsRunning.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val filteredServers by viewModel.filteredServers.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val protocolFilter by viewModel.protocolFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val testingServerIds by viewModel.testingServerIds.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    var showEngineDiagnostics by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.vpnPermissionRequest.collect { intent ->
            vpnPermissionLauncher.launch(intent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = remember {
        listOf(
            NavTabItem(HomeRoute, "خانه", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
            NavTabItem(ServersRoute, "سرورها", Icons.Rounded.Dns, Icons.Outlined.Dns, "nav_servers"),
            NavTabItem(ImportRoute, "افزودن", Icons.Rounded.AddCircle, Icons.Outlined.AddCircleOutline, "nav_import"),
            NavTabItem(SubscriptionsRoute, "اشتراک‌ها", Icons.Rounded.Subscriptions, Icons.Outlined.Subscriptions, "nav_subscriptions"),
            NavTabItem(SettingsRoute, "تنظیمات", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    XrayClientTheme(
        themeMode = settings.themeMode,
        language = settings.language
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute
                        ) {
                            composable<HomeRoute> {
                                HomeScreen(
                                    vpnSnapshot = vpnSnapshot,
                                    selectedServer = selectedServer,
                                    engineAvailability = engineAvailability,
                                    trafficStats = trafficStats,
                                    onToggleConnection = viewModel::toggleConnection,
                                    onOpenServers = { navigateToTab(ServersRoute) },
                                    onOpenImport = { navigateToTab(ImportRoute) },
                                    onOpenEngineDiagnostics = { showEngineDiagnostics = true }
                                )
                            }

                            composable<ServersRoute> {
                                ServersScreen(
                                    servers = filteredServers,
                                    selectedServerId = selectedServer?.id,
                                    searchQuery = searchQuery,
                                    protocolFilter = protocolFilter,
                                    sortOrder = sortOrder,
                                    testingServerIds = testingServerIds,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onProtocolFilterChange = viewModel::setProtocolFilter,
                                    onSortOrderChange = viewModel::setSortOrder,
                                    onSelectServer = viewModel::selectServer,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onTestLatency = viewModel::testServerLatency,
                                    onTestAllLatency = viewModel::testAllServersLatency,
                                    onSaveProfile = viewModel::saveProfile,
                                    onDuplicateServer = viewModel::duplicateServer,
                                    onDeleteServer = viewModel::deleteServer,
                                    exportShareUri = viewModel::exportShareUri,
                                    buildXrayJsonPreview = viewModel::buildXrayJsonPreview,
                                    onCopiedMessage = { msg ->
                                        uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable<ImportRoute> {
                                ImportScreen(
                                    onImportText = viewModel::importFromText,
                                    onAddSubscription = viewModel::addSubscription,
                                    onSaveManualProfile = viewModel::saveProfile,
                                    onMessage = { msg ->
                                        uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable<SubscriptionsRoute> {
                                SubscriptionScreen(
                                    subscriptions = subscriptions,
                                    onAddSubscription = viewModel::addSubscription,
                                    onRefreshSubscription = viewModel::refreshSubscription,
                                    onRefreshAll = viewModel::refreshAllSubscriptions,
                                    onRenameSubscription = viewModel::renameSubscription,
                                    onToggleAutoUpdate = viewModel::toggleSubscriptionAutoUpdate,
                                    onDeleteSubscription = viewModel::deleteSubscription
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    settings = settings,
                                    engineAvailability = engineAvailability,
                                    cl2IsRunning = cl2IsRunning,
                                    installedApps = installedApps,
                                    onUpdateSettings = viewModel::updateSettings,
                                    onLoadInstalledApps = viewModel::loadInstalledApps,
                                    onOpenEngineDiagnostics = { showEngineDiagnostics = true }
                                )
                            }
                        }
                    }
                }
            }

            if (showEngineDiagnostics) {
                EngineDiagnosticsDialog(
                    availability = engineAvailability,
                    logs = engineLogs,
                    onRefresh = { viewModel.refreshEngineAvailability() },
                    onDismiss = { showEngineDiagnostics = false }
                )
            }
        }
    }
}
