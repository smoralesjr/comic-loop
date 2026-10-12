package com.comicloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.comicloop.app.reader.ReaderScreen
import com.comicloop.app.ui.LibraryScreen
import com.comicloop.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComicLoopTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(
                            onOpenBook = { nav.navigate("reader/$it") },
                            onOpenSettings = { nav.navigate("settings") }
                        )
                    }
                    composable("reader/{bookId}") { entry ->
                        val bookId = entry.arguments?.getString("bookId") ?: ""
                        ReaderScreen(
                            bookId = bookId,
                            onBack = { nav.popBackStack() },
                            onOpenSettings = { nav.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
fun ComicLoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
