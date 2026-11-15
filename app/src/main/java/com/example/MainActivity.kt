package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.EffectiveMode
import com.example.ui.TaskPulseTab
import com.example.ui.TaskPulseViewModel
import com.example.ui.components.AppInspectorBottomSheet
import com.example.ui.components.StatusPill
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.WhitelistScreen
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.TaskPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskPulseTheme {
                TaskPulseApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPulseApp(
    viewModel: TaskPulseViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semantic = LocalTelemetryColors.current
    var currentTab by rememberSaveable { mutableStateOf(TaskPulseTab.MONITOR) }

    val queueAppInfoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReturnedFromAppInfo()
    }

    val settingsReturnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshAll()
    }

    BackHandler(enabled = currentTab != TaskPulseTab.MONITOR || uiState.selectedPackages.isNotEmpty()) {
        if (uiState.selectedPackages.isNotEmpty()) {
            viewModel.clearSelection()
        } else {
            currentTab = TaskPulseTab.MONITOR
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        val isRoot = uiState.effectiveMode == EffectiveMode.ROOT_ACTIVE
                        StatusPill(
                            text = if (isRoot) "ROOT" else "NON-ROOT",
                            textColor = if (isRoot) semantic.rootBadge else semantic.nonRootBadge,
                            containerColor = if (isRoot) semantic.rootBadgeContainer else semantic.nonRootBadgeContainer,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TaskPulseTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tabTitle(tab)
                                    )
                                },
                                label = { Text(tabTitle(tab)) },
                                modifier = Modifier.testTag("nav_${tab.route}")
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
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TaskPulseTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tabTitle(tab),
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = { Text(tabTitle(tab)) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                when (currentTab) {
                    TaskPulseTab.MONITOR -> MonitorScreen(
                        uiState = uiState,
                        onRefresh = viewModel::refreshAll,
                        onPrimaryKillClick = viewModel::killSelectedOrAllActive,
                        onFilterSelected = viewModel::setFilter,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onSortSelected = viewModel::setSortOption,
                        onToggleShowSystem = viewModel::setShowSystemApps,
                        onToggleSelectApp = viewModel::toggleSelectPackage,
                        onSelectAllKillable = viewModel::selectAllVisibleKillable,
                        onClearSelection = viewModel::clearSelection,
                        onProtectSelected = viewModel::protectSelected,
                        onInspectApp = { pkg -> viewModel.inspectApp(pkg) },
                        onStopSingleApp = viewModel::stopSingleApp,
                        onToggleWhitelist = viewModel::toggleWhitelist,
                        onStartForceStopQueue = viewModel::startForceStopQueue,
                        onLaunchQueueAppInfo = { pkg ->
                            viewModel.markQueueSettingsLaunched()
                            runCatching {
                                queueAppInfoLauncher.launch(viewModel.buildAppDetailsIntent(pkg))
                            }
                        },
                        onSkipQueueItem = viewModel::skipQueueItem,
                        onDismissQueue = viewModel::dismissQueue,
                        onDismissSummary = viewModel::dismissKillSummary,
                        onOpenUsageAccess = {
                            runCatching {
                                settingsReturnLauncher.launch(viewModel.buildUsageAccessIntent())
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    TaskPulseTab.WHITELIST -> WhitelistScreen(
                        uiState = uiState,
                        onToggleWhitelist = viewModel::toggleWhitelist,
                        onClearWhitelist = viewModel::clearWhitelist,
                        modifier = Modifier.weight(1f)
                    )

                    TaskPulseTab.SYSTEM -> DiagnosticsScreen(
                        uiState = uiState,
                        onSelectExecutionMode = viewModel::setExecutionMode,
                        onReprobeRoot = viewModel::reprobeRoot,
                        onOpenUsageAccess = {
                            runCatching {
                                settingsReturnLauncher.launch(viewModel.buildUsageAccessIntent())
                            }
                        },
                        onClearLogs = viewModel::clearLogs,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    uiState.inspectedApp?.let { inspected ->
        AppInspectorBottomSheet(
            app = inspected,
            effectiveMode = uiState.effectiveMode,
            onDismiss = { viewModel.inspectApp(null) },
            onForceStop = viewModel::stopSingleApp,
            onKillPid = viewModel::killSinglePid,
            onOpenAppInfo = { pkg ->
                runCatching {
                    settingsReturnLauncher.launch(viewModel.buildAppDetailsIntent(pkg))
                }
            },
            onToggleWhitelist = viewModel::toggleWhitelist
        )
    }
}

@Composable
private fun tabTitle(tab: TaskPulseTab): String = when (tab) {
    TaskPulseTab.MONITOR -> stringResource(id = R.string.nav_monitor)
    TaskPulseTab.WHITELIST -> stringResource(id = R.string.nav_whitelist)
    TaskPulseTab.SYSTEM -> stringResource(id = R.string.nav_diagnostics)
}

private fun tabIcon(tab: TaskPulseTab, selected: Boolean) = when (tab) {
    TaskPulseTab.MONITOR -> if (selected) Icons.Filled.Memory else Icons.Outlined.Memory
    TaskPulseTab.WHITELIST -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
    TaskPulseTab.SYSTEM -> if (selected) Icons.Filled.Terminal else Icons.Outlined.Terminal
}
