# Test protocol 0.9.9

Date: 16 September 2026

## What is new

**Bookmarks.** Until now a book had one place in it: where you stopped. Someone who hears a passage worth coming back to had no way to keep it.

- *Stelle merken* keeps the place being listened to and says so at once: "Lesezeichen gesetzt: Kapitel 3, Minute 12." A screen reader keeps its focus on the button that was pressed and would never read what changed, so the app has to say it.
- *Lesezeichen* opens the list. Each entry names its section and minute, tapping it continues there, and each has its own *Entfernen*.
- Both are reachable by voice: "Stelle merken" and "Lesezeichen".

Twenty per book, oldest falls off the end.

## The one design decision worth writing down

**Pressing the button twice in the same passage leaves one bookmark, not two.** Without sight you cannot glance at the screen to see whether it worked, so pressing again is what people do. Two entries twenty seconds apart, read out one after the other, are worse than one: the listener now has to tell them apart by ear and cannot.

A bookmark is otherwise nothing new under the hood: it stores the same three numbers the resume position stores — section, part, seconds inside the part — and going back to one hands them to exactly the same machinery that continues a book after the app was closed. Including what happens when the voice has changed since: the part is kept, the seconds are given up, because the same millisecond is a different word in another voice.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.9, version code 30.

## Automated tests

| Run | Result |
| --- | --- |
| `BookmarksTest` (unit) | 8 of 8 |
| `BookmarkTest` | 2 of 2, including surviving the app being closed; runs in CI |
| `AccessibilityRulesTest` | 9 of 9, the bookmark list added to the seven screens already checked |
| `ReaderCoreTest`, `PageColumnsTest`, `NarrationTest`, `ContrastTest` and the rest | all passed |
| Lint | no errors |

## What stays open

- No search inside a book.
- A table beside a paragraph still reads interleaved.
- Telling the characters of a novel apart by voice.
- Focus order and everything that only appears under a real screen reader still need the manual walk in `docs/TESTING.md`. Still nothing tested across manufacturers.
