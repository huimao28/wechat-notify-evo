package com.huimao.wechatnotifyevo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationFactory.ensureChannels(this)
        ConversationShortcuts.pruneOrphans(this)
        enableEdgeToEdge()
        setContent {
            WeChatNotifyEvoTheme {
                SettingsRoute()
            }
        }
    }
}
