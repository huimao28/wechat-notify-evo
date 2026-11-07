package com.huimao.wechatnotifyevo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationFactory.ensureChannels(this)
        ConversationShortcuts.pruneOrphans(this)
        KeepAliveCoordinator.restoreIfEnabled(this)
        enableEdgeToEdge()
        setContent {
            WeChatNotifyEvoTheme {
                var destination by rememberSaveable { mutableStateOf("settings") }
                val navigateBack = { destination = "settings" }
                BackHandler(enabled = destination == "about", onBack = navigateBack)

                when (destination) {
                    "about" -> AboutScreen(onBack = navigateBack)
                    else -> SettingsRoute(onOpenAbout = { destination = "about" })
                }
            }
        }
    }
}
