package app.core.model

/**
 * 敏感凭据的存取契约（纯接口，Android 实现见 `:core:data` 的 `KeystoreSecretStore`）。
 *
 * 目前只存 **tsnet auth key**——它等价于 tailnet 的入场券，必须落 Keystore 加密，
 * 不允许明文写 SharedPreferences。
 *
 * 设计参考：AGENTS.md §9（C-01 / C-04）/ contracts.md C-01
 */
interface SecretStore {
    /** 读取 auth key；没有或解密失败返回 null。 */
    suspend fun authKey(): String?

    /** 保存（覆盖）auth key。 */
    suspend fun saveAuthKey(value: String)

    /** 清除 auth key（断开并吊销本地凭据）。 */
    suspend fun clearAuthKey()

    /** 是否已配置 auth key。 */
    suspend fun hasAuthKey(): Boolean
}
