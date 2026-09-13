# Test protocol 0.8.0

Date: 13 September 2026

## What is new

**The book keeps reading when the app is swiped away.** Playback of the current section already survived, because the service outlives the activity, but the section after it was nobody's job: the ViewModel owned preparing it, and the ViewModel dies with the activity. So a book stopped wherever the listener happened to be.

The section loop moved out of the ViewModel into `playback/SectionPreparer`, and the service now uses the same one. It reconstructs everything from the current media item, which already carries the document id, the section and the voice, and reads the text from the same store, so nothing has to be handed across. The ViewModel marks itself alive in a preference and the service only steps in when that flag is gone, so the path with a living app is untouched.

**Books of several thousand pages.** The limits went from 3000 pages to 10,000, and from six to twenty million characters, after a 3700-page book was refused.

**The reading speed can be aimed at.** A step was a quarter, so from normal the next one down was noticeably slow and the next one up noticeably fast, with nothing in between. A step is now a tenth, and every change is spoken, because a screen reader keeps its focus on the button it pressed and never reads the value that changed.

## Measured

The book that prompted the higher limits, in its real size:

| | |
| --- | --- |
| pages | 3700 |
| extracted characters | 7,375,140 |
| import | 148 s |
| memory in use afterwards | 74 MB of a 192 MB heap |
| sections after grouping | 617 |

Continuing without the app, the test destroys the activity, which is what swiping away does to the ViewModel:

| | |
| --- | --- |
| before | the stored position stayed on section 1 for the full three minutes of the test |
| after | section 2 starts on its own and the stored position follows |

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.8.0, version code 20, release build.
- TalkBack turned off for the automated runs.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| `application-debuggable` | not present |
| Version in the package | 0.8.0, code 20 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 28 of 28 passed |
| `KeepsReadingWithoutTheAppTest` | passed, and it failed before the change |
| `LargeBookTest` | passed, 3700 pages with 7.4 million characters |
| `PdfImportTest` | 5 of 5 |
| `ProgressivePreparationTest` | 4 of 4 |
| `ResumePositionTest` | passed |
| `PageSectionsTest` | 2 of 2 |
| `ReaderUiTest` | 3 of 3, including the new speed control |
| `MediaButtonTest` | 2 of 2 |
| `LibraryResumeTest` | passed |
| Lint | no errors |

The extraction of the section loop was done first and the whole suite run against it before the service was touched, so a regression there would have shown up on its own.

## What stays open

- Chapter changes by media key still go through the app. With the app gone the book continues section by section, but the headset buttons for the next section have no one to ask.
- A 3700-page book takes about two and a half minutes to import. The progress is announced throughout.
- A table next to a paragraph still reads interleaved.
- EPUB is not supported. It would be an easier input format than PDF, with real chapters and no page furniture to strip.
