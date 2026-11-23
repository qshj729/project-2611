package app.core.data.bookmarks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.lang.reflect.Type

/**
 * 浏览器书签存储（用 Gson 避免 kotlinx.serialization 编译期插件问题）。
 */
data class Bookmark(
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

interface BookmarkStore {
    fun observe(): Flow<List<Bookmark>>

    suspend fun current(): List<Bookmark>

    /** 新增或更新（按 URL 去重）。 */
    suspend fun upsert(bookmark: Bookmark)

    suspend fun remove(url: String)

    suspend fun contains(url: String): Boolean
}

private data class BookmarkRecord(
    val url: String,
    val title: String,
    val createdAt: Long,
)

class DataStoreBookmarkStore(
    private val dataStore: DataStore<Preferences>,
) : BookmarkStore {

    private val gson = Gson()
    private val listType: Type = TypeToken.getParameterized(List::class.java, BookmarkRecord::class.java).type

    override fun observe(): Flow<List<Bookmark>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> decode(prefs[KEY_BOOKMARKS]) }

    override suspend fun current(): List<Bookmark> = try {
        decode(dataStore.data.first()[KEY_BOOKMARKS])
    } catch (error: IOException) {
        emptyList()
    }

    override suspend fun upsert(bookmark: Bookmark) {
        val list = current().toMutableList()
        val index = list.indexOfFirst { it.url == bookmark.url }
        if (index >= 0) {
            list[index] = list[index].copy(title = bookmark.title)
        } else {
            list += bookmark
        }
        write(list)
    }

    override suspend fun remove(url: String) {
        write(current().filterNot { it.url == url })
    }

    override suspend fun contains(url: String): Boolean = current().any { it.url == url }

    private suspend fun write(list: List<Bookmark>) {
        val text = gson.toJson(
            list.map { BookmarkRecord(it.url, it.title, it.createdAt) }
        )
        dataStore.edit { prefs -> prefs[KEY_BOOKMARKS] = text }
    }

    private fun decode(text: String?): List<Bookmark> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            (gson.fromJson(text, listType) as List<BookmarkRecord>).map { Bookmark(it.url, it.title, it.createdAt) }
        }.getOrDefault(emptyList())
    }

    companion object {
        internal val KEY_BOOKMARKS = stringPreferencesKey("bookmarks_json")
    }
}

private val Context.myAppsBookmarkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "myapps_bookmarks",
)

object BookmarkStores {
    fun create(context: Context): BookmarkStore =
        DataStoreBookmarkStore(context.applicationContext.myAppsBookmarkDataStore)
}