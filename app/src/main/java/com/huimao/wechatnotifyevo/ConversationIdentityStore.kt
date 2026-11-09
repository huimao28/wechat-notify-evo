package com.huimao.wechatnotifyevo

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/**
 * App-private identity storage. It stores only a random device salt and HMAC outputs;
 * Main_User, titles and source shortcut ids are never persisted in plaintext.
 */
object ConversationIdentityStore {
    private const val PREFS = "conversation_identity_v2"
    private const val SALT = "device_salt"
    private const val BINDING = "binding_"

    enum class Kind { STABLE, TEMPORARY }
    data class Resolution(val id: String, val kind: Kind, val reused: Boolean)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun salt(context: Context): ByteArray {
        val p = prefs(context)
        p.getString(SALT, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val generated = ByteArray(32).also(SecureRandom()::nextBytes)
        p.edit().putString(SALT, Base64.encodeToString(generated, Base64.NO_WRAP)).apply()
        return generated
    }

    private fun key(sourceUserId: Int, nid: Int) = "$BINDING${sourceUserId}_$nid"

    fun resolve(context: Context, nid: Int, sourceUserId: Int, title: String,
                sourceIdentity: String?): Resolution {
        val salt = salt(context)
        val identityProof = ConversationIdentityCore.proof(salt, "source", sourceIdentity)
        val titleProof = ConversationIdentityCore.proof(
            salt, "title-source", "$title|${sourceIdentity.orEmpty()}"
        )
        val old = ConversationIdentityCore.decode(prefs(context).getString(key(sourceUserId, nid), null))
        val reusable = old != null && if (old.identityProof.startsWith("main:")) {
            old.version == 2 && old.stableId.startsWith(ConversationIdentityCore.PREFIX) &&
                old.titleProof == titleProof
        } else {
            ConversationIdentityCore.reusable(old, identityProof, titleProof)
        }
        if (old != null && reusable) {
            val kind = if (old.identityProof.startsWith("main:")) Kind.STABLE else Kind.TEMPORARY
            touch(context, sourceUserId, nid, old.copy(lastSeen = System.currentTimeMillis()))
            return Resolution(old.stableId, kind, true)
        }
        val fallback = ConversationIdentityCore.fallbackId(salt, context.packageName, sourceUserId, nid)
        save(context, sourceUserId, nid, fallback, identityProof, titleProof)
        return Resolution(fallback, Kind.TEMPORARY, false)
    }

    fun bindMainUser(context: Context, conv: Conversation, mainUser: String): String {
        val salt = salt(context)
        val id = ConversationIdentityCore.stableId(salt, context.packageName, conv.sourceUserId, mainUser)
        // Prefix marks proof provenance without exposing the input.
        val proof = "main:" + ConversationIdentityCore.proof(salt, "main", mainUser)
        val titleProof = ConversationIdentityCore.proof(
            salt, "title-source", "${conv.title}|${conv.sourceIdentity.orEmpty()}"
        )
        save(context, conv.sourceUserId, conv.nid, id, proof, titleProof)
        return id
    }

    private fun save(context: Context, sourceUserId: Int, nid: Int, id: String,
                     identityProof: String, titleProof: String) = touch(
        context, sourceUserId, nid,
        ConversationIdentityCore.Binding(id, identityProof, titleProof, System.currentTimeMillis())
    )

    private fun touch(context: Context, sourceUserId: Int, nid: Int,
                      binding: ConversationIdentityCore.Binding) {
        prefs(context).edit().putString(key(sourceUserId, nid),
            ConversationIdentityCore.encode(binding)).apply()
    }

    fun counts(context: Context): Pair<Int, Int> {
        val values = prefs(context).all.filterKeys { it.startsWith(BINDING) }.values
        val decoded = values.mapNotNull { ConversationIdentityCore.decode(it as? String) }
        return decoded.count { it.identityProof.startsWith("main:") } to
            decoded.count { !it.identityProof.startsWith("main:") }
    }
}
