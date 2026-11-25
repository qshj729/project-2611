package app.core.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.core.model.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 加密保存 tsnet auth key。
 *
 * - 密钥（AES-256-GCM）只存在于 Keystore，密文与 IV 以 base64 存进 SharedPreferences；
 * - 删 App 数据 / 卸载即失效；
 * - **不再使用明文 SharedPreferences**（v1 的 `TunnelProvider` 曾经这么做）。
 *
 * 设计参考：design.md §4.5（C-04 凭据）
 */
class KeystoreSecretStore(
    private val context: Context,
) : SecretStore {

    override suspend fun authKey(): String? = withContext(Dispatchers.IO) {
        val raw = prefs().getString(KEY_AUTH_KEY, null) ?: return@withContext null
        runCatching {
            val parts = raw.split(SEPARATOR)
            if (parts.size != 2) return@runCatching null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun saveAuthKey(value: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val cipherText = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            SEPARATOR +
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
        prefs().edit().putString(KEY_AUTH_KEY, payload).apply()
    }

    override suspend fun clearAuthKey() = withContext(Dispatchers.IO) {
        prefs().edit().remove(KEY_AUTH_KEY).apply()
    }

    override suspend fun hasAuthKey(): Boolean = authKey()?.isNotBlank() == true

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "myapps.authkey.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "myapps_secrets"
        private const val KEY_AUTH_KEY = "tsnet_auth_key"
        private const val SEPARATOR = ":"
    }
}