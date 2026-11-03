package com.huimao.wechatnotifyevo

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ConversationSnapshotTest {
    @After fun clear() = ConversationStore.clear()

    private fun current(): Conversation = ConversationStore.getOrCreate(7, "same title").apply {
        replaceSnapshot("notification-key", 1L, null)
    }

    @Test fun acceptsOnlyCurrentObjectKeyAndGeneration() {
        val c = current()
        assertTrue(c.isCurrentSnapshot(c.snapshotToken, "notification-key", null))
        assertFalse(c.isCurrentSnapshot(c.snapshotToken, "wrong-key", null))
        assertFalse(c.isCurrentSnapshot(c.snapshotToken, null, null))
        assertFalse(c.isCurrentSnapshot("old-generation", "notification-key", null))
    }

    @Test fun continuousUpdateKeepsLearnedRouteButRejectsInflightSnapshot() {
        val c = current()
        val old = c.snapshotToken
        val shortcut = ConversationShortcuts.shortcutId(c.nid)
        c.learnedMainUser = "synthetic-test-value"
        c.shortcutPushed = true
        c.replaceSnapshot("notification-key", 2L, null)
        assertEquals("synthetic-test-value", c.learnedMainUser)
        assertTrue(c.shortcutPushed)
        assertFalse(c.isCurrentSnapshot(old, "notification-key", null))
        assertEquals(shortcut, ConversationShortcuts.shortcutId(c.nid))
    }

    @Test fun removalAndSameTitleIdReuseCannotBindLateCallback() {
        val old = current()
        val generation = old.snapshotToken
        ConversationStore.remove(old.nid)
        val fresh = current()
        assertNotSame(old, fresh)
        assertFalse(old.isCurrentSnapshot(generation, "notification-key", null))
        assertNotEquals(generation, fresh.snapshotToken)
        assertNotEquals(old.routeToken, fresh.routeToken)
    }

    @Test fun changedTitleReplacesObject() {
        val old = current()
        val fresh = ConversationStore.getOrCreate(old.nid, "another chat")
        assertNotSame(old, fresh)
        assertFalse(old.isCurrentSnapshot(old.snapshotToken, "notification-key", null))
    }

    @Test fun processStoreResetInvalidatesPendingLearning() {
        val c = current()
        ConversationStore.clear()
        assertFalse(c.isCurrentSnapshot(c.snapshotToken, "notification-key", null))
    }
}
