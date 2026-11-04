package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            startService = { events += "start" },
            stopService = { events += "stop" },
        ).setEnabled(true)

        assertEquals(listOf("write:true", "start"), events)
    }

    @Test
    fun disablingPersistsThenStopsImmediately() {
        val events = mutableListOf<String>()
        KeepAliveTransition(
            writePreference = { events += "write:$it" },
            startService = { events += "start" },
            stopService = { events += "stop" },
        ).setEnabled(false)

        assertEquals(listOf("write:false", "stop"), events)
    }
}
