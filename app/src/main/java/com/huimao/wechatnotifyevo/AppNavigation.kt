package com.huimao.wechatnotifyevo

enum class AppDestination { SETTINGS, ABOUT, DIAGNOSTICS }

object AppNavigationPolicy {
    fun restore(value: String?): AppDestination =
        AppDestination.entries.firstOrNull { it.name == value } ?: AppDestination.SETTINGS

    fun backTarget(destination: AppDestination): AppDestination? = when (destination) {
        AppDestination.SETTINGS -> null
        AppDestination.ABOUT, AppDestination.DIAGNOSTICS -> AppDestination.SETTINGS
    }
}
