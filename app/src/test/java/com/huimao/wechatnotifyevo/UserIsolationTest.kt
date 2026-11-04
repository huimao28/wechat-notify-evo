package com.huimao.wechatnotifyevo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class UserIsolationTest {
    @After fun clear() = ConversationStore.clear()

    @Test fun cloneSourceIsNotProcessed() {
        assertEquals(
            UserIsolationDecision.IGNORE_SOURCE_USER,
            UserIsolationPolicy.decide(sourceUserId = 999, currentUserId = 0, creatorUserId = 999),
        )
    }

    @Test fun knownForeignCreatorIsNotProcessed() {
        assertEquals(
            UserIsolationDecision.IGNORE_CREATOR_USER,
            UserIsolationPolicy.decide(sourceUserId = 0, currentUserId = 0, creatorUserId = 999),
        )
    }

    @Test fun mainUserAndUnknownSystemCreatorAreProcessed() {
        assertEquals(UserIsolationDecision.PROCESS, UserIsolationPolicy.decide(0, 0, 0))
        assertEquals(UserIsolationDecision.PROCESS, UserIsolationPolicy.decide(0, 0, null))
    }

    @Test fun sameNotificationIdInDifferentUsersNeverOverwrites() {
        val main = ConversationStore.getOrCreate(42, "main", sourceUserId = 0)
        val clone = ConversationStore.getOrCreate(42, "clone", sourceUserId = 999)
        assertNotSame(main, clone)
        assertSame(main, ConversationStore.get(42, 0))
        assertSame(clone, ConversationStore.get(42, 999))
        ConversationStore.remove(42, 999)
        assertSame(main, ConversationStore.get(42, 0))
    }

    @Test fun cleanupRemovesForeignSessionsOnly() {
        val main = ConversationStore.getOrCreate(7, "main", sourceUserId = 0)
        val clone = ConversationStore.getOrCreate(7, "clone", sourceUserId = 999)
        assertEquals(listOf(clone), ConversationStore.removeOtherUsers(0))
        assertSame(main, ConversationStore.get(7, 0))
        assertEquals(null, ConversationStore.get(7, 999))
    }
}
