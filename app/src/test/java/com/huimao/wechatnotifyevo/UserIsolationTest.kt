package com.huimao.wechatnotifyevo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UserIsolationTest {
    @After fun clear() = ConversationStore.clear()

    @Test fun userAllAndOtherNegativeSourcesFailOpenAndNormalize() {
        val current = 10
        for (source in listOf(-1, -10_000, -2_147_483_648)) {
            assertEquals(
                UserIsolationDecision.PROCESS,
                UserIsolationPolicy.decide(source, current, creatorUserId = null),
            )
            assertEquals(current, UserIsolationPolicy.normalizeSourceUserId(source, current))
        }
    }

    @Test fun sameUserIsProcessed() {
        assertEquals(
            UserIsolationDecision.PROCESS,
            UserIsolationPolicy.decide(sourceUserId = 10, currentUserId = 10, creatorUserId = 10),
        )
    }

    @Test fun concreteForeignUserIsNotProcessed() {
        assertEquals(
            UserIsolationDecision.IGNORE_SOURCE_USER,
            UserIsolationPolicy.decide(sourceUserId = 999, currentUserId = 10, creatorUserId = 10),
        )
    }

    @Test fun creatorMismatchIsDiagnosticOnlyAndDoesNotBlock() {
        assertEquals(
            UserIsolationDecision.PROCESS,
            UserIsolationPolicy.decide(sourceUserId = 10, currentUserId = 10, creatorUserId = 999),
        )
    }

    @Test fun normalizedConversationRemainsUsableForBubbleRoutingAndLearning() {
        val current = 10
        val conv = ConversationStore.getOrCreate(
            42,
            "main",
            sourceUserId = -1,
            currentUserId = current,
        )
        val routeToken = conv.routeToken

        assertEquals(current, conv.sourceUserId)
        assertSame(conv, ConversationStore.get(42, current))
        assertTrue(CurrentUserScope.accepts(conv, current))
        assertEquals(routeToken, conv.routeToken)
        assertTrue(AutoLearningPolicy().admit(conv, enabled = true, busy = false, now = 1L))
    }

    @Test fun sameNotificationIdInDifferentConcreteUsersNeverOverwrites() {
        val current = 10
        val foreign = current + 999
        val main = ConversationStore.getOrCreate(42, "main", sourceUserId = current)
        val clone = ConversationStore.getOrCreate(42, "clone", sourceUserId = foreign)
        assertNotSame(main, clone)
        assertSame(main, ConversationStore.get(42, current))
        assertSame(clone, ConversationStore.get(42, foreign))
        ConversationStore.remove(42, foreign)
        assertSame(main, ConversationStore.get(42, current))
    }

    @Test fun cleanupRemovesForeignSessionsOnly() {
        val current = 10
        val foreign = current + 999
        val main = ConversationStore.getOrCreate(7, "main", sourceUserId = current)
        val clone = ConversationStore.getOrCreate(7, "clone", sourceUserId = foreign)
        assertEquals(listOf(clone), ConversationStore.removeOtherUsers(current))
        assertSame(main, ConversationStore.get(7, current))
        assertEquals(null, ConversationStore.get(7, foreign))
    }
}
