package com.huimao.wechatnotifyevo

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationFactory.ensureChannels(this)
        ConversationShortcuts.pruneOrphans(this)
        KeepAliveCoordinator.restoreIfEnabled(this)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent { WeChatNotifyEvoTheme { AppContent() } }
    }
}

@Composable
private fun AppContent(
    historyViewModel: HistoryViewModel = viewModel(),
    aiViewModel: AiViewModel = viewModel(),
) {
    val historyState by historyViewModel.state.collectAsStateWithLifecycle()
    val aiState by aiViewModel.state.collectAsStateWithLifecycle()
    // One snapshot write per navigation event: NavDisplay can never observe an empty stack.
    val backStackSaver = remember { listSaver<List<AppRoute>, String>(
        save = { stack -> stack.map(AppRouteSavePolicy::encode) },
        restore = { saved -> saved.mapNotNull(AppRouteSavePolicy::decode).ifEmpty { listOf(HomeRoute) } },
    ) }
    var backStack by rememberSaveable(stateSaver = backStackSaver) { mutableStateOf<List<AppRoute>>(listOf(HomeRoute)) }
    val current = backStack.lastOrNull() ?: HomeRoute
    val selectedTab = TopLevelNavigationPolicy.tabFor(current)

    fun switchTab(tab: TopLevelTab) {
        backStack = TopLevelNavigationPolicy.switchStack(backStack, tab)
        historyViewModel.closeConversation()
    }
    fun openDetail(route: AppRoute) {
        backStack = TopLevelNavigationPolicy.openDetail(backStack, route)
    }
    fun navigateBack() {
        if (backStack.size > 1) {
            if (backStack.last() is HistoryDetailRoute) historyViewModel.closeConversation()
            backStack = TopLevelNavigationPolicy.navigateBack(backStack)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { historyViewModel.onResume() }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelTab.entries.forEach { tab ->
                item(
                    selected = selectedTab == tab,
                    onClick = { switchTab(tab) },
                    icon = { Icon(tab.icon(), contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = ::navigateBack,
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeScreen(historyState, historyViewModel::setRecordingEnabled, { switchTab(TopLevelTab.HISTORY) }, { switchTab(TopLevelTab.SETTINGS) })
                }
                entry<HistoryRoute> {
                    HistoryScreen(historyState, historyViewModel::setRange, { id -> historyViewModel.openConversation(id); openDetail(HistoryDetailRoute(id)) }, historyViewModel::loadMoreConversations, historyViewModel::clearAll)
                }
                entry<HistoryDetailRoute> {
                    HistoryDetailScreen(historyState, ::navigateBack, historyViewModel::loadMoreMessages, historyViewModel::deleteConversation, historyViewModel::clearAll)
                }
                entry<AiRoute> {
                    AiSummaryScreen(aiState, AiScreenActions(
                        aiViewModel::setRange, aiViewModel::selectProvider, aiViewModel::toggleConversation, aiViewModel::preview,
                        aiViewModel::requestConfirmation, aiViewModel::dismissConfirmation,
                        aiViewModel::generate, aiViewModel::cancel, aiViewModel::retry, aiViewModel::onLeave,
                    ))
                }
                entry<SettingsRouteKey> {
                    SettingsRoute(
                        onOpenAbout = { switchTab(TopLevelTab.ABOUT) },
                        onOpenDiagnostics = { openDetail(DiagnosticsRouteKey) },
                        privacyContent = {
                            HistoryPrivacySettingsCard(historyState, historyViewModel::setRecordingEnabled, historyViewModel::setRetention, historyViewModel::saveExcluded, historyViewModel::clearAll)
                        },
                        aiContent = { ProviderSettingsSummary(aiState) { openDetail(ProviderListRoute) } },
                    )
                }
                entry<DiagnosticsRouteKey> { DiagnosticsRoute(onBack = ::navigateBack) }
                entry<ProviderListRoute> { ProviderListScreen(aiState, ::navigateBack, { openDetail(AddProviderChooseRoute) }, { openDetail(ProviderEditorRoute(it)) }) }
                entry<AddProviderChooseRoute> { AddProviderChooseScreen(::navigateBack) { openDetail(ProviderEditorRoute(preset = it.name)) } }
                entry<ProviderEditorRoute> { route -> ProviderEditorScreen(aiState, route.id, route.preset, ProviderEditorActions(aiViewModel::saveProvider, aiViewModel::deleteProvider, aiViewModel::clearProviderKey), ::navigateBack, { openDetail(ProviderModelsRoute(it)) }, { openDetail(ProviderQuickTestRoute(it)) }) }
                entry<ProviderModelsRoute> { route -> ProviderModelsScreen(aiState, route.providerId, ::navigateBack, aiViewModel::addProviderModel, aiViewModel::deleteProviderModel, aiViewModel::setDefaultModel, aiViewModel::refreshProviderModels) }
                entry<ProviderQuickTestRoute> { route -> ProviderQuickTestScreen(aiState, route.providerId, route.modelEntryId, ::navigateBack, aiViewModel::quickTest, aiViewModel::cancel) }
                entry<AboutRoute> { AboutScreen() }
            },
        )
    }
}

private fun TopLevelTab.icon(): ImageVector = when (this) {
    TopLevelTab.HOME -> Icons.Default.Home
    TopLevelTab.HISTORY -> Icons.AutoMirrored.Filled.List
    TopLevelTab.AI -> Icons.Default.Star
    TopLevelTab.SETTINGS -> Icons.Default.Settings
    TopLevelTab.ABOUT -> Icons.Default.Info
}
