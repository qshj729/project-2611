package app.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.core.model.Endpoint
import app.core.model.EndpointScope
import app.core.model.HealthMode
import app.core.model.IconKind
import app.core.model.ServiceKind

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val categoryId: String?,
    val iconKind: String,
    val iconValue: String?,
    val notes: String?,
    val pinned: Boolean,
    val sortIndex: Int,
    val healthMode: String,
    val healthPath: String,
    val healthTimeoutMs: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "endpoints",
    foreignKeys = [
        ForeignKey(
            entity = ServiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("serviceId")],
)
data class EndpointEntity(
    @PrimaryKey val id: String,
    val serviceId: String,
    val scope: String,
    val urlTemplate: String,
    val host: String?,
    val port: Int?,
    val isPrimary: Boolean,
    val note: String?,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val sortIndex: Int,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "service_tags", primaryKeys = ["serviceId", "tagId"])
data class ServiceTagEntity(
    val serviceId: String,
    val tagId: String,
)

@Entity(tableName = "health_samples", indices = [Index("serviceId"), Index(value = ["serviceId", "checkedAt"])])
data class HealthSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val serviceId: String,
    val endpointId: String,
    val reachable: Boolean,
    val latencyMs: Int?,
    val checkedAt: Long,
    val reason: String?,
)

/** Credentials are encrypted with an AES-256-GCM key held in the Android Keystore. */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val id: String,
    val label: String,
    val kind: String,
    val cipherText: ByteArray,
    val iv: ByteArray,
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is CredentialEntity && id == other.id)

    override fun hashCode(): Int = id.hashCode()
}

fun ServiceEntity.toDomain(
    endpoints: List<Endpoint> = emptyList(),
    tagIds: List<String> = emptyList(),
) = app.core.model.Service(
    id = id,
    name = name,
    kind = runCatching { ServiceKind.valueOf(kind) }.getOrDefault(ServiceKind.WEB),
    categoryId = categoryId,
    tagIds = tagIds,
    iconKind = runCatching { IconKind.valueOf(iconKind) }.getOrDefault(IconKind.NONE),
    iconValue = iconValue,
    notes = notes,
    pinned = pinned,
    sortIndex = sortIndex,
    endpoints = endpoints,
    health = app.core.model.HealthConfig(
        mode = runCatching { HealthMode.valueOf(healthMode) }.getOrDefault(HealthMode.NONE),
        path = healthPath,
        timeoutMs = healthTimeoutMs,
    ),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun EndpointEntity.toDomain() = Endpoint(
    id = id,
    serviceId = serviceId,
    scope = runCatching { EndpointScope.valueOf(scope) }.getOrDefault(EndpointScope.LAN),
    urlTemplate = urlTemplate,
    host = host,
    port = port,
    isPrimary = isPrimary,
    note = note,
)
