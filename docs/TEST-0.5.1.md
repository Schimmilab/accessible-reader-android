# Test protocol 0.5.1

Date: 12 September 2026

## What is new

Three findings from a round of listening feedback.

- **Resuming from the library is now spoken.** Continuing already worked before, but the screen only said „noch nicht vorbereitet“ and the jump buttons were off. Someone who cannot see concludes from this that the app has forgotten the position. The opening message now names the section where it continues.
- **A scan is rejected after a sample, not at the end.** Previously the app read a 500-page scan up to the last page before admitting that there is no text in it. Now 25 pages without a single character are enough, and the message names the missing text recognition.
- **Print typesetting is cleaned up beforehand.** A 524-page book read its page numbers aloud in the middle of a sentence and broke every hyphenated word. The cause was soft hyphens and, in another book, 302 occurrences of the old character `¬` as a hyphen.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.5.1, version code 12, release build.
- TalkBack turned off for the automated runs.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `application-debuggable` | not present, as it must be |
| Version in the package | 0.5.1, code 12 |
| Size | 19.1 MB |

## Real books

Six books were sent through the importer with `RealBooksTest`. The run is in the log under the tag `ReaderBooks`.

| Book | Pages in the PDF | Sections | Characters | Import time |
| --- | --- | --- | --- | --- |
| Novel, short | 100 | 15 | 213,026 | 7.2 s |
| Non-fiction, large | 524 | 60 | 827,204 | 13.7 s |
| Novel, long | 458 | 458 | 669,970 | 17.7 s |
| Technical book | 272 | 272 | 481,720 | 4.6 s |
| Non-fiction | 194 | 194 | 690,714 | 13.4 s |
| Brochure | 8 | 8 | 47,360 | 0.5 s |

All six readable, in none of them did a hyphen remain. Two of the books would not have passed the old limit of 300 pages.

## Automated tests

| Run | Result |
| --- | --- |
| JVM tests (`ReaderCoreTest`) | 16 of 16 passed |
| `PdfImportTest` | 4 of 4 passed |
| `LibraryResumeTest` | passed, proves the resume |
| `ReaderUiTest` | 2 of 2 passed |
| `RealBooksTest` | passed, 6 of 6 books readable |
| Lint | without errors |

## Smoke test of the release build

Release APK installed, app started, user interface read out. All controls present, no crash in the log.

## What stays open

- **Text recognition for scans is missing.** Most of the test reader's books are scanned. The way without a cloud is ML Kit on the device, see below.
- **Two-column pages mix their columns.** In the brochure the text of the neighbouring column appears in the middle of a sentence. The importer reads by position, but without column detection.
- Headers that repeat on every page are not detected yet. The books that were checked had none, in specialist books they are common.
- Switching the section with a media key needs the app in the background.
