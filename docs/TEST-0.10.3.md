# Test protocol 0.10.3

Date: 16 September 2026

## What is new

**The message a listener gets when the voice cannot keep up, and the wait before it.**

This is the failure that nearly ended the project. The report was:

> "Oh Jürgen, ich glaube irgendwann haue ich den Reader in die Tonne. Also jetzt habe ich 25 Seiten von meinem hier geopolitischen China-Buch da und dann hat er abgebrochen und die Meldung war, Die Seiten konnten nicht geladen werden. Die Stimme hat länger als 60 Sekunden gebraucht… Ich glaube, ich bin blöd."

Two things were wrong with that, and both are fixed.

**The message told her to choose another voice and did not say which.** She had no way to know, and the one thing it also suggested — trying again — is the single thing that cannot help against a voice that is simply too slow. The app has measured every voice it has ever read with, so it now names the ones that kept up:

> Diese Stimme hat für ein Stück Text länger als 60 Sekunden gebraucht und ist für dieses Buch zu langsam. Gut mitgekommen sind bisher: Stimme 2, Stimme 3. Du findest sie unter „Stimme und Einstellungen".

Only voices that have really read something are named. A recommendation the app has not measured would be a guess, and this message exists because the old one left her guessing.

**And she waited twice as long as she needed to.** A timeout was being retried. That retry exists for a different failure — an engine answering with an empty file right after another client of it shut down, where a second attempt costs nothing — but a voice that is too slow is too slow, and trying again makes someone who is already waiting wait the whole budget again. Worst case at the limit the budget allows: five minutes of silence, then five more.

`VoiceTooSlowException` is its own kind for exactly this reason. It is an `IllegalStateException` like every other failure in the speech layer, which is what made it invisible to the retry, and is the third time this project has been caught by that shape of trap. It is written down as a runnable test.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.10.3, version code 34.

## Automated tests

| Run | Result |
| --- | --- |
| `SlowVoiceMessageTest` (unit) | 6 of 6, including that a too-slow voice is an `IllegalStateException` |
| All unit tests | 109, all passed |
| Lint | no errors |

## What stays open

- Whether the voice on the target device keeps up at all is still unknown here; that is what the message is for.
- A table beside a paragraph still reads interleaved.
- The interface is German only.
- Focus order and everything that only appears under a real screen reader still need the manual walk in `docs/TESTING.md`.
