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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.EffectiveMode
import com.example.model.ExecutionMode
import com.example.model.ShellLogEntry
import com.example.ui.TaskPulseUiState
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun DiagnosticsScreen(
    uiState: TaskPulseUiState,
    onSelectExecutionMode: (ExecutionMode) -> Unit,
    onReprobeRoot: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalTelemetryColors.current
    val root = uiState.rootStatus
    val env = uiState.environmentInfo

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
            // Execution Mode Selector
            item(key = "execution_mode_card") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Task Engine Mode",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        ExecutionMode.entries.forEach { mode ->
                            val selected = uiState.preferences.executionMode == mode
                            val disabled = mode == ExecutionMode.ROOT && !root.isRootGranted
                            Surface(
                                onClick = { if (!disabled) onSelectExecutionMode(mode) },
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mode_option_${mode.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { if (!disabled) onSelectExecutionMode(mode) },
                                        enabled = !disabled
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.label + if (disabled) " (Root Required)" else "",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (disabled) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Root Access Status Card
            item(key = "root_diagnostics_card") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Root Capability Probe",
                                style = MaterialTheme.typography.titleMedium
                            )
                            StatusPill(
                                text = if (root.isRootGranted) "ROOT GRANTED" else "NON-ROOTED",
                                textColor = if (root.isRootGranted) semantic.rootBadge else semantic.nonRootBadge,
                                containerColor = if (root.isRootGranted) semantic.rootBadgeContainer else semantic.nonRootBadgeContainer
                            )
                        }

                        InfoRow("su Binary", root.suPath ?: "Not detected")
                        InfoRow("Root Shell", root.suVersion ?: "Unavailable")
                        InfoRow("UID Verification", root.uidOutput)
                        InfoRow("SELinux Status", root.selinuxState)
                        InfoRow(
                            "Active Engine",
                            if (uiState.effectiveMode == EffectiveMode.ROOT_ACTIVE) {
                                "Root (am force-stop + kill -9)"
                            } else {
                                "Standard (killBackgroundProcesses + App Info)"
                            }
                        )

                        FilledTonalButton(
                            onClick = onReprobeRoot,
                            enabled = !uiState.isProbingRoot,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("reprobe_root_button")
                        ) {
                            if (uiState.isProbingRoot) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isProbingRoot) "Probing su..." else "Re-check Root Access")
                        }
                    }
                }
            }

            // Usage Stats + Device Environment Card
            if (env != null) {
                item(key = "system_env_card") {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("System & Permissions", style = MaterialTheme.typography.titleMedium)
                                }
                                StatusPill(
                                    text = if (env.hasUsageStatsPermission) "USAGE OK" else "USAGE OFF",
                                    textColor = if (env.hasUsageStatsPermission) semantic.protectedAccent else semantic.nonRootBadge,
                                    containerColor = if (env.hasUsageStatsPermission) semantic.protectedContainer else semantic.nonRootBadgeContainer
                                )
                            }

                            InfoRow("OS Version", "Android ${env.androidVersion} (API ${env.sdkInt})")
                            InfoRow("Device", "${env.manufacturer} ${env.deviceModel}")
                            InfoRow("Architecture", "${env.primaryAbi} · ${env.cpuCores} cores")
                            InfoRow("Total RAM", "${uiState.memoryTelemetry.totalRamMb} MB")

                            if (!env.hasUsageStatsPermission) {
                                Button(
                                    onClick = onOpenUsageAccess,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("open_usage_settings_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Grant Usage Access in Settings")
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = semantic.protectedAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Usage Access active — recent foreground telemetry enabled across all apps.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.protectedAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Shell & Action Console
            item(key = "shell_log_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Execution Console", style = MaterialTheme.typography.titleMedium)
                    }
                    if (uiState.shellLogs.isNotEmpty()) {
                        TextButton(onClick = onClearLogs) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }

            if (uiState.shellLogs.isEmpty()) {
                item(key = "shell_log_empty") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = semantic.consoleSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$ awaiting task actions...",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.consoleText.copy(alpha = 0.7f),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(items = uiState.shellLogs, key = { it.id }) { entry ->
                    ConsoleLogCard(entry = entry)
                }
            }

            item(key = "diagnostics_bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConsoleLogCard(entry: ShellLogEntry) {
    val semantic = LocalTelemetryColors.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = semantic.consoleSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[${entry.timestamp}] ${entry.modeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.isSuccess) semantic.protectedAccent else semantic.nonRootBadge
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (entry.isSuccess) semantic.protectedAccent else semantic.nonRootBadge,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = "$ ${entry.command}",
                style = MaterialTheme.typography.labelMedium,
                color = semantic.consoleText
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Text(
                text = entry.resultSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
