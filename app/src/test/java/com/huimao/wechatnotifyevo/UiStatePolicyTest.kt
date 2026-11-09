package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiStatePolicyTest {
    @Test fun diagnosticStatesCarryExpectedData() {
        assertEquals(DiagnosticRunState.Idle, DiagnosticRunState.Idle)
        assertEquals(12L, DiagnosticRunState.Running(12L).startedAtMillis)
        assertEquals(34L, DiagnosticRunState.Complete(34L).completedAtMillis)
        assertEquals("denied", DiagnosticRunState.Error(56L, "denied").reason)
    }

    @Test fun minimumDisplayOnlyFillsRemainingWindow() {
        assertEquals(300L, MinimumDetectionDisplay.remainingMillis(1_000, 1_000, true))
        assertEquals(125L, MinimumDetectionDisplay.remainingMillis(1_000, 1_175, true))
        assertEquals(0L, MinimumDetectionDisplay.remainingMillis(1_000, 1_400, true))
    }

    @Test fun reducedMotionSkipsMinimumDisplay() {
        assertEquals(0L, MinimumDetectionDisplay.remainingMillis(1_000, 1_000, false))
    }

    @Test fun detailScreensReturnToSettingsAndRootDelegatesToSystem() {
        assertNull(AppNavigationPolicy.backTarget(AppDestination.SETTINGS))
        assertEquals(AppDestination.SETTINGS, AppNavigationPolicy.backTarget(AppDestination.ABOUT))
        assertEquals(AppDestination.SETTINGS, AppNavigationPolicy.backTarget(AppDestination.DIAGNOSTICS))
    }

    @Test fun destinationRestorationIsDefensive() {
        assertEquals(AppDestination.DIAGNOSTICS, AppNavigationPolicy.restore("DIAGNOSTICS"))
        assertEquals(AppDestination.SETTINGS, AppNavigationPolicy.restore("removed-route"))
        assertEquals(AppDestination.SETTINGS, AppNavigationPolicy.restore(null))
    }
}
