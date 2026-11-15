package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppFilter
import com.example.model.AppRunState
import com.example.model.AppTaskItem
import com.example.model.EffectiveMode
import com.example.model.KillSummary
import com.example.model.SortOption
import com.example.ui.TaskPulseUiState
import com.example.ui.components.AppIconImage
import com.example.ui.components.ForceStopQueueCard
import com.example.ui.components.RamTelemetryHeroCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale
import kotlin.math.max

@Composable
fun MonitorScreen(
    uiState: TaskPulseUiState,
    onRefresh: () -> Unit,
    onPrimaryKillClick: () -> Unit,
    onFilterSelected: (AppFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onToggleShowSystem: (Boolean) -> Unit,
    onToggleSelectApp: (String) -> Unit,
    onSelectAllKillable: () -> Unit,
    onClearSelection: () -> Unit,
    onProtectSelected: () -> Unit,
    onInspectApp: (String) -> Unit,
    onStopSingleApp: (AppTaskItem) -> Unit,
    onToggleWhitelist: (String) -> Unit,
    onStartForceStopQueue: (List<String>) -> Unit,
    onLaunchQueueAppInfo: (String) -> Unit,
    onSkipQueueItem: () -> Unit,
    onDismissQueue: () -> Unit,
    onDismissSummary: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalTelemetryColors.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showFloatingStop by remember(uiState.activeKillableCount, uiState.selectedPackages.size, uiState.isExecutingKill) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 &&
                !uiState.isExecutingKill &&
                (uiState.selectedPackages.isNotEmpty() || uiState.activeKillableCount > 0)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero_telemetry") {
                RamTelemetryHeroCard(
                    memoryTelemetry = uiState.memoryTelemetry,
                    effectiveMode = uiState.effectiveMode,
                    environmentInfo = uiState.environmentInfo,
                    activeKillableCount = uiState.activeKillableCount,
                    selectedCount = uiState.selectedPackages.size,
                    runningProcessCount = uiState.runningProcessCount,
                    isExecutingKill = uiState.isExecutingKill,
                    oneTapStopProgress = uiState.oneTapStopProgress,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    onPrimaryKillClick = onPrimaryKillClick,
                    onRequestUsageAccess = onOpenUsageAccess
                )
            }

            // Guided Force-Stop Queue (Non-Root deep stop stepper)
            if (uiState.forceStopQueue.isActive) {
                item(key = "force_stop_queue") {
                    val currentApp = uiState.allApps.firstOrNull {
                        it.packageName == uiState.forceStopQueue.currentPackage
                    }
                    ForceStopQueueCard(
                        queueState = uiState.forceStopQueue,
                        currentApp = currentApp,
                        onOpenAppInfo = onLaunchQueueAppInfo,
                        onSkip = onSkipQueueItem,
                        onDismiss = onDismissQueue
                    )
                }
            }

            // Last Kill Summary Banner
            uiState.lastKillSummary?.let { summary ->
                item(key = "kill_summary") {
                    KillSummaryBanner(
                        summary = summary,
                        onStartQueue = { onStartForceStopQueue(summary.remainingActivePackages) },
                        onDismiss = onDismissSummary
                    )
                }
            }

            // Search + Sort Row
            item(key = "search_and_sort") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        placeholder = { Text("Search apps or packages...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("app_search_input")
                    )

                    Box {
                        Surface(
                            onClick = { sortMenuExpanded = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort apps",
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = uiState.preferences.sortOption.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onSortSelected(option)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Filter Chips Row
            item(key = "filter_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppFilter.entries.forEach { filter ->
                        val countLabel = when (filter) {
                            AppFilter.ACTIVE -> " (${uiState.activeAppCount})"
                            AppFilter.STOPPED -> " (${uiState.stoppedAppCount})"
                            AppFilter.ALL -> " (${uiState.allApps.size})"
                            AppFilter.SYSTEM -> ""
                        }
                        FilterChip(
                            selected = uiState.activeFilter == filter,
                            onClick = { onFilterSelected(filter) },
                            label = { Text("${filter.label}$countLabel") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }

                    FilterChip(
                        selected = uiState.preferences.showSystemApps,
                        onClick = { onToggleShowSystem(!uiState.preferences.showSystemApps) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Include System") }
                    )
                }
            }

            // Selection Contextual Toolbar
            item(key = "selection_bar") {
                AnimatedVisibility(visible = uiState.selectedPackages.isNotEmpty() || uiState.filteredApps.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.selectedPackages.isNotEmpty()) {
                                "${uiState.selectedPackages.size} selected"
                            } else {
                                "${uiState.filteredApps.size} apps shown"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (uiState.selectedPackages.isNotEmpty()) {
                                TextButton(onClick = onProtectSelected) {
                                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Protect")
                                }
                                TextButton(onClick = onClearSelection) {
                                    Text("Clear")
                                }
                            } else {
                                TextButton(onClick = onSelectAllKillable) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Select Active")
                                }
                            }
                        }
                    }
                }
            }

            // Loading / Empty / Task List
            if (uiState.isInitialLoading) {
                item(key = "initial_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Probing system processes & root shell...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (uiState.filteredApps.isEmpty()) {
                item(key = "empty_state") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = semantic.protectedAccent,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "All clear in this view",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "No matching apps found. Try switching filters or tapping Refresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.filteredApps,
                    key = { it.packageName }
                ) { app ->
                    AppTaskRowCard(
                        app = app,
                        isSelected = app.packageName in uiState.selectedPackages,
                        effectiveMode = uiState.effectiveMode,
                        onToggleSelect = { onToggleSelectApp(app.packageName) },
                        onInspect = { onInspectApp(app.packageName) },
                        onStopClick = { onStopSingleApp(app) },
                        onToggleWhitelist = { onToggleWhitelist(app.packageName) }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        AnimatedVisibility(
            visible = showFloatingStop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = onPrimaryKillClick,
                containerColor = semantic.stopAction,
                contentColor = Color.White,
                icon = {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                },
                text = {
                    Text(
                        if (uiState.selectedPackages.isNotEmpty()) {
                            "Stop (${uiState.selectedPackages.size})"
                        } else {
                            "One-Tap Stop (${uiState.activeKillableCount})"
                        }
                    )
                },
                modifier = Modifier.testTag("floating_one_tap_stop_fab")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KillSummaryBanner(
    summary: KillSummary,
    onStartQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalTelemetryColors.current
    val hasRemaining = !summary.usedRoot && summary.remainingActivePackages.isNotEmpty()

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (hasRemaining) {
                semantic.nonRootBadgeContainer.copy(alpha = 0.55f)
            } else {
                semantic.protectedContainer.copy(alpha = 0.65f)
            }
        ),
        border = BorderStroke(
            1.dp,
            (if (hasRemaining) semantic.nonRootBadge else semantic.protectedAccent).copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("one_tap_stop_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (hasRemaining) semantic.nonRootBadge else semantic.protectedAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = summary.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Dismiss summary", modifier = Modifier.size(18.dp))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    text = "${summary.stoppedCount} STOPPED",
                    textColor = semantic.stopAction,
                    containerColor = semantic.stopActionContainer
                )
                StatusPill(
                    text = "+${summary.freedRamMb} MB FREED",
                    textColor = semantic.rootBadge,
                    containerColor = semantic.rootBadgeContainer
                )
                if (summary.skippedWhitelistCount > 0) {
                    StatusPill(
                        text = "${summary.skippedWhitelistCount} WHITELISTED",
                        textColor = semantic.protectedAccent,
                        containerColor = semantic.protectedContainer
                    )
                }
                if (summary.skippedSystemCount > 0) {
                    StatusPill(
                        text = "${summary.skippedSystemCount} SYSTEM KEPT",
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            if (hasRemaining) {
                TextButton(
                    onClick = onStartQueue,
                    colors = ButtonDefaults.textButtonColors(contentColor = semantic.nonRootBadge),
                    modifier = Modifier.testTag("launch_guided_queue_button")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Guided Force-Stop Queue (${summary.remainingActivePackages.size} apps)")
                }
            }
        }
    }
}

@Composable
private fun AppTaskRowCard(
    app: AppTaskItem,
    isSelected: Boolean,
    effectiveMode: EffectiveMode,
    onToggleSelect: () -> Unit,
    onInspect: () -> Unit,
    onStopClick: () -> Unit,
    onToggleWhitelist: () -> Unit
) {
    val semantic = LocalTelemetryColors.current
    val isStoppable = !app.isSelf && app.runState != AppRunState.STOPPED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect)
            .testTag("app_card_${app.packageName}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                enabled = !app.isSelf && !app.isCoreCritical,
                modifier = Modifier.minimumInteractiveComponentSize()
            )

            AppIconImage(
                drawable = app.icon,
                packageName = app.packageName,
                contentDescription = app.appName,
                size = 44.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = buildTelemetrySubtitle(app),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (app.runState) {
                        AppRunState.RUNNING_PROCESS -> StatusPill(
                            text = if (app.pids.size > 1) "${app.pids.size} PIDS" else "RUNNING",
                            textColor = semantic.rootBadge,
                            containerColor = semantic.rootBadgeContainer
                        )
                        AppRunState.ACTIVE_RECENT -> StatusPill(
                            text = "ACTIVE",
                            textColor = semantic.nonRootBadge,
                            containerColor = semantic.nonRootBadgeContainer
                        )
                        AppRunState.STOPPED -> StatusPill(
                            text = "STOPPED",
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    if (app.isWhitelisted) {
                        StatusPill(
                            text = "PROTECTED",
                            textColor = semantic.protectedAccent,
                            containerColor = semantic.protectedContainer
                        )
                    } else if (app.isCoreCritical) {
                        StatusPill(
                            text = "CORE",
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else if (app.isSystemApp) {
                        StatusPill(
                            text = "SYS",
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // Protect / Whitelist toggle
            IconButton(
                onClick = onToggleWhitelist,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("protect_button_${app.packageName}")
            ) {
                Icon(
                    imageVector = if (app.isWhitelisted) Icons.Filled.Shield else Icons.Outlined.Shield,
                    contentDescription = if (app.isWhitelisted) "Remove from protected list" else "Protect app",
                    tint = if (app.isWhitelisted) semantic.protectedAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Individual Kill / Stop button
            IconButton(
                onClick = onStopClick,
                enabled = isStoppable,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("stop_button_${app.packageName}")
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = if (effectiveMode == EffectiveMode.ROOT_ACTIVE) {
                        "Force stop ${app.appName}"
                    } else {
                        "Kill background processes of ${app.appName}"
                    },
                    tint = if (isStoppable) {
                        semantic.stopAction
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }
                )
            }
        }
    }
}

private fun buildTelemetrySubtitle(app: AppTaskItem): String {
    val parts = mutableListOf<String>()
    if (app.memoryMb > 0.5) {
        parts += String.format(Locale.US, "%.1f MB", app.memoryMb)
    }
    if (app.lastUsedMillis > 0L) {
        val diffMin = max(0L, (System.currentTimeMillis() - app.lastUsedMillis) / 60_000L)
        parts += when {
            diffMin < 1L -> "Just now"
            diffMin < 60L -> "${diffMin}m ago"
            else -> "${diffMin / 60L}h ago"
        }
    }
    if (parts.isEmpty()) {
        parts += app.packageName
    }
    return parts.joinToString(" · ")
}
