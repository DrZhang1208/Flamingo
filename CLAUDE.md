# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Flamingo is an Android music player app built with Kotlin and Jetpack Compose. It uses AndroidX Media3 (ExoPlayer) for audio playback and targets API 23+.

## Build & Run

```bash
# Build the project
./gradlew assembleDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "yos.music.player.ExampleUnitTest"

# Clean build
./gradlew clean
```

Gradle wrapper is included. The project uses Kotlin 2.0.0, AGP 8.4.0, and the Compose compiler Gradle plugin (not the legacy `kotlinCompilerExtensionVersion` approach in `app/` — though `overscroll_core/` still uses the legacy approach).

## Architecture

### Module structure
- **`app/`** — Main application module (`yos.music.player`). Contains UI, data, and playback logic.
- **`overscroll_core/`** — Library module (`com.cormor.overscroll.core`) providing a custom iOS-style overscroll bounce effect for Compose scrollable containers. Used by the app module via `implementation project(":overscroll_core")`.

### UI layer (Compose + Navigation)
- **`MainActivity`** is the single-activity entry point. It sets up the theme, system UI, and the primary drag-to-expand bottom sheet that hosts both the mini-player bar and the full NowPlaying screen.
- Navigation uses `AnimatedNavHost` with string-based routes defined in the `UI` interface (`app/src/main/java/yos/music/player/ui/NavHost.kt`). All route constants live under `UI.HomePage`, `UI.Library`, `UI.Settings.*`, etc.
- Pages are under `ui/pages/`: `Home.kt`/`HomeNav.kt` (home pager), `library/` (albums, artists, playlists, song list), `NowPlaying.kt`, and `settings/` (multiple settings subpages).
- Reusable widgets live in `ui/widgets/` — bottom navigator, shadow image, bottom sheet dialog, lyric view, visual effects.
- Theming is in `ui/theme/` with light/dark color schemes (`YosMusicTheme`). Dark mode detection is via `isFlamingoInDarkMode()` rather than system setting alone.

### Data layer
- **`MusicLibrary`** (`data/libraries/MusicLibrary.kt`) — Singleton object that scans local media via the `libPhonograph` library and persists results. Exposes reactive properties (`songs`, `folders`, `hideSongs`, etc.) backed by `DataSaver` (which syncs with MMKV). Also handles playlist/play-state save/load via Gson + MMKV.
- **`YosMediaItem`** — Parcelable data class wrapping Media3's `MediaItem` metadata. Conversion functions `toMediaItem()` and `toYosMediaItem()` live in `MusicLibrary`.
- **`SettingLibrary`** — App settings stored via `DataSaver`/MMKV.
- **`PlayListLibrary`** — Playlist CRUD operations.
- **ViewModels**: `MainViewModel`, `MediaViewModel`, `ImageViewModel` under `data/models/`. State objects live in `data/objects/` (e.g., `MediaViewModelObject`, `MainViewModelObject`).

### Playback (Media3)
- **`MediaController`** (`code/MediaController.kt`) — Singleton object holding the Media3 `MediaController` instance, the currently playing `YosMediaItem`, and the playing music list. `prepare()` handles both playlist changes and in-place track switches.
- **`YosPlaybackService`** (`code/MediaController.kt`, same file) — `MediaSessionService` implementation. Creates the `ExoPlayer`, configures audio attributes and renderers, sets up the notification with custom shuffle/repeat buttons, and handles persistence of play state (with debounced `saveDataWithDelay()`).
- **`FadeExo`** — Custom fade-in/fade-out playback transitions.
- **`YosRenderFactory`** — Custom `DefaultRenderersFactory` for ExoPlayer renderer selection (supports FFmpeg extension decoder).
- **`SystemMediaControlResolver`** — Handles system media button events.
- **`YosLrcFactory`** — Parses LRC lyrics files into timed entries for the lyric view.

### Application startup
`YosBasicApplication` (`app/src/main/java/yos/music/player/YosBasicApplication.kt`) initializes MMKV, registers Gson type adapters for `DataSaver`, builds the Media3 controller, and restores previous playback state. It also sets a global uncaught exception handler that launches `CrashActivity`.

## Key conventions
- Reactive state uses a mix of Compose `mutableStateOf` and the `DataSaver` library's `mutableDataSaverListStateOf` (which persists to MMKV automatically).
- `YosWrapper` composable is used throughout as a debug/logging wrapper (similar to a no-op scaffold for recomposition logging).
- The `withNight` infix extension function provides dark/light color switching: `Color.Black withNight Color.White`.
- Stability configuration is in `stability_config.conf` at the project root, used by the Compose compiler's strong skipping mode.
- ProGuard is enabled for release builds with custom keep rules for Gson type tokens, Lyric-Getter-Api classes, and MMKV data classes.
