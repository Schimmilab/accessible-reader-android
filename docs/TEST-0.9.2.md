# Test protocol 0.9.2

Date: 15 September 2026

## What is new

A listener reported that a book stopped after about 25 pages with the message that the voice had needed more than sixty seconds and she should pick another one. She pressed for the next section, heard the announcement "Seite 25 bis 33", heard a little of the book, and then it stopped again. Her own summary was that she must be doing something wrong.

She was not. The app was.

## What it was

The app speaks short feedback over a second connection to the speech engine while the first one is turning the book into audio. Google, Vocalizer and Acapela serve both at once. An engine with a single native synthesizer, such as the local neural voice she had chosen, does not: the book's audio waits behind the sentence of feedback.

Measured on an emulator with that engine, the same piece of text:

| | |
| --- | --- |
| synthesis alone | 12.1 s |
| synthesis while an announcement is spoken | 40.1 s |

A piece near the synthesis budget then runs past it, and the app stops the section with a message about the voice being too slow. Which is true in a sense, and useless: the listener cannot act on it, and she had already chosen the only neural voice she has.

Both now go through one lock. A spoken announcement waits for the piece being synthesized and holds the lock until its own sentence is out, so nothing overlaps. On a fast engine the delay is imperceptible; on a slow one the announcement arrives a few seconds later than before, which is the correct trade.

| | |
| --- | --- |
| synthesis while an announcement is spoken, after the fix | 12.1 s |

That is exactly the time the synthesis takes with no announcement at all.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.2, version code 23, release build.
- Speech engines: Google, and VoxSherpa with the Piper voice de_DE-thorsten-high.

## Automated tests

| Run | Result |
| --- | --- |
| `AnnouncementCollisionTest` | passed, and it failed before the change |
| Unit tests (`ReaderCoreTest`) | 28 of 28 passed |
| `SlowVoiceTest` | 2 of 2, the whole flow on the neural engine |
| `PageSectionsTest` | 2 of 2 |
| `ProgressivePreparationTest` | 4 of 4 |
| `ReaderUiTest` | 3 of 3 |
| Lint | no errors |

## What stays open

- The neural voice is still slower than listening on that device, and long sections will still take their time. The app says so under the voice list, in words per minute.
- A piece that genuinely exceeds the budget still stops the section. With the collision gone that should be rare, but it has not been proven on the listener's own device.
- The message for a timeout names the voice and suggests another one. It does not offer to switch.
