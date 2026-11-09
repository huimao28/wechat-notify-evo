package com.huimao.wechatnotifyevo

enum class DiagnosticStage(val wireName: String) {
    RECEIVED("received"), DISABLED("disabled"), FOREIGN_USER("foreignUser"),
    MISSING_CONTENT_INTENT("missingContentIntent"), ONGOING("ongoing"), SUMMARY("summary"),
    MISSING_TITLE("missingTitle"), MISSING_TICKER("missingTicker"), MISSING_TEXT("missingText"),
    GROUP_DISABLED("groupDisabled"), EVOLVED_POSTED("evolvedPosted"), SHORTCUT_FAILED("shortcutFailed"),
    BUBBLE_ELIGIBILITY("bubbleEligibility"), AUTO_LEARN_STARTED("autoLearnStarted"),
    AUTO_LEARN_RESULT("autoLearnResult"), AUTO_LEARN_SKIP("autoLearnSkip");

    fun explanation(): String = when (this) {
        RECEIVED -> "已收到微信通知，正在检查是否可进化"
        DISABLED -> "总开关已关闭"
        FOREIGN_USER -> "通知来自其他 Android 用户或工作资料"
        MISSING_CONTENT_INTENT -> "微信未提供可公开调用的通知入口"
        ONGOING -> "持续性通知不进行进化"
        SUMMARY -> "这是微信汇总通知，已交由应用汇总替代"
        MISSING_TITLE -> "通知缺少标题"
        MISSING_TICKER -> "通知缺少 ticker，可能不是聊天消息"
        MISSING_TEXT -> "通知缺少消息文本"
        GROUP_DISABLED -> "检测为群聊，但群聊进化已关闭"
        EVOLVED_POSTED -> "进化版通知已发布"
        SHORTCUT_FAILED -> "会话 Shortcut 未能确认发布"
        BUBBLE_ELIGIBILITY -> "已按当前气泡开关记录本条资格"
        AUTO_LEARN_STARTED -> "已开始自动学习（可能短暂打开微信）"
        AUTO_LEARN_RESULT -> "自动学习已结束；结果不含会话信息"
        AUTO_LEARN_SKIP -> "自动学习条件不满足或已跳过"
    }
}

/** Deliberately contains no title, text, contact, key, intent, extras or session identifier. */
data class DiagnosticEvent(
    val timestampMillis: Long,
    val sourceUserId: Int,
    val stage: DiagnosticStage,
)

class DiagnosticRingBuffer(private val capacity: Int = 20) {
    init { require(capacity > 0) }
    private val values = ArrayDeque<DiagnosticEvent>(capacity)
    @Synchronized fun add(event: DiagnosticEvent) {
        if (values.size == capacity) values.removeFirst()
        values.addLast(event)
    }
    @Synchronized fun snapshot(): List<DiagnosticEvent> = values.toList().asReversed()
    @Synchronized fun clear() = values.clear()
}

object InMemoryDiagnostics {
    private val events = DiagnosticRingBuffer(20)
    fun record(sourceUserId: Int, stage: DiagnosticStage) =
        events.add(DiagnosticEvent(System.currentTimeMillis(), sourceUserId, stage))
    fun snapshot(): List<DiagnosticEvent> = events.snapshot()
}

enum class VendorFamily(val displayName: String) {
    COLOR_OS("ColorOS（OPPO / OnePlus / realme）"), MAGIC_OS("MagicOS（HONOR）"),
    XIAOMI("HyperOS / MIUI（Xiaomi / Redmi / POCO）"), ONE_UI("One UI（Samsung）"),
    VIVO("vivo / iQOO"), HUAWEI("Huawei"), GENERIC("通用 Android")
}

object VendorGuidePolicy {
    fun identify(manufacturer: String, brand: String): VendorFamily {
        val value = "$manufacturer $brand".lowercase()
        return when {
            listOf("oppo", "oneplus", "realme").any(value::contains) -> VendorFamily.COLOR_OS
            value.contains("honor") -> VendorFamily.MAGIC_OS
            listOf("xiaomi", "redmi", "poco").any(value::contains) -> VendorFamily.XIAOMI
            value.contains("samsung") -> VendorFamily.ONE_UI
            listOf("vivo", "iqoo").any(value::contains) -> VendorFamily.VIVO
            value.contains("huawei") -> VendorFamily.HUAWEI
            else -> VendorFamily.GENERIC
        }
    }

    fun steps(family: VendorFamily): List<String> {
        val prefix = when (family) {
            VendorFamily.COLOR_OS -> "在系统设置中搜索“自启动/关联启动/后台运行”"
            VendorFamily.MAGIC_OS -> "在系统管家或应用启动管理中查找“自动管理/手动管理”"
            VendorFamily.XIAOMI -> "在应用管理中查找“自启动/省电策略/后台活动”"
            VendorFamily.ONE_UI -> "在电池与设备维护中查找“后台使用限制/从不自动休眠”"
            VendorFamily.VIVO -> "在权限管理或电池中查找“自启动/高耗电后台运行”"
            VendorFamily.HUAWEI -> "在应用启动管理中查找“自动管理/手动管理”"
            VendorFamily.GENERIC -> "在应用、电池或后台管理中查找相应选项"
        }
        return listOf(
            "$prefix，并允许本应用自启动和后台运行。",
            "在最近任务界面按系统说明锁定本应用（若系统提供）。",
            "将本应用电池策略设为“不优化”或“不受限制”。",
            "确认通知使用权保持开启。",
            "在通知设置中允许通知，并在系统支持时允许气泡。",
            "菜单名称和路径会随系统版本变化；本应用只打开标准设置页，不会自动修改设置。",
        )
    }
}

data class DiagnosticStatus(val label: String, val value: String, val healthy: Boolean? = null)
data class DiagnosticSnapshot(
    val appVersion: String,
    val statuses: List<DiagnosticStatus>,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val osVersion: String,
)

object DiagnosticReportBuilder {
    const val PRIVACY_NOTICE = "本报告不含聊天正文、联系人、Main_User、通知 key、Intent extras 或凭据；最近事件仅保存在进程内存，进程重启即清空。"
    fun build(snapshot: DiagnosticSnapshot, events: List<DiagnosticEvent>): String {
        val counts = events.groupingBy { it.stage.wireName }.eachCount().toSortedMap()
        return buildString {
            appendLine("# 微信通知进化 · 脱敏诊断报告")
            appendLine()
            appendLine(PRIVACY_NOTICE)
            appendLine("应用版本: ${snapshot.appVersion}")
            appendLine("设备: ${snapshot.manufacturer} / ${snapshot.brand} / ${snapshot.model}")
            appendLine("系统: ${snapshot.osVersion}")
            appendLine()
            appendLine("## 状态")
            snapshot.statuses.forEach { appendLine("- ${it.label}: ${it.value}") }
            appendLine()
            appendLine("## 原因计数")
            if (counts.isEmpty()) appendLine("- 无内存事件") else counts.forEach { (k, v) -> appendLine("- $k: $v") }
            appendLine()
            appendLine("## 最近事件（仅时间、枚举）")
            if (events.isEmpty()) appendLine("- 无；进程重启会清空") else events.forEach {
                appendLine("- ${it.timestampMillis} | ${it.stage.wireName}")
            }
        }
    }
}

object ShortcutCleanupPolicy {
    fun orphanIds(allIds: Collection<String>, liveIds: Set<String>): List<String> =
        allIds.filter { it.startsWith("conv_") && it !in liveIds }.distinct()
}
