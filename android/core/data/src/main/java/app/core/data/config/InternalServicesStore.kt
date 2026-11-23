package app.core.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.core.model.InternalService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 内网服务清单的读写入口。
 *
 * 存储形式：**一段 YAML 文本**（DataStore preferences 的单个 string key）。
 * 选文本而不是结构化表，是因为这份数据天然是"配置文件"——用户可以直接编辑、导入、导出。
 */
interface InternalServicesStore {
    /** 服务清单流。 */
    fun observe(): Flow<List<InternalService>>

    /** 当前清单。 */
    suspend fun current(): List<InternalService>

    /** 覆盖整份清单。 */
    suspend fun replaceAll(services: List<InternalService>)

    /** 从 YAML 导入；返回导入条数。解析失败抛异常。 */
    suspend fun importYaml(text: String): Int

    /** 导出为 YAML 文本。 */
    suspend fun exportYaml(): String
}

class DataStoreInternalServicesStore(
    private val dataStore: DataStore<Preferences>,
) : InternalServicesStore {

    override fun observe(): Flow<List<InternalService>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> parseOrEmpty(prefs[KEY_SERVICES_YAML]) }

    override suspend fun current(): List<InternalService> = try {
        parseOrEmpty(dataStore.data.first()[KEY_SERVICES_YAML])
    } catch (error: IOException) {
        emptyList()
    }

    override suspend fun replaceAll(services: List<InternalService>) {
        val text = InternalServicesConfig.render(services)
        dataStore.edit { prefs -> prefs[KEY_SERVICES_YAML] = text }
    }

    override suspend fun importYaml(text: String): Int {
        val result = InternalServicesConfig.parse(text)
        replaceAll(result.services)
        return result.services.size
    }

    override suspend fun exportYaml(): String =
        InternalServicesConfig.render(current())

    private fun parseOrEmpty(text: String?): List<InternalService> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { InternalServicesConfig.parse(text).services }.getOrDefault(emptyList())
    }

    companion object {
        internal val KEY_SERVICES_YAML = stringPreferencesKey("internal_services_yaml")
    }
}

private val Context.internalServicesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "myapps_services",
)

object InternalServicesStores {
    fun create(context: Context): InternalServicesStore =
        DataStoreInternalServicesStore(context.applicationContext.internalServicesDataStore)
}
