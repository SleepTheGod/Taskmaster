package com.example.data.engine

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.model.AppRunState
import com.example.model.AppTaskItem
import com.example.model.MemoryTelemetry
import com.example.model.RootProcessInfo
import com.example.model.SystemEnvironmentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemTelemetryManager(
    private val context: Context,
    private val rootShellExecutor: RootShellExecutor
) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    private val coreCriticalPrefixes = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.providers.",
        "com.android.inputmethod",
        "com.google.android.inputmethod",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.shell"
    )

    fun readMemoryTelemetry(): MemoryTelemetry {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)
        val thresholdMb = memInfo.threshold / (1024L * 1024L)
        return MemoryTelemetry(
            totalRamMb = totalMb,
            availableRamMb = availMb,
            usedRamMb = usedMb,
            lowMemoryThresholdMb = thresholdMb,
            isLowMemory = memInfo.lowMemory
        )
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun readEnvironmentInfo(): SystemEnvironmentInfo {
        return SystemEnvironmentInfo(
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL ?: "Android Device",
            manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: "Generic",
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            hasUsageStatsPermission = hasUsageStatsPermission()
        )
    }

    suspend fun loadAppTasks(
        useRootProcesses: Boolean,
        whitelistedPackages: Set<String>
    ): List<AppTaskItem> = withContext(Dispatchers.IO) {
        val selfPkg = context.packageName
        val appInfoMap = collectVisibleApplications()

        // Usage stats (if granted)
        val usageMap = queryRecentUsageStats()

        // Running processes from ActivityManager (always returns own + accessible processes)
        val amProcesses = runCatching { activityManager.runningAppProcesses.orEmpty() }
            .getOrDefault(emptyList())
        val amPidMap = mutableMapOf<String, MutableList<Int>>()
        for (proc in amProcesses) {
            for (pkg in proc.pkgList.orEmpty()) {
                amPidMap.getOrPut(pkg) { mutableListOf() }.add(proc.pid)
            }
        }

        // Live processes from Root shell `ps` if Root mode is active
        val rootProcesses: List<RootProcessInfo> = if (useRootProcesses) {
            rootShellExecutor.queryRunningProcesses()
        } else {
            emptyList()
        }

        val rootPidByPkg = mutableMapOf<String, MutableList<Int>>()
        val rootRssKbByPkg = mutableMapOf<String, Long>()
        for (proc in rootProcesses) {
            val basePkg = proc.processName.substringBefore(":")
            if (basePkg in appInfoMap) {
                rootPidByPkg.getOrPut(basePkg) { mutableListOf() }.add(proc.pid)
                rootRssKbByPkg[basePkg] = (rootRssKbByPkg[basePkg] ?: 0L) + proc.rssKb
            }
        }

        // For non-root visible PIDs, query Debug.MemoryInfo
        val allAmPids = amPidMap.values.flatten().distinct().toIntArray()
        val amMemoryByPid = mutableMapOf<Int, Int>()
        if (allAmPids.isNotEmpty()) {
            runCatching {
                val infos = activityManager.getProcessMemoryInfo(allAmPids)
                allAmPids.forEachIndexed { index, pid ->
                    val pssKb = infos.getOrNull(index)?.totalPss ?: 0
                    amMemoryByPid[pid] = pssKb
                }
            }
        }

        val now = System.currentTimeMillis()
        val recentThresholdMs = 24L * 60L * 60L * 1000L

        val items = appInfoMap.values.mapNotNull { appInfo ->
            val pkg = appInfo.packageName
            val isSelf = pkg == selfPkg
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
            val isCore = isCoreCriticalPackage(pkg)

            val pids = when {
                useRootProcesses && !rootPidByPkg[pkg].isNullOrEmpty() -> rootPidByPkg[pkg]!!.distinct()
                !amPidMap[pkg].isNullOrEmpty() -> amPidMap[pkg]!!.distinct()
                else -> emptyList()
            }

            val memoryMb = when {
                useRootProcesses && (rootRssKbByPkg[pkg] ?: 0L) > 0L ->
                    (rootRssKbByPkg[pkg]!! / 1024.0)
                pids.isNotEmpty() -> {
                    val totalKb = pids.sumOf { amMemoryByPid[it] ?: 0 }
                    totalKb / 1024.0
                }
                else -> 0.0
            }

            val usage = usageMap[pkg]
            val lastUsed = usage?.lastTimeUsed ?: 0L
            val fgMinutes = (usage?.totalTimeInForeground ?: 0L) / 60_000L

            val runState = when {
                pids.isNotEmpty() -> AppRunState.RUNNING_PROCESS
                !isStopped && (lastUsed > now - recentThresholdMs || !isSystem) -> AppRunState.ACTIVE_RECENT
                !isStopped -> AppRunState.ACTIVE_RECENT
                else -> AppRunState.STOPPED
            }

            val label = runCatching {
                packageManager.getApplicationLabel(appInfo).toString().trim()
            }.getOrDefault(pkg).ifBlank { pkg }

            val versionName = runCatching {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkg, 0).versionName.orEmpty()
            }.getOrDefault("")

            val icon = runCatching { packageManager.getApplicationIcon(appInfo) }.getOrNull()

            AppTaskItem(
                packageName = pkg,
                appName = label,
                versionName = versionName,
                uid = appInfo.uid,
                isSystemApp = isSystem,
                isCoreCritical = isCore,
                isSelf = isSelf,
                isStoppedFlag = isStopped,
                runState = runState,
                pids = pids,
                memoryMb = memoryMb,
                lastUsedMillis = lastUsed,
                foregroundTimeMinutes = fgMinutes,
                isWhitelisted = pkg in whitelistedPackages,
                icon = icon
            )
        }

        items.sortedBy { it.appName.lowercase() }
    }

    private fun collectVisibleApplications(): Map<String, ApplicationInfo> {
        val map = linkedMapOf<String, ApplicationInfo>()

        // 1. LauncherApps (works cleanly across API 24-36 with <queries> MAIN/LAUNCHER)
        runCatching {
            val profiles = launcherApps?.profiles ?: listOf(Process.myUserHandle())
            for (profile in profiles) {
                launcherApps?.getActivityList(null, profile)?.forEach { info ->
                    val appInfo = info.applicationInfo
                    map[appInfo.packageName] = appInfo
                }
            }
        }

        // 2. Query MAIN/LAUNCHER intent activities as supplement
        runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            }
            for (resolveInfo in resolved) {
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
                map.putIfAbsent(appInfo.packageName, appInfo)
            }
        }

        // 3. Installed applications accessible to this UID (returns all on API <30, visible on API 30+)
        runCatching {
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
            for (appInfo in installed) {
                map.putIfAbsent(appInfo.packageName, appInfo)
            }
        }

        return map
    }

    private fun queryRecentUsageStats(): Map<String, UsageStats> {
        val manager = usageStatsManager ?: return emptyMap()
        if (!hasUsageStatsPermission()) return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - 24L * 60L * 60L * 1000L
        return runCatching {
            val list = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .orEmpty()
            val merged = mutableMapOf<String, UsageStats>()
            for (stats in list) {
                val existing = merged[stats.packageName]
                if (existing == null || stats.lastTimeUsed > existing.lastTimeUsed) {
                    merged[stats.packageName] = stats
                }
            }
            merged
        }.getOrDefault(emptyMap())
    }

    fun killBackgroundProcessesNonRoot(packages: List<String>): Int {
        var count = 0
        for (pkg in packages) {
            val ok = runCatching {
                activityManager.killBackgroundProcesses(pkg)
                true
            }.getOrDefault(false)
            if (ok) count++
        }
        return count
    }

    fun isPackageStopped(packageName: String): Boolean {
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        }.getOrDefault(false)
    }

    fun createAppDetailsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
    }

    fun createUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    private fun isCoreCriticalPackage(packageName: String): Boolean {
        if (packageName == context.packageName) return true
        return coreCriticalPrefixes.any { prefix ->
            packageName == prefix || packageName.startsWith(prefix)
        }
    }
}
