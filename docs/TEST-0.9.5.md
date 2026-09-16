# Test protocol 0.9.5

Date: 16 September 2026

## What is new

`docs/TESTING.md` claimed for weeks that the Android Accessibility Test Framework was running against this app. **No such code existed anywhere in the repository.** For an app whose entire purpose is being usable without sight, that is the worst kind of false claim: it says the thing that matters most is being checked, and it is not.

The framework's own Compose binding is not public in the version this project uses, so the rules this project had already written down are now checked directly against the real screen, on the main screen, the contents and the settings:

- every control a screen reader can reach carries a name
- every one measures at least 48 by 48 density pixels
- no two controls share a name, which cost this project a release once
- the screen offers headings, because a screen reader moves by them

## What the first run found

Five controls of 40 density pixels instead of 48:

| Control | Size |
| --- | --- |
| Fertig, the button that closes the settings | 69 by 40 dp |
| Schließen in the contents | 100 by 40 dp |
| Übersicht vorlesen | 168 by 40 dp |
| Alle Befehle und Texteingabe | 248 by 40 dp |

All of them are `TextButton`s. Every `Button` and `OutlinedButton` in this app carries a minimum height; the text buttons were missed, and Material's default for them is 40. Someone exploring the screen with a finger has to land on them without seeing them.

All thirteen text buttons now carry a minimum of 48. Nothing else the rules cover was wrong: 34 controls across three screens, all named, no duplicates, four headings on the main screen.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.5, version code 26, release build.

## Automated tests

| Run | Result |
| --- | --- |
| `AccessibilityRulesTest` | 4 of 4, and two of them failed before the change |
| Unit tests (`ReaderCoreTest`) | 29 of 29 |
| `ReaderUiTest` | 3 of 3 |
| `LibraryUiTest` | passed |
| Lint | no errors |

The check now runs on every push, alongside the import tests. It needs no speech engine.

## What stays open

- Contrast is not checked, nor focus order, nor anything that only appears under a real screen reader. Those still need the manual walk in `docs/TESTING.md`.
- The dialogs for the library, for removing a document and for the error message are not covered yet; the three screens that were checked are the ones a listener is in most of the time.
- Still no testing across manufacturers.
