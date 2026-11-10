package com.huimao.wechatnotifyevo

sealed interface AppRoute
data object HomeRoute : AppRoute
data object HistoryRoute : AppRoute
data object AiRoute : AppRoute
data object SettingsRouteKey : AppRoute
data object AboutRoute : AppRoute
data object DiagnosticsRouteKey : AppRoute
data class HistoryDetailRoute(val stableId: String) : AppRoute

enum class TopLevelTab(val route: AppRoute, val label: String) {
    HOME(HomeRoute, "首页"),
    HISTORY(HistoryRoute, "记录"),
    AI(AiRoute, "AI总结"),
    SETTINGS(SettingsRouteKey, "设置"),
    ABOUT(AboutRoute, "关于"),
}

object TopLevelNavigationPolicy {
    fun tabFor(route: AppRoute): TopLevelTab = when (route) {
        HomeRoute -> TopLevelTab.HOME
        HistoryRoute, is HistoryDetailRoute -> TopLevelTab.HISTORY
        AiRoute -> TopLevelTab.AI
        SettingsRouteKey, DiagnosticsRouteKey -> TopLevelTab.SETTINGS
        AboutRoute -> TopLevelTab.ABOUT
    }

    /** Selecting a tab replaces the visible stack, so repeated taps never accumulate destinations. */
    fun switchStack(current: List<AppRoute>, tab: TopLevelTab): List<AppRoute> =
        if (current.size == 1 && current.firstOrNull() == tab.route) current else listOf(tab.route)

    fun openDetail(current: List<AppRoute>, detail: AppRoute): List<AppRoute> =
        current.filterNot { it is HistoryDetailRoute || it == DiagnosticsRouteKey } + detail
}
