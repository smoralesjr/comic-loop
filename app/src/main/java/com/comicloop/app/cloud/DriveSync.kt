package com.comicloop.app.cloud

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DriveSync {
    private const val FILE_NAME = "comicloop_progress.json"
    private const val MIME_FOLDER = "application/vnd.google-apps.folder"
    private const val APP_FOLDER = "ComicLoop"

    fun signInOptions(): GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
        .build()

    fun getSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isSignedIn(context: Context): Boolean = getSignedInAccount(context) != null

    fun signOut(context: Context) {
        GoogleSignIn.getClient(context, signInOptions()).signOut()
    }

    private fun driveFor(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .setSelectedAccount(account.account)
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ComicLoop")
            .build()
    }

    private fun findOrCreateFolder(drive: Drive, name: String): String {
        val query = "mimeType = '$MIME_FOLDER' and name = '$name' and trashed = false"
        val result = drive.files().list().setQ(query).setSpaces("drive").setPageSize(1).execute()
        result.files.firstOrNull()?.let { return it.id }
        val meta = com.google.api.services.drive.model.File()
            .setName(name)
            .setMimeType(MIME_FOLDER)
        return drive.files().create(meta).execute().id
    }

    suspend fun uploadProgress(context: Context, json: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val account = getSignedInAccount(context) ?: error("Not signed in")
            val drive = driveFor(context, account)
            val folderId = findOrCreateFolder(drive, APP_FOLDER)
            val query = "name = '$FILE_NAME' and '$folderId' in parents and trashed = false"
            val existing = drive.files().list().setQ(query).setSpaces("drive").setPageSize(1).execute()
                .files.firstOrNull()

            val meta = com.google.api.services.drive.model.File()
                .setName(FILE_NAME)
                .setParents(listOf(folderId))
                .setMimeType("application/json")
            val content = com.google.api.client.http.ByteArrayContent("application/json", json.toByteArray())

            if (existing == null) {
                drive.files().create(meta, content).execute()
            } else {
                drive.files().update(existing.id, meta, content).execute()
            }
        }
    }

    suspend fun downloadProgress(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val account = getSignedInAccount(context) ?: error("Not signed in")
            val drive = driveFor(context, account)
            val folderId = findOrCreateFolder(drive, APP_FOLDER)
            val query = "name = '$FILE_NAME' and '$folderId' in parents and trashed = false"
            val file = drive.files().list().setQ(query).setSpaces("drive").setPageSize(1).execute()
                .files.firstOrNull() ?: error("No synced progress on Drive")
            drive.files().get(file.id).executeMediaAsInputStream().use { it.readBytes().decodeToString() }
        }
    }
}
