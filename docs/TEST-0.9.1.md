# Test protocol 0.9.1

Date: 13 September 2026

## What is new

Six real EPUB books were put through the importer, and every one of them broke the first version of it in a different way. All six are readable now.

| Book | Sections at first | Sections now | What was wrong |
| --- | --- | --- | --- |
| Health book, 755,000 characters | 30 | 70 | Its contents name 565 entries, all pointing into a handful of files by anchor; the importer kept one title per file and threw away 535 chapters |
| Health book, 261,000 characters | 2 | 18 | 54 entries, all with anchors, into four files |
| Programming book, English | 12 | 25 | same |
| Manual, 33,000 characters | 13 | 4 | its entries are shorter than a section, so they are folded |
| Sleep book, 283,000 characters | 27 | 25 | same |
| Health book, 527,000 characters | 1 | 48 | one single file with no usable contents at all, about nine hours in one piece |

Four rules came out of it, and each one exists because a real book demanded it:

- A document is cut where its own contents point into it, anchors included.
- A titled entry starts a new section only once the current one has grown to about ten thousand characters. A book that names 565 entries would otherwise make every "next section" move two minutes, with a spoken section number each time.
- A title that is nothing but a number is a page label the book carries in its contents. "Abschnitt 5: 217" helps nobody, so those become "Teil 5".
- A section far past the target is cut into parts at paragraph boundaries.

**Two of the six carry encryption and still read.** Their `encryption.xml` covers the fonts, not the text. Genuine DRM on the content would still fail, and the message for it is still the generic one about a damaged file.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.1, version code 22, release build.
- Six EPUB books from a private collection, imported through the app's own importer on the device.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| Version in the package | 0.9.1, code 22 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 28 of 28 passed |
| `EpubTest` | 10 of 10, including anchors, folding and numeric titles |
| `EpubImportTest` | 4 of 4 |
| `RealBooksTest` | passed, six real EPUB books |
| `PdfImportTest` | 5 of 5 |
| `ReaderUiTest` | 3 of 3 |
| Lint | no errors |

## What stays open

- A book whose contents are only page numbers gets sections called "Teil 1", "Teil 2". Honest, but of no help in finding a passage again.
- Genuine DRM still produces the generic message about a damaged file.
- Tables and footnotes are read as they come, in both formats.
