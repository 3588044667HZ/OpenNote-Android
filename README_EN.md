# Open Note

A native Android note-taking app with rich text editing, eye protection skins, dark mode, and share-as-image — built with Jetpack Compose + WebView.

## Features

- **Rich Text Editing** — Dual editor (title + content) powered by WebView contentEditable with Markdown ↔ HTML conversion
- **Eye Protection Skins** — 7 color skins with warm yellow paper-like background, CSS variable-based theming
- **Dark Mode** — System-driven dark mode with automatic skin switching, real-time re-evaluation
- **Share as Image** — Capture editor content as PNG, preview with skin colors + watermark, save to gallery or share
- **Notebook Management** — Organize notes into notebooks with server sync
- **Trash & Recovery** — Soft-delete notes with 30-day trash retention
- **Offline First** — Room local database, syncs with server when network is available
- **JWT Authentication** — Token-based auth with automatic refresh interceptor

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Editor | Android WebView (contentEditable) + JavaScript bridge |
| Database | Room (offline-first) |
| Networking | Retrofit + OkHttp (JWT interceptor) |
| DI | Hilt |
| State | Kotlin StateFlow / DataStore |
| Export | View → Canvas → Bitmap pipeline |

## Architecture

```
UI Layer (Compose)
  ├── LoginActivity
  ├── MainActivity (Notes / Trash / Settings tabs)
  ├── NoteEditorActivity (WebView editor)
  └── SharePreviewActivity

ViewModel Layer
  ├── Auth / NoteList / NoteEditor / Trash / Settings
  └── SkinViewModel

Data Layer
  ├── Room (NoteDao, FolderDao)
  ├── DataStore (Auth, Skin, Server config)
  ├── Retrofit API (Notes, Auth, Notebooks, Share Settings)
  └── Repository (offline-first pattern)

Share Module
  ├── ContentCaptureEngine  → WebView screenshot
  ├── ShareImageComposer    → View tree → Bitmap
  └── ImageExporter         → Gallery / Intent share
```

## Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Min SDK 26, Target SDK 34

### Build
```bash
git clone <repo-url>
cd android-app
./gradlew assembleDebug
```

### Server Setup
The app connects to a REST API server. Default URL: `http://10.0.2.2:5000/api/` (Android emulator).

Please see https://github.com/3588044667HZ/open-note-server

Change the server URL from the login screen (gear icon) or Settings page.

## Project Structure

```
android-app/
├── app/src/main/java/com/open/note/
│   ├── MainActivity.kt
│   ├── OpenNoteApp.kt
│   ├── di/AppModule.kt
│   ├── data/
│   │   ├── local/          (Room DB, DataStore)
│   │   ├── remote/         (Retrofit API, DTOs, Interceptors)
│   │   ├── repository/     (Auth, Note, Sync)
│   │   └── skin/           (Skin models, WebView CSS)
│   ├── share/              (Capture engine, Image composer, Exporter)
│   └── ui/
│       ├── login/
│       ├── notes/
│       ├── editor/
│       ├── trash/
│       └── settings/
├── app/src/main/assets/
│   ├── editor.html         (Dual contentEditable editor)
│   └── editor-core.js      (Editor JS: Markdown, Bridge, Focus)
└── mock-server/            (Flask API mock)
```

## API

REST API documented in [API.md](./API.md):
- `POST /api/auth/login|register|refresh|logout`
- `GET|POST|PUT|DELETE /api/notes`
- `GET|POST|PUT|DELETE /api/notebooks`
- `GET|PUT /api/settings/share`
- `GET /api/notes/sync` (incremental sync)

## License

MIT
