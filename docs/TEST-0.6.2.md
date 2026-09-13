# Test protocol 0.6.2

Date: 13 September 2026

## What is new

From a listening review of 0.6.1. The reader itself worked: a new book was imported and read aloud, and switching sections was called easy. Two things came out of it.

**A message made someone wait four minutes for nothing.** After importing a book the position line read „noch nicht vorbereitet" [not prepared yet]. That sounds like work in progress, so the listener waited, three or four minutes, for an app that was not doing anything. Eventually she pressed read aloud anyway and the book played immediately.

This is the same defect as the library message fixed in 0.5.1, in a different place. The state was correct and the wording was not. The line now says what to do instead of what is missing: „Vorlesen drücken" [press read aloud], and for a screen reader the full sentence „Für diesen Abschnitt ist noch kein Audio da. Vorlesen drücken, dann wird es erzeugt."

**A diagnosis report and the app disagreed about the number of voices.** The report from that device lists thirteen offline German voices for the Google engine, the app offers five. Nothing on either side said why, which leaves everyone guessing. Two changes, neither of them a guess about the cause:

- The diagnosis now says, for every voice, whether the reader can use it, and lists the voice features the engine reports. One report from that device will now answer the question.
- Opening the settings asks the engine for its voices again. An engine may report only its built-in voices right after starting and its downloaded ones a moment later, which would explain the difference without any of the more exotic theories.

The app also skips a voice the engine marks as `notInstalled`, which it previously would have offered and then failed on.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.6.2, version code 15, release build.
- TalkBack turned off for the automated runs.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| `application-debuggable` | not present |
| Version in the package | 0.6.2, code 15 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 21 of 21 passed |
| `ReaderUiTest` | 2 of 2 |
| `DiagnosticsTest` | 3 of 3 |
| `LibraryUiTest` | passed |
| Lint | no errors |

## What stays open

- **Why that device offers five voices instead of thirteen is not yet proven.** The next diagnosis report from it will say, because the report now names the reason per voice. Until then this is not a fixed bug, only a better question.
- The newer Google voices that belong to the assistant are not available to any app through the Android speech interface. That is not something this app can change; only voices an installed speech engine exposes can be used.
- A section switch with a local neural voice still takes about 26 seconds before the first sound, and the app does not say so.
