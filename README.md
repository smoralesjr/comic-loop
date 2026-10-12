# ComicLoop

A modern comic book reader for Android. Opens `.cbr` (RAR) and `.cbz` (ZIP) files from
local storage or any cloud provider, and syncs reading progress to Google Drive.

## Features

- **CBR / CBZ support** — RAR via [junrar](https://github.com/junrar/junrar), ZIP via zip4j
- **Cloud files** — uses the Storage Access Framework file picker, so Google Drive,
  Dropbox, OneDrive, and any installed cloud provider appear directly in the picker
- **Library** — grid of covers with per-book progress bars, remembers last page
- **Reader modes** — LTR, RTL (manga), and vertical (webtoon) scroll
- **Gestures** — pinch-zoom up to 5x, tap left/right edges to flip pages, double-tap to advance
- **Fit modes** — fit width / fit height / fit screen
- **Google Drive sync** — reading progress uploads as `comicloop_progress.json`
  into a `ComicLoop` folder in your Drive (drive.file scope; the app only touches files it created)
- Opens `.cbr`/`.cbz` attachments from other apps via intent filters
- Keep-screen-on option while reading

## Build

1. Open the project in **Android Studio** (Koala or newer, AGP 8.5, JDK 17).
2. Let Gradle sync (Compose BOM 2024.06.00, Kotlin 1.9.24).
3. Run on a device or emulator with API 26+.

Or from the command line (no wrapper is committed — Android Studio will generate one, or run `gradle wrapper` once Gradle 8.7+ is installed):

```bash
gradle wrapper && ./gradlew :app:assembleDebug
```

## Google sign-in setup (required for Drive sync)

The app uses Google Sign-In (`play-services-auth`) plus the Drive REST API
(`google-api-services-drive`) with the `drive.file` scope.

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. Configure the **OAuth consent screen** (External is fine for personal use).
3. Create an **OAuth Client ID** of type *Android*.
4. Enter your package name `com.comicloop.app` and your signing SHA-1:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
   ```
5. Paste the client ID (and optionally the reversed client ID) into
   `strings.xml` if using `requestIdToken`; for the default `GoogleAccountCredential`
   flow used here, the Android client registration with SHA-1 is sufficient.
6. Add yourself as a test user on the OAuth consent screen while the app is unverified.

Progress JSON format on Drive:

```json
[
  { "id": "content://…", "title": "Saga 01", "pageCount": 34, "currentPage": 12, "lastOpenedAt": 1720000000000 }
]
```

## Project layout

```
app/src/main/java/com/comicloop/app/
├── MainActivity.kt            # Nav host, theme
├── archive/                   # CBR/CBZ readers, image filtering, cache import
├── data/                      # Book + settings persistence (DataStore)
├── cloud/                     # Google Drive progress sync
├── reader/                   # Reader VM + Compose reader UI
└── ui/                        # Library + settings screens
```

## Notes & limitations

- CBR is extracted fully to cache before reading (junrar does not stream from
  content URIs); large archives may take a few seconds on first open.
- Drive sync uses `drive.file` scope: the app can only read/write
  `comicloop_progress.json` it created itself.
- Password-protected RAR5 archives are not supported by junrar.
- Sync is manual (Sync button in the library toolbar); auto-sync is a roadmap item.
