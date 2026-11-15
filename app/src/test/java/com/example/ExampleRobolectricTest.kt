package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.AppRunState
import com.example.model.AppTaskItem
import com.example.model.EffectiveMode
import com.example.model.KillSummary
import com.example.model.MemoryTelemetry
import com.example.model.OneTapStopProgress
import com.example.ui.TaskPulseUiState
import com.example.ui.screens.MonitorScreen
import com.example.ui.theme.TaskPulseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches TaskPulse`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TaskPulse", appName)
    }

    @Test
    fun `one tap stop button triggers callback and renders progress and summary feedback`() {
        var killTriggered = false
        val sampleApps = listOf(
            AppTaskItem(
                packageName = "com.example.music",
                appName = "Music Stream",
                versionName = "1.0",
                uid = 10101,
                isSystemApp = false,
                isCoreCritical = false,
                isSelf = false,
                isStoppedFlag = false,
                runState = AppRunState.ACTIVE_RECENT,
                isWhitelisted = true
            ),
            AppTaskItem(
                packageName = "com.example.video",
                appName = "Video Player",
                versionName = "2.1",
                uid = 10102,
                isSystemApp = false,
                isCoreCritical = false,
                isSelf = false,
                isStoppedFlag = false,
                runState = AppRunState.RUNNING_PROCESS,
                pids = listOf(4211),
                memoryMb = 148.5,
                isWhitelisted = false
            )
        )

        val state = TaskPulseUiState(
            isInitialLoading = false,
            isExecutingKill = false,
            effectiveMode = EffectiveMode.NON_ROOT_ACTIVE,
            memoryTelemetry = MemoryTelemetry(totalRamMb = 8192L, availableRamMb = 3072L, usedRamMb = 5120L),
            allApps = sampleApps,
            filteredApps = sampleApps,
            lastKillSummary = KillSummary(
                stoppedCount = 1,
                attemptedCount = 1,
                freedRamMb = 148L,
                usedRoot = false,
                skippedWhitelistCount = 1,
                skippedSystemCount = 2,
                message = "One-Tap Stop cleared 1 app(s) and reclaimed 148 MB RAM."
            )
        )

        composeRule.setContent {
            TaskPulseTheme {
                MonitorScreen(
                    uiState = state,
                    onRefresh = {},
                    onPrimaryKillClick = { killTriggered = true },
                    onFilterSelected = {},
                    onSearchQueryChange = {},
                    onSortSelected = {},
                    onToggleShowSystem = {},
                    onToggleSelectApp = {},
                    onSelectAllKillable = {},
                    onClearSelection = {},
                    onProtectSelected = {},
                    onInspectApp = {},
                    onStopSingleApp = {},
                    onToggleWhitelist = {},
                    onStartForceStopQueue = {},
                    onLaunchQueueAppInfo = {},
                    onSkipQueueItem = {},
                    onDismissQueue = {},
                    onDismissSummary = {},
                    onOpenUsageAccess = {}
                )
            }
        }

        composeRule.onNodeWithTag("primary_kill_button").assertIsDisplayed().performClick()
        assertTrue(killTriggered)

        composeRule.onNodeWithTag("one_tap_stop_summary_card").assertIsDisplayed()
        composeRule.onNodeWithText("1 WHITELISTED").assertIsDisplayed()
        composeRule.onNodeWithText("2 SYSTEM KEPT").assertIsDisplayed()
    }

    @Test
    fun `one tap stop progress card displays live progress during execution`() {
        val state = TaskPulseUiState(
            isInitialLoading = false,
            isExecutingKill = true,
            oneTapStopProgress = OneTapStopProgress(
                isRunning = true,
                currentAppName = "Video Player",
                completedCount = 2,
                totalCount = 5
            ),
            memoryTelemetry = MemoryTelemetry(totalRamMb = 8192L, availableRamMb = 3072L, usedRamMb = 5120L)
        )

        composeRule.setContent {
            TaskPulseTheme {
                MonitorScreen(
                    uiState = state,
                    onRefresh = {},
                    onPrimaryKillClick = {},
                    onFilterSelected = {},
                    onSearchQueryChange = {},
                    onSortSelected = {},
                    onToggleShowSystem = {},
                    onToggleSelectApp = {},
                    onSelectAllKillable = {},
                    onClearSelection = {},
                    onProtectSelected = {},
                    onInspectApp = {},
                    onStopSingleApp = {},
                    onToggleWhitelist = {},
                    onStartForceStopQueue = {},
                    onLaunchQueueAppInfo = {},
                    onSkipQueueItem = {},
                    onDismissQueue = {},
                    onDismissSummary = {},
                    onOpenUsageAccess = {}
                )
            }
        }

        composeRule.onNodeWithTag("one_tap_stop_progress_card").assertIsDisplayed()
        composeRule.onNodeWithText("Stopping Video Player...").assertIsDisplayed()
        composeRule.onNodeWithText("2/5").assertIsDisplayed()
    }
}
