# Test protocol 0.9.3

Date: 16 September 2026

## What is new

A copy-protected EPUB used to be reported as damaged. That is the wrong thing to tell someone: a damaged file sends a listener looking for a broken download, asking for it again, wondering what went wrong with the transfer. The file is fine. It is locked.

Telling the two apart needed care, because a `META-INF/encryption.xml` does not mean copy protection. **Two books in the test collection carry one and read perfectly**, because the entries name the IDPF or the Adobe scheme, both of which only scramble the embedded fonts. Calling those books protected would have been just as wrong in the other direction.

`Epub.encryptedPaths` therefore ignores those two algorithms and reports the rest. If any document of the reading order is genuinely encrypted, the book is refused before anything is read, with a message that says what it is and where the book can be read instead. A book with no readable text at all and any encryption present gets the same message.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.3, version code 24, release build.

## Measured

| Book | Encryption | Result |
| --- | --- | --- |
| Sleep book, real | fonts, IDPF scheme | 25 sections, 283,316 characters |
| Health book, real | fonts, IDPF scheme | 48 sections, 527,011 characters |
| Programming book, real | none | 25 sections, 343,819 characters |
| Fixture with an encrypted chapter | content, AES | refused as copy-protected |
| Fixture with a scrambled font | fonts | reads |

## Automated tests

| Run | Result |
| --- | --- |
| `EpubTest` | 11 of 11, including the two font schemes and a percent-encoded path |
| `EpubImportTest` | 6 of 6, including the locked book and the font-only one |
| `RealBooksTest` | 3 of 3 real books |
| Unit tests (`ReaderCoreTest`) | 28 of 28 |
| Lint | no errors |

## What stays open

- The message names the shop's own app as the place to read a locked book. It cannot name which shop, because the file does not say.
- A book whose contents are only page numbers still gets sections called "Teil 1", "Teil 2".
- Chapter changes by media key still go through the app.
- The neural voice remains slower than listening on the reference device.
