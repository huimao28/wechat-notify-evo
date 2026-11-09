package com.huimao.wechatnotifyevo

/** Small pure contract used to keep shortcut -> channel -> notification ordering explicit. */
object ConversationPublicationPolicy {
    fun associateAsConversation(shortcutPublished: Boolean): Boolean = shortcutPublished
}

object ConversationPersonKeys {
    fun participant(shortcutId: String): String = "person_$shortcutId"
    fun sender(shortcutId: String, sender: String): String =
        "sender_${shortcutId}_${sender.hashCode()}"
    const val SELF = "wne_self"
}
