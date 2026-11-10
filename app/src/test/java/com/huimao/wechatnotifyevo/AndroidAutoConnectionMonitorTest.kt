package com.huimao.wechatnotifyevo

import androidx.car.app.connection.CarConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoConnectionMonitorTest {
    @Test fun mapsEveryOfficialConnectionType() {
        assertEquals(AndroidAutoConnectionType.NOT_CONNECTED, AndroidAutoConnectionMonitor.mapConnectionType(CarConnection.CONNECTION_TYPE_NOT_CONNECTED))
        assertEquals(AndroidAutoConnectionType.NATIVE, AndroidAutoConnectionMonitor.mapConnectionType(CarConnection.CONNECTION_TYPE_NATIVE))
        assertEquals(AndroidAutoConnectionType.PROJECTION, AndroidAutoConnectionMonitor.mapConnectionType(CarConnection.CONNECTION_TYPE_PROJECTION))
    }

    @Test fun mapsNullAndFutureValuesToUnknown() {
        assertEquals(AndroidAutoConnectionType.UNKNOWN, AndroidAutoConnectionMonitor.mapConnectionType(null))
        assertEquals(AndroidAutoConnectionType.UNKNOWN, AndroidAutoConnectionMonitor.mapConnectionType(Int.MAX_VALUE))
    }

    @Test fun sameTypePreservesStateAndTimestamp() {
        val current = AndroidAutoConnectionState(AndroidAutoConnectionType.PROJECTION, 100L)
        val unchanged = AndroidAutoConnectionStateStore.nextState(current, AndroidAutoConnectionType.PROJECTION, 200L)
        assertSame(current, unchanged)
        assertEquals(100L, unchanged.updatedAtMillis)

        val changed = AndroidAutoConnectionStateStore.nextState(current, AndroidAutoConnectionType.NOT_CONNECTED, 300L)
        assertEquals(AndroidAutoConnectionType.NOT_CONNECTED, changed.type)
        assertEquals(300L, changed.updatedAtMillis)
    }

    @Test fun diagnosticsTextAndHealthPreserveAllStates() {
        assertEquals("未连接", DiagnosticsPlatform.connectionDescription(AndroidAutoConnectionType.NOT_CONNECTED))
        assertEquals("运行于 Android Automotive OS", DiagnosticsPlatform.connectionDescription(AndroidAutoConnectionType.NATIVE))
        assertEquals("已连接 Android Auto 投影", DiagnosticsPlatform.connectionDescription(AndroidAutoConnectionType.PROJECTION))
        assertTrue(DiagnosticsPlatform.connectionDescription(AndroidAutoConnectionType.UNKNOWN).contains("未知"))
        assertFalse(DiagnosticsPlatform.connectionHealth(AndroidAutoConnectionType.NOT_CONNECTED)!!)
        assertTrue(DiagnosticsPlatform.connectionHealth(AndroidAutoConnectionType.NATIVE)!!)
        assertTrue(DiagnosticsPlatform.connectionHealth(AndroidAutoConnectionType.PROJECTION)!!)
        assertNull(DiagnosticsPlatform.connectionHealth(AndroidAutoConnectionType.UNKNOWN))
    }
}
