package app.feature.settings

import app.core.data.config.InternalServicesConfig
import app.core.data.config.InternalServicesStore
import app.core.model.InternalService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [InternalServicesStore] 的假实现，用于 JVM 单测。
 * 清单保存在内存里，导入导出直接操作内存状态。
 */
class FakeInternalServicesStore : InternalServicesStore {

    private val _services = MutableStateFlow<List<InternalService>>(emptyList())

    override fun observe(): Flow<List<InternalService>> = _services

    override suspend fun current(): List<InternalService> = _services.value

    override suspend fun replaceAll(services: List<InternalService>) {
        _services.value = services
    }

    override suspend fun importYaml(text: String): Int {
        val result = InternalServicesConfig.parse(text)
        replaceAll(result.services)
        return result.services.size
    }

    override suspend fun exportYaml(): String =
        InternalServicesConfig.render(_services.value)

    /** 测试辅助：直接设置服务列表。 */
    fun setServices(services: List<InternalService>) {
        _services.value = services
    }
}