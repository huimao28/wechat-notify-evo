package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectChatModeTest {
    @Test fun linkedFlagsAlwaysMoveTogether() {
        assertEquals(DirectChatFlags(true, true, true), DirectChatFlags.linked(true))
        assertEquals(DirectChatFlags(false, false, false), DirectChatFlags.linked(false))
    }

    @Test fun migrationEnablesOnlyWhenAllThreeLegacyFlagsWereEnabled() {
        assertTrue(DirectChatModePolicy.migrate(DirectChatFlags(true, true, true)).enabled)
        listOf(
            DirectChatFlags(false, true, true),
            DirectChatFlags(true, false, true),
            DirectChatFlags(true, true, false),
            DirectChatFlags(false, false, false),
        ).forEach { legacy ->
            assertEquals(DirectChatFlags.linked(false), DirectChatModePolicy.migrate(legacy))
        }
    }

    @Test fun enablingOnlyWritesLinkedStateWithoutRefreshOrCleanup() {
        var flags = DirectChatFlags.linked(false)
        val events = mutableListOf<String>()
        DirectChatTransition(
            writeLinkedFlags = {
                flags = DirectChatFlags.linked(it)
                events += "write:$it"
            },
            cancelPendingLearning = { events += "cancel" },
            cleanupLearnedRoutes = { events += "cleanup" },
            removeActiveNotifications = { events += "remove-notifications" },
        ).setEnabled(true)
        assertTrue(flags.enabled)
        assertEquals(listOf("write:true"), events)
    }

    @Test fun disablingInvalidatesWritesCleansThenRemovesWithoutRefresh() {
        var flags = DirectChatFlags.linked(true)
        val events = mutableListOf<String>()
        DirectChatTransition(
            writeLinkedFlags = {
                flags = DirectChatFlags.linked(it)
                events += "write:$it"
            },
            cancelPendingLearning = { events += "cancel" },
            cleanupLearnedRoutes = { events += "cleanup" },
            removeActiveNotifications = { events += "remove-notifications" },
        ).setEnabled(false)
        assertEquals(DirectChatFlags.linked(false), flags)
        assertEquals(
            listOf("cancel", "write:false", "cleanup", "remove-notifications"),
            events,
        )
    }
}
