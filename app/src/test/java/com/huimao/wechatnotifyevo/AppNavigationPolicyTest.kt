package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationPolicyTest {
    @Test fun `switching tab replaces stack and cannot grow forever`() {
        val detail = listOf<AppRoute>(HistoryRoute, HistoryDetailRoute("a"))
        assertEquals(listOf<AppRoute>(SettingsRouteKey), TopLevelNavigationPolicy.switchStack(detail, TopLevelTab.SETTINGS))
        assertEquals(listOf<AppRoute>(SettingsRouteKey), TopLevelNavigationPolicy.switchStack(listOf(SettingsRouteKey), TopLevelTab.SETTINGS))
    }

    @Test fun `history detail remains associated with history tab`() {
        assertEquals(TopLevelTab.HISTORY, TopLevelNavigationPolicy.tabFor(HistoryDetailRoute("id")))
    }

    @Test fun `opening another detail replaces old detail`() {
        assertEquals(
            listOf<AppRoute>(HistoryRoute, HistoryDetailRoute("new")),
            TopLevelNavigationPolicy.openDetail(listOf(HistoryRoute, HistoryDetailRoute("old")), HistoryDetailRoute("new")),
        )
    }
}
