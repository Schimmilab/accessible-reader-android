# Test protocol 0.2.0

Date: 11 September 2026

## Changes compared to 0.1.1

1. Reading aloud starts after the first audio part. The remaining parts of the chapter are prepared and appended while the listener is listening, without status announcements for TalkBack.
2. Every chapter starts with a spoken announcement in the book voice. At the end of a chapter the app keeps reading in the next chapter, without input and without a TalkBack announcement. The last chapter stops and offers a restart.
3. Headphones, Bluetooth and the media notification: „Weiter“ changes the chapter, „Zurück“ jumps to the start of the chapter and after that into the previous chapter. The notification has buttons for 30 seconds forward and back that compute across all audio parts.

Commits: bdf91e0 (build upgrade), fe35dae (points 1 and 2), 4fe1732 (point 3), plus the version commit.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64, Google Play image.
- TalkBack 17.0.0.889642762, Google speech engine 20260511.02, local German voice.
- App 0.2.0, version code 3, sample reading with three sections at normal speed.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.

## Results of the automatic checks

| Check | Result |
| --- | --- |
| Build the debug app and the test app, Android Lint | passed, Lint without errors |
| Six core logic tests (JVM), among them the chapter announcement as a new one | passed |
| PlaybackFocusTest, five checks, new: keep reading without a status announcement, restart only in the last chapter | passed |
| ProgressivePreparationTest, two checks: playback starts before the preparation ends, a chapter change aborts it | passed, about 3 minutes because the emulator synthesizes slowly |
| MediaButtonTest, two checks through a second MediaController as with headphones | passed |
| ReaderUiTest, two checks | passed |
| SpeechAudioTest, one check | passed, German offline voice present |
| PdfImportTest, two checks | first run failed, without usable output, because the following run overwrote the result file; the repetition passed on its own. Cause not determined, the import code was not changed in 0.2.0. Watch for it if it happens again |

The emulator generates one audio part in about 30 seconds and is therefore slower than playback. The preparation test has therefore probably also run through the case where the player runs dry before the next part and keeps playing after it is appended. On a real device the synthesis is much faster than real time.

## Manual run with TalkBack (open)

Without looking at the screen, only TalkBack, sound and double tap. No ADB, no UI automation during this run.

| No. | Task | Expectation | Result |
| --- | --- | --- | --- |
| 1 | Start the app, activate „Leseprobe“ | The title and three sections are announced | |
| 2 | „Vorlesen“ by double tap | Within a few seconds „Abschnitt 1 von 3: Ankommen.“ starts, then the text | |
| 3 | Listen for 20 seconds, then trigger a menu announcement by TalkBack (move the focus) | The book continues after the announcement, the button stays „Pause“ | |
| 4 | „Pause“ by double tap, wait for the TalkBack announcement | It stays paused, the button shows „Vorlesen“ | |
| 5 | „30 Sekunden zurück“ | The jump distance is announced, playback jumps correctly, also across the chapter announcement | |
| 6 | Listen until the end of „Ankommen“ | „Abschnitt 2 von 3: Unterwegs.“ follows without input and without a TalkBack announcement | |
| 7 | Switch off the screen, headphones: forward button or double click | The next chapter is announced and played | |
| 8 | Headphones: back button twice in quick succession | First the start of the chapter, then the previous chapter | |
| 9 | Open the notification | Five buttons: Zurück, 30 s zurück, Pause, 30 s vor, Weiter, each one named by TalkBack | |
| 10 | Listen to the last chapter until the end | Playback ends, the button shows „Vorlesen“, activating it again starts the chapter from the beginning | |
| 11 | Close the app and open it again, „Vorlesen“ | It continues at the last position | |
| 12 | Remove the app from the overview while it is playing | The current chapter plays to the end, no further reading (known limit) | |

Every dead end, every unclear announcement and every unnecessary swipe is noted.

## Still open

- The manual TalkBack run above and voice input through the Mac microphone.
- A real Android device. There in addition: Bluetooth headphones, screen switching off, a phone call, battery management.
- The listening test by the test reader. Only after that are a voice comparison and OCR worthwhile.
- Known limit: after the app is removed from the overview, chapter changes by media key and automatic further reading no longer work, because both live in the ViewModel. Moving the document and the audio generation into the service is the next larger rebuild.
