package com.hubdeck.app.core.data.repo

import com.hubdeck.app.core.data.db.CategoryDao
import com.hubdeck.app.core.data.db.EndpointDao
import com.hubdeck.app.core.data.db.HealthDao
import com.hubdeck.app.core.data.db.ServiceDao
import com.hubdeck.app.core.data.db.ServiceEntity
import com.hubdeck.app.core.data.db.EndpointEntity
import com.hubdeck.app.core.data.db.toDomain
import com.hubdeck.app.core.model.Category
import com.hubdeck.app.core.model.HealthSample
import com.hubdeck.app.core.model.Service
import kotlinx.coroutines.flow.Flow
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

    fun observeServices(): Flow<List<Service>> =
        combine(serviceDao.observeAll(), endpointDao.allFlow()) { services, endpoints ->
            val byService = endpoints.groupBy { it.serviceId }
            services.map { entity ->
                entity.toDomain(byService[entity.id].orEmpty().map { it.toDomain() })
            }
        }

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { Category(it.id, it.name, it.color, it.sortIndex) } }

    fun observeHealth(serviceId: String): Flow<List<HealthSample>> =
        healthDao.observeRecent(serviceId).map { list ->
            list.map { HealthSample(it.id, it.serviceId, it.endpointId, it.reachable, it.latencyMs, it.checkedAt, it.reason) }
        }

    suspend fun findService(id: String): Service? {
        val entity = serviceDao.findById(id) ?: return null
        val endpoints = endpointDao.forService(id).map { it.toDomain() }
        return entity.toDomain(endpoints)
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
                    serviceId = service.id,
                    scope = endpoint.scope.name,
                    urlTemplate = endpoint.urlTemplate,
                    host = endpoint.host,
                    port = endpoint.port,
                    isPrimary = endpoint.isPrimary,
                    note = endpoint.note,
                )
            },
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
            ),
        )
    }

    suspend fun searchServices(query: String): List<Service> {
        val entities = serviceDao.search(query)
        val endpoints = endpointDao.forServices(entities.map { it.id })
        val byService = endpoints.groupBy { it.serviceId }
        return entities.map { entity ->
            entity.toDomain(byService[entity.id].orEmpty().map { it.toDomain() })
        }
    }
}
