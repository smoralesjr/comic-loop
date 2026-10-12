package com.comicloop.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "comicloop")

data class Book(
    val id: String,
    val title: String,
    val kind: String,
    val uriString: String,
    val pageCount: Int,
    val currentPage: Int,
    val lastOpenedAt: Long,
    val addedAt: Long
) {
    val progress: Float get() = if (pageCount > 0) currentPage.toFloat() / pageCount else 0f
}

data class ReaderSettings(
    val direction: String = "ltr",
    val fitMode: String = "fit_width",
    val keepScreenOn: Boolean = true,
    val brightness: Int = 100
)

class LibraryRepository(private val context: Context) {
    private val booksKey = stringPreferencesKey("books_json")
    private val settingsKey = stringPreferencesKey("reader_settings_json")

    suspend fun loadBooks(): List<Book> {
        val raw = context.dataStore.data.first()[booksKey] ?: return emptyList()
        return runCatching {
            JSONArray(raw).let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Book(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        kind = o.getString("kind"),
                        uriString = o.getString("uri"),
                        pageCount = o.getInt("pageCount"),
                        currentPage = o.getInt("currentPage"),
                        lastOpenedAt = o.optLong("lastOpenedAt", 0L),
                        addedAt = o.optLong("addedAt", 0L)
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveBooks(books: List<Book>) {
        val arr = JSONArray()
        books.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("kind", b.kind)
                put("uri", b.uriString)
                put("pageCount", b.pageCount)
                put("currentPage", b.currentPage)
                put("lastOpenedAt", b.lastOpenedAt)
                put("addedAt", b.addedAt)
            })
        }
        context.dataStore.edit { it[booksKey] = arr.toString() }
    }

    suspend fun loadSettings(): ReaderSettings {
        val raw = context.dataStore.data.first()[settingsKey] ?: return ReaderSettings()
        return runCatching {
            JSONObject(raw).let { o ->
                ReaderSettings(
                    direction = o.optString("direction", "ltr"),
                    fitMode = o.optString("fitMode", "fit_width"),
                    keepScreenOn = o.optBoolean("keepScreenOn", true),
                    brightness = o.optInt("brightness", 100)
                )
            }
        }.getOrDefault(ReaderSettings())
    }

    suspend fun saveSettings(settings: ReaderSettings) {
        val o = JSONObject().apply {
            put("direction", settings.direction)
            put("fitMode", settings.fitMode)
            put("keepScreenOn", settings.keepScreenOn)
            put("brightness", settings.brightness)
        }
        context.dataStore.edit { it[settingsKey] = o.toString() }
    }

    suspend fun upsert(book: Book) {
        val books = loadBooks().filterNot { it.id == book.id } + book
        saveBooks(books)
    }

    suspend fun updateProgress(id: String, page: Int) {
        val books = loadBooks().map {
            if (it.id == id) it.copy(currentPage = page, lastOpenedAt = System.currentTimeMillis()) else it
        }
        saveBooks(books)
    }

    suspend fun remove(id: String) {
        saveBooks(loadBooks().filterNot { it.id == id })
    }
}
