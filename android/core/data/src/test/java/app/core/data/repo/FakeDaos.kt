package app.core.data.repo

import app.core.data.db.CategoryDao
import app.core.data.db.CategoryEntity
import app.core.data.db.EndpointDao
import app.core.data.db.EndpointEntity
import app.core.data.db.HealthDao
import app.core.data.db.HealthSampleEntity
import app.core.data.db.ServiceDao
import app.core.data.db.ServiceEntity
import app.core.data.db.TagDao
import app.core.data.db.TagEntity
import app.core.data.db.ServiceTagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 内存版 DAO —— 给仓储做 JVM 单测用（Room 本身要 Android 运行时，跑不了纯 JVM 单测）。
 *
 * 这些假实现**故意不排序**：排序规则由仓储保证（契约 C-02 不变量 1 / 2 / 3），
 * 这样测试真的能抓住「排序被漏掉」这种回归。
 */
class FakeServiceDao : ServiceDao {
    private val state = MutableStateFlow<List<ServiceEntity>>(emptyList())

    /** 让查询抛异常，验证「仓储永不抛异常」（C-02 不变量 8）。 */
    var observeError: Throwable? = null
    var findError: Throwable? = null

    val upserts = mutableListOf<ServiceEntity>()
    val deleted = mutableListOf<String>()

    fun seed(vararg entities: ServiceEntity) {
        state.value = entities.toList()
    }

    override fun observeAll(): Flow<List<ServiceEntity>> = flow {
        observeError?.let { throw it }
        emitAll(state)
    }

    override fun observeByCategory(categoryId: String): Flow<List<ServiceEntity>> = flow {
        observeError?.let { throw it }
        emitAll(state.map { list -> list.filter { it.categoryId == categoryId } })
    }

    override fun observePinned(): Flow<List<ServiceEntity>> = flow {
        observeError?.let { throw it }
        emitAll(state.map { list -> list.filter { it.pinned } })
    }

    override fun observeByName(query: String): Flow<List<ServiceEntity>> = flow {
        observeError?.let { throw it }
        emitAll(state.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } })
    }

    override suspend fun search(query: String): List<ServiceEntity> {
        observeError?.let { throw it }
        return state.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun findById(id: String): ServiceEntity? {
        findError?.let { throw it }
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun findAllById(ids: List<String>): List<ServiceEntity> =
        state.value.filter { it.id in ids }

    override suspend fun upsert(service: ServiceEntity) {
        upserts += service
        state.value = state.value.filterNot { it.id == service.id } + service
    }

    override suspend fun upsertAll(services: List<ServiceEntity>) {
        services.forEach { upsert(it) }
    }

    override suspend fun update(service: ServiceEntity) = upsert(service)

    override suspend fun delete(service: ServiceEntity) {
        state.value = state.value.filterNot { it.id == service.id }
    }

    override suspend fun deleteById(id: String) {
        deleted += id
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeEndpointDao : EndpointDao {
    private val state = MutableStateFlow<List<EndpointEntity>>(emptyList())

    /** 记录调用顺序，验证「先删后插」（C-02 不变量 5）。 */
    val calls = mutableListOf<String>()

    fun seed(vararg entities: EndpointEntity) {
        state.value = entities.toList()
    }

    override suspend fun forService(serviceId: String): List<EndpointEntity> =
        state.value.filter { it.serviceId == serviceId }

    override suspend fun forServices(serviceIds: List<String>): List<EndpointEntity> =
        state.value.filter { it.serviceId in serviceIds }

    override suspend fun all(): List<EndpointEntity> = state.value

    override fun allFlow(): Flow<List<EndpointEntity>> = state

    override suspend fun upsertAll(endpoints: List<EndpointEntity>) {
        calls += "upsertAll"
        state.value = state.value.filterNot { existing -> endpoints.any { it.id == existing.id } } + endpoints
    }

    override suspend fun deleteForService(serviceId: String) {
        calls += "deleteForService"
        state.value = state.value.filterNot { it.serviceId == serviceId }
    }
}

class FakeCategoryDao : CategoryDao {
    private val state = MutableStateFlow<List<CategoryEntity>>(emptyList())

    var observeError: Throwable? = null

    fun seed(vararg entities: CategoryEntity) {
        state.value = entities.toList()
    }

    override fun observeAll(): Flow<List<CategoryEntity>> = flow {
        observeError?.let { throw it }
        emitAll(state)
    }

    override suspend fun upsertAll(categories: List<CategoryEntity>) {
        state.value = categories
    }
}

class FakeHealthDao : HealthDao {
    private val state = MutableStateFlow<List<HealthSampleEntity>>(emptyList())

    var observeError: Throwable? = null

    /** 记录最近一次 `observeRecent` 用的 limit。 */
    var lastLimit: Int? = null

    private var nextId = 1L

    fun seed(vararg entities: HealthSampleEntity) {
        state.value = entities.toList()
    }

    override fun observeRecent(serviceId: String, limit: Int): Flow<List<HealthSampleEntity>> {
        lastLimit = limit
        return flow {
            observeError?.let { throw it }
            emitAll(state.map { list -> list.filter { it.serviceId == serviceId }.take(limit) })
        }
    }

    override suspend fun latestFor(serviceId: String): HealthSampleEntity? =
        state.value.filter { it.serviceId == serviceId }.maxByOrNull { it.checkedAt }

    override suspend fun insert(sample: HealthSampleEntity) {
        val stored = if (sample.id == 0L) sample.copy(id = nextId++) else sample
        state.value = state.value.filterNot { it.id == stored.id } + stored
    }

    override suspend fun prune(olderThan: Long) {
        state.value = state.value.filterNot { it.checkedAt < olderThan }
    }
}

class FakeTagDao : TagDao {
    private val state = MutableStateFlow<List<TagEntity>>(emptyList())
    val links = mutableListOf<ServiceTagEntity>()

    var observeError: Throwable? = null

    fun seed(vararg entities: TagEntity) {
        state.value = entities.toList()
    }

    override suspend fun all(): List<TagEntity> = state.value

    override fun allFlow(): Flow<List<TagEntity>> = state

    override fun allLinksFlow(): Flow<List<ServiceTagEntity>> = flow { emit(links) }

    override suspend fun allLinks(): List<ServiceTagEntity> = links

    override suspend fun upsert(tag: TagEntity) {
        state.value = state.value.filterNot { it.id == tag.id } + tag
    }

    override suspend fun upsertAll(tags: List<TagEntity>) {
        tags.forEach { upsert(it) }
    }

    override suspend fun linkAll(links: List<ServiceTagEntity>) {
        this.links.addAll(links)
    }

    override suspend fun tagsForService(serviceId: String): List<ServiceTagEntity> =
        links.filter { it.serviceId == serviceId }

    override suspend fun deleteLinksForService(serviceId: String) {
        links.removeAll { it.serviceId == serviceId }
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}