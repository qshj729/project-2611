package app.feature.settings

import app.core.model.SecretStore

/**
 * [SecretStore] 的假实现，用于 JVM 单测。
 * auth key 保存在内存变量里。
 */
class FakeSecretStore : SecretStore {

    private var _authKey: String? = null

    override suspend fun authKey(): String? = _authKey

    override suspend fun saveAuthKey(value: String) {
        _authKey = value
    }

    override suspend fun clearAuthKey() {
        _authKey = null
    }

    override suspend fun hasAuthKey(): Boolean = _authKey != null

    /** 测试辅助：直接设置 auth key。 */
    fun setAuthKey(key: String?) {
        _authKey = key
    }
}