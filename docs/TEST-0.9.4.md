# Test protocol 0.9.4

Date: 16 September 2026

## What is new

**The headset buttons for the next section work without the app.** Since 0.8.0 a book keeps reading when the app is swiped out of the recents list, but the buttons for moving a section still asked the app, which is not there. So on a book that was otherwise reading fine, those buttons went nowhere. The service now answers them itself, through the same code it already uses to prepare the next section.

While the app is there it keeps answering, because it also has to move its own screen along.

## Two things found while testing it

Neither was the thing being worked on. Both came out of reading the log of a passing test rather than only its result.

**The engine sometimes answers with an empty file.** Right after another client of it shuts down, which is exactly what the app being swiped away does mid-book, `synthesizeToFile` reported success and produced nothing. The service then logged that the next section could not be prepared. A later attempt succeeded, so the tests passed and the book advanced, but on a real device that is a section that fails to start for no reason a listener could act on. Synthesis now tries twice, and the second attempt produced the audio every time this appeared.

**A `CancellationException` is an `IllegalStateException`.** The retry above was written as `catch (e: IllegalStateException)`, which quietly made it retry cancellations as well: a listener pressing for another section would have waited through the old one first. The log of the very first run showed `Zweiter Versuch: Job was cancelled`, which is how it was caught.

This is the second time this exact relationship has cost this project a defect. The first was a timeout being swallowed as a cancellation, which turned a slow voice into an app that sat there silently. It is now written down as a runnable test rather than a comment.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.4, version code 25, release build.

## Automated tests

| Run | Result |
| --- | --- |
| `KeepsReadingWithoutTheAppTest` | 2 of 2, continuing and the headset button, both with the app destroyed |
| Unit tests (`ReaderCoreTest`) | 29 of 29 |
| `ProgressivePreparationTest` | 4 of 4 |
| `ResumePositionTest` | passed |
| `PageSectionsTest` | 2 of 2 |
| `SlowVoiceTest` | 2 of 2 |
| `MediaButtonTest` | 2 of 2 |
| Lint | no errors |

The service log was checked for warnings as well as the test results, which is the only reason the empty file was noticed at all.

## What stays open

- A book whose contents are only page numbers still gets sections called "Teil 1", "Teil 2".
- Tables next to a paragraph still read interleaved, in both formats.
- The user interface is German.
- Why the engine returns an empty file at that moment is not understood, only handled.
