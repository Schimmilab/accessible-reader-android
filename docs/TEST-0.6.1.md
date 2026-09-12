# Test protocol 0.6.1

Date: 12 September 2026

## What is new

A listener reported that the app churned for over a minute with the neural Thorsten voice and then nothing happened at all. The stock voices worked. Three findings, all measured.

**A timeout was swallowed in silence.** `withTimeout` throws a `TimeoutCancellationException`, and that is a `CancellationException`. The preparation loop rethrows every cancellation, because that is how the cancel button works. So a synthesis that ran out of time was indistinguishable from a user pressing cancel: no sound, no message, nothing to press. This was the worst defect in the app, because a blind listener has no way to tell the difference between "still working" and "dead".

**Synthesis was assumed to be faster than playback.** That holds for the stock engines by a wide margin and not at all for a local neural voice.

**The announcement voice competes with the book voice.** Two `TextToSpeech` instances are fine for Google, Vocalizer and Acapela, but an engine with a single native synthesizer serves them one after the other.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.6.1, version code 14, release build.
- Speech engines on the device: Google, and VoxSherpa with the Piper voice de_DE-thorsten-high.
- TalkBack turned off for the automated runs.

## Measured: how fast each voice synthesizes

Time to turn text into an audio file, against the length of the audio produced. A factor below 1 means the voice is faster than listening to it.

| Voice | 100 characters | 250 | 500 | 1000 |
| --- | --- | --- | --- | --- |
| Google, de-DE-language | 0.21 | 0.06 | 0.03 | 0.04 |
| Thorsten, neural, local | 1.63 | 0.60 | 0.78 | 0.64 |

The neural voice is roughly as slow as real time, and for a short piece slower. Under load, while playback runs and an announcement is spoken, one 664-character piece took 76 seconds.

## The fix, measured on the same flow

Open the sample document, press read aloud, then switch to the next section, exactly what the listener did.

| Step | 0.6.0 | 0.6.1 |
| --- | --- | --- |
| first sound | 26.4 s | 9.8 s |
| section switch to the next sound | never, and no error within 300 s | 26.2 s |
| rest of the chapter prepared | not reached | passed, still playing |

Pieces now grow instead of being one size: 250 characters, then 500, then 1000. The first sound therefore no longer waits for a full-size piece. The synthesis limit scales with the length of the text instead of being a flat 90 seconds, and a timeout now raises a real error that says which voice was too slow.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| `application-debuggable` | not present |
| Version in the package | 0.6.1, code 14 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 19 of 19 passed |
| `SlowVoiceTest` | passed, drives the whole flow with the neural voice |
| `SynthesisSpeedTest` | passed, times every installed engine |
| `ProgressivePreparationTest` | 4 of 4 |
| `ReaderUiTest` | 2 of 2 |
| `PdfImportTest` | 5 of 5 |
| `LibraryResumeTest` | passed |
| `MediaButtonTest` | 2 of 2 |
| `SpeechAudioTest` | 6 of 6 |
| Lint | no errors |

## What stays open

- A section switch with the neural voice still takes about 26 seconds before the first sound. That is the voice, not the app, but the app could say so instead of leaving a silence.
- The announcement voice and the book voice still share one engine. On an engine with a single synthesizer they take turns, which costs time exactly when a listener is waiting.
- The app does not yet warn that a chosen voice is slower than listening to it, although it now has the measurement to do so.
