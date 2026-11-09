package com.huimao.wechatnotifyevo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationFactory.ensureChannels(this)
        ConversationShortcuts.pruneOrphans(this)
        KeepAliveCoordinator.restoreIfEnabled(this)
        enableEdgeToEdge()
        setContent {
            WeChatNotifyEvoTheme { AppContent() }
        }
    }
}

@Composable
private fun AppContent() {
    var savedDestination by rememberSaveable { mutableStateOf(AppDestination.SETTINGS.name) }
    val destination = AppNavigationPolicy.restore(savedDestination)
    val navigateTo: (AppDestination) -> Unit = { savedDestination = it.name }
    val navigateBack = { savedDestination = AppDestination.SETTINGS.name }
    val backState = rememberNavigationEventState<NavigationEventInfo>(NavigationEventInfo.None)
    val transition = backState.transitionState
    val event = (transition as? NavigationEventTransitionState.InProgress)?.latestEvent
    val progress = event?.progress?.coerceIn(0f, 1f) ?: 0f
    val direction = if (event?.swipeEdge == NavigationEvent.EDGE_RIGHT) -1f else 1f
    val predictiveShape = MaterialTheme.shapes.extraLarge

    NavigationBackHandler(
        state = backState,
        isBackEnabled = AppNavigationPolicy.backTarget(destination) != null,
        onBackCompleted = navigateBack,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (destination == AppDestination.SETTINGS) {
            SettingsRoute(
                onOpenAbout = { navigateTo(AppDestination.ABOUT) },
                onOpenDiagnostics = { navigateTo(AppDestination.DIAGNOSTICS) },
            )
        } else {
            // Keep the real parent destination behind the detail page so the system gesture
            // reveals the correct surface without a flash at either system edge.
            SettingsRoute(onOpenAbout = {}, onOpenDiagnostics = {})
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = direction * size.width * 0.08f * progress
                        scaleX = 1f - 0.06f * progress
                        scaleY = 1f - 0.06f * progress
                        alpha = 1f - 0.12f * progress
                        clip = true
                        shape = predictiveShape
                    },
            ) {
                when (destination) {
                    AppDestination.ABOUT -> AboutScreen(onBack = navigateBack)
                    AppDestination.DIAGNOSTICS -> DiagnosticsRoute(onBack = navigateBack)
                    AppDestination.SETTINGS -> Unit
                }
            }
        }
    }
}
