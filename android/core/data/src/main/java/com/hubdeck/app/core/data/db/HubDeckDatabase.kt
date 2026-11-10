package com.hubdeck.app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServiceEntity::class,
        EndpointEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        ServiceTagEntity::class,
        HealthSampleEntity::class,
        CredentialEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HubDeckDatabase : RoomDatabase() {
    abstract fun serviceDao(): ServiceDao
    abstract fun endpointDao(): EndpointDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun healthDao(): HealthDao
    abstract fun credentialDao(): CredentialDao
}
