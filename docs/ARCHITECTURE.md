# Architecture 0.6

> German strings quoted in this document are the app's own wording. The README section "Another language" translates the ones that recur.

Status: 12 September 2026 (selectable speech engine, library, self-diagnosis, separate announcement voice, text recognition for scans, text cleanup for print typesetting)

## Responsibilities

| Part | Task |
| --- | --- |
| `core/ReaderModels.kt` | Document and chapter, text splitting, German commands, time positions across audio files |
| `core/Library.kt` | Library entry and its spoken label |
| `core/SpeechReport.kt` | Result of the self-diagnosis and its text form |
| `data/DocumentStore.kt` | PDF import, pages and bookmarks, private local JSON files, library list |
| `data/PageOcr.kt` | Renders a page without a text layer and recognizes the writing on it, entirely on the device |
| `speech/SpeechProvider.kt` | Interchangeable audio generation, local Android TTS implementation, cache |
| `speech/SpeechProbe.kt` | Checks a single installed speech engine for the self-diagnosis |
| `playback/ReaderPlaybackService.kt` | Media3 playback, audio focus, media notification, saving during background playback, chapter semantics for media keys (`ChapterPlayer`) |
| `ReaderViewModel.kt` | Import and playback state, preparation of a chapter, navigation |
| `MainActivity.kt` | File picker, share intents, permissions, local speech recognition |
| `ui/ReaderScreen.kt` | Compose user interface and TalkBack semantics |

## Path from PDF to audio

The file picker grants a URI. The import copies the content into a temporary file, limits the file size and extracts the text page by page with PDFBox-Android. The temporary original file is removed afterwards. The extracted text stays in private app storage. The app does not request a blanket storage permission.

A page that comes back without any text is handed to `PageOcr`. It renders the page with `PdfRenderer` and recognizes the writing with the ML Kit Latin model that ships inside the APK, at about 1.2 seconds per page. Nothing leaves the device, and the manifest removes the network permissions the recognition library declares. A book where neither extraction nor recognition finds writing after a sample of 25 pages is refused, rather than after being read to the end.

Every page then goes through `TextChunks.clean`, and the order of its steps carries weight. Whitespace is collapsed, the running page number on the first or last line is dropped, and only then are hyphens repaired. Repairing first would glue the page number onto the word that the page break cut in half, which is what a listener heard as "chao einundzwanzig tisch". Print typesetting wraps with a soft hyphen and some older books use `¬` for the same job, so both are treated as line-end hyphens. `TextChunks.joinPages` puts a word back together across a page boundary, which is why a trailing soft hyphen survives the cleanup.

Bookmarks provide section starts. Without bookmarks, pages become sections. The app does not guess headings. Pages without text are reported, password protection and text extraction that is not allowed lead to an understandable error message.

The player splits a chapter into parts of at most 1,000 UTF-16 characters, preferably at sentence or word boundaries. `SpeechProvider` creates one audio file per part. Android TTS may only use voices here that do not require network access. The generated audio is checked for a positive duration and only then stored under its final cache name.

Playback starts as soon as a minimum lead of `MIN_LEAD_MS` (12 seconds) of audio is ready from the start position on, or all parts are finished. Since the chapter announcement (part 0) lasts only about three seconds, at least the first text part is always prepared along with it before the start. Without this lead the player ran the short announcement dry before the text part was synthesized, and produced an audible pause. The remaining parts are generated in the same job and appended with `addMediaItem`. `ReaderState.preparing` stays set for that long; `busy` ends when playback starts, so that all buttons remain operable. During the background preparation the status is not updated, because every live region announcement from TalkBack would interrupt the book. If the player runs dry before the next part, that does not count as the end of the chapter: the button stays on pause and the new part is played after it has been appended, unless the user paused deliberately. Chapter, voice and document changes as well as clearing the cache abort the running preparation.

Part 0 of every chapter is always the spoken chapter announcement from `ChapterAnnouncement`. It is a normal audio part in the book voice, so that saved part indexes stay stable and the announcement can also be heard without TalkBack, with the screen switched off and over headphones. If the player reaches the end of a fully prepared chapter while `playWhenReady` is active, `syncPlayer` selects the next chapter without a status change (`chapter(index, announce = false)`, `play(quiet = true)`) and starts it. In the last chapter the previous behaviour stays: end, button „Vorlesen“, restart from the beginning.

Media3 receives the prepared files and their metadata. The app computes jumps from the sum of the actual audio durations. A 30 second jump is independent of text length and speaking rate. It means 30 seconds on the original audio timeline.

### Media keys and notification

The MediaSession does not get the ExoPlayer directly, but `ChapterPlayer`, a `ForwardingSimpleBasePlayer`. For media keys, Bluetooth and the notification a playlist is therefore one chapter: `seekBack`/`seekForward` compute with `AudioTimeline` across all parts, "next" and "previous" are intercepted in `handleSeek`. "Previous" follows the Media3 rule: more than three seconds into the chapter jumps to the start of the chapter, otherwise to the previous chapter. The service does not know chapters and cannot generate audio; it therefore sends `COMMAND_NEXT_CHAPTER` or `COMMAND_PREVIOUS_CHAPTER` as a custom command to the connected controllers, and the ViewModel runs `chapter()`. So that "next" also stays available on the last part, `getState()` always reports the commands and announces `REPEAT_MODE_ALL` to the outside; the ExoPlayer itself does not repeat. The notification additionally shows 30 seconds forward and back via `setMediaButtonPreferences`.

Consequence: chapter changes by media key and the automatic continuation require a live ViewModel. After the app is removed from the recents overview, only the current chapter plays to its end. The clean solution, moving document and audio generation into the service, is a later stage of expansion.

An empty buffer reports the same player state as a real end of a chapter. The service must therefore not conclude "finished" from it, because a chapter marked that way starts from the beginning on the next start instead of resuming. During the preparation the ViewModel therefore sets the flag `KEY_PREPARING` in the preferences, and the service writes `finished` only when it is not set. A freshly created ViewModel clears the flag in case a terminated process left it behind.

The service saves document, chapter, audio file, offset and voice about once per second. It keeps working even when the activity is closed. After a process restart, the cache is built or found again the next time reading aloud starts, and the last position is restored.

## Library

Every imported document sits in private storage as `<id>.json` with the complete text. The library does not read these files. It reads one small side file `<id>.meta.json` each, with title, number of sections and time of the import. Otherwise opening the list with ten books would have to read in ten complete texts, up to one million characters per document. Documents from older versions do not have a side file yet; it is written once when they are first listed.

The listening position is still in the preferences under `<id>.chapter` and its siblings. The app recognizes that a document has already been listened to by the fact that the service has written `<id>.voice`. `libraryLabel` builds the spoken line from that: title, number of sections, and where the user stopped.

Removing deletes the extracted text, the side file and the listening position. The user's PDF file stays untouched, the app never owned it. Generated audio stays in the cache, because the cache is addressed by text and voice and not by document; it is cleared through „Erzeugtes Audio löschen“ or the 500 MB limit.

Because removing cannot be undone, the app asks first. The confirming button is called „Ja, entfernen“ and not „Entfernen“ a second time, so that it differs from the button in the list when heard.

## Speech and privacy

### TalkBack and playback state

`ReaderState.playing` describes audio that is actually running. `playbackRequested` by contrast describes whether the player should play at the user's request. The read aloud/pause button and the chapter changes follow `playWhenReady`, except when idle and at the end of a chapter. Player events update the state immediately; the half second polling interval stays in place for the time position.

Media3 may stop the audio stream briefly during a TalkBack announcement. Because of that the focused button must not change between „Vorlesen“ and „Pause“, otherwise TalkBack reads the new state out again and interrupts the book once more. There is no self-built resume timer. Media3 still manages the audio focus, including permanent focus loss and deliberate pausing during an announcement.

Basis: [Media3 player events and the difference between playWhenReady and isPlaying](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).

### Spoken announcements of the app

`AndroidSpeechProvider` holds two TextToSpeech instances. The first writes chapter audio into files, the second speaks short feedback. A single instance cannot speak and write a file at the same time; as long as both shared one, the app swallowed every announcement while a chapter was being prepared in the background. Since progressive preparation the buttons are operable during that time, which made the bug visible.

`screenReaderActive()` decides through `AccessibilityManager.isTouchExplorationEnabled` whether an announcement is spoken or only written into the live region. If TalkBack is running it reads the status text out anyway; speaking in the book voice as well would double every message. „Inhaltsverzeichnis vorlesen“ and the sample reading always speak in the book voice, because that is exactly their purpose, and they set no status.

### Choice of the speech engine

The app does not take the speech engine preset by the system, but a saved selection under `engine`; if none is set, the system default applies. The reason is the finding on the target device, see [Decision 0003](decisions/0003-selectable-speech-engine.md): the engine preset there reports not a single voice through `getVoices()`, although it speaks fluent German.

`AndroidSpeechProvider.voices()` therefore asks in two stages. If `getVoices()` delivers German offline voices, these are offered by name. Otherwise `isLanguageAvailable(Locale.GERMAN)` decides, and if it is available a single entry `ENGINE_DEFAULT_VOICE` appears with the label „Standardstimme dieser Sprachausgabe“. During generation `setLanguage` is then used instead of `setVoice`.

The cache key contains the engine actually in use, no longer the system default. Otherwise the audio of the old engine would keep being used after an engine change.

For the diagnosis `SpeechProbe` checks every installed engine one at a time, each with its own short-lived TextToSpeech instance: does it start, does it know German, which voices does it report, and does it write audio into a file. Android silently replaces an unknown engine package with the default instead of failing; a saved selection therefore cannot make the app unusable even after that engine has been uninstalled.

### Voice input and local data

`SpeechRecognizer.createOnDeviceSpeechRecognizer` is used from Android 12 on. The voice commands are mapped locally to known actions. The recording starts only on a button press and pauses the book. If speech data is missing, the labelled buttons are offered. There is no dependency on the system-wide Voice Access app.

The main app has no internet permission. Cloud providers are planned exclusively as later implementations of the provider interface. Before they are integrated, consent, cost limit and access key have to be implemented separately.

## Deliberate interim solutions

JSON and private preferences are enough for the first reader. Room becomes necessary as soon as library, search and bookmarks are added. WorkManager and progressive audio preparation follow for large documents. The current version plays one prepared chapter at a time.

## Dependencies

- Android Gradle Plugin 9.4.0, Gradle 9.6.0 and Java 17. The upgrade assistant of Android Studio set compatibility flags for the old DSL behaviour in `gradle.properties` (`android.newDsl=false`, `android.builtInKotlin=false` and others); they stay until the project is deliberately moved to the new DSL.
- Kotlin 2.2.21, Compose BOM 2026.01.00, Activity 1.12.3 and Lifecycle 2.10.0.
- [Media3](https://developer.android.com/jetpack/androidx/releases/media3) 1.9.2 for audio and MediaSession.
- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) 2.0.27.0 for the prototype text import.
- [ML Kit text recognition](https://developers.google.com/ml-kit/vision/text-recognition) 16.0.1, the bundled Latin variant, for scanned pages. The model is part of the APK, which is why the package grew from 19 to 60 MB. The library declares `INTERNET` and `ACCESS_NETWORK_STATE` in its own manifest; both are removed again in the app manifest with `tools:node="remove"`, and the built package is checked with `aapt2 dump permissions`.

PDFBox-Android is at an older release level. Before a public version, the parser and the transitive cryptography dependencies are assessed again. No voice models are shipped; the only model in the package is the one for text recognition.
