package com.huimao.wechatnotifyevo

import org.junit.Assert.*
import org.junit.Test

class DiagnosticCoreTest {
    @Test fun everyReasonHasReadableExplanation() {
        DiagnosticStage.values().forEach {
            assertTrue(it.wireName.isNotBlank())
            assertTrue(it.explanation().length >= 6)
        }
        assertTrue(DiagnosticStage.MISSING_TICKER.explanation().contains("ticker"))
    }

    @Test fun ringBufferIsBoundedNewestFirstAndPrivacyShapeIsMinimal() {
        val buffer = DiagnosticRingBuffer(20)
        repeat(25) { buffer.add(DiagnosticEvent(it.toLong(), it % 2, DiagnosticStage.RECEIVED)) }
        val values = buffer.snapshot()
        assertEquals(20, values.size)
        assertEquals(24, values.first().timestampMillis)
        assertEquals(5, values.last().timestampMillis)
        assertEquals(setOf("timestampMillis", "sourceUserId", "stage"),
            DiagnosticEvent::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet())
    }

    @Test fun recognizesSupportedVendorFamilies() {
        assertEquals(VendorFamily.COLOR_OS, VendorGuidePolicy.identify("OPPO", "OPPO"))
        assertEquals(VendorFamily.COLOR_OS, VendorGuidePolicy.identify("OnePlus", "oneplus"))
        assertEquals(VendorFamily.COLOR_OS, VendorGuidePolicy.identify("realme", "realme"))
        assertEquals(VendorFamily.MAGIC_OS, VendorGuidePolicy.identify("HONOR", "HONOR"))
        assertEquals(VendorFamily.XIAOMI, VendorGuidePolicy.identify("Xiaomi", "POCO"))
        assertEquals(VendorFamily.ONE_UI, VendorGuidePolicy.identify("samsung", "Samsung"))
        assertEquals(VendorFamily.VIVO, VendorGuidePolicy.identify("vivo", "iQOO"))
        assertEquals(VendorFamily.HUAWEI, VendorGuidePolicy.identify("HUAWEI", "Huawei"))
        assertEquals(VendorFamily.GENERIC, VendorGuidePolicy.identify("unknown", "other"))
    }

    @Test fun reportExcludesSensitiveSentinelsAndSummarizesStatuses() {
        val sentinels = listOf("SECRET_CHAT_BODY", "CONTACT_SENTINEL", "Main_User=SENSITIVE", "notification-key-full")
        val snapshot = DiagnosticSnapshot("1.2.3", listOf(
            DiagnosticStatus("通知使用权", "已配置", true),
            DiagnosticStatus("系统气泡支持", "支持", true),
        ), "Acme", "brand", "model", "Android 15")
        val report = DiagnosticReportBuilder.build(snapshot, listOf(
            DiagnosticEvent(123, 0, DiagnosticStage.EVOLVED_POSTED),
            DiagnosticEvent(124, 0, DiagnosticStage.MISSING_TEXT),
        ))
        sentinels.forEach { assertFalse(report.contains(it)) }
        assertTrue(report.contains("通知使用权: 已配置"))
        assertTrue(report.contains("missingText: 1"))
        assertTrue(report.contains(DiagnosticReportBuilder.PRIVACY_NOTICE))
    }

    @Test fun shortcutCleanupOnlySelectsWneOrphans() {
        val selected = ShortcutCleanupPolicy.orphanIds(
            listOf("conv_live", "conv_old", "other_app", "conv_old"), setOf("conv_live")
        )
        assertEquals(listOf("conv_old"), selected)
    }
}
