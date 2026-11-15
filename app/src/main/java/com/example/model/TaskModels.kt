package com.example.model

import android.graphics.drawable.Drawable

enum class ExecutionMode(val label: String, val description: String) {
    AUTO("Auto-Detect", "Uses Root am force-stop if granted, otherwise Non-Root engine"),
    ROOT("Root Shell (su)", "Force-stops packages & kills PIDs directly via su"),
    NON_ROOT("Non-Root Standard", "Uses killBackgroundProcesses & guided App Info Force-Stop")
}

enum class EffectiveMode {
    ROOT_ACTIVE,
    NON_ROOT_ACTIVE
}

enum class AppFilter(val label: String) {
    ACTIVE("Active"),
    ALL("All Apps"),
    STOPPED("Stopped"),
    SYSTEM("System")
}

enum class SortOption(val label: String) {
    ACTIVE_FIRST("Active First"),
    MEMORY_DESC("Memory"),
    RECENT_USAGE("Recent"),
    NAME_ASC("Name A–Z")
}

enum class AppRunState {
    RUNNING_PROCESS, // Live PID observed via Root ps or ActivityManager
    ACTIVE_RECENT,   // Not FLAG_STOPPED; recently active or cached in background
    STOPPED          // ApplicationInfo.FLAG_STOPPED is set (fully force-stopped / dormant)
}

data class RootStatus(
    val isRootBinaryPresent: Boolean = false,
    val isRootGranted: Boolean = false,
    val suPath: String? = null,
    val suVersion: String? = null,
    val selinuxState: String = "Unknown",
    val uidOutput: String = "Not probed",
    val checkedAtMillis: Long = 0L
)

data class MemoryTelemetry(
    val totalRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val usedRamMb: Long = 0L,
    val lowMemoryThresholdMb: Long = 0L,
    val isLowMemory: Boolean = false
) {
    val usedFraction: Float
        get() = if (totalRamMb > 0L) (usedRamMb.toFloat() / totalRamMb.toFloat()).coerceIn(0f, 1f) else 0f

    val usedPercent: Int
        get() = (usedFraction * 100).toInt()
}

data class SystemEnvironmentInfo(
    val androidVersion: String,
    val sdkInt: Int,
    val deviceModel: String,
    val manufacturer: String,
    val primaryAbi: String,
    val cpuCores: Int,
    val hasUsageStatsPermission: Boolean
)

data class AppTaskItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val isCoreCritical: Boolean,
    val isSelf: Boolean,
    val isStoppedFlag: Boolean,
    val runState: AppRunState,
    val pids: List<Int> = emptyList(),
    val memoryMb: Double = 0.0,
    val lastUsedMillis: Long = 0L,
    val foregroundTimeMinutes: Long = 0L,
    val isWhitelisted: Boolean = false,
    val icon: Drawable? = null
) {
    val canBatchKill: Boolean
        get() = !isWhitelisted && !isCoreCritical && !isSelf && runState != AppRunState.STOPPED
}

data class RootProcessInfo(
    val pid: Int,
    val rssKb: Long,
    val user: String,
    val processName: String
)

data class ShellLogEntry(
    val id: Long,
    val timestamp: String,
    val modeLabel: String,
    val command: String,
    val resultSummary: String,
    val isSuccess: Boolean
)

data class OneTapStopProgress(
    val isRunning: Boolean = false,
    val currentAppName: String = "",
    val completedCount: Int = 0,
    val totalCount: Int = 0
) {
    val fraction: Float
        get() = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
}

data class KillSummary(
    val stoppedCount: Int,
    val attemptedCount: Int,
    val freedRamMb: Long,
    val usedRoot: Boolean,
    val skippedWhitelistCount: Int = 0,
    val skippedSystemCount: Int = 0,
    val remainingActivePackages: List<String> = emptyList(),
    val message: String
)

data class ForceStopQueueState(
    val queuePackages: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val confirmedStoppedCount: Int = 0,
    val awaitingReturnFromSettings: Boolean = false
) {
    val isActive: Boolean get() = queuePackages.isNotEmpty() && currentIndex < queuePackages.size
    val currentPackage: String? get() = queuePackages.getOrNull(currentIndex)
    val totalCount: Int get() = queuePackages.size
}
