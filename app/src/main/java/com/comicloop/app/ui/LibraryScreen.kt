package com.comicloop.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.comicloop.app.data.Book
import com.comicloop.app.reader.LocalBookHolder
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(androidx.compose.ui.platform.LocalContext.current))
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val name = queryDisplayName(context, uri) ?: "comic.cbr"
        vm.importFromUri(uri, name)
    }

    CompositionLocalProvider(LocalBookHolder provides state.books) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ComicLoop") },
                    actions = {
                        IconButton(onClick = { vm.syncUp() }, enabled = state.signedIn) {
                            Icon(Icons.Outlined.CloudSync, "Sync now")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { openFile.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Filled.FolderOpen, null) },
                    text = { Text("Open file") }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                }
                if (state.books.isEmpty() && !state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No comics yet.\nOpen a .cbr or .cbz file to get started.\nYou can pick files from Google Drive or any cloud provider in the picker.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.books, key = { it.id }) { book ->
                            BookCard(book = book, onClick = { onOpenBook(book.id) }, onDelete = { vm.remove(book.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookCard(book: Book, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                val cover = coverFor(book)
                if (cover != null) {
                    AsyncImage(
                        model = File(cover),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Book, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }
            Column(Modifier.padding(8.dp)) {
                Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${book.currentPage + 1} / ${book.pageCount}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun coverFor(book: Book): String? {
    val dir = File(coverDir(book.id))
    return dir.listFiles()?.sortedBy { it.name }?.firstOrNull()?.absolutePath
}

private fun coverDir(bookId: String): File {
    val safeId = bookId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(100)
    return File(com.comicloop.app.ComicLoopApp.instance.cacheDir, "pages/$safeId")
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
}
