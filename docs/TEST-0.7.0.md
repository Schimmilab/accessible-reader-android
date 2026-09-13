# Test protocol 0.7.0

Date: 13 September 2026

## What is new

The test reader asked whether the app could tell Wi-Fi from mobile data and offer better online voices when it is on Wi-Fi. It can, and the useful part of the answer costs nothing.

**Voices that fetch their audio from the internet are now offered.** The installed speech engine already exposes them. The engine does the network access in its own process, so this app still has no internet permission.

**A setting decides when they may be used**, defaulting to Wi-Fi only. A voice that cannot work right now is refused before anything is prepared, with a sentence that names what to change.

Cloud providers were compared and none is being integrated. The reasoning is in [decision 0005](decisions/0005-online-voices-of-the-speech-engine.md).

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.7.0, version code 16, release build.
- TalkBack turned off for the automated runs.

## Measured

| Question | Answer |
| --- | --- |
| German voices the Google engine exposes | 9, four of which need the internet |
| Can `synthesizeToFile` use a network voice | yes, 1.4 MB of audio in 3362 ms |
| Does the app need `INTERNET` for that | no, verified on the built package |
| Can the app tell Wi-Fi from mobile data | yes, with `ACCESS_NETWORK_STATE`: Wi-Fi, unmetered, validated |
| Same check without that permission | `getNetworkCapabilities` returns null |
| Same check with the radios off | no network, reported cleanly |
| Voices offered in the app | 5 before, 9 now |

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` in the package | not present |
| `ACCESS_NETWORK_STATE` in the package | present, deliberately, reads the state and grants no access |
| `application-debuggable` | not present |
| Version in the package | 0.7.0, code 16 |

## Automated tests

| Run | Result |
| --- | --- |
| Unit tests (`ReaderCoreTest`) | 24 of 24 passed |
| `OnlineVoiceTest` | 2 of 2, marking and refusal |
| `NetworkVoiceTest` | passed, an engine network voice writes a file |
| `ReaderUiTest` | 2 of 2 |
| `SpeechAudioTest` | 6 of 6 |
| `DiagnosticsTest` | 3 of 3 |
| `ProgressivePreparationTest` | 4 of 4 |
| Lint | no errors |

## What stays open

- **Whether these voices actually sound better is a listening question**, not a technical one, and it has not been answered. The test reader has to judge that.
- A network voice can stop mid-chapter when the connection drops. The existing error path covers it, but that has not been tested against a connection that fails halfway.
- Why one device offers five voices where its diagnosis lists thirteen is still open. The report from 0.6.2 will say.
