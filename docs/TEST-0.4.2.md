# Test protocol 0.4.2

Date: 12 September 2026

## What is new

1. The import limits are now sized for books. Previously 40 MB, 300 pages and one million characters, now 120 MB, 3,000 pages and six million characters. The trigger was the test reader's attempt to load a novel that was refused with a note about 300 pages.
2. Generated audio is cleaned up automatically at 500 MB, the least recently heard first. Previously playback refused to work at this limit and asked for the cache to be emptied by hand. A book of this length generates a multiple of that; the user would have been stranded in the middle of the book.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64, Google Play image.
- App 0.4.2, version code 10.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.
- ⛔ TalkBack turned off for the automated run, see below.

## Results

| Check | Result |
| --- | --- |
| Build debug app and test app, Android Lint | passed, Lint without errors |
| Eleven core logic tests (JVM) | passed |
| PdfImportTest, three checks, new: import of a generated 600-page PDF | passed, import in 4.0 seconds, 600 sections |
| SpeechAudioTest, six checks, new: the cache drops the least recently heard files, and leaves a cache within budget alone | passed |
| ProgressivePreparationTest, ResumePositionTest, PlaybackFocusTest, ReaderUiTest, MediaButtonTest, LibraryTest, LibraryUiTest, DiagnosticsTest | passed |

Total: 30 instrumented tests in ten classes, none skipped.

## The finding that cost the most time

Two checks from `ProgressivePreparationTest` repeatedly ran into their time limits. The suspicion first fell on the Mac's tight memory, then on our own changes. Both were wrong.

The speech engine log showed gaps of 79 and 194 seconds in which the app requested no synthesis at all. The cause: **TalkBack was active** on the emulator. It reads every change of the status line aloud and holds the audio focus while doing so; playback then waits correctly, just for minutes.

With TalkBack turned off the same four checks take 43 to 79 seconds instead of more than 250. The rule is now in [TESTING.md](TESTING.md) together with the two commands for turning it off and on.

🎯 The lesson: the automated test and the manual TalkBack test exclude each other. That was already in the protocol for 0.1.1 as a warning, but it was not phrased as a precondition for every run.

## Still open

- TalkBack is currently turned off on the emulator. Turn it back on before the next manual test.
- The manual run on a real device. The test reader now uses the reader daily, and there is no formal protocol for that.
- Her choice of voice, see [VOICES.md](VOICES.md).
