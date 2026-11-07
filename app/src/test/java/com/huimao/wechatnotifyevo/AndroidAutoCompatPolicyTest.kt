package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoCompatPolicyTest {
    @Test
    fun `real actions always win when compatibility is enabled`() {
        val plan = AndroidAutoCompatPolicy.plan(true, hasRealReply = true, hasRealMarkRead = true)
        assertEquals(CompatActionKind.REAL, plan.reply)
        assertEquals(CompatActionKind.REAL, plan.markRead)
    }

    @Test
    fun `enabled compatibility fills only missing action`() {
        val missingRead = AndroidAutoCompatPolicy.plan(true, true, false)
        assertEquals(CompatActionKind.REAL, missingRead.reply)
        assertEquals(CompatActionKind.SIMULATED, missingRead.markRead)

        val missingReply = AndroidAutoCompatPolicy.plan(true, false, true)
        assertEquals(CompatActionKind.SIMULATED, missingReply.reply)
        assertEquals(CompatActionKind.REAL, missingReply.markRead)
    }

    @Test
    fun `disabled compatibility adds no missing actions`() {
        val plan = AndroidAutoCompatPolicy.plan(false, false, false)
        assertEquals(CompatActionKind.NONE, plan.reply)
        assertEquals(CompatActionKind.NONE, plan.markRead)
    }

    @Test
    fun `route requires current user nid and random token`() {
        val route = CompatRoute(42, 10, "random-generation")
        assertTrue(AndroidAutoCompatRoutePolicy.isCurrent(route, 10, 42, 10, "random-generation"))
        assertFalse(AndroidAutoCompatRoutePolicy.isCurrent(route.copy(routeToken = "old"), 10, 42, 10, "random-generation"))
        assertFalse(AndroidAutoCompatRoutePolicy.isCurrent(route.copy(sourceUserId = 11), 10, 42, 10, "random-generation"))
        assertFalse(AndroidAutoCompatRoutePolicy.isCurrent(route.copy(nid = 43), 10, 42, 10, "random-generation"))
        assertFalse(AndroidAutoCompatRoutePolicy.isCurrent(route.copy(routeToken = ""), 10, 42, 10, ""))
    }

    @Test
    fun `simulated reply is explicit local echo and never persisted or forwarded`() {
        val effect = requireNotNull(SimulatedReplyPolicy.create("收到"))
        assertEquals("【仅车机本地回显，未发送微信】我：收到", effect.localEchoText)
        assertFalse(effect.forwardToWechat)
        assertFalse(effect.persistReplyText)
        assertNull(SimulatedReplyPolicy.create("  "))
    }

    @Test
    fun `action configuration uses stable key and no UI`() {
        assertEquals("wne_android_auto_compat_reply", AndroidAutoCompatPolicy.REMOTE_INPUT_RESULT_KEY)
        assertFalse(AndroidAutoCompatPolicy.MARK_READ_SHOWS_USER_INTERFACE)
        assertFalse(AndroidAutoCompatPolicy.REPLY_SHOWS_USER_INTERFACE)
        assertEquals(
            "android.support.action.showsUserInterface",
            AndroidAutoCompatPolicy.EXTRA_SHOWS_USER_INTERFACE,
        )
    }
}
