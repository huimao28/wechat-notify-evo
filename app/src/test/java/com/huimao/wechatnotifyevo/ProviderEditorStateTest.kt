/* SPDX-License-Identifier: GPL-3.0-only */
package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderEditorStateTest {
    private val provider = AiProvider(
        AiProviderEntity(
            id = "provider-1", displayName = "OpenAI", baseUrl = "",
            endpoint = "https://api.openai.com/v1/chat/completions",
            modelId = "gpt-4o-mini", createdAt = 1, updatedAt = 1, sortOrder = 0,
        ),
        hasApiKey = true,
    )

    @Test fun closedCreateAndEditHaveExplicitVisibility() {
        val closed = ProviderEditorState()
        assertFalse(closed.isVisible)
        assertTrue(closed.mode is ProviderEditorMode.Closed)

        val create = closed.beginCreate()
        assertTrue(create.isVisible)
        assertTrue(create.mode is ProviderEditorMode.Create)
        assertNull(create.editingId)

        val edit = create.beginEdit(provider)
        assertTrue(edit.isVisible)
        assertEquals(ProviderEditorMode.Edit("provider-1"), edit.mode)
        assertEquals("OpenAI", edit.name)
    }

    @Test fun createCanSaveAsDefaultWithoutSelectedProvider() {
        val create = ProviderEditorState().beginCreate()
        assertNull(create.editingId)
        assertTrue(create.canSaveAsDefault)
    }

    @Test fun cancelClosesAndClearsDraft() {
        val cancelled = ProviderEditorState().beginEdit(provider).copy(name = "draft").cancel()
        assertFalse(cancelled.isVisible)
        assertTrue(cancelled.mode is ProviderEditorMode.Closed)
        assertEquals("", cancelled.name)
        assertEquals("", cancelled.baseUrl)
        assertEquals("", cancelled.model)
        assertNull(cancelled.editingId)
    }
}
