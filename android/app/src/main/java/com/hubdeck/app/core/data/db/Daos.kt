package com.hubdeck.app.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services ORDER BY pinned DESC, sortIndex ASC, name ASC")
    fun observeAll(): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun findById(id: String): ServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(service: ServiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(services: List<ServiceEntity>)

    @Update
    suspend fun update(service: ServiceEntity)

    @Delete
    suspend fun delete(service: ServiceEntity)

    @Query("DELETE FROM services WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface EndpointDao {
    @Query("SELECT * FROM endpoints WHERE serviceId = :serviceId ORDER BY isPrimary DESC, scope ASC")
    suspend fun forService(serviceId: String): List<EndpointEntity>

    @Query("SELECT * FROM endpoints WHERE serviceId IN (:serviceIds)")
    suspend fun forServices(serviceIds: List<String>): List<EndpointEntity>

    @Query("SELECT * FROM endpoints")
    suspend fun all(): List<EndpointEntity>

    @Query("SELECT * FROM endpoints")
    fun allFlow(): Flow<List<EndpointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(endpoints: List<EndpointEntity>)

    @Query("DELETE FROM endpoints WHERE serviceId = :serviceId")
    suspend fun deleteForService(serviceId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortIndex ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags")
    suspend fun all(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkAll(links: List<ServiceTagEntity>)

    @Query("SELECT * FROM service_tags WHERE serviceId = :serviceId")
    suspend fun tagsForService(serviceId: String): List<ServiceTagEntity>
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_samples WHERE serviceId = :serviceId ORDER BY checkedAt DESC LIMIT :limit")
    fun observeRecent(serviceId: String, limit: Int = 50): Flow<List<HealthSampleEntity>>

    @Query("SELECT * FROM health_samples WHERE serviceId = :serviceId ORDER BY checkedAt DESC LIMIT 1")
    suspend fun latestFor(serviceId: String): HealthSampleEntity?

    @Insert
    suspend fun insert(sample: HealthSampleEntity)

    @Query("DELETE FROM health_samples WHERE checkedAt < :olderThan")
    suspend fun prune(olderThan: Long)
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials")
    suspend fun all(): List<CredentialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(credential: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: String)
}
