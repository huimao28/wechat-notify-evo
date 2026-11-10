package com.huimao.wechatnotifyevo

enum class AppDestination { SETTINGS, ABOUT, DIAGNOSTICS }

/** Compatibility policy retained for existing state restoration tests and old saved values. */
object AppNavigationPolicy {
    fun restore(value: String?): AppDestination =
        AppDestination.entries.firstOrNull { it.name == value } ?: AppDestination.SETTINGS

    fun backTarget(destination: AppDestination): AppDestination? = when (destination) {
        AppDestination.SETTINGS -> null
        AppDestination.ABOUT, AppDestination.DIAGNOSTICS -> AppDestination.SETTINGS
    }
}

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

object AppRouteSavePolicy {
    fun encode(route: AppRoute): String = when (route) {
        HomeRoute -> "home"; HistoryRoute -> "history"; AiRoute -> "ai"
        SettingsRouteKey -> "settings"; AboutRoute -> "about"; DiagnosticsRouteKey -> "diagnostics"
        is HistoryDetailRoute -> "history-detail:${route.stableId}"
    }
    fun decode(value: String): AppRoute? = when {
        value == "home" -> HomeRoute; value == "history" -> HistoryRoute; value == "ai" -> AiRoute
        value == "settings" -> SettingsRouteKey; value == "about" -> AboutRoute; value == "diagnostics" -> DiagnosticsRouteKey
        value.startsWith("history-detail:") -> HistoryDetailRoute(value.substringAfter("history-detail:"))
        else -> null
    }
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
        (current.ifEmpty { listOf(HomeRoute) }.filterNot { it is HistoryDetailRoute || it == DiagnosticsRouteKey } + detail)
            .ifEmpty { listOf(HomeRoute) }

    fun navigateBack(current: List<AppRoute>): List<AppRoute> =
        if (current.size > 1) current.dropLast(1) else current.ifEmpty { listOf(HomeRoute) }
}
