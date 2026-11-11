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
    val baseUrl: String = "",
    val model: String = "",
    val appendV1Suffix: Boolean = true,
) {
    val isVisible get() = mode !is ProviderEditorMode.Closed
    val editingId get() = (mode as? ProviderEditorMode.Edit)?.providerId
    val canSaveAsDefault get() = isVisible
    fun beginCreate() = ProviderEditorState(mode = ProviderEditorMode.Create)
    fun beginEdit(provider: AiProvider) = ProviderEditorState(
        mode = ProviderEditorMode.Edit(provider.id), name = provider.displayName,
        baseUrl = if (provider.entity.apiPathMode == AiApiPathMode.FULL_ENDPOINT) provider.endpoint else provider.baseUrl,
        model = provider.modelId,
        appendV1Suffix = provider.entity.appendV1Suffix,
    )
    fun cancel() = ProviderEditorState()
}
