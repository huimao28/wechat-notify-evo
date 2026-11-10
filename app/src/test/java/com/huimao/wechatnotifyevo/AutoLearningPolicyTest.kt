package com.huimao.wechatnotifyevo

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AutoLearningPolicyTest {
    companion object { private const val TEST_USER = 0 }
    @After fun clear() = ConversationStore.clear()
    private fun conversation(id: Int = 1) = ConversationStore.getOrCreate(id, "same", TEST_USER).apply {
        replaceSnapshot("key-$id", 1L, null)
    }

    @Test fun disabledDoesNotConsumeButDoesNotRetryWhileDisabled() {
        val p = AutoLearningPolicy()
        val c = conversation()
        assertFalse(p.admit(c, false, false, 0))
        assertFalse(c.autoLearningConsidered)
        assertFalse(p.admit(c, false, false, 60_000))
        assertTrue(p.admit(c, true, false, 60_000))
    }
    @Test fun subsequentMessagesNeverRepeatAnAttempt() {
        val p = AutoLearningPolicy()
        val c = conversation()
        assertTrue(p.admit(c, true, false, 0))
        c.replaceSnapshot("key-1", 2L, null)
        assertFalse(p.admit(c, true, false, 3_600_000))
    }
    @Test fun busyDoesNotConsumeAndLaterNotificationCanRetry() {
        val p = AutoLearningPolicy()
        val c = conversation()
        assertFalse(p.admit(c, true, true, 0))
        assertFalse(c.autoLearningConsidered)
        assertTrue(p.admit(c, true, false, 60_000))
    }
    @Test fun minuteLimitDoesNotConsumeSkippedConversation() {
        val p = AutoLearningPolicy()
        assertTrue(p.admit(conversation(1), true, false, 0))
        val skipped = conversation(2)
        assertFalse(p.admit(skipped, true, false, 59_999))
        assertFalse(skipped.autoLearningConsidered)
        assertTrue(p.admit(skipped, true, false, 60_000))
        assertFalse(p.admit(conversation(3), true, false, 60_000))
    }
    @Test fun sixPerRollingHourAndNoBurstAtExpiry() {
        val p = AutoLearningPolicy()
        repeat(6) { assertTrue(p.admit(conversation(it), true, false, it * 60_000L)) }
        assertFalse(p.admit(conversation(6), true, false, 360_000))
        assertTrue(p.admit(conversation(7), true, false, 3_600_000))
        assertFalse(p.admit(conversation(8), true, false, 3_600_001))
    }
    @Test fun failedAttemptCanRetryButSuccessfulAttemptCannot() {
        val p = AutoLearningPolicy()
        val failed = conversation(20)
        assertTrue(p.admit(failed, true, false, 0))
        p.failed(failed)
        assertFalse(failed.autoLearningConsidered)
        assertTrue(p.admit(failed, true, false, 60_000))

        val success = conversation(21)
        assertTrue(p.admit(success, true, false, 120_000))
        success.learnedMainUser = "synthetic"
        p.failed(success)
        assertTrue(success.autoLearningConsidered)
        assertFalse(p.admit(success, true, false, 180_000))
    }
    @Test fun alreadyLearnedConversationIsNotSent() {
        val c = conversation().apply { learnedMainUser = "synthetic" }
        assertFalse(AutoLearningPolicy().admit(c, true, false, 0))
    }
    @Test fun changedKeyDropsMappingAndChangesRoute() {
        val c = conversation().apply { learnedMainUser = "synthetic"; autoLearningConsidered = true }
        val route = c.routeToken
        c.replaceSnapshot("different-key", 2, null)
        assertNull(c.learnedMainUser)
        assertNotEquals(route, c.routeToken)
        assertFalse(c.autoLearningConsidered)
    }
    @Test fun appearanceChangeAndDisappearanceOfSourceIdentityInvalidate() {
        val c = conversation()
        for (identity in listOf("source-a", "source-b", null)) {
            c.learnedMainUser = "synthetic"
            val route = c.routeToken
            c.replaceSnapshot("key-1", 2, null, identity)
            assertNull(c.learnedMainUser)
            assertNotEquals(route, c.routeToken)
        }
    }
    @Test fun sameIdTitleKeyAfterRemovalCannotRecoverMapping() {
        val c = conversation().apply { learnedMainUser = "synthetic" }
        val route = c.routeToken
        ConversationStore.remove(1, TEST_USER)
        val next = conversation()
        assertNull(c.learnedMainUser)
        assertNull(next.learnedMainUser)
        assertNotEquals(route, next.routeToken)
    }
    @Test fun clearErasesLiveObjectMapping() {
        val c = conversation().apply { learnedMainUser = "synthetic" }
        ConversationStore.clear()
        assertNull(c.learnedMainUser)
    }
    @Test fun disableThenEnableRejectsOldCallbackAndDoesNotStealNewSlot() {
        val gate = LearningRequestGate()
        val old = gate.begin()!!
        assertNull(gate.begin())
        gate.cancel()
        val fresh = gate.begin()!!
        assertFalse(gate.finish(old))
        assertTrue(gate.accepts(fresh))
        assertTrue(gate.finish(fresh))
        assertFalse(gate.finish(fresh))
    }
    @Test fun timeoutAndSendFailureRejectLateCallback() {
        val gate = LearningRequestGate()
        val timeout = gate.begin()!!
        assertTrue(gate.finish(timeout))
        assertFalse(gate.finish(timeout))
        val failure = gate.begin()!!
        gate.cancel()
        assertFalse(gate.finish(failure))
    }
}
