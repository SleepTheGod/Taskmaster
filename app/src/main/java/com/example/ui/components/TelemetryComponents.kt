package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppRunState
import com.example.model.AppTaskItem
import com.example.model.EffectiveMode
import com.example.model.ForceStopQueueState
import com.example.model.MemoryTelemetry
import com.example.model.OneTapStopProgress
import com.example.model.SystemEnvironmentInfo
import com.example.ui.theme.LocalTelemetryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppIconImage(
    drawable: Drawable?,
    packageName: String,
    contentDescription: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val bitmap: ImageBitmap? = remember(packageName, drawable) {
        drawable?.let { drawableToBitmap(it).asImageBitmap() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width.coerceAtMost(144), height.coerceAtMost(144), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun RamTelemetryHeroCard(
    memoryTelemetry: MemoryTelemetry,
    effectiveMode: EffectiveMode,
    environmentInfo: SystemEnvironmentInfo?,
    activeKillableCount: Int,
    selectedCount: Int,
    runningProcessCount: Int,
    isExecutingKill: Boolean,
    oneTapStopProgress: OneTapStopProgress,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPrimaryKillClick: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalTelemetryColors.current
    val animatedFraction by animateFloatAsState(
        targetValue = memoryTelemetry.usedFraction,
        animationSpec = spring(stiffness = 280f),
        label = "ram_fraction"
    )

    val usedGb = memoryTelemetry.usedRamMb / 1024.0
    val totalGb = memoryTelemetry.totalRamMb / 1024.0
    val isRoot = effectiveMode == EffectiveMode.ROOT_ACTIVE

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_telemetry_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.24f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Mode + Refresh Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isRoot) semantic.rootBadgeContainer else semantic.nonRootBadgeContainer,
                        border = BorderStroke(
                            1.dp,
                            (if (isRoot) semantic.rootBadge else semantic.nonRootBadge).copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRoot) semantic.rootBadge else semantic.nonRootBadge)
                            )
                            Text(
                                text = if (isRoot) "ROOT · am force-stop" else "NON-ROOT · Standard",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isRoot) semantic.rootBadge else semantic.nonRootBadge
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_tasks_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh tasks and RAM",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Gauge + RAM breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    val trackColor = MaterialTheme.colorScheme.outlineVariant
                    val activeArcColor = when {
                        memoryTelemetry.usedPercent >= 85 -> semantic.stopAction
                        memoryTelemetry.usedPercent >= 68 -> semantic.nonRootBadge
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 10.dp.toPx()
                            val diameter = size.minDimension - strokeWidth
                            val topLeft = Offset(
                                (size.width - diameter) / 2f,
                                (size.height - diameter) / 2f
                            )
                            val arcSize = Size(diameter, diameter)
                            drawArc(
                                color = trackColor,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = activeArcColor,
                                startAngle = 135f,
                                sweepAngle = 270f * animatedFraction,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${memoryTelemetry.usedPercent}%",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "RAM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f / %.1f GB", usedGb, totalGb),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${memoryTelemetry.availableRamMb} MB free · $runningProcessCount live proc(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (environmentInfo != null) {
                            Text(
                                text = "Android ${environmentInfo.androidVersion} (API ${environmentInfo.sdkInt}) · ${environmentInfo.deviceModel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Usage permission prompt if Non-Root and Usage Stats not granted
                if (environmentInfo != null && !environmentInfo.hasUsageStatsPermission) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = semantic.nonRootBadgeContainer.copy(alpha = 0.65f),
                        onClick = onRequestUsageAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_usage_access_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = semantic.nonRootBadge,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Grant Usage Access for accurate recent app detection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Enable",
                                style = MaterialTheme.typography.labelLarge,
                                color = semantic.nonRootBadge
                            )
                        }
                    }
                }

                // Live One-Tap Stop Progress Visual Feedback
                AnimatedVisibility(visible = oneTapStopProgress.isRunning) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = semantic.stopActionContainer.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("one_tap_stop_progress_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Stopping ${oneTapStopProgress.currentAppName}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${oneTapStopProgress.completedCount}/${oneTapStopProgress.totalCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = semantic.stopAction
                                )
                            }
                            LinearProgressIndicator(
                                progress = { oneTapStopProgress.fraction },
                                color = semantic.stopAction,
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }

                // Prominent One-Tap Stop CTA
                val buttonText = when {
                    isExecutingKill && oneTapStopProgress.totalCount > 0 ->
                        "Stopping (${oneTapStopProgress.completedCount}/${oneTapStopProgress.totalCount})..."
                    isExecutingKill -> "Stopping tasks..."
                    selectedCount > 0 -> "One-Tap Stop Selected ($selectedCount)"
                    else -> "One-Tap Stop · $activeKillableCount Apps"
                }

                Button(
                    onClick = onPrimaryKillClick,
                    enabled = !isExecutingKill && (selectedCount > 0 || activeKillableCount > 0),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.stopAction,
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(vertical = 15.dp, horizontal = 18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("primary_kill_button")
                ) {
                    if (isExecutingKill) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ForceStopQueueCard(
    queueState: ForceStopQueueState,
    currentApp: AppTaskItem?,
    onOpenAppInfo: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalTelemetryColors.current
    val pkg = queueState.currentPackage ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, semantic.nonRootBadge.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = semantic.nonRootBadge
                    )
                    Text(
                        text = "Guided Force-Stop Queue · ${queueState.currentIndex + 1}/${queueState.totalCount}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Force-Stop Queue"
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconImage(
                    drawable = currentApp?.icon,
                    packageName = pkg,
                    contentDescription = currentApp?.appName ?: pkg,
                    size = 40.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentApp?.appName ?: pkg,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap Force Stop in System Settings, then press Back to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpenAppInfo(pkg) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.stopAction,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("queue_open_app_info_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open App Info")
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("queue_skip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Skip")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppInspectorBottomSheet(
    app: AppTaskItem,
    effectiveMode: EffectiveMode,
    onDismiss: () -> Unit,
    onForceStop: (AppTaskItem) -> Unit,
    onKillPid: (Int, String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onToggleWhitelist: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semantic = LocalTelemetryColors.current
    val isRoot = effectiveMode == EffectiveMode.ROOT_ACTIVE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AppIconImage(
                    drawable = app.icon,
                    packageName = app.packageName,
                    contentDescription = app.appName,
                    size = 54.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onToggleWhitelist(app.packageName) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (app.isWhitelisted) Icons.Filled.Shield else Icons.Outlined.Shield,
                        contentDescription = "Toggle protection",
                        tint = if (app.isWhitelisted) semantic.protectedAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Telemetry grid
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow("Process State", when (app.runState) {
                        AppRunState.RUNNING_PROCESS -> "Running (${app.pids.size} PID)"
                        AppRunState.ACTIVE_RECENT -> "Active / Cached (FLAG_STOPPED = 0)"
                        AppRunState.STOPPED -> "Stopped (FLAG_STOPPED = 1)"
                    })
                    DetailRow("Package Type", if (app.isSystemApp) "System Application" else "User Application")
                    if (app.versionName.isNotBlank()) DetailRow("Version", app.versionName)
                    DetailRow("Linux UID", app.uid.toString())
                    if (app.memoryMb > 0.0) {
                        DetailRow("Observed RAM", String.format(Locale.US, "%.1f MB", app.memoryMb))
                    }
                    if (app.lastUsedMillis > 0L) {
                        val formatted = remember(app.lastUsedMillis) {
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                .format(Date(app.lastUsedMillis))
                        }
                        DetailRow("Last Active", formatted)
                    }
                    if (app.foregroundTimeMinutes > 0L) {
                        DetailRow("Foreground (24h)", "${app.foregroundTimeMinutes} min")
                    }
                }
            }

            // Live PIDs (if any)
            if (app.pids.isNotEmpty()) {
                Text(
                    text = "Live Process IDs",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    app.pids.forEach { pid ->
                        AssistChip(
                            onClick = { if (isRoot && !app.isSelf) onKillPid(pid, app.packageName) },
                            label = {
                                Text(
                                    text = if (isRoot && !app.isSelf) "PID $pid · kill -9" else "PID $pid",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onForceStop(app)
                        onDismiss()
                    },
                    enabled = !app.isSelf && app.runState != AppRunState.STOPPED,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.stopAction,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("inspector_stop_button")
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRoot) "Force-Stop (Root)" else "Kill Background")
                }

                FilledTonalButton(
                    onClick = { onOpenAppInfo(app.packageName) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("inspector_app_info_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("App Info")
                }
            }

            AnimatedVisibility(visible = app.isCoreCritical) {
                Text(
                    text = "Core system component — excluded from automatic batch kills for device stability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.nonRootBadge
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
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
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
