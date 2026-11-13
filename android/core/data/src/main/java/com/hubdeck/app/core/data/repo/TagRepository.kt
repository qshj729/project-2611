package com.hubdeck.app.core.data.repo

import com.hubdeck.app.core.data.db.TagDao
import com.hubdeck.app.core.data.db.TagEntity
import com.hubdeck.app.core.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签仓储（M1 暂不在 UI 暴露，先落库与单测）。
 *
 * 冻结契约：C-01 的 Tag 模型；TagDao 在 C-02 的 Daos.kt 里。
 */
@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao,
) {

    /** 观察所有标签。 */
    fun observeAll(): Flow<List<Tag>> =
        tagDao.all().map { entities -> entities.map { it.toDomain() } }

    /** 按 ID 批量查询。 */
    fun findAllById(ids: List<String>): Flow<List<Tag>> =
        flow { emit(tagDao.all().filter { it.id in ids }.map { it.toDomain() }) }

    /** 创建或更新标签。 */
    suspend fun upsert(tag: Tag) {
        tagDao.upsert(tag.toEntity())
    }

    /** 批量创建或更新标签。 */
    suspend fun upsertAll(tags: List<Tag>) {
        tagDao.upsertAll(tags.map { it.toEntity() })
    }

    /** 删除标签。 */
    suspend fun delete(tagId: String) {
        tagDao.deleteById(tagId)
    }
}

/** TagEntity 与领域模型互转。 */
internal fun TagEntity.toDomain() = Tag(id = id, name = name)

internal fun Tag.toEntity() = TagEntity(id = id, name = name)