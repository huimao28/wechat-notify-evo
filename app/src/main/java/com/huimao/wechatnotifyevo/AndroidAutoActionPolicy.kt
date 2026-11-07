package com.huimao.wechatnotifyevo

/** Pure selection policy; Android PendingIntents are never executed or recreated here. */
enum class ActionBridgeSource { STANDARD, CAR_EXTENDER }

data class ActionCandidate(
    val index: Int,
    val hasPendingIntent: Boolean,
    val remoteInputResultKeys: List<String> = emptyList(),
    val allowsFreeFormInput: Boolean = false,
    val semanticAction: Int = 0,
)

data class SelectedAction(
    val source: ActionBridgeSource,
    val index: Int,
    val remoteInputResultKey: String? = null,
)

data class ActionBridgeSelection(
    val reply: SelectedAction?,
    val markRead: SelectedAction?,
)

object AndroidAutoActionPolicy {
    const val SEMANTIC_REPLY = 1
    const val SEMANTIC_MARK_AS_READ = 2

    fun select(
        standard: List<ActionCandidate>,
        carReply: ActionCandidate? = null,
        carRead: ActionCandidate? = null,
    ): ActionBridgeSelection {
        val standardReply = standard.firstOrNull {
            it.hasPendingIntent && it.allowsFreeFormInput &&
                it.remoteInputResultKeys.any(String::isNotBlank)
        }
        val standardRead = standard.firstOrNull {
            it.hasPendingIntent && it.semanticAction == SEMANTIC_MARK_AS_READ &&
                it.remoteInputResultKeys.isEmpty()
        }
        val reply = standardReply?.toSelected(ActionBridgeSource.STANDARD)
            ?: carReply?.takeIf {
                it.hasPendingIntent && it.allowsFreeFormInput &&
                    it.remoteInputResultKeys.any(String::isNotBlank)
            }?.toSelected(ActionBridgeSource.CAR_EXTENDER)
        val read = standardRead?.toSelected(ActionBridgeSource.STANDARD)
            ?: carRead?.takeIf { it.hasPendingIntent }
                ?.toSelected(ActionBridgeSource.CAR_EXTENDER)
        return ActionBridgeSelection(reply, read)
    }

    private fun ActionCandidate.toSelected(source: ActionBridgeSource) = SelectedAction(
        source = source,
        index = index,
        remoteInputResultKey = remoteInputResultKeys.firstOrNull { it.isNotBlank() },
    )
}

data class ConversationNotificationMetadata(
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
)

object ConversationMetadataPolicy {
    fun forConversation(title: String, type: ConversationType) =
        ConversationNotificationMetadata(
            conversationTitle = title.takeIf { type == ConversationType.GROUP },
            isGroupConversation = type == ConversationType.GROUP,
        )
}
