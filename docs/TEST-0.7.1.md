# Test protocol 0.7.1

Date: 13 September 2026

## What is new

From a listening report: „Ich habe jetzt das Buch, der kleine Hausdoktor, und ich muss irgendwie Seite für Seite reinladen, dass er das dann vorliest." [I have this book now and somehow I have to load it page by page for it to read.]

**A book without bookmarks became one section per page.** That looked tidy and listened badly. Measured on the emulator with a four-page document and nothing pressed: the next page does start on its own, but playback stops completely in between, for 8199, 12077 and 9686 milliseconds. On a four hundred page book that is a ten second silence after every page, which is exactly what a listener would take for the app waiting to be told something.

Pages are now grouped into sections of about ten thousand characters, roughly ten minutes of listening. The silence arrives once per ten minutes instead of once per minute, and the spoken section number stops interrupting every page. A PDF that brings its own bookmarks is untouched.

**The diagnosis report contradicted the app.** 0.7.0 started offering online voices while the report still called them „im Reader nicht wählbar" [not selectable in the reader]. That line exists precisely to stop the two from disagreeing, so both now follow one rule, and a test compares them on a device.

**The report now says what to do about missing voice data.** A report from the target device listed thirteen offline German voices where the app offered five. The answer is in the features: eight of them are flagged `notInstalled`, announced by the engine but never downloaded. The report names that and points at the Android settings where they can be fetched.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64. The emulator crashed during the session and was restarted.
- App 0.7.1, version code 17, release build.
- TalkBack turned off for the automated runs.

## Measured

| Book | Sections before | Sections now |
| --- | --- | --- |
| Novel, 458 pages, no bookmarks | 458 | 64 |
| Non-fiction, 524 pages, with bookmarks | 60 | 60, unchanged |
| Generated book, 600 pages | 600 | 4 |

| Silence between sections, four page document | Value |
| --- | --- |
| page 1 to 2 | 8199 ms |
| page 2 to 3 | 12077 ms |
| page 3 to 4 | 9686 ms |

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| `application-debuggable` | not present |
| Version in the package | 0.7.1, code 17 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 25 of 25 passed |
| `PageSectionsTest` | passed, four pages run to the end untouched |
| `PdfImportTest` | 5 of 5 |
| `RealBooksTest` | 2 of 2 real books |
| `VoiceListTest` | 2 of 2, including report against app |
| `OnlineVoiceTest` | 2 of 2 |
| `ReaderUiTest` | 2 of 2 |
| Lint | no errors |

## What stays open

- **The silence itself is still there**, now once per section instead of once per page. Removing it means preparing the next section while the current one still plays, which is a real piece of work and has not been started.
- Eight voices on the target device are announced but not downloaded. Whether fetching them helps is a listening question for the test reader.
- A section switch with a local neural voice still takes about 26 seconds before the first sound.
