# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Native Android PDF reader for blind users (Kotlin, Jetpack Compose, Media3), version 0.2.3.

**The reference device is a Samsung Galaxy S25, Android 16 / One UI 8.5, Samsung TalkBack 16.2, Vocalizer speech engine, mostly a Bluetooth speaker** (`docs/decisions/0002-zielgeraet-samsung-s25.md`). Only a Pixel emulator with Google TTS is available here, so the emulator proves logic and regressions, never that something works on the target. It is still unverified whether Vocalizer supports `synthesizeToFile`, which the whole audio pipeline depends on; the in-app diagnosis in the settings dialog exists to answer that remotely.
 Text PDFs are imported, split into chapters, synthesized to audio with a local German Android TTS voice and played through a MediaSession. Everything is designed around TalkBack, voice commands and media keys. Android is the lead platform by decision (`docs/decisions/0001-android-first.md`); do not design for a shared iOS codebase.

Language conventions: docs, UI strings and status messages are German; commit messages are English (`fix:`, `feat:`, `docs:` prefixes). Keep new user-facing strings in German.

## Build and test

Requires Java 17 as Gradle JDK (daemon toolchain configured in `gradle/gradle-daemon-jvm.properties`), Android SDK 36, Build Tools 36.0.0. minSdk 26, targetSdk 36. AGP 9.4.0 / Gradle 9.6.0 with legacy-DSL compatibility flags in `gradle.properties`; do not remove those flags casually.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug   # build, JVM tests, lint
./gradlew :app:connectedDebugAndroidTest                              # instrumented tests, needs device/emulator
./gradlew :app:testDebugUnitTest --tests "de.schimmilab.accessiblereader.ReaderCoreTest"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.schimmilab.accessiblereader.PlaybackFocusTest
```

**Turn TalkBack off before an automated run, and back on for manual accessibility testing.** With it enabled, every status live-region update makes TalkBack speak and hold audio focus, so playback tests stall for minutes and time out; measured 43-79 s per test with it off against 250 s timeouts with it on. `docs/TESTING.md` has the two commands.

Pass one test class per `connectedDebugAndroidTest` invocation; a comma-separated class list only runs the first class under AGP 9. Instrumented tests that seek or wait for a chapter end must first `waitUntil { !state.preparing }`: the emulator synthesizes at roughly 30 s per part, slower than playback. Start playback in tests through the model, not by clicking "Vorlesen", because a fresh install first shows the notification permission dialog.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Test layout:
- `app/src/test` (`ReaderCoreTest`): pure JVM tests for `core/ReaderModels.kt` (chunking, command parsing, audio timeline). Only place for logic that must run without a device.
- `app/src/androidTest`: `PdfImportTest`, `ReaderUiTest`, `SpeechAudioTest`, `PlaybackFocusTest`, `ProgressivePreparationTest` (synthesizes a 5-chunk chapter, takes about 3 minutes on the emulator), `MediaButtonTest` (drives the session through a second `MediaController`, the way headsets and the notification do). They need a Google-Play emulator or device with a German *offline* TTS voice installed. `SpeechAudioTest` skips via `assumeTrue` if none exists; `PlaybackFocusTest` drives a real ExoPlayer and real audio focus and can take minutes.
- TalkBack behaviour is verified manually on the emulator (see `docs/TESTING.md`). A Compose click test does not replace a TalkBack double-tap test, and UI automation must not run while TalkBack gestures are being tested because it suppresses other accessibility services.

## Architecture

Single module `app`, package `de.schimmilab.accessiblereader`. The pipeline is:

1. `MainActivity` receives a PDF via file picker, `VIEW` or `SEND` intent and owns the on-device `SpeechRecognizer` (API 31+, German, started only by button press).
2. `data/DocumentStore` copies the PDF to a temp file, extracts text per page with PDFBox-Android, maps PDF bookmarks to chapters (else one chapter per page), stores the result as JSON in private app storage and deletes the temp copy. No headings are guessed. It also writes a small `<id>.meta.json` beside it; `library()` lists those side files so opening the library never parses a whole book, and heals a missing side file once. `remove()` deletes text and side file, never the user's PDF.
3. `ReaderViewModel.play()` splits the chapter with `TextChunks.split` (max 1000 UTF-16 chars, at sentence/word boundaries), calls `SpeechProvider.synthesize` per chunk, and builds one Media3 `MediaItem` per chunk. Each item carries extras `document`, `chapter`, `voice`, `duration`. Chunk 0 is always the spoken chapter intro from `ChapterAnnouncement`, so saved item indexes stay stable. Playback starts once at least `MIN_LEAD_MS` (12 s) of audio is buffered ahead of the start position, or all chunks are ready; the remaining chunks are appended with `addMediaItem` while `ReaderState.preparing` is true. The lead exists because the ~3 s intro alone would drain into silence before the first text chunk finished synthesizing. `busy` only covers the wait before the first sound. When a fully prepared chapter ends with `playWhenReady` still true, `syncPlayer()` advances to the next chapter via `chapter(i, announce = false)` and `play(quiet = true)`; the last chapter stops and offers a restart.
4. `speech/AndroidSpeechProvider` takes an engine package (blank = system default, persisted under `engine`) — never assume the default engine, the target device's default reports zero voices via `getVoices()`. `voices()` falls back to `isLanguageAvailable` and offers `ENGINE_DEFAULT_VOICE` when an engine names none; `synthesize` then uses `setLanguage` instead of `setVoice`. The cache key contains the engine actually in use. `speech/SpeechProbe` probes one engine at a time for the diagnosis. It holds two TextToSpeech instances: one writes chapter audio to files, a second one (`say`/`isSaying`/`stopSaying`) speaks short feedback. One engine cannot speak and write a file at once, so sharing it silenced every announcement during background preparation. It synthesizes to WAV files in `cacheDir/speech`, keyed by SHA-256 of `providerId|engine|voiceId|text`. A file is only committed under its final name after its duration is verified > 0. Only voices with `isNetworkConnectionRequired == false` and locale `de` are offered.
5. `playback/ReaderPlaybackService` (a `MediaSessionService` with ExoPlayer) plays the items in the background and writes `chapter`, `item`, `offset`, `voice`, `finished` into SharedPreferences `"reader"` once per second, keyed by document id. Resume on next start reads these keys. The session wraps ExoPlayer in `ChapterPlayer` (a `ForwardingSimpleBasePlayer`): seek back/forward run on the chapter timeline, next/previous become custom commands `COMMAND_NEXT_CHAPTER` / `COMMAND_PREVIOUS_CHAPTER` that the ViewModel's `MediaController.Listener` turns into `chapter()` calls. It advertises `REPEAT_MODE_ALL` only so SimpleBasePlayer keeps routing "next" on the last part into `handleSeek`.

State rules worth knowing before editing `ReaderViewModel`:
- The player is the source of truth. `syncPlayer()` reads the current item's extras back and recomputes `chapter`, `positionMs`, `durationMs`, `playing` and `playbackRequested`. `preparedKey` (`docId:chapter:voiceId`) tells whether the loaded playlist matches the current selection; anything that changes document, chapter or voice must clear it and the media items.
- `ReaderState.playing` is `Player.isPlaying`. `ReaderState.playbackRequested` is `playWhenReady` outside IDLE/ENDED, where ENDED counts as "still requested" while `preparing` (the player merely ran out of appended chunks). The Play/Pause button and chapter switching key off `playbackRequested`, never `playing`. Reason: TalkBack announcements take transient audio focus and Media3 pauses briefly; if the button flipped to "Vorlesen" TalkBack would re-announce it and interrupt again. Do not add a custom resume timer; Media3 handles audio focus.
- An announcement goes either to the live region or to the book voice, never both: `screenReaderActive()` (touch exploration) decides, because TalkBack already reads the status live region and speaking too would double it. Reading the contents aloud and the voice preview always use the book voice and set no status.
- Never write to `ReaderState.status` from the background preparation loop. It is a polite live region, and every update interrupts the book under TalkBack. Anything that replaces the playlist (chapter, voice, document, clear cache) must call `stopPreparation()` first.
- 30-second jumps are computed on the absolute audio timeline across all chunk files (`AudioTimeline`), not per media item, both in the ViewModel and in `ChapterPlayer`.
- Do not wrap Media3 listeners with Kotlin `by` delegation: Kotlin does not delegate Java default methods, so the session silently loses every state callback. Use `ForwardingSimpleBasePlayer` for player-level behaviour changes.
- Chapter changes from media keys and auto-advance need a live ViewModel; once the task is swiped away only the current chapter finishes (documented limit).
- Resuming where the listener stopped is the user's one named requirement (kept privately, see `docs/COLLABORATION.md`), guarded by `ResumePositionTest`. An empty buffer reports the same player state as a real chapter end, so the service writes `finished` only while `ReaderPlaybackService.KEY_PREPARING` is unset; a chapter wrongly marked finished restarts instead of resuming.
- Position, chapter and voice are read from SharedPreferences on resume; a voice change restarts the chapter from the beginning (no text-accurate position transfer yet).

## Hard constraints

- The app has no `INTERNET` permission and must not gain one silently. Cloud voices (Chirp 3 HD, Neural2) are planned only as additional `SpeechProvider` implementations, and each needs explicit consent, a cost limit and key handling before integration.
- Voice commands never fall back to a cloud recognizer. If German offline speech data is missing, the labelled buttons are the fallback.
- PDFs and generated audio stay on the device in private storage. No blanket storage permission.
- Every core action must be reachable via TalkBack, voice command (`CommandParser` in `core/ReaderModels.kt`) and media keys, never only by gesture.
- Text cleanup happens once, per page, in `TextChunks.clean` and the order of its steps is load-bearing: collapse whitespace, drop the running page number, then repair hyphens. Repairing first glues the page number onto the word the page break cut in half. Words split across a page boundary are rejoined in `TextChunks.joinPages`, which is why a trailing soft hyphen survives `clean`.
- Real books are checked with `RealBooksTest`, a harness that imports whatever PDFs sit in the app's own cache folder (`cache/books`) and logs the result to the `ReaderBooks` tag. Gradle uninstalls the app after a connected run and takes that folder with it, so install both APKs with `adb install -r` and start it with `adb shell am instrument`. Generated fixtures say nothing about print typesetting.
- Limits enforced in code (`DocumentStore.MAX_BYTES` / `MAX_PAGES` / `MAX_CHARACTERS`): 120 MB / 3000 pages / 6 M chars per PDF, sized for real books after a 600-page novel was refused. Contents read-aloud capped at 20 entries.
- The audio cache is trimmed, never a wall: `AndroidSpeechProvider.trimCache` drops the least recently used files down to 80 % of `ReaderViewModel.CACHE_BUDGET_BYTES` before preparing a chapter. A cache hit touches the file so recently heard chapters count as recent. Refusing to play once the cache is full would strand a listener mid-book.

## Docs to keep in sync

- `README.md` "Grenzen dieser Version" and `docs/ARCHITECTURE.md` describe the current version; update them when behaviour or limits change.
- Each tested APK gets a `docs/TEST-<version>.md` protocol (environment, results, open items). Bump `versionCode`/`versionName` in `app/build.gradle.kts` together.
- Platform or product decisions go to `docs/decisions/NNNN-*.md`. `docs/PLAN.md` is the full product target, not a list of finished features.
