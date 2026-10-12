package com.comicloop.app.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val vm: ReaderViewModel = viewModel(
        key = bookId,
        factory = remember(bookId) { ReaderViewModel.Factory(context, bookId) }
    )
    val state by vm.state.collectAsState()
    val pagerState = rememberPagerState(initialPage = state.currentPage) { state.pages.size }
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }

    BackHandler { onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(state.settings.direction, state.pages.size) {
                detectTapGestures(
                    onTap = { pos ->
                        val isRtl = state.settings.direction == "rtl"
                        val leftEdge = pos.x < size.width * 0.3f
                        val rightEdge = pos.x > size.width * 0.7f
                        when {
                            leftEdge -> {
                                val target = if (isRtl) pagerState.currentPage + 1 else pagerState.currentPage - 1
                                if (target in state.pages.indices) scope.launch { pagerState.animateScrollToPage(target) }
                            }
                            rightEdge -> {
                                val target = if (isRtl) pagerState.currentPage - 1 else pagerState.currentPage + 1
                                if (target in state.pages.indices) scope.launch { pagerState.animateScrollToPage(target) }
                            }
                            else -> controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = {
                        val dir = if (state.settings.direction == "rtl") -1 else 1
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage + dir).coerceIn(0, state.pages.size - 1)
                            )
                        }
                    }
                )
            }
    ) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> Text(
                state.error ?: "", color = Color.White, modifier = Modifier.align(Alignment.Center)
            )
            state.settings.direction == "vertical" -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Single,
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.pages.size) { i ->
                    ZoomablePage(path = state.pages[i], fitMode = state.settings.fitMode)
                }
            }
            else -> {
                LaunchedEffect(pagerState.currentPage) { vm.setPage(pagerState.currentPage) }
                val isRtl = state.settings.direction == "rtl"
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = isRtl
                ) { page ->
                    ZoomablePage(path = state.pages[page], fitMode = state.settings.fitMode)
                }
            }
        }

        if (controlsVisible) {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                Surface(color = Color.Black.copy(alpha = 0.6f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Text(state.title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            "${pagerState.currentPage + 1} / ${state.pages.size}",
                            color = Color.Gray, style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, "Settings", tint = Color.White)
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { if (state.pages.isNotEmpty()) (pagerState.currentPage + 1f) / state.pages.size else 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
        }
    }
}

@Composable
fun ZoomablePage(path: String, fitMode: String) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange
    }
    val contentScale = when (fitMode) {
        "fit_height" -> ContentScale.Fit
        "fit_screen" -> ContentScale.FillBounds
        else -> ContentScale.FillWidth
    }
    AsyncImage(
        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(File(path))
            .build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier
            .fillMaxSize()
            .transformable(transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    )
}

val LocalBookHolder = staticCompositionLocalOf<List<com.comicloop.app.data.Book>> { emptyList() }
