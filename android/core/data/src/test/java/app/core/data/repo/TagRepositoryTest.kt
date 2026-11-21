package app.core.data.repo

import app.core.data.db.ServiceTagEntity
import app.core.data.db.TagEntity
import app.core.model.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TagRepository` 单测。
 */
class TagRepositoryTest {

    private val tagDao = FakeTagDao()
    private val repository = TagRepository(tagDao)

    @Test
    fun `observeAll returns all tags`() = runTest {
        tagDao.seed(
            TagEntity(id = "t1", name = "娱乐"),
            TagEntity(id = "t2", name = "工具"),
        )

        val tags = repository.observeAll().first()

        assertEquals(listOf("t1", "t2"), tags.map { it.id })
    }

    @Test
    fun `findAllById returns matching tags`() = runTest {
        tagDao.seed(
            TagEntity(id = "t1", name = "娱乐"),
            TagEntity(id = "t2", name = "工具"),
        )

        val tags = repository.findAllById(listOf("t1")).first()

        assertEquals(listOf("t1"), tags.map { it.id })
    }

    @Test
    fun `upsert adds new tag`() = runTest {
        val tag = Tag(id = "t-new", name = "新标签")

        repository.upsert(tag)

        val stored = tagDao.all().single()
        assertEquals("t-new", stored.id)
        assertEquals("新标签", stored.name)
    }

    @Test
    fun `upsertAll adds multiple tags`() = runTest {
        val tags = listOf(
            Tag(id = "t1", name = "标签1"),
            Tag(id = "t2", name = "标签2"),
        )

        repository.upsertAll(tags)

        assertEquals(2, tagDao.all().size)
    }

    @Test
    fun `delete removes tag`() = runTest {
        tagDao.seed(TagEntity(id = "t1", name = "娱乐"))

        repository.delete("t1")

        assertTrue(tagDao.all().isEmpty())
    }

    @Test
    fun `setServiceTags links tags to service`() = runTest {
        tagDao.seed(
            TagEntity(id = "t1", name = "娱乐"),
            TagEntity(id = "t2", name = "工具"),
        )

        repository.setServiceTags("svc-1", listOf("t1", "t2"))

        val links = tagDao.links.filter { it.serviceId == "svc-1" }
        assertEquals(2, links.size)
        assertEquals(listOf("t1", "t2"), links.map { it.tagId }.sorted())
    }

    @Test
    fun `setServiceTags replaces existing links`() = runTest {
        tagDao.seed(
            TagEntity(id = "t1", name = "娱乐"),
            TagEntity(id = "t2", name = "工具"),
            TagEntity(id = "t3", name = "新标签"),
        )
        tagDao.links.add(ServiceTagEntity(serviceId = "svc-1", tagId = "t1"))
        tagDao.links.add(ServiceTagEntity(serviceId = "svc-1", tagId = "t2"))

        repository.setServiceTags("svc-1", listOf("t2", "t3"))

        val links = tagDao.links.filter { it.serviceId == "svc-1" }
        assertEquals(2, links.size)
        assertEquals(listOf("t2", "t3"), links.map { it.tagId }.sorted())
    }

    @Test
    fun `setServiceTags with empty list clears links`() = runTest {
        tagDao.seed(TagEntity(id = "t1", name = "娱乐"))
        tagDao.links.add(ServiceTagEntity(serviceId = "svc-1", tagId = "t1"))

        repository.setServiceTags("svc-1", emptyList())

        val links = tagDao.links.filter { it.serviceId == "svc-1" }
        assertTrue(links.isEmpty())
    }
}