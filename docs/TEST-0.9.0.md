# Test protocol 0.9.0

Date: 13 September 2026

## What is new

**EPUB books can be read.** An EPUB states its reading order and nearly always its chapters, which makes it a friendlier source than a PDF in every way that has cost this project releases: no page numbers to strip out of the middle of sentences, no hyphens broken across pages, no text recognition, and a real table of contents instead of a guess.

The parser is pure text in and text out, so it runs without a device. It handles both shapes that exist in the wild, EPUB 3 with a navigation document and EPUB 2 with an NCX, skips anything the book marks as outside the reading flow such as a cover, and drops scripts, styles and comments. A book without a table of contents has its parts grouped the same way pages are.

An EPUB has no pages, so the screen says nothing about them. The twelfth file in an archive is not page twelve, and saying so would be an invention.

**Every voice keeps its own speed.** A listener reported that one neural voice speaks much faster than the others and had to be slowed down, while the stock voices were right at normal.

## Measured: voices do not speak at the same rate

Same text, same setting, on one emulator:

| Voice | 600 characters become | Words per minute |
| --- | --- | --- |
| Google de-DE-language | 41.8 s of audio | about 132 |
| Piper Thorsten | 27.2 s of audio | about 204 |

So the report was right and it is not a fault in the reading: that voice simply speaks half again as fast at the same setting. One speed for all voices therefore means that changing the voice silently changes how fast the book is read. Each voice now keeps its own, and the app shows the measured rate under the voice list, in words per minute, which is the unit the listener herself used.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.0, version code 21, release build.
- TalkBack turned off for the automated runs.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| `application-debuggable` | not present |
| Version in the package | 0.9.0, code 21 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 28 of 28 passed |
| `EpubTest` | 6 of 6, the parser without a device |
| `EpubImportTest` | 3 of 3, including a book without a table of contents and a broken file |
| `SpeedPerVoiceTest` | passed, each voice keeps its own speed |
| `VoiceSpeedTest` | 2 of 2, the rate is shown and only a slow voice is warned about |
| `PdfImportTest` | 5 of 5 |
| `ReaderUiTest` | 3 of 3 |
| `ProgressivePreparationTest` | 4 of 4 |
| `KeepsReadingWithoutTheAppTest` | passed |
| Lint | no errors |

## What stays open

- Only text in the EPUB is read. Images with captions, tables and footnotes are still read as they come.
- DRM-protected EPUB files cannot be opened, and the message for them is the generic one about a damaged file.
- A table next to a paragraph still reads interleaved, in both formats.
- Chapter changes by media key still go through the app.
