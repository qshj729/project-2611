package com.hubdeck.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hubdeck.app.core.data.db.CredentialDao
import com.hubdeck.app.core.data.db.CredentialEntity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores sensitive strings (Tailscale Local API secret, control plane token, service API
 * tokens) encrypted with an AES-256-GCM key that never leaves the Android Keystore.
 *
 * Design reference: docs/design.md §8 and docs/tailscale-integration.md §5.6.
 *
 * Exported files never contain these values unless the user explicitly opts in.
 */
interface SecretProvider {
    suspend fun localApiSecret(): String?
    suspend fun controlPlaneToken(): String?
}

class CredentialStore(
    private val context: Context,
    private val credentialDao: CredentialDao,
) : SecretProvider {

    override suspend fun localApiSecret(): String? = readByKind(KIND_LOCAL_API_SECRET)

    override suspend fun controlPlaneToken(): String? = readByKind(KIND_CONTROL_TOKEN)

    suspend fun save(label: String, kind: String, secret: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val cipherText = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        credentialDao.upsert(
            CredentialEntity(
                id = "$kind:$label",
                label = label,
                kind = kind,
                cipherText = cipherText,
                iv = cipher.iv,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: String) = credentialDao.deleteById(id)

    suspend fun list(): List<CredentialEntity> = credentialDao.all()

    private suspend fun readByKind(kind: String): String? {
        val entry = credentialDao.all().firstOrNull { it.kind == kind } ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, entry.iv))
            String(cipher.doFinal(entry.cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Helper for exports: base64 of the encrypted payload (still useless without the Keystore key). */
    fun encodeForExport(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    companion object {
        const val KIND_LOCAL_API_SECRET = "localapi_secret"
        const val KIND_CONTROL_TOKEN = "oauth_token"
        const val KIND_SERVICE_TOKEN = "api_token"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "hubdeck.credentials.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
