package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ExecutionMode
import com.example.model.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.taskPulseDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "taskpulse_preferences"
)

data class TaskPulsePreferences(
    val whitelistedPackages: Set<String> = emptySet(),
    val executionMode: ExecutionMode = ExecutionMode.AUTO,
    val sortOption: SortOption = SortOption.ACTIVE_FIRST,
    val showSystemApps: Boolean = false,
    val totalKilledCount: Int = 0,
    val totalRamFreedMb: Long = 0L
)

class TaskPreferencesRepository(private val context: Context) {

    private object Keys {
        val WHITELISTED_PACKAGES = stringSetPreferencesKey("whitelisted_packages")
        val EXECUTION_MODE = stringPreferencesKey("execution_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val TOTAL_KILLED_COUNT = intPreferencesKey("total_killed_count")
        val TOTAL_RAM_FREED_MB = longPreferencesKey("total_ram_freed_mb")
    }

    val preferencesFlow: Flow<TaskPulsePreferences> = context.taskPulseDataStore.data.map { prefs ->
        val modeName = prefs[Keys.EXECUTION_MODE]
        val sortName = prefs[Keys.SORT_OPTION]
        TaskPulsePreferences(
            whitelistedPackages = prefs[Keys.WHITELISTED_PACKAGES] ?: emptySet(),
            executionMode = ExecutionMode.entries.firstOrNull { it.name == modeName } ?: ExecutionMode.AUTO,
            sortOption = SortOption.entries.firstOrNull { it.name == sortName } ?: SortOption.ACTIVE_FIRST,
            showSystemApps = prefs[Keys.SHOW_SYSTEM_APPS] ?: false,
            totalKilledCount = prefs[Keys.TOTAL_KILLED_COUNT] ?: 0,
            totalRamFreedMb = prefs[Keys.TOTAL_RAM_FREED_MB] ?: 0L
        )
    }

    suspend fun toggleWhitelist(packageName: String) {
        context.taskPulseDataStore.edit { prefs ->
            val current = prefs[Keys.WHITELISTED_PACKAGES] ?: emptySet()
            prefs[Keys.WHITELISTED_PACKAGES] = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
        }
    }

    suspend fun addAllToWhitelist(packages: Collection<String>) {
        context.taskPulseDataStore.edit { prefs ->
            val current = prefs[Keys.WHITELISTED_PACKAGES] ?: emptySet()
            prefs[Keys.WHITELISTED_PACKAGES] = current + packages
        }
    }

    suspend fun clearWhitelist() {
        context.taskPulseDataStore.edit { prefs ->
            prefs[Keys.WHITELISTED_PACKAGES] = emptySet()
        }
    }

    suspend fun setExecutionMode(mode: ExecutionMode) {
        context.taskPulseDataStore.edit { prefs ->
            prefs[Keys.EXECUTION_MODE] = mode.name
        }
    }

    suspend fun setSortOption(sortOption: SortOption) {
        context.taskPulseDataStore.edit { prefs ->
            prefs[Keys.SORT_OPTION] = sortOption.name
        }
    }

    suspend fun setShowSystemApps(show: Boolean) {
        context.taskPulseDataStore.edit { prefs ->
            prefs[Keys.SHOW_SYSTEM_APPS] = show
        }
    }

    suspend fun recordKillMetrics(killedCount: Int, freedRamMb: Long) {
        if (killedCount <= 0 && freedRamMb <= 0L) return
        context.taskPulseDataStore.edit { prefs ->
            prefs[Keys.TOTAL_KILLED_COUNT] = (prefs[Keys.TOTAL_KILLED_COUNT] ?: 0) + killedCount
            prefs[Keys.TOTAL_RAM_FREED_MB] = (prefs[Keys.TOTAL_RAM_FREED_MB] ?: 0L) + freedRamMb.coerceAtLeast(0L)
        }
    }
}
