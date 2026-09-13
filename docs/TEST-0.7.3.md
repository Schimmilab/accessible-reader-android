# Test protocol 0.7.3

Date: 13 September 2026

## What is new

**A voice that cannot keep up with listening now says so.** The app has been timing every piece it synthesizes since 0.6.1, but the measurement never left the log. Whether a voice can keep ahead of playback decides whether a book runs through or keeps stopping, and there is no way to hear that from a short sample. A neural voice sounds better and takes about thirty times longer to produce.

Under the voice list, a voice that needs half the listening time or more is now described in plain words, with the way out named: another voice reads more smoothly.

Only real syntheses count. A cache hit costs no time, and counting those would make every voice look instant on a second run.

## A claim withdrawn

The documentation said a two-column page can interleave its columns sentence by sentence. Checked against the books actually at hand: 180 pages across five real books, and **not one of them has a genuinely two-column layout**. What was observed and mistaken for columns is a table next to a paragraph, which text extraction reads row by row. The README now says that instead.

Nothing was built for it. There is no failing case to build against, and a column heuristic applied to pages that are not two-column would damage books that work today.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.7.3, version code 19, release build.
- Speech engines: Google, and VoxSherpa with the Piper voice de_DE-thorsten-high.
- TalkBack turned off for the automated runs.

## Measured

| Voice | Note shown |
| --- | --- |
| Google de-DE-language, about 0.04 of the listening time | none, and that is correct |
| Piper Thorsten, about 1.0 | „Diese Stimme braucht zum Erzeugen ungefähr so lange, wie das Zuhören dauert …" |

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| `application-debuggable` | not present |
| Version in the package | 0.7.3, code 19 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 26 of 26 passed |
| `VoiceSpeedTest` | 2 of 2, the slow voice is named and the fast one is not |
| `PageSectionsTest` | 2 of 2 |
| `ProgressivePreparationTest` | 4 of 4 |
| `ReaderUiTest` | 2 of 2 |
| `OnlineVoiceTest` | 2 of 2 |
| Lint | no errors |

## What stays open

- A table next to a paragraph still reads interleaved. Understood, not fixed, and no longer described as a column problem.
- A section change with a local neural voice can still take several seconds, because that voice has no spare capacity to work ahead.
- Chapter changes by media key and automatic continuation still need the app alive. Once it is swiped out of the recents list only the current section plays to the end. Moving document handling and synthesis into the playback service is the largest open piece.
- Whether the recent changes fix what the test reader described has not been confirmed on her device.
