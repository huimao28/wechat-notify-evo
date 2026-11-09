package com.huimao.wechatnotifyevo

/** Pure channel naming/creation decision; the pair parentId + shortcutId is the durable key. */
object ConversationChannelPolicy {
    fun channelId(parentId: String, shortcutId: String): String = "$parentId:$shortcutId"
    fun shouldCreate(existing: Boolean, shortcutPublished: Boolean): Boolean =
        shortcutPublished && !existing
}
