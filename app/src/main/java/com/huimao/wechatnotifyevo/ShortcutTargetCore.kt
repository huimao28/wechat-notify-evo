package com.huimao.wechatnotifyevo

/** Android-free shortcut routing policy, kept pure for local unit tests. */
object ShortcutTargetCore {
    const val WECHAT_PACKAGE = "com.tencent.mm"
    const val WECHAT_LAUNCHER_CLASS = "com.tencent.mm.ui.LauncherUI"
    const val MAIN_USER_EXTRA = "Main_User"
    const val MULTI_TALKER_EXTRA = "Intro_Is_Muti_Talker"

    enum class Kind { DIRECT, TRAMPOLINE }

    data class LauncherAccess(
        val exported: Boolean,
        val activityEnabled: Boolean,
        val applicationEnabled: Boolean,
        val permission: String?,
    ) {
        val usable: Boolean get() = exported && activityEnabled && applicationEnabled && permission.isNullOrEmpty()
    }

    data class Target(
        val componentPackage: String,
        val componentClass: String,
        val action: String,
        val categories: Set<String> = emptySet(),
        val mainUser: String? = null,
        val introIsMultiTalker: Boolean? = null,
        val snapshot: String? = null,
        val nid: Int? = null,
        val sourceUserId: Int? = null,
    )

    data class OwnShortcut(
        val id: String,
        val componentPackage: String?,
        val componentClass: String?,
        val mainUser: String?,
    )

    fun kind(mainUser: String?, directChatEnabled: Boolean, launcherUsable: Boolean): Kind =
        if (!mainUser.isNullOrEmpty() && directChatEnabled && launcherUsable) Kind.DIRECT else Kind.TRAMPOLINE

    fun sameTarget(actual: Target?, expected: Target): Boolean = actual == expected

    /** Candidates are entries returned by this app's ShortcutManager only. */
    fun restorableMainUser(stableId: String, candidates: Iterable<OwnShortcut>): String? = candidates
        .firstOrNull {
            it.id == stableId &&
                it.componentPackage == WECHAT_PACKAGE &&
                it.componentClass == WECHAT_LAUNCHER_CLASS &&
                !it.mainUser.isNullOrEmpty()
        }?.mainUser
}
