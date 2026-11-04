package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAlivePolicyTest {
    @Test
    fun missingPreferenceDefaultsToDisabled() {
        var requestedKey: String? = null
        var requestedDefault: Boolean? = null

        val enabled = KeepAlivePreferencePolicy.read { key, default ->
            requestedKey = key
            requestedDefault = default
            default
        }

        assertFalse(enabled)
        assertEquals(KeepAlivePreferencePolicy.KEY, requestedKey)
        assertFalse(requestedDefault!!)
    }

    @Test
    fun enablingPersistsBeforeStarting() {
        val events = mutableListOf<String>()
        KeepAliveTransition(
            writePreference = { events += "write:$it" },
            enableKeepAlive = { events += "enable" },
            disableKeepAlive = { events += "disable" },
        ).setEnabled(true)

        assertEquals(listOf("write:true", "enable"), events)
    }

    @Test
    fun disablingPersistsThenCancelsEverythingImmediately() {
        val events = mutableListOf<String>()
        KeepAliveTransition(
            writePreference = { events += "write:$it" },
            enableKeepAlive = { events += "enable" },
            disableKeepAlive = { events += "cancel-jobs-stop-service-remove-callbacks" },
        ).setEnabled(false)

        assertEquals(listOf("write:false", "cancel-jobs-stop-service-remove-callbacks"), events)
    }

    @Test
    fun enabledPolicySchedulesHealthCheck() {
        val events = mutableListOf<String>()
        KeepAliveSchedulingPolicy.reconcile(
            enabled = true,
            schedule = { events += "schedule" },
            cancel = { events += "cancel" },
        )
        assertEquals(listOf("schedule"), events)
    }

    @Test
    fun disabledPolicyCancelsHealthCheck() {
        val events = mutableListOf<String>()
        KeepAliveSchedulingPolicy.reconcile(
            enabled = false,
            schedule = { events += "schedule" },
            cancel = { events += "cancel" },
        )
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun rebindRequestsAreCooledDown() {
        val cooldown = RebindCooldown(cooldownMillis = 120_000L)

        assertTrue(cooldown.tryAcquire(1_000L))
        assertFalse(cooldown.tryAcquire(120_999L))
        assertTrue(cooldown.tryAcquire(121_000L))
        assertFalse(cooldown.tryAcquire(120_000L)) // monotonic clock rollback is rejected safely
    }
}
