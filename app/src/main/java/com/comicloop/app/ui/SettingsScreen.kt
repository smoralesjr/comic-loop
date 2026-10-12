package com.comicloop.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.comicloop.app.cloud.DriveSync
import com.comicloop.app.data.LibraryRepository
import com.comicloop.app.data.ReaderSettings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { LibraryRepository(context) }
    var settings by remember { mutableStateOf(ReaderSettings()) }
    var signedIn by remember { mutableStateOf(DriveSync.isSignedIn(context)) }
    var lastSync by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        settings = repository.loadSettings()
        signedIn = DriveSync.isSignedIn(context)
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn
            .getSignedInAccountFromIntent(result.data)
        account.addOnSuccessListener { signedIn = it != null }
        account.addOnFailureListener { signedIn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Reading", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Direction", Modifier.weight(1f))
                DirectionChips(settings.direction) { d ->
                    settings = settings.copy(direction = d)
                    scope.launch { repository.saveSettings(settings) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fit mode", Modifier.weight(1f))
                FitChips(settings.fitMode) { f ->
                    settings = settings.copy(fitMode = f)
                    scope.launch { repository.saveSettings(settings) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep screen on", Modifier.weight(1f))
                Switch(checked = settings.keepScreenOn, onCheckedChange = { on ->
                    settings = settings.copy(keepScreenOn = on)
                    scope.launch { repository.saveSettings(settings) }
                })
            }

            HorizontalDivider()
            Text("Cloud", style = MaterialTheme.typography.titleMedium)
            Text("Reading progress syncs as JSON to Google Drive → ComicLoop folder.")
            if (signedIn) {
                Text(
                    if (lastSync == 0L) "Last sync: never"
                    else "Last sync: " + SimpleDateFormat.getDateTimeInstance().format(Date(lastSync)),
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { DriveSync.signOut(context); signedIn = false }) {
                    Text("Sign out")
                }
            } else {
                Button(onClick = {
                    val intent = com.google.android.gms.auth.api.signin.GoogleSignIn
                        .getClient(context, DriveSync.signInOptions()).signInIntent
                    signInLauncher.launch(intent)
                }) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}

@Composable
fun DirectionChips(current: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ltr" to "LTR", "rtl" to "Manga", "vertical" to "Webtoon").forEach { (key, label) ->
            FilterChip(selected = current == key, onClick = { onPick(key) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitChips(current: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("fit_width" to "Width", "fit_height" to "Height", "fit_screen" to "Screen").forEach { (key, label) ->
            FilterChip(selected = current == key, onClick = { onPick(key) }, label = { Text(label) })
        }
    }
}
