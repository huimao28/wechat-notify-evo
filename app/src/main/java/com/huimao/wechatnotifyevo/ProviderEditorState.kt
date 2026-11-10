/* SPDX-License-Identifier: GPL-3.0-only */
package com.huimao.wechatnotifyevo

sealed interface ProviderEditorMode {
    data object Closed : ProviderEditorMode
    data object Create : ProviderEditorMode
    data class Edit(val providerId: String) : ProviderEditorMode
}

data class ProviderEditorState(
    val mode: ProviderEditorMode = ProviderEditorMode.Closed,
    val name: String = "",
    val endpoint: String = "",
    val model: String = "",
) {
    val isVisible: Boolean get() = mode !is ProviderEditorMode.Closed
    val editingId: String? get() = (mode as? ProviderEditorMode.Edit)?.providerId
    val canSaveAsDefault: Boolean get() = isVisible

    fun beginCreate() = ProviderEditorState(mode = ProviderEditorMode.Create)

    fun beginEdit(provider: AiProvider) = ProviderEditorState(
        mode = ProviderEditorMode.Edit(provider.id),
        name = provider.displayName,
        endpoint = provider.endpoint,
        model = provider.modelId,
    )

    fun cancel() = ProviderEditorState()
}
