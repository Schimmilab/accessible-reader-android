# Test protocol 0.10.0

Date: 16 September 2026

## What is new

**A sleep timer.** After 15, 30, 45 or 60 minutes, or at the end of the current section.

Three decisions in it are worth more than the feature itself.

**It lives in the playback service, not in the app.** Someone who sets a sleep timer and puts the phone down is exactly the person whose app is about to go away, and this reader deliberately keeps reading when that happens: the service prepares the next section by itself. A timer held by the app would therefore have been a timer that stops nothing, on the one occasion it was set for. The service keeps the end as a wall-clock time and ticks it in the same once-a-second job that saves the listening position.

**The sound is faded over the last twenty seconds, not cut.** Silence arriving mid-sentence wakes the person the timer was set for, which is the one thing it must not do.

**With no timer set, the volume is forced back up, every second.** A listener left with a silent reader and no way to see why would have no way to find out. It costs one comparison a second and removes a whole class of failure.

The remaining time is on the button itself — "Einschlaftimer, noch 24 Minuten" — so hearing how long is left does not mean opening a dialog and listening through it.

"Am Ende des Abschnitts" had to be taught twice, to the app and to the service, because both of them know how to carry a book into the next section and both therefore had to learn to stop.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.10.0, version code 31.

## Automated tests

| Run | Result |
| --- | --- |
| `SleepTimerTest` (unit) | 6 of 6 |
| `SleepTimerSettingTest` | 2 of 2, without a speech engine; runs in CI |
| `SleepTimerFlowTest` | 2 of 2 with a real voice: the book stops by itself when the time is up and keeps its place, and it stops at the end of a section instead of starting the next one |
| `AccessibilityRulesTest` | 10 of 10, the timer dialog added to the eight screens already checked |
| Everything else in `app/src/test` | passed |
| Lint | no errors |

## What this version carries in total

It is the first version since 0.9.7 meant to reach a listener, and it holds three things:

- **0.9.8**: a page with real columns is read one column at a time. Measured over 332 pages of eight real books, ten pages of a product catalogue are read differently and the other 322 come out character for character as before.
- **0.9.9**: bookmarks, up to twenty per book, set and reached by button or by voice.
- **0.10.0**: this sleep timer.

## What stays open

- No search inside a book.
- A table beside a paragraph still reads interleaved.
- Telling the characters of a novel apart by voice.
- Focus order and everything that only appears under a real screen reader still need the manual walk in `docs/TESTING.md`. Still nothing tested across manufacturers.
