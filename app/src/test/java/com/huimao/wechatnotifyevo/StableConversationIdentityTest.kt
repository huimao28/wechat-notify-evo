package com.huimao.wechatnotifyevo

import org.junit.Assert.*
import org.junit.Test

class StableConversationIdentityTest {
    private val salt = ByteArray(32) { it.toByte() }

    @Test fun hmacIdentityIsDeterministicAndOpaque() {
        val raw = "wxid_sensitive_value"
        val a = ConversationIdentityCore.stableId(salt, "pkg", 0, raw)
        val b = ConversationIdentityCore.stableId(salt, "pkg", 0, raw)
        assertEquals(a, b)
        assertTrue(a.startsWith(ConversationIdentityCore.PREFIX))
        assertFalse(a.contains(raw))
        assertEquals(48, a.length)
    }

    @Test fun saltAccountAndMainUserSeparateIdentities() {
        val id = ConversationIdentityCore.stableId(salt, "pkg", 0, "same")
        assertNotEquals(id, ConversationIdentityCore.stableId(ByteArray(32) { 7 }, "pkg", 0, "same"))
        assertNotEquals(id, ConversationIdentityCore.stableId(salt, "pkg", 10, "same"))
        assertNotEquals(id, ConversationIdentityCore.stableId(salt, "pkg", 0, "other"))
    }

    @Test fun routeTokenNeverChangesShortcutIdentity() {
        val c = Conversation(9, 0)
        c.conversationStableId = ConversationIdentityCore.stableId(salt, "pkg", 0, "chat")
        val before = ConversationShortcuts.shortcutId(c)
        c.routeToken = "new-security-generation"
        assertEquals(before, ConversationShortcuts.shortcutId(c))
    }

    @Test fun persistedBindingRoundTripsAndRejectsConflicts() {
        val binding = ConversationIdentityCore.Binding(
            ConversationIdentityCore.fallbackId(salt, "pkg", 0, 42), "source-proof", "title-proof", 10L
        )
        val restored = ConversationIdentityCore.decode(ConversationIdentityCore.encode(binding))!!
        assertEquals(binding, restored)
        assertTrue(ConversationIdentityCore.reusable(restored, "source-proof", "title-proof"))
        assertFalse(ConversationIdentityCore.reusable(restored, "other-source", "title-proof"))
        assertFalse(ConversationIdentityCore.reusable(restored, "source-proof", "other-title"))
    }

    @Test fun learnedIdentityMigratesOnceThenRemainsStable() {
        val fallback = ConversationIdentityCore.fallbackId(salt, "pkg", 0, 42)
        val learned = ConversationIdentityCore.stableId(salt, "pkg", 0, "wxid")
        assertNotEquals(fallback, learned)
        assertEquals(learned, ConversationIdentityCore.stableId(salt, "pkg", 0, "wxid"))
    }

    @Test fun conversationChannelIdAndCreationPolicyAreStable() {
        val shortcut = ConversationIdentityCore.stableId(salt, "pkg", 0, "chat")
        val id = ConversationChannelPolicy.channelId("evo_message", shortcut)
        assertEquals(id, ConversationChannelPolicy.channelId("evo_message", shortcut))
        assertTrue(ConversationChannelPolicy.shouldCreate(false, true))
        assertFalse(ConversationChannelPolicy.shouldCreate(true, true))
        assertFalse(ConversationChannelPolicy.shouldCreate(false, false))
    }

    @Test fun notificationAssociationRequiresPublishedShortcut() {
        assertFalse(ConversationPublicationPolicy.associateAsConversation(false))
        assertTrue(ConversationPublicationPolicy.associateAsConversation(true))
    }

    @Test fun personKeysAreStableAndScopedToConversation() {
        assertEquals("wne_self", ConversationPersonKeys.SELF)
        assertEquals("person_conv", ConversationPersonKeys.participant("conv"))
        assertEquals(ConversationPersonKeys.sender("conv", "sender"),
            ConversationPersonKeys.sender("conv", "sender"))
        assertNotEquals(ConversationPersonKeys.sender("conv-a", "sender"),
            ConversationPersonKeys.sender("conv-b", "sender"))
    }
}
