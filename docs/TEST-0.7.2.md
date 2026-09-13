# Test protocol 0.7.2

Date: 13 September 2026

## What is new

The next section is now synthesized into the audio cache while the current one is still playing, so a section change no longer falls silent while the reader waits for audio that does not exist yet.

## A measurement that was wrong

0.7.1 reported "8 to 12 seconds of silence" between sections. That number was not a silence. The test timed how long it took for the section index to change again, and that interval contains the audio of the section itself. The right way to measure is to watch playback stop and start.

Measured properly, with sections long enough for the result to mean anything:

| Voice | Silence at a section change, before | after |
| --- | --- | --- |
| Google, stock | 9011 ms | 307 to 1019 ms |
| Piper Thorsten, local neural | 21181 ms | 145 to 8581 ms |

So the problem was real and in fact worse than the protocol claimed, but the figure in that protocol was not evidence for it. The wrong number also went into the 0.7.1 release notes and to the test readers, and both have been corrected.

## Why the neural voice stays slower

It synthesizes at roughly the speed of playback, between 0.6 and 1.2 times real time on this emulator. A voice with no spare capacity cannot work ahead, whatever the code does. The stock voices run at about a thirtieth of real time and have room to spare. That is why the guard in `PageSectionsTest` is strict for the stock voice and only catches a total regression for the neural one.

## How it works

`ReaderViewModel.prefetchNext` synthesizes the opening of the next section, up to `MIN_LEAD_MS` of audio, after the current section is fully prepared and only while playback is actually requested. It writes nothing to the player and nothing to the state. The audio cache is keyed by provider, engine, voice and text, so the later `play()` simply finds the pieces already there. In the worst case it is wasted work, never a wrong result.

It deliberately stays out of `ReaderState.preparing`, because that flag gates automatic continuation to the next section. Setting it there would stop the very thing this exists to smooth.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.7.2, version code 18, release build.
- Speech engines: Google, and VoxSherpa with the Piper voice de_DE-thorsten-high.
- TalkBack turned off for the automated runs.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| `application-debuggable` | not present |
| Version in the package | 0.7.2, code 18 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 25 of 25 passed |
| `PageSectionsTest` | 2 of 2, both voices, and it fails without the prefetch |
| `ProgressivePreparationTest` | 4 of 4 |
| `ReaderUiTest` | 2 of 2 |
| `LibraryResumeTest` | passed |
| Lint | no errors |

## What stays open

- A section change with a local neural voice can still take several seconds. That is the voice running at real time, not the code.
- Eight voices on the target device are announced by the engine but have no data, and the Android voice screen does not offer them for download at all. Nothing this app can do; it hides them, which is correct.
- Whether the grouping and the prefetch together fix what the test reader described has not been confirmed on her device.
