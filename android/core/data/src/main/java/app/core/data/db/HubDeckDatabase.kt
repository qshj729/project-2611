package app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true,
)
abstract class HubDeckDatabase : RoomDatabase() {
    abstract fun serviceDao(): ServiceDao
    abstract fun endpointDao(): EndpointDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun healthDao(): HealthDao
    abstract fun credentialDao(): CredentialDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_services_categoryId ON services(categoryId)")
            }
        }
    }
}
