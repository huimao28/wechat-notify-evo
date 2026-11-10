package com.huimao.wechatnotifyevo

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A database field encrypted with a unique AES-GCM nonce. */
data class EncryptedField(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
)

/** Crypto boundary kept injectable so policy/repository tests never touch Android Keystore. */
interface HistoryCrypto {
    fun encrypt(plaintext: String): EncryptedField
    fun decrypt(field: EncryptedField): String
    fun dedupeHmac(parts: List<String>): String
}

private object AndroidKeyProvider {
    private val lock = Any()

    fun aes(alias: String): SecretKey = getOrCreate(alias) {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    fun hmac(alias: String): SecretKey = getOrCreate(alias) {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generateKey()
        }
    }

    private fun getOrCreate(alias: String, create: () -> SecretKey): SecretKey = synchronized(lock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: create()
    }
}

/** AES-GCM fields and keyed dedupe tokens backed by non-exportable Android Keystore keys. */
class AndroidKeystoreHistoryCrypto(
    private val encryptionAlias: String = "wne_history_fields_aes_gcm_v1",
    private val dedupeAlias: String = "wne_history_dedupe_hmac_sha256_v1",
) : HistoryCrypto {
    override fun encrypt(plaintext: String): EncryptedField {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, AndroidKeyProvider.aes(encryptionAlias))
        return EncryptedField(
            ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8)),
            nonce = cipher.iv.copyOf(),
        )
    }

    override fun decrypt(field: EncryptedField): String {
        require(field.nonce.isNotEmpty()) { "Missing AES-GCM nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            AndroidKeyProvider.aes(encryptionAlias),
            GCMParameterSpec(128, field.nonce),
        )
        return String(cipher.doFinal(field.ciphertext), StandardCharsets.UTF_8)
    }

    override fun dedupeHmac(parts: List<String>): String {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(AndroidKeyProvider.hmac(dedupeAlias))
        }
        parts.forEach { part ->
            val bytes = part.toByteArray(StandardCharsets.UTF_8)
            mac.update((bytes.size ushr 24).toByte())
            mac.update((bytes.size ushr 16).toByte())
            mac.update((bytes.size ushr 8).toByte())
            mac.update(bytes.size.toByte())
            mac.update(bytes)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal())
    }
}
