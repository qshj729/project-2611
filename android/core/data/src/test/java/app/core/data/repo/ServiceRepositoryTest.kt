package app.core.data.repo

import app.core.data.db.CategoryEntity
import app.core.data.db.EndpointEntity
import app.core.data.db.HealthSampleEntity
import app.core.data.db.ServiceEntity
import app.core.data.db.ServiceTagEntity
import app.core.data.db.TagEntity
import app.core.model.Endpoint
import app.core.model.EndpointScope
import app.core.model.HealthConfig
import app.core.model.HealthMode
import app.core.model.HealthSample
import app.core.model.IconKind
import app.core.model.Service
import app.core.model.ServiceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ServiceRepository` 的行为单测（冻结契约 C-02）。
 *
 * 用内存假 DAO 而不是 Room：Room 需要 Android 运行时，纯 JVM 跑不了；
 * 假 DAO **故意不排序**，这样排序规则真的由仓储兜住。
 */
class ServiceRepositoryTest {

    private val serviceDao = FakeServiceDao()
    private val endpointDao = FakeEndpointDao()
    private val categoryDao = FakeCategoryDao()
    private val healthDao = FakeHealthDao()
    private val tagDao = FakeTagDao()
    private val repository = ServiceRepository(serviceDao, endpointDao, categoryDao, healthDao, tagDao)

    @Test
    fun `observeServices sorts pinned first then sortIndex then name`() = runTest {
        serviceDao.seed(
            entity(id = "c", name = "Zeta", sortIndex = 2, pinned = false),
            entity(id = "b", name = "Bravo", sortIndex = 1, pinned = false),
            entity(id = "a", name = "Alpha", sortIndex = 1, pinned = false),
            entity(id = "p", name = "Pinned", sortIndex = 9, pinned = true),
        )

        val services = repository.observeServices().first()

        assertEquals(listOf("p", "a", "b", "c"), services.map { it.id })
    }

    @Test
    fun `observeServices sorts endpoints by primary first then scope`() = runTest {
        serviceDao.seed(entity(id = "a"))
        endpointDao.seed(
            endpointEntity(id = "e3", serviceId = "a", scope = EndpointScope.PUBLIC, primary = false),
            endpointEntity(id = "e1", serviceId = "a", scope = EndpointScope.PUBLIC, primary = true),
            endpointEntity(id = "e2", serviceId = "a", scope = EndpointScope.LAN, primary = false),
        )

        val service = repository.observeServices().first().single()

        assertEquals(listOf("e1", "e2", "e3"), service.endpoints.map { it.id })
    }

    @Test
    fun `observeServices includes tag ids from service_tags`() = runTest {
        serviceDao.seed(entity(id = "a"))
        tagDao.seed(TagEntity(id = "t1", name = "娱乐"), TagEntity(id = "t2", name = "工具"))
        tagDao.links.add(ServiceTagEntity(serviceId = "a", tagId = "t1"))
        tagDao.links.add(ServiceTagEntity(serviceId = "a", tagId = "t2"))

        val service = repository.observeServices().first().single()

        assertEquals(listOf("t1", "t2"), service.tagIds)
    }

    @Test
    fun `observeServices survives a failing dao`() = runTest {
        serviceDao.observeError = IllegalStateException("db exploded")

        assertTrue(repository.observeServices().first().isEmpty())
    }

    @Test
    fun `observeCategories sorts by sortIndex and keeps argb colour`() = runTest {
        categoryDao.seed(
            CategoryEntity(id = "b", name = "影音", color = 0xFFF59E0BL, sortIndex = 2),
            CategoryEntity(id = "a", name = "工具", color = 0xFF14B8A6L, sortIndex = 1),
        )

        val categories = repository.observeCategories().first()

        assertEquals(listOf("a", "b"), categories.map { it.id })
        assertEquals(0xFFF59E0BL, categories.last().color)
    }

    @Test
    fun `observeHealth returns newest first and caps at fifty`() = runTest {
        healthDao.seed(
            HealthSampleEntity(id = 1, serviceId = "a", endpointId = "e", reachable = true, latencyMs = 10, checkedAt = 100, reason = null),
            HealthSampleEntity(id = 2, serviceId = "a", endpointId = "e", reachable = false, latencyMs = null, checkedAt = 200, reason = null),
        )

        val samples = repository.observeHealth("a").first()

        assertEquals(listOf(2L, 1L), samples.map { it.id })
        assertEquals(50, healthDao.lastLimit)
    }

    @Test
    fun `findService returns null when missing and never throws`() = runTest {
        assertNull(repository.findService("nope"))

        serviceDao.findError = IllegalStateException("db exploded")
        assertNull(repository.findService("a"))
    }

    @Test
    fun `findService returns the service with its endpoints`() = runTest {
        serviceDao.seed(entity(id = "a"))
        endpointDao.seed(endpointEntity(id = "e1", serviceId = "a"))

        val service = repository.findService("a")

        assertNotNull(service)
        assertEquals(listOf("e1"), service!!.endpoints.map { it.id })
    }

    @Test
    fun `findService returns the service with its tag ids`() = runTest {
        serviceDao.seed(entity(id = "a"))
        tagDao.seed(TagEntity(id = "t1", name = "娱乐"))
        tagDao.links.add(ServiceTagEntity(serviceId = "a", tagId = "t1"))

        val service = repository.findService("a")

        assertNotNull(service)
        assertEquals(listOf("t1"), service!!.tagIds)
    }

    @Test
    fun `upsert rewrites updatedAt but keeps createdAt`() = runTest {
        val before = System.currentTimeMillis()
        val service = domainService(id = "a", createdAt = 1_000L, updatedAt = 2_000L)

        repository.upsert(service)

        val stored = serviceDao.upserts.single()
        assertEquals(1_000L, stored.createdAt)
        assertTrue("updatedAt 应被仓储改写", stored.updatedAt >= before)
        assertTrue("updatedAt 不等于传入值", stored.updatedAt != 2_000L)
    }

    @Test
    fun `upsert deletes endpoints before inserting them`() = runTest {
        endpointDao.seed(endpointEntity(id = "old", serviceId = "a"))

        repository.upsert(domainService(id = "a", endpoints = listOf(endpoint(id = "new", serviceId = "a"))))

        assertEquals(listOf("deleteForService", "upsertAll"), endpointDao.calls)
        assertEquals(listOf("new"), endpointDao.all().map { it.id })
    }

    @Test
    fun `upsert updates tag associations`() = runTest {
        serviceDao.seed(entity(id = "a"))
        tagDao.seed(TagEntity(id = "t1", name = "旧标签"))
        tagDao.links.add(ServiceTagEntity(serviceId = "a", tagId = "t1"))

        // Upsert with new tags
        repository.upsert(domainService(id = "a", tagIds = listOf("t2", "t3")))

        // Old link should be deleted, new links added
        assertEquals(2, tagDao.links.count { it.serviceId == "a" })
        assertEquals(listOf("t2", "t3"), tagDao.links.filter { it.serviceId == "a" }.map { it.tagId }.sorted())
    }

    @Test
    fun `upsert clears tags when tagIds is empty`() = runTest {
        serviceDao.seed(entity(id = "a"))
        tagDao.seed(TagEntity(id = "t1", name = "旧标签"))
        tagDao.links.add(ServiceTagEntity(serviceId = "a", tagId = "t1"))

        // Upsert with empty tags
        repository.upsert(domainService(id = "a", tagIds = emptyList()))

        // All links should be deleted
        assertTrue(tagDao.links.filter { it.serviceId == "a" }.isEmpty())
    }

    @Test
    fun `delete removes the service`() = runTest {
        serviceDao.seed(entity(id = "a"))

        repository.delete("a")

        assertEquals(listOf("a"), serviceDao.deleted)
    }

    @Test
    fun `recordHealth lets the database assign the id`() = runTest {
        repository.recordHealth(
            HealthSample(serviceId = "a", endpointId = "e", reachable = true, checkedAt = 42),
        )

        val samples = repository.observeHealth("a").first()
        assertEquals(1, samples.size)
        assertTrue("id 应由数据库自增", samples.single().id != 0L)
    }

    @Test
    fun `search matches names case insensitively`() = runTest {
        serviceDao.seed(
            entity(id = "a", name = "Jellyfin"),
            entity(id = "b", name = "Nginx"),
        )

        val hits = repository.searchServices("jelly")

        assertEquals(listOf("a"), hits.map { it.id })
    }

    @Test
    fun `search includes tag ids`() = runTest {
        serviceDao.seed(entity(id = "a", name = "Jellyfin"))
        tagDao.seed(TagEntity(id = "t1", name = "娱乐"))
        tagDao.links.add(ServiceTagEntity(serviceId = "a", tagId = "t1"))

        val hits = repository.searchServices("jelly")

        assertEquals(listOf("t1"), hits.single().tagIds)
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private fun entity(
        id: String,
        name: String = id,
        sortIndex: Int = 0,
        pinned: Boolean = false,
    ) = ServiceEntity(
        id = id,
        name = name,
        kind = ServiceKind.WEB.name,
        categoryId = null,
        iconKind = IconKind.NONE.name,
        iconValue = null,
        notes = null,
        pinned = pinned,
        sortIndex = sortIndex,
        healthMode = HealthMode.NONE.name,
        healthPath = "/",
        healthTimeoutMs = 3000,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun endpoint(
        id: String,
        serviceId: String,
        scope: EndpointScope = EndpointScope.LAN,
        primary: Boolean = false,
    ) = Endpoint(
        id = id,
        serviceId = serviceId,
        scope = scope,
        urlTemplate = "{host}",
        host = "example.local",
        port = null,
        isPrimary = primary,
        note = null,
    )

    private fun endpointEntity(
        id: String,
        serviceId: String,
        scope: EndpointScope = EndpointScope.LAN,
        primary: Boolean = false,
    ) = EndpointEntity(
        id = id,
        serviceId = serviceId,
        scope = scope.name,
        urlTemplate = "{host}",
        host = "example.local",
        port = null,
        isPrimary = primary,
        note = null,
    )

    private fun domainService(
        id: String,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
        endpoints: List<Endpoint> = emptyList(),
        tagIds: List<String> = emptyList(),
    ) = Service(
        id = id,
        name = "Jellyfin",
        kind = ServiceKind.WEB,
        iconKind = IconKind.NONE,
        endpoints = endpoints,
        health = HealthConfig(mode = HealthMode.TCP, path = "/", timeoutMs = 3000),
        createdAt = createdAt,
        updatedAt = updatedAt,
        tagIds = tagIds,
    )
}