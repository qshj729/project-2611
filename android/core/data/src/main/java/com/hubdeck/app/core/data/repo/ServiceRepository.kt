package com.hubdeck.app.core.data.repo

import com.hubdeck.app.core.data.db.CategoryDao
import com.hubdeck.app.core.data.db.EndpointDao
import com.hubdeck.app.core.data.db.HealthDao
import com.hubdeck.app.core.data.db.ServiceDao
import com.hubdeck.app.core.data.db.ServiceEntity
import com.hubdeck.app.core.data.db.EndpointEntity
import com.hubdeck.app.core.data.db.toDomain
import com.hubdeck.app.core.model.Category
import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.HealthSample
import com.hubdeck.app.core.model.Service
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the service catalog.
 *
 * The repository maps Room entities to the pure domain model used by the UI
 * (see docs/design.md §7.4).
 */
@Singleton
class ServiceRepository @Inject constructor(
    private val serviceDao: ServiceDao,
    private val endpointDao: EndpointDao,
    private val categoryDao: CategoryDao,
    private val healthDao: HealthDao,
) {

    /**
     * 服务排序口径（冻结契约 C-02）：置顶优先 → sortIndex 升序 → 名称升序。
     *
     * DAO 的 SQL 已经是同样顺序，但单测用的假 DAO 故意不排序，所以仓储这里必须再兜一次。
     * 不用 `compareValuesBy`：它有 `(a, b, selector)` 重载，多个 lambda 会解析到错误重载。
     */
    private val serviceOrder: Comparator<Service> = Comparator { a, b ->
        val pinned = b.pinned.compareTo(a.pinned)
        if (pinned != 0) {
            pinned
        } else {
            val index = a.sortIndex.compareTo(b.sortIndex)
            if (index != 0) index else a.name.compareTo(b.name)
        }
    }

    /** 端点排序口径（冻结契约 C-02）：主端点优先 → scope 升序（LAN < TAILNET < PUBLIC）。 */
    private val endpointOrder: Comparator<Endpoint> = Comparator { a, b ->
        val primary = b.isPrimary.compareTo(a.isPrimary)
        if (primary != 0) primary else a.scope.compareTo(b.scope)
    }

    fun observeServices(): Flow<List<Service>> =
        combine(serviceDao.observeAll(), endpointDao.allFlow()) { services, endpoints ->
            val byService = endpoints.groupBy { it.serviceId }
            services
                .map { entity ->
                    entity.toDomain(
                        byService[entity.id]
                            .orEmpty()
                            .map { it.toDomain() }
                            .sortedWith(endpointOrder)
                    )
                }
                .sortedWith(serviceOrder)
        }.catch { emit(emptyList()) }

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll()
            .map { list ->
                list.map { Category(it.id, it.name, it.color, it.sortIndex) }
                    .sortedBy { it.sortIndex }
            }
            .catch { emit(emptyList()) }

    fun observeHealth(serviceId: String): Flow<List<HealthSample>> =
        healthDao.observeRecent(serviceId, 50)
            .map { list ->
                list.map { sample ->
                    HealthSample(
                        id = sample.id,
                        serviceId = sample.serviceId,
                        endpointId = sample.endpointId,
                        reachable = sample.reachable,
                        latencyMs = sample.latencyMs,
                        checkedAt = sample.checkedAt,
                        reason = sample.reason,
                    )
                }.sortedByDescending { it.checkedAt }
            }
            .catch { emit(emptyList()) }

    suspend fun findService(id: String): Service? {
        return try {
            val entity = serviceDao.findById(id)
            if (entity == null) {
                null
            } else {
                val endpoints = endpointDao.forService(id).map { it.toDomain() }
                entity.toDomain(endpoints)
            }
        } catch (error: Exception) {
            null
        }
    }

    suspend fun upsert(service: Service) {
        serviceDao.upsert(
            ServiceEntity(
                id = service.id,
                name = service.name,
                kind = service.kind.name,
                categoryId = service.categoryId,
                iconKind = service.iconKind.name,
                iconValue = service.iconValue,
                notes = service.notes,
                pinned = service.pinned,
                sortIndex = service.sortIndex,
                healthMode = service.health.mode.name,
                healthPath = service.health.path,
                healthTimeoutMs = service.health.timeoutMs,
                createdAt = service.createdAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        endpointDao.deleteForService(service.id)
        endpointDao.upsertAll(
            service.endpoints.map { endpoint ->
                EndpointEntity(
                    id = endpoint.id,
                    serviceId = endpoint.serviceId,
                    scope = endpoint.scope.name,
                    urlTemplate = endpoint.urlTemplate,
                    host = endpoint.host,
                    port = endpoint.port,
                    isPrimary = endpoint.isPrimary,
                    note = endpoint.note,
                )
            }
        )
    }

    suspend fun delete(serviceId: String) = serviceDao.deleteById(serviceId)

    suspend fun recordHealth(sample: HealthSample) {
        healthDao.insert(
            com.hubdeck.app.core.data.db.HealthSampleEntity(
                id = sample.id,
                serviceId = sample.serviceId,
                endpointId = sample.endpointId,
                reachable = sample.reachable,
                latencyMs = sample.latencyMs,
                checkedAt = sample.checkedAt,
                reason = sample.reason,
            )
        )
    }

    suspend fun searchServices(query: String): List<Service> {
        val entities = serviceDao.search(query)
        val endpoints = endpointDao.forServices(entities.map { it.id })
        val byService = endpoints.groupBy { it.serviceId }
        return entities
            .map { entity ->
                entity.toDomain(
                    byService[entity.id]
                        .orEmpty()
                        .map { it.toDomain() }
                        .sortedWith(endpointOrder)
                )
            }
            .sortedWith(serviceOrder)
    }
}
