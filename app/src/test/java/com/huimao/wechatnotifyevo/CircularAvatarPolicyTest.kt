package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircularAvatarPolicyTest {
    @Test
    fun defaultIsEnabled() {
        assertTrue(CircularAvatarPolicy.DEFAULT_ENABLED)
    }

    @Test
    fun disabledAlwaysUsesOriginal() {
        assertFalse(CircularAvatarPolicy.useProcessed(enabled = false, processedAvailable = true))
        assertTrue(CircularAvatarPolicy.useProcessed(enabled = true, processedAvailable = true))
        assertFalse(CircularAvatarPolicy.useProcessed(enabled = true, processedAvailable = false))
    }

    @Test
    fun outputIsBoundedAndNeverUpscalesKnownSource() {
        assertEquals(256, circularAvatarOutputSize(1024, 1024))
        assertEquals(64, circularAvatarOutputSize(64, 64))
        assertEquals(80, circularAvatarOutputSize(320, 80))
    }

    @Test
    fun wideImageCenterCropTrimsBothHorizontalEdges() {
        val crop = centerCropGeometry(sourceWidth = 400, sourceHeight = 200, targetSize = 100)
        assertEquals(0.5f, crop.scale, 0.0001f)
        assertEquals(-50f, crop.left, 0.0001f)
        assertEquals(0f, crop.top, 0.0001f)
        assertEquals(200f, crop.width, 0.0001f)
        assertEquals(100f, crop.height, 0.0001f)
    }

    @Test
    fun tallImageCenterCropTrimsBothVerticalEdges() {
        val crop = centerCropGeometry(sourceWidth = 120, sourceHeight = 360, targetSize = 60)
        assertEquals(0.5f, crop.scale, 0.0001f)
        assertEquals(0f, crop.left, 0.0001f)
        assertEquals(-60f, crop.top, 0.0001f)
        assertEquals(60f, crop.width, 0.0001f)
        assertEquals(180f, crop.height, 0.0001f)
    }
}
