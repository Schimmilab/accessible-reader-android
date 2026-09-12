# Test protocol 0.3.0

Date: 12 September 2026

## What is new

The library. Until now the app only knew the document that was opened last. A PDF imported earlier was no longer reachable, even though its text still sat in private storage. The MVP list in [PLAN.md](PLAN.md) explicitly requires a library that can be operated entirely with TalkBack.

- All imported documents are listed, newest first.
- Each row names the title, the number of sections and where the user stopped.
- A document can be opened and, after a confirmation prompt, removed.
- Reachable through the „Bibliothek“ button and through the voice command „Bibliothek“ or „Meine Bücher“.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64, Google Play image.
- App 0.3.0, version code 7.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.

## Results of the automated checks

| Check | Result |
| --- | --- |
| Build debug app and test app, Android Lint | passed, Lint without errors |
| Ten core logic tests (JVM), new: label of a library row, voice command | passed |
| LibraryTest, three checks: order, removal, existing document without side file, empty library | passed |
| LibraryUiTest, one check: named rows, confirmation prompt before removal, cancelling keeps the document | passed |
| ProgressivePreparationTest, PlaybackFocusTest, ReaderUiTest, MediaButtonTest, PdfImportTest, SpeechAudioTest | passed |

Total: 21 instrumented tests in eight classes, none skipped.

## A finding from the user interface test

The first run of `LibraryUiTest` failed because two buttons were called „Entfernen“ at the same time, one in the list and one in the confirmation dialog. For a sighted person this is unambiguous, because the dialogs lie on top of each other. When listening it is not. The confirming button is now called „Ja, entfernen“. The test was not adjusted, the user interface was.

## Additional manual checks

In addition to the checklists in [TEST-0.2.md](TEST-0.2.md) and [TEST-0.2.2.md](TEST-0.2.2.md):

| No. | Task | Expectation | Result |
| --- | --- | --- | --- |
| 16 | Import two PDFs one after the other, then „Bibliothek“ | Both appear, the one imported last at the top | |
| 17 | Listen to the first document for a while, open the library | The row names the section listened to last | |
| 18 | Open the other document from the library | Switch without a restart, playback starts from the beginning when reading aloud | |
| 19 | Remove a document, choose „Abbrechen“ in the dialog | The document stays in the list | |
| 20 | Remove the same one and confirm with „Ja, entfernen“ | It disappears from the list, the listening position is gone, the PDF file on the device still exists | |
| 21 | Remove the document that is currently being listened to | The app falls back to the sample reading, without a crash | |
| 22 | Voice command „Bibliothek“ | The library opens | |

## Still open

- The complete manual run on a real Android device.
- The diagnosis report from the target device. It decides whether the audio generation can stay as it is.
- The listening test by the test reader, see [VOICES.md](VOICES.md).
