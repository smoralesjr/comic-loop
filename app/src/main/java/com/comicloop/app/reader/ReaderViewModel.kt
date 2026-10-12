package com.comicloop.app.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comicloop.app.archive.ArchiveFactory
import com.comicloop.app.archive.ArchiveReader
import com.comicloop.app.data.Book
import com.comicloop.app.data.LibraryRepository
import com.comicloop.app.data.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val pages: List<String> = emptyList(),
    val currentPage: Int = 0,
    val title: String = "",
    val settings: ReaderSettings = ReaderSettings()
)

class ReaderViewModel(
    private val appContext: Context,
    private val repository: LibraryRepository,
    private val bookId: String
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var archive: ArchiveReader? = null
    private var archiveFile: File? = null
    private var book: Book? = null

    init {
        viewModelScope.launch {
            runCatching {
                val loaded = repository.loadBooks().firstOrNull { it.id == bookId }
                    ?: error("Book not found in library")
                book = loaded
                _state.value = _state.value.copy(
                    title = loaded.title,
                    currentPage = loaded.currentPage,
                    settings = repository.loadSettings(),
                    loading = true,
                    error = null
                )
                val uri = Uri.parse(loaded.uriString)
                archiveFile = ArchiveFactory.copyToCache(appContext, uri, loaded.title)
                val reader = ArchiveFactory.open(archiveFile!!, loaded.kind)
                archive = reader
                val extracted = extractPages(reader)
                _state.value = _state.value.copy(loading = false, pages = extracted)
                repository.upsert(loaded.copy(pageCount = reader.pageCount, lastOpenedAt = System.currentTimeMillis()))
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Could not open file")
            }
        }
    }

    private val safeId: String =
        bookId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(100)

    private fun extractPages(reader: ArchiveReader): List<String> {
        val pagesDir = File(appContext.cacheDir, "pages/$safeId").apply { deleteRecursively(); mkdirs() }
        return (0 until reader.pageCount).map { i ->
            val page = reader.pageAt(i)
            val outFile = File(pagesDir, "%04d_%s".format(i, page.fileName.substringAfterLast('/')))
            page.open().use { input -> outFile.outputStream().use { input.copyTo(it) } }
            outFile.absolutePath
        }
    }

    fun setPage(index: Int) {
        val clamped = index.coerceIn(0, (_state.value.pages.size - 1).coerceAtLeast(0))
        _state.value = _state.value.copy(currentPage = clamped)
        viewModelScope.launch(Dispatchers.IO) { repository.updateProgress(bookId, clamped) }
    }

    fun setDirection(direction: String) {
        _state.value = _state.value.copy(settings = _state.value.settings.copy(direction = direction))
        viewModelScope.launch { repository.saveSettings(_state.value.settings) }
    }

    fun setFitMode(mode: String) {
        _state.value = _state.value.copy(settings = _state.value.settings.copy(fitMode = mode))
        viewModelScope.launch { repository.saveSettings(_state.value.settings) }
    }

    override fun onCleared() {
        archive?.close()
        archiveFile?.delete()
        super.onCleared()
    }

    class Factory(private val appContext: Context, private val bookId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(appContext.applicationContext, LibraryRepository(appContext.applicationContext), bookId) as T
    }
}
