# Test protocol 0.6.0

Date: 12 September 2026

## What is new

**Scanned books are read.** A page without a text layer is rendered and sent through text recognition. Everything on the device, the recognition model is in the package. The reasoning and the measurements are in [decision 0004](decisions/0004-on-device-text-recognition.md).

**The voice list says which speech engine it belongs to.** A diagnosis report named 17 German voices for one speech engine while the app offered the four of another one, and nothing on the screen distinguished the two. The heading now reads, for example, „Stimmen von Google: 5".

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.6.0, version code 13, release build.
- TalkBack turned off for the automated runs.
- **Wi-Fi and mobile data turned off on the device** for all text recognition runs.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present, although the recognition library brings it along |
| `ACCESS_NETWORK_STATE` in the package | not present |
| `application-debuggable` | not present |
| Version in the package | 0.6.0, code 13 |
| Size | 60.2 MB compared to 19.1 MB in 0.5.1 |

The size is the price for the recognition model and the libraries for all four processor architectures. If it becomes a problem, separate packages per architecture are the next step.

## Text recognition

| Measurement | Value |
| --- | --- |
| per page, render and recognize | 1180 ms |
| 30-page scan, complete import | 28 seconds |
| characters recognized on 10 pages | 16,949 |
| extrapolated to 500 pages | about 10 minutes, once at import |

The render factor was measured instead of estimated. At 764, 900, 1000 and 1200 pixels of page width the recognition delivered practically the same text, while the time per page grew. Rendering therefore uses only what is needed.

A first measurement had produced 251 ms per page. It could not be reproduced and has been retracted.

## Real books

Seven files through the importer, among them a real scan for the first time.

| File | Pages | Sections | Characters | Time |
| --- | --- | --- | --- | --- |
| Scan, images without any text layer | 30 | 30 | 48,384 | 28.4 s |
| Non-fiction, large | 524 | 60 | 827,334 | 44.6 s |
| Novel, long | 458 | 458 | 670,006 | 36.5 s |
| Non-fiction | 194 | 194 | 691,163 | 60.6 s |
| Technical book | 272 | 272 | 481,720 | 20.1 s |
| Novel, short | 100 | 15 | 213,084 | 25.6 s |
| Brochure | 8 | 8 | 47,360 | 1.7 s |

Seven of seven readable. The scan would still have been rejected in 0.5.1.

## Automated tests

| Run | Result |
| --- | --- |
| JVM tests (`ReaderCoreTest`) | 17 of 17 passed |
| `PdfImportTest` | 5 of 5, among them a page whose words exist only as pixels |
| `OcrSpeedTest` | passed |
| `RealBooksTest` | passed, 7 of 7 |
| `VoiceListTest` | passed, app and diagnosis report the same voices |
| `ReaderUiTest` | 2 of 2 |
| `LibraryUiTest`, `LibraryResumeTest` | passed |
| `DiagnosticsTest` | 3 of 3 |
| `MediaButtonTest` | 2 of 2 |
| `PlaybackFocusTest` | 5 of 5 |
| `ProgressivePreparationTest` | 4 of 4 in the second run |
| `SpeechAudioTest` | 6 of 6 in the second run |
| Lint | without errors |

Two classes failed in the first run and not in the second: once a timeout of the speech engine after 90 seconds, once a skipped assumption. Both concerned the emulator's speech engine after the network was switched, not the changed code. It is written down here because a failure kept quiet costs time next time.

## Smoke test of the release build

Release APK installed, device without network, app started, user interface read out. All controls present, no crash in the log.

## What stays open

- **Two-column pages mix their columns.** This applies to text pages as well as scans. The importer reads by position, but without column detection.
- Headers that repeat on every page are not detected.
- A 500-page scan takes around ten minutes to import. The progress is announced, the import can be cancelled.
- Switching the section with a media key needs the app in the background.
- The user interface is German. Multiple languages are the next item.
