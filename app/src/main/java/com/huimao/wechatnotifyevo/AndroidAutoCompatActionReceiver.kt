package com.huimao.wechatnotifyevo

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles WNE-only Android Auto compatibility actions. This receiver never invokes WeChat.
 * It is non-exported and every notification route is bound to the current in-memory conversation.
 */
class AndroidAutoCompatActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SIMULATED_REPLY &&
            intent.action != ACTION_SIMULATED_MARK_READ
        ) return
        if (!Prefs.isAndroidAutoCompatActionsEnabled(context)) return

        val route = CompatRoute(
            nid = intent.getIntExtra(EXTRA_NID, -1),
            sourceUserId = intent.getIntExtra(EXTRA_SOURCE_USER_ID, -1),
            routeToken = intent.getStringExtra(EXTRA_ROUTE_TOKEN).orEmpty(),
        )
        val live = ConversationStore.get(route.nid, route.sourceUserId)
        if (!AndroidAutoCompatRoutePolicy.isCurrent(
                requested = route,
                currentUserId = CurrentUserScope.id(),
                liveNid = live?.nid,
                liveSourceUserId = live?.sourceUserId,
                liveRouteToken = live?.routeToken,
            ) || live == null || !CurrentUserScope.accepts(live)
        ) return

        when (intent.action) {
            ACTION_SIMULATED_MARK_READ -> {
                ConversationShortcuts.remove(context, live.nid, live.sourceUserId)
                ConversationStore.remove(live.nid, live.sourceUserId)
                WeChatListenerService.cancelEvolved(context, live.nid, live.sourceUserId)
            }
            ACTION_SIMULATED_REPLY -> {
                if (!Prefs.isReplyEnabled(context)) return
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(AndroidAutoCompatPolicy.REMOTE_INPUT_RESULT_KEY)
                    ?.toString() ?: return
                val effect = SimulatedReplyPolicy.create(text) ?: return
                // Process memory only: no log, preference, file, database, network, or WeChat call.
                live.messages.add(
                    ChatMessage(effect.localEchoText, null, System.currentTimeMillis())
                )
                WeChatListenerService.repostConversation(context, live, silent = true)
            }
        }
    }

    companion object {
        const val ACTION_SIMULATED_REPLY =
            "com.huimao.wechatnotifyevo.ACTION_AA_SIMULATED_REPLY"
        const val ACTION_SIMULATED_MARK_READ =
            "com.huimao.wechatnotifyevo.ACTION_AA_SIMULATED_MARK_READ"
        const val EXTRA_NID = "nid"
        const val EXTRA_SOURCE_USER_ID = "source_user_id"
        const val EXTRA_ROUTE_TOKEN = "route_token"
    }
}
