package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppTaskItem
import com.example.ui.TaskPulseUiState
import com.example.ui.components.AppIconImage
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun WhitelistScreen(
    uiState: TaskPulseUiState,
    onToggleWhitelist: (String) -> Unit,
    onClearWhitelist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalTelemetryColors.current
    var search by remember { mutableStateOf("") }

    val userWhitelisted = remember(uiState.allApps, search) {
        val q = search.trim().lowercase()
        uiState.allApps.filter {
            it.isWhitelisted && (q.isEmpty() || it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q))
        }
    }

    val coreProtected = remember(uiState.allApps, search) {
        val q = search.trim().lowercase()
        uiState.allApps.filter {
            (it.isCoreCritical || it.isSelf) && !it.isWhitelisted &&
                (q.isEmpty() || it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q))
        }
    }

    val unprotectedCandidates = remember(uiState.allApps, search) {
        val q = search.trim().lowercase()
        uiState.allApps.filter {
            !it.isWhitelisted && !it.isCoreCritical && !it.isSelf && !it.isSystemApp &&
                (q.isEmpty() || it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q))
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Telemetry Card
            item(key = "whitelist_stats") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = semantic.protectedAccent
                            )
                            Text(
                                text = "Protected Whitelist",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        Text(
                            text = "Apps in your whitelist and essential OS processes are always skipped during One-Tap Stop.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatTile(
                                label = "Whitelisted",
                                value = "${uiState.preferences.whitelistedPackages.size}",
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Total Stopped",
                                value = "${uiState.preferences.totalKilledCount}",
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "RAM Reclaimed",
                                value = "${uiState.preferences.totalRamFreedMb} MB",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Search input
            item(key = "whitelist_search") {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    placeholder = { Text("Filter protected or installed apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whitelist_search_input")
                )
            }

            // User Whitelist Section Header
            item(key = "user_whitelist_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Whitelist (${userWhitelisted.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (uiState.preferences.whitelistedPackages.isNotEmpty()) {
                        TextButton(onClick = onClearWhitelist) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear All")
                        }
                    }
                }
            }

            if (userWhitelisted.isEmpty()) {
                item(key = "whitelist_empty") {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No custom apps whitelisted yet",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Toggle any app below to keep music players, alarms, or messaging apps running during One-Tap Stop.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(items = userWhitelisted, key = { "wl_${it.packageName}" }) { app ->
                    WhitelistAppRow(
                        app = app,
                        checked = true,
                        locked = false,
                        onToggle = { onToggleWhitelist(app.packageName) }
                    )
                }
            }

            // Quick Add User Apps
            if (unprotectedCandidates.isNotEmpty()) {
                item(key = "quick_add_header") {
                    Text(
                        text = "Installed User Apps (${unprotectedCandidates.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(items = unprotectedCandidates, key = { "cand_${it.packageName}" }) { app ->
                    WhitelistAppRow(
                        app = app,
                        checked = false,
                        locked = false,
                        onToggle = { onToggleWhitelist(app.packageName) }
                    )
                }
            }

            // Essential System Guards
            if (coreProtected.isNotEmpty()) {
                item(key = "core_guards_header") {
                    Text(
                        text = "Essential System Processes (Always Protected)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(items = coreProtected, key = { "core_${it.packageName}" }) { app ->
                    WhitelistAppRow(
                        app = app,
                        checked = true,
                        locked = true,
                        onToggle = {}
                    )
                }
            }

            item(key = "whitelist_bottom_space") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WhitelistAppRow(
    app: AppTaskItem,
    checked: Boolean,
    locked: Boolean,
    onToggle: () -> Unit
) {
    val semantic = LocalTelemetryColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconImage(
                drawable = app.icon,
                packageName = app.packageName,
                contentDescription = app.appName,
                size = 40.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (locked) {
                StatusPill(
                    text = "CORE",
                    textColor = semantic.protectedAccent,
                    containerColor = semantic.protectedContainer
                )
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Always protected",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Switch(
                    checked = checked,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("whitelist_switch_${app.packageName}")
                )
            }
        }
    }
}
