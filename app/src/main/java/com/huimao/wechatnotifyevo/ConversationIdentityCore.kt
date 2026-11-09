package com.huimao.wechatnotifyevo

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Pure, testable derivation and persisted nid-binding policy. */
object ConversationIdentityCore {
    const val PREFIX = "conv_v2_"
    private const val VERSION = 2

    data class Binding(
        val stableId: String,
        val identityProof: String,
        val titleProof: String,
        val lastSeen: Long,
        val version: Int = VERSION,
    )

    fun hmacHex(salt: ByteArray, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun stableId(salt: ByteArray, packageName: String, accountScope: Int, mainUser: String): String =
        PREFIX + hmacHex(salt, "$packageName|user:$accountScope|main:$mainUser").take(40)

    fun fallbackId(salt: ByteArray, packageName: String, accountScope: Int, nid: Int,
                   discriminator: String = ""): String =
        PREFIX + hmacHex(salt,
            "$packageName|user:$accountScope|nid:$nid|proof:$discriminator").take(40)

    fun proof(salt: ByteArray, scope: String, value: String?): String =
        hmacHex(salt, "$scope:${value.orEmpty()}").take(32)

    fun reusable(binding: Binding, identityProof: String, titleProof: String): Boolean =
        binding.version == VERSION && binding.stableId.startsWith(PREFIX) &&
            binding.identityProof == identityProof && binding.titleProof == titleProof

    fun encode(binding: Binding): String = listOf(
        binding.version.toString(), binding.stableId, binding.identityProof,
        binding.titleProof, binding.lastSeen.toString(),
    ).joinToString("|")

    fun decode(value: String?): Binding? {
        val p = value?.split('|') ?: return null
        if (p.size != 5) return null
        return Binding(p[1], p[2], p[3], p[4].toLongOrNull() ?: return null,
            p[0].toIntOrNull() ?: return null)
    }
}
