package com.comicloop.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comicloop.app.archive.ArchiveFactory
import com.comicloop.app.cloud.DriveSync
import com.comicloop.app.data.Book
import com.comicloop.app.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val signedIn: Boolean = false,
    val lastSync: Long = 0L
)

class LibraryViewModel(private val appContext: Context) : ViewModel() {

    private val repository = LibraryRepository(appContext)
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            val books = repository.loadBooks()
            _state.value = _state.value.copy(signedIn = DriveSync.isSignedIn(appContext))
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val books = repository.loadBooks()
            _state.value = _state.value.copy(books = books.sortedByDescending { it.lastOpenedAt }, loading = false)
        }
    }

    fun importFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            runCatching {
                val kind = ArchiveFactory.detect(displayName)
                    ?: error("Not a .cbr/.cbz file")
                val file = ArchiveFactory.copyToCache(appContext, uri, displayName)
                val reader = ArchiveFactory.open(file, kind)
                val count = reader.pageCount
                reader.close()
                val id = uri.toString()
                val book = Book(
                    id = id,
                    title = displayName.substringBeforeLast('.'),
                    kind = kind,
                    uriString = id,
                    pageCount = count,
                    currentPage = 0,
                    lastOpenedAt = System.currentTimeMillis(),
                    addedAt = System.currentTimeMillis()
                )
                repository.upsert(book)
                reader.close()
            }.onSuccess {
                _state.value = _state.value.copy(loading = false, message = null)
                refresh()
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, message = e.message)
            }
        }
    }

    fun remove(bookId: String) {
        viewModelScope.launch { repository.remove(bookId); refresh() }
    }

    fun markOpened(bookId: String) {
        viewModelScope.launch {
            repository.updateProgress(bookId, _state.value.books.first { it.id == bookId }.currentPage)
            refresh()
        }
    }

    fun syncUp() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            val books = repository.loadBooks()
            val arr = JSONArray()
            books.forEach { b ->
                arr.put(JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("pageCount", b.pageCount)
                    put("currentPage", b.currentPage)
                    put("lastOpenedAt", b.lastOpenedAt)
                })
            }
            DriveSync.uploadProgress(appContext, arr.toString())
                .onSuccess {
                    _state.value = _state.value.copy(loading = false, lastSync = System.currentTimeMillis())
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, message = e.message)
                }
        }
    }

    fun syncDown() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            DriveSync.downloadProgress(appContext)
                .onSuccess { json ->
                    val arr = JSONArray(json)
                    val books = repository.loadBooks().toMutableList()
                    (0 until arr.length()).forEach { i ->
                        val o = arr.getJSONObject(i)
                        val idx = books.indexOfFirst { it.id == o.getString("id") }
                        if (idx >= 0) {
                            books[idx] = books[idx].copy(
                                currentPage = o.getInt("currentPage"),
                                pageCount = if (o.getInt("pageCount") > 0) o.getInt("pageCount") else books[idx].pageCount
                            )
                        }
                    }
                    repository.saveBooks(books)
                    _state.value = _state.value.copy(loading = false, lastSync = System.currentTimeMillis())
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, message = e.message)
                }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(appContext.applicationContext) as T
    }
}
