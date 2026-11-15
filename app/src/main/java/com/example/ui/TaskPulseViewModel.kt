package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.RootShellExecutor
import com.example.data.engine.SystemTelemetryManager
import com.example.data.preferences.TaskPreferencesRepository
import com.example.data.preferences.TaskPulsePreferences
import com.example.model.AppFilter
import com.example.model.AppRunState
import com.example.model.AppTaskItem
import com.example.model.EffectiveMode
import com.example.model.ExecutionMode
import com.example.model.ForceStopQueueState
import com.example.model.KillSummary
import com.example.model.MemoryTelemetry
import com.example.model.OneTapStopProgress
import com.example.model.RootStatus
import com.example.model.ShellLogEntry
import com.example.model.SortOption
import com.example.model.SystemEnvironmentInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TaskPulseTab(val route: String) {
    MONITOR("monitor"),
    WHITELIST("whitelist"),
    SYSTEM("system")
}

data class TaskPulseUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isExecutingKill: Boolean = false,
    val oneTapStopProgress: OneTapStopProgress = OneTapStopProgress(),
    val isProbingRoot: Boolean = false,
    val rootStatus: RootStatus = RootStatus(),
    val memoryTelemetry: MemoryTelemetry = MemoryTelemetry(),
    val environmentInfo: SystemEnvironmentInfo? = null,
    val preferences: TaskPulsePreferences = TaskPulsePreferences(),
    val effectiveMode: EffectiveMode = EffectiveMode.NON_ROOT_ACTIVE,
    val allApps: List<AppTaskItem> = emptyList(),
    val filteredApps: List<AppTaskItem> = emptyList(),
    val activeFilter: AppFilter = AppFilter.ACTIVE,
    val searchQuery: String = "",
    val selectedPackages: Set<String> = emptySet(),
    val inspectedApp: AppTaskItem? = null,
    val lastKillSummary: KillSummary? = null,
    val forceStopQueue: ForceStopQueueState = ForceStopQueueState(),
    val shellLogs: List<ShellLogEntry> = emptyList()
) {
    val activeKillableCount: Int
        get() = allApps.count {
            it.canBatchKill && (preferences.showSystemApps || !it.isSystemApp)
        }

    val runningProcessCount: Int
        get() = allApps.count { it.runState == AppRunState.RUNNING_PROCESS }

    val activeAppCount: Int
        get() = allApps.count { it.runState != AppRunState.STOPPED }

    val stoppedAppCount: Int
        get() = allApps.count { it.runState == AppRunState.STOPPED }
}

private data class InternalEngineState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isExecutingKill: Boolean = false,
    val oneTapStopProgress: OneTapStopProgress = OneTapStopProgress(),
    val isProbingRoot: Boolean = false,
    val rootStatus: RootStatus = RootStatus(),
    val memoryTelemetry: MemoryTelemetry = MemoryTelemetry(),
    val environmentInfo: SystemEnvironmentInfo? = null,
    val rawApps: List<AppTaskItem> = emptyList(),
    val activeFilter: AppFilter = AppFilter.ACTIVE,
    val searchQuery: String = "",
    val selectedPackages: Set<String> = emptySet(),
    val inspectedPackageName: String? = null,
    val lastKillSummary: KillSummary? = null,
    val forceStopQueue: ForceStopQueueState = ForceStopQueueState(),
    val shellLogs: List<ShellLogEntry> = emptyList()
)

class TaskPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val rootShellExecutor = RootShellExecutor()
    private val telemetryManager = SystemTelemetryManager(application, rootShellExecutor)
    private val preferencesRepository = TaskPreferencesRepository(application)

    private val internalState = MutableStateFlow(InternalEngineState())
    private var logCounter = 0L
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val uiState: StateFlow<TaskPulseUiState> = combine(
        internalState,
        preferencesRepository.preferencesFlow
    ) { state, prefs ->
        val effectiveMode = resolveEffectiveMode(prefs.executionMode, state.rootStatus)
        val mergedApps = state.rawApps.map { app ->
            app.copy(isWhitelisted = app.packageName in prefs.whitelistedPackages)
        }
        val filtered = applyFilterAndSort(
            apps = mergedApps,
            filter = state.activeFilter,
            query = state.searchQuery,
            sortOption = prefs.sortOption,
            showSystemApps = prefs.showSystemApps
        )
        val inspected = state.inspectedPackageName?.let { pkg ->
            mergedApps.firstOrNull { it.packageName == pkg }
        }
        TaskPulseUiState(
            isInitialLoading = state.isInitialLoading,
            isRefreshing = state.isRefreshing,
            isExecutingKill = state.isExecutingKill,
            oneTapStopProgress = state.oneTapStopProgress,
            isProbingRoot = state.isProbingRoot,
            rootStatus = state.rootStatus,
            memoryTelemetry = state.memoryTelemetry,
            environmentInfo = state.environmentInfo,
            preferences = prefs,
            effectiveMode = effectiveMode,
            allApps = mergedApps,
            filteredApps = filtered,
            activeFilter = state.activeFilter,
            searchQuery = state.searchQuery,
            selectedPackages = state.selectedPackages,
            inspectedApp = inspected,
            lastKillSummary = state.lastKillSummary,
            forceStopQueue = state.forceStopQueue,
            shellLogs = state.shellLogs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TaskPulseUiState()
    )

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        viewModelScope.launch {
            val env = telemetryManager.readEnvironmentInfo()
            val mem = telemetryManager.readMemoryTelemetry()
            internalState.update {
                it.copy(
                    environmentInfo = env,
                    memoryTelemetry = mem,
                    isProbingRoot = true
                )
            }

            val rootStatus = rootShellExecutor.probeRootStatus()
            appendLog(
                modeLabel = if (rootStatus.isRootGranted) "ROOT" else "PROBE",
                command = "which su && su -c id",
                resultSummary = if (rootStatus.isRootGranted) {
                    "Granted (${rootStatus.suVersion ?: "su"}) · ${rootStatus.uidOutput}"
                } else {
                    "Standard mode (${rootStatus.uidOutput})"
                },
                isSuccess = rootStatus.isRootGranted
            )

            val currentPrefs = uiState.value.preferences
            val effective = resolveEffectiveMode(currentPrefs.executionMode, rootStatus)
            val apps = telemetryManager.loadAppTasks(
                useRootProcesses = effective == EffectiveMode.ROOT_ACTIVE,
                whitelistedPackages = currentPrefs.whitelistedPackages
            )

            internalState.update {
                it.copy(
                    isInitialLoading = false,
                    isProbingRoot = false,
                    rootStatus = rootStatus,
                    rawApps = apps,
                    memoryTelemetry = telemetryManager.readMemoryTelemetry(),
                    environmentInfo = telemetryManager.readEnvironmentInfo()
                )
            }
        }
    }

    fun refreshAll() {
        if (internalState.value.isRefreshing) return
        viewModelScope.launch {
            internalState.update { it.copy(isRefreshing = true) }
            val state = uiState.value
            val useRoot = state.effectiveMode == EffectiveMode.ROOT_ACTIVE
            val apps = telemetryManager.loadAppTasks(
                useRootProcesses = useRoot,
                whitelistedPackages = state.preferences.whitelistedPackages
            )
            internalState.update {
                it.copy(
                    isRefreshing = false,
                    rawApps = apps,
                    memoryTelemetry = telemetryManager.readMemoryTelemetry(),
                    environmentInfo = telemetryManager.readEnvironmentInfo()
                )
            }
        }
    }

    fun reprobeRoot() {
        if (internalState.value.isProbingRoot) return
        viewModelScope.launch {
            internalState.update { it.copy(isProbingRoot = true) }
            val rootStatus = rootShellExecutor.probeRootStatus()
            appendLog(
                modeLabel = if (rootStatus.isRootGranted) "ROOT" else "PROBE",
                command = "su -c 'id; su -v; getenforce'",
                resultSummary = if (rootStatus.isRootGranted) {
                    "Root active (${rootStatus.suVersion}) · SELinux: ${rootStatus.selinuxState}"
                } else {
                    "Root unavailable: ${rootStatus.uidOutput}"
                },
                isSuccess = rootStatus.isRootGranted
            )
            internalState.update {
                it.copy(
                    isProbingRoot = false,
                    rootStatus = rootStatus
                )
            }
            refreshAll()
        }
    }

    fun setFilter(filter: AppFilter) {
        internalState.update { it.copy(activeFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        internalState.update { it.copy(searchQuery = query) }
    }

    fun setSortOption(sortOption: SortOption) {
        viewModelScope.launch {
            preferencesRepository.setSortOption(sortOption)
        }
    }

    fun setShowSystemApps(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowSystemApps(show)
        }
    }

    fun setExecutionMode(mode: ExecutionMode) {
        viewModelScope.launch {
            preferencesRepository.setExecutionMode(mode)
            appendLog(
                modeLabel = "MODE",
                command = "mode = ${mode.name}",
                resultSummary = mode.description,
                isSuccess = true
            )
            refreshAll()
        }
    }

    fun toggleSelectPackage(packageName: String) {
        internalState.update { state ->
            val updated = if (packageName in state.selectedPackages) {
                state.selectedPackages - packageName
            } else {
                state.selectedPackages + packageName
            }
            state.copy(selectedPackages = updated)
        }
    }

    fun clearSelection() {
        internalState.update { it.copy(selectedPackages = emptySet()) }
    }

    fun selectAllVisibleKillable() {
        val killable = uiState.value.filteredApps
            .filter { !it.isWhitelisted && !it.isCoreCritical && !it.isSelf && it.runState != AppRunState.STOPPED }
            .map { it.packageName }
            .toSet()
        internalState.update { it.copy(selectedPackages = killable) }
    }

    fun inspectApp(packageName: String?) {
        internalState.update { it.copy(inspectedPackageName = packageName) }
    }

    fun toggleWhitelist(packageName: String) {
        viewModelScope.launch {
            preferencesRepository.toggleWhitelist(packageName)
        }
    }

    fun clearWhitelist() {
        viewModelScope.launch {
            preferencesRepository.clearWhitelist()
        }
    }

    fun protectSelected() {
        val selected = internalState.value.selectedPackages
        if (selected.isEmpty()) return
        viewModelScope.launch {
            preferencesRepository.addAllToWhitelist(selected)
            internalState.update { it.copy(selectedPackages = emptySet()) }
        }
    }

    fun killSelectedOrAllActive() {
        val state = uiState.value
        if (state.isExecutingKill) return

        val targets = if (state.selectedPackages.isNotEmpty()) {
            state.allApps.filter {
                it.packageName in state.selectedPackages && !it.isCoreCritical && !it.isSelf
            }
        } else {
            state.allApps.filter {
                it.canBatchKill && (state.preferences.showSystemApps || !it.isSystemApp)
            }
        }

        if (targets.isEmpty()) {
            val activeWhitelisted = state.allApps.count { it.isWhitelisted && it.runState != AppRunState.STOPPED }
            val activeSystem = state.allApps.count { (it.isCoreCritical || it.isSelf) && it.runState != AppRunState.STOPPED }
            internalState.update {
                it.copy(
                    lastKillSummary = KillSummary(
                        stoppedCount = 0,
                        attemptedCount = 0,
                        freedRamMb = 0L,
                        usedRoot = state.effectiveMode == EffectiveMode.ROOT_ACTIVE,
                        skippedWhitelistCount = activeWhitelisted,
                        skippedSystemCount = activeSystem,
                        message = "All eligible apps are already stopped ($activeWhitelisted whitelisted, $activeSystem system preserved)."
                    )
                )
            }
            return
        }

        executeBatchKill(targets)
    }

    fun stopSingleApp(app: AppTaskItem) {
        if (app.isSelf) return
        executeBatchKill(listOf(app))
    }

    fun killSinglePid(pid: Int, packageName: String) {
        viewModelScope.launch {
            val ok = rootShellExecutor.killPid(pid)
            appendLog(
                modeLabel = "ROOT",
                command = "su -c 'kill -9 $pid' ($packageName)",
                resultSummary = if (ok) "Process PID $pid terminated" else "Failed to terminate PID $pid",
                isSuccess = ok
            )
            delay(200L)
            refreshAll()
        }
    }

    private fun executeBatchKill(targetApps: List<AppTaskItem>) {
        val packageNames = targetApps.map { it.packageName }
        viewModelScope.launch {
            val state = uiState.value
            val useRoot = state.effectiveMode == EffectiveMode.ROOT_ACTIVE
            val skippedWhitelist = state.allApps.count { it.isWhitelisted && it.runState != AppRunState.STOPPED }
            val skippedSystem = state.allApps.count { (it.isCoreCritical || it.isSelf) && it.runState != AppRunState.STOPPED }

            internalState.update {
                it.copy(
                    isExecutingKill = true,
                    oneTapStopProgress = OneTapStopProgress(
                        isRunning = true,
                        currentAppName = targetApps.firstOrNull()?.appName.orEmpty(),
                        completedCount = 0,
                        totalCount = targetApps.size
                    )
                )
            }

            val beforeAvail = telemetryManager.readMemoryTelemetry().availableRamMb

            if (useRoot) {
                // Execute batch am force-stop in a single su shell and animate per-app progress feedback
                val results = rootShellExecutor.forceStopPackages(packageNames)
                targetApps.forEachIndexed { index, app ->
                    internalState.update {
                        it.copy(
                            oneTapStopProgress = OneTapStopProgress(
                                isRunning = true,
                                currentAppName = app.appName,
                                completedCount = index + 1,
                                totalCount = targetApps.size
                            )
                        )
                    }
                    if (targetApps.size > 1) delay(35L)
                }
                val successCount = results.values.count { it }
                delay(200L)
                val afterAvail = telemetryManager.readMemoryTelemetry().availableRamMb
                val freed = (afterAvail - beforeAvail).coerceAtLeast(0L)

                appendLog(
                    modeLabel = "ROOT",
                    command = if (packageNames.size == 1) {
                        "su -c 'am force-stop ${packageNames.first()}'"
                    } else {
                        "su batch am force-stop (${packageNames.size} pkgs)"
                    },
                    resultSummary = "Force-stopped $successCount/${packageNames.size} · Skipped $skippedWhitelist whitelist, $skippedSystem core · +${freed} MB RAM",
                    isSuccess = successCount > 0
                )

                preferencesRepository.recordKillMetrics(successCount, freed)

                val summary = KillSummary(
                    stoppedCount = successCount,
                    attemptedCount = packageNames.size,
                    freedRamMb = freed,
                    usedRoot = true,
                    skippedWhitelistCount = skippedWhitelist,
                    skippedSystemCount = skippedSystem,
                    message = "One-Tap Stop finished: $successCount of ${packageNames.size} app(s) force-stopped."
                )
                internalState.update {
                    it.copy(
                        isExecutingKill = false,
                        oneTapStopProgress = OneTapStopProgress(),
                        selectedPackages = emptySet(),
                        lastKillSummary = summary
                    )
                }
            } else {
                var bgKilled = 0
                targetApps.forEachIndexed { index, app ->
                    internalState.update {
                        it.copy(
                            oneTapStopProgress = OneTapStopProgress(
                                isRunning = true,
                                currentAppName = app.appName,
                                completedCount = index + 1,
                                totalCount = targetApps.size
                            )
                        )
                    }
                    bgKilled += telemetryManager.killBackgroundProcessesNonRoot(listOf(app.packageName))
                    if (targetApps.size > 1) delay(28L)
                }
                delay(250L)
                val afterAvail = telemetryManager.readMemoryTelemetry().availableRamMb
                val freed = (afterAvail - beforeAvail).coerceAtLeast(0L)

                val remaining = packageNames.filter { !telemetryManager.isPackageStopped(it) }
                val confirmedStopped = packageNames.size - remaining.size

                appendLog(
                    modeLabel = "NON-ROOT",
                    command = if (packageNames.size == 1) {
                        "ActivityManager.killBackgroundProcesses(${packageNames.first()})"
                    } else {
                        "killBackgroundProcesses(${packageNames.size} pkgs)"
                    },
                    resultSummary = "Cleared $bgKilled bg tasks · ${remaining.size} still active (App Info optional) · +${freed} MB RAM",
                    isSuccess = bgKilled > 0
                )

                val effectiveCount = maxOf(confirmedStopped, bgKilled)
                preferencesRepository.recordKillMetrics(effectiveCount, freed)

                val summary = KillSummary(
                    stoppedCount = effectiveCount,
                    attemptedCount = packageNames.size,
                    freedRamMb = freed,
                    usedRoot = false,
                    skippedWhitelistCount = skippedWhitelist,
                    skippedSystemCount = skippedSystem,
                    remainingActivePackages = remaining,
                    message = if (remaining.isEmpty()) {
                        "One-Tap Stop cleared $effectiveCount app(s) and reclaimed ${freed} MB RAM."
                    } else {
                        "Background tasks cleared for $bgKilled app(s). ${remaining.size} app(s) remain cached/active — launch Guided Force-Stop Queue for deep stop."
                    }
                )
                internalState.update {
                    it.copy(
                        isExecutingKill = false,
                        oneTapStopProgress = OneTapStopProgress(),
                        selectedPackages = emptySet(),
                        lastKillSummary = summary
                    )
                }
            }

            refreshAll()
        }
    }

    fun startForceStopQueue(packages: List<String>) {
        val activeOnly = packages.filter { !telemetryManager.isPackageStopped(it) }
        val finalQueue = activeOnly.ifEmpty { packages }
        if (finalQueue.isEmpty()) return
        internalState.update {
            it.copy(
                forceStopQueue = ForceStopQueueState(
                    queuePackages = finalQueue,
                    currentIndex = 0,
                    confirmedStoppedCount = 0,
                    awaitingReturnFromSettings = false
                )
            )
        }
    }

    fun markQueueSettingsLaunched() {
        internalState.update {
            it.copy(
                forceStopQueue = it.forceStopQueue.copy(awaitingReturnFromSettings = true)
            )
        }
    }

    fun onReturnedFromAppInfo() {
        val queue = internalState.value.forceStopQueue
        if (!queue.isActive) return

        val pkg = queue.currentPackage ?: return
        val stoppedNow = telemetryManager.isPackageStopped(pkg)
        if (stoppedNow) {
            appendLog(
                modeLabel = "NON-ROOT",
                command = "App Info -> Force Stop ($pkg)",
                resultSummary = "Verified FLAG_STOPPED = true",
                isSuccess = true
            )
            viewModelScope.launch { preferencesRepository.recordKillMetrics(1, 0L) }
        } else {
            appendLog(
                modeLabel = "NON-ROOT",
                command = "App Info returned ($pkg)",
                resultSummary = "App still reports active (user skipped or system protected)",
                isSuccess = false
            )
        }

        val nextIndex = queue.currentIndex + 1
        val updatedQueue = if (nextIndex >= queue.queuePackages.size) {
            ForceStopQueueState()
        } else {
            queue.copy(
                currentIndex = nextIndex,
                confirmedStoppedCount = queue.confirmedStoppedCount + if (stoppedNow) 1 else 0,
                awaitingReturnFromSettings = false
            )
        }

        internalState.update {
            it.copy(
                forceStopQueue = updatedQueue,
                lastKillSummary = if (!updatedQueue.isActive) {
                    KillSummary(
                        stoppedCount = queue.confirmedStoppedCount + if (stoppedNow) 1 else 0,
                        attemptedCount = queue.totalCount,
                        freedRamMb = 0L,
                        usedRoot = false,
                        message = "Guided Force-Stop Queue finished: ${queue.confirmedStoppedCount + if (stoppedNow) 1 else 0}/${queue.totalCount} stopped."
                    )
                } else {
                    it.lastKillSummary
                }
            )
        }
        refreshAll()
    }

    fun skipQueueItem() {
        val queue = internalState.value.forceStopQueue
        if (!queue.isActive) return
        val nextIndex = queue.currentIndex + 1
        internalState.update {
            it.copy(
                forceStopQueue = if (nextIndex >= queue.queuePackages.size) {
                    ForceStopQueueState()
                } else {
                    queue.copy(currentIndex = nextIndex, awaitingReturnFromSettings = false)
                }
            )
        }
    }

    fun dismissQueue() {
        internalState.update { it.copy(forceStopQueue = ForceStopQueueState()) }
    }

    fun dismissKillSummary() {
        internalState.update { it.copy(lastKillSummary = null) }
    }

    fun clearLogs() {
        internalState.update { it.copy(shellLogs = emptyList()) }
    }

    fun buildAppDetailsIntent(packageName: String): Intent =
        telemetryManager.createAppDetailsIntent(packageName)

    fun buildUsageAccessIntent(): Intent =
        telemetryManager.createUsageAccessSettingsIntent()

    private fun resolveEffectiveMode(
        preferenceMode: ExecutionMode,
        rootStatus: RootStatus
    ): EffectiveMode {
        return when (preferenceMode) {
            ExecutionMode.AUTO -> if (rootStatus.isRootGranted) EffectiveMode.ROOT_ACTIVE else EffectiveMode.NON_ROOT_ACTIVE
            ExecutionMode.ROOT -> if (rootStatus.isRootGranted) EffectiveMode.ROOT_ACTIVE else EffectiveMode.NON_ROOT_ACTIVE
            ExecutionMode.NON_ROOT -> EffectiveMode.NON_ROOT_ACTIVE
        }
    }

    private fun applyFilterAndSort(
        apps: List<AppTaskItem>,
        filter: AppFilter,
        query: String,
        sortOption: SortOption,
        showSystemApps: Boolean
    ): List<AppTaskItem> {
        val q = query.trim().lowercase()
        val filtered = apps.filter { app ->
            val matchesSystem = when (filter) {
                AppFilter.SYSTEM -> app.isSystemApp
                else -> showSystemApps || !app.isSystemApp
            }
            val matchesTab = when (filter) {
                AppFilter.ACTIVE -> app.runState != AppRunState.STOPPED
                AppFilter.STOPPED -> app.runState == AppRunState.STOPPED
                AppFilter.ALL, AppFilter.SYSTEM -> true
            }
            val matchesQuery = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            matchesSystem && matchesTab && matchesQuery
        }

        return when (sortOption) {
            SortOption.ACTIVE_FIRST -> filtered.sortedWith(
                compareBy<AppTaskItem> { it.runState.ordinal }
                    .thenByDescending { it.memoryMb }
                    .thenByDescending { it.lastUsedMillis }
                    .thenBy { it.appName.lowercase() }
            )
            SortOption.MEMORY_DESC -> filtered.sortedWith(
                compareByDescending<AppTaskItem> { it.memoryMb }
                    .thenBy { it.runState.ordinal }
                    .thenBy { it.appName.lowercase() }
            )
            SortOption.RECENT_USAGE -> filtered.sortedWith(
                compareByDescending<AppTaskItem> { it.lastUsedMillis }
                    .thenBy { it.runState.ordinal }
                    .thenBy { it.appName.lowercase() }
            )
            SortOption.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
        }
    }

    private fun appendLog(
        modeLabel: String,
        command: String,
        resultSummary: String,
        isSuccess: Boolean
    ) {
        val entry = ShellLogEntry(
            id = ++logCounter,
            timestamp = timeFormatter.format(Date()),
            modeLabel = modeLabel,
            command = command,
            resultSummary = resultSummary,
            isSuccess = isSuccess
        )
        internalState.update { state ->
            state.copy(shellLogs = (listOf(entry) + state.shellLogs).take(60))
        }
    }
}
