package com.huimao.wechatnotifyevo

import org.junit.Assert.*
import org.junit.Test

class ShortcutTargetCoreTest {
    @Test fun learnedTargetIsDirectOnlyWhenLauncherIsPublicAndFeatureEnabled() {
        assertEquals(
            ShortcutTargetCore.Kind.DIRECT,
            ShortcutTargetCore.kind("synthetic-user", directChatEnabled = true, launcherUsable = true),
        )
        assertEquals(
            ShortcutTargetCore.Kind.TRAMPOLINE,
            ShortcutTargetCore.kind(null, directChatEnabled = true, launcherUsable = true),
        )
        assertEquals(
            ShortcutTargetCore.Kind.TRAMPOLINE,
            ShortcutTargetCore.kind("synthetic-user", directChatEnabled = false, launcherUsable = true),
        )
        assertFalse(ShortcutTargetCore.LauncherAccess(true, true, true, "signature.permission").usable)
        assertTrue(ShortcutTargetCore.LauncherAccess(true, true, true, null).usable)
    }

    @Test fun existingOwnStableShortcutRestoresMainUser() {
        val restored = ShortcutTargetCore.restorableMainUser(
            "conv_stable",
            listOf(
                ShortcutTargetCore.OwnShortcut(
                    "conv_stable",
                    ShortcutTargetCore.WECHAT_PACKAGE,
                    ShortcutTargetCore.WECHAT_LAUNCHER_CLASS,
                    "synthetic-user",
                )
            ),
        )
        assertEquals("synthetic-user", restored)
    }

    @Test fun wrongComponentOrWrongStableIdIsRejected() {
        val candidates = listOf(
            ShortcutTargetCore.OwnShortcut(
                "conv_stable", ShortcutTargetCore.WECHAT_PACKAGE,
                "com.tencent.mm.plugin.SomePrivateActivity", "synthetic-user"
            ),
            ShortcutTargetCore.OwnShortcut(
                "other", ShortcutTargetCore.WECHAT_PACKAGE,
                ShortcutTargetCore.WECHAT_LAUNCHER_CLASS, "synthetic-user"
            ),
        )
        assertNull(ShortcutTargetCore.restorableMainUser("conv_stable", candidates))
    }

    @Test fun sameTargetComparesMainUserAndIntroFlag() {
        val expected = ShortcutTargetCore.Target(
            ShortcutTargetCore.WECHAT_PACKAGE,
            ShortcutTargetCore.WECHAT_LAUNCHER_CLASS,
            "android.intent.action.MAIN",
            setOf("android.intent.category.LAUNCHER"),
            mainUser = "synthetic-a",
            introIsMultiTalker = false,
        )
        assertTrue(ShortcutTargetCore.sameTarget(expected.copy(), expected))
        assertFalse(ShortcutTargetCore.sameTarget(expected.copy(mainUser = "synthetic-b"), expected))
        assertFalse(ShortcutTargetCore.sameTarget(expected.copy(introIsMultiTalker = true), expected))
        assertFalse(ShortcutTargetCore.sameTarget(expected.copy(mainUser = null), expected))
    }
}
