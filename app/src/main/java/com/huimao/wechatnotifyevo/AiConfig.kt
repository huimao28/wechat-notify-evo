package com.huimao.wechatnotifyevo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiConfig(val endpoint: String = "", val model: String = "", val hasApiKey: Boolean = false) {
    val endpointHost: String? get() = AiEndpointValidator.validate(endpoint).getOrNull()?.host
    val configured: Boolean get() = endpointHost != null && model.isNotBlank() && hasApiKey
}

interface AiSecretStore {
    fun put(secret: CharArray)
    fun get(): CharArray?
    fun clear()
    fun exists(): Boolean = get() != null
}

class AndroidAiSecretStore(context: Context) : AiSecretStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun put(secret: CharArray) {
        require(secret.isNotEmpty())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = secret.concatToString().toByteArray(Charsets.UTF_8)
        try {
            val encrypted = cipher.doFinal(bytes)
            prefs.edit().putString(BLOB, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
        } finally { bytes.fill(0); secret.fill('\u0000') }
    }

    override fun get(): CharArray? {
        val encoded = prefs.getString(BLOB, null) ?: return null
        return try {
            val all = Base64.decode(encoded, Base64.NO_WRAP)
            require(all.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, IV_BYTES)))
            val plain = cipher.doFinal(all.copyOfRange(IV_BYTES, all.size))
            try { plain.toString(Charsets.UTF_8).toCharArray() } finally { plain.fill(0) }
        } catch (_: Throwable) {
            // Lock reset, invalidated key, or corrupt ciphertext: fail closed.
            clear(); null
        }
    }

    override fun clear() { prefs.edit().remove(BLOB).apply() }
    override fun exists(): Boolean {
        val value = get() ?: return false
        value.fill('\u0000')
        return true
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "ai_secret_ciphertext_v1"
        const val BLOB = "api_key_aes_gcm"
        const val ALIAS = "wne_ai_api_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

object AiConfigStore {
    private const val PREFS = "ai_config_v1"
    private const val ENDPOINT = "endpoint"
    private const val MODEL = "model"

    fun load(context: Context, secrets: AiSecretStore = AndroidAiSecretStore(context)): AiConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AiConfig(prefs.getString(ENDPOINT, "").orEmpty(), prefs.getString(MODEL, "").orEmpty(), secrets.exists())
    }

    fun save(context: Context, endpoint: String, model: String, apiKey: CharArray?, secrets: AiSecretStore = AndroidAiSecretStore(context)): Result<AiConfig> = runCatching {
        val validated = AiEndpointValidator.validate(endpoint).getOrThrow()
        require(model.isNotBlank() && model.length <= 200) { "模型不能为空" }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ENDPOINT, validated.url).putString(MODEL, model.trim()).apply()
        if (apiKey != null && apiKey.isNotEmpty()) secrets.put(apiKey)
        load(context, secrets)
    }
}
