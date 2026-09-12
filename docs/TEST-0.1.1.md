# Test protocol 0.1.1

Date: 11 September 2026

## Fault and correction

When the sample reading started with TalkBack switched on, the focused button alternated between „Pause“ and „Vorlesen“. TalkBack announced the state change and by doing so interrupted the book playback again. Before the correction, only about 1.3 seconds of book audio had played about 13 seconds after the start. The Android audio focus log showed repeated requests and releases by TalkBack.

Until now the app did not distinguish between the request to play and audio that was actually running. Now `playWhenReady` and the loading/end state drive the button and the toggle action. A short interruption by TalkBack does not change the button label. The automatic audio focus handling of Media3 stays active. There is no timer that overrides a deliberate pause.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- TalkBack 17.0.0.889642762, German, with touch exploration enabled.
- App 0.1.1, version code 2, local German Android voice, sample reading „Ankommen“ at normal speed.
- Java 17, Gradle 8.13, Android SDK 36.

## Results

| Check | Result |
| --- | --- |
| Build the debug app and the test app | passed |
| Android Lint | no errors, the existing hints remain |
| Five core logic tests | passed, none skipped |
| Four new instrumented tests for audio focus, pause and chapter end | passed |
| Five existing instrumented tests for PDF import, user interface, playback, jumps and audio cache | passed |
| Playback with TalkBack switched on and bound | stable after the initial announcement; five status samples over 20 seconds consistently PLAYING, position from 12,915 to 33,927 ms |
| Install the updated APK on the emulator | successful, without deleting the app data |

The four new focus checks take 36.478 seconds, the five existing instrumented tests 15.480 seconds. The normal instrumented tests do not replace a full TalkBack gesture check.

## Still open

The final manual run with the TalkBack focus reliably on the button, a double tap and pausing again could not be completed, because the emulator was closed while it was running. The stable playback logged above happened with TalkBack active, but the touch input was injected through ADB. That is not full proof of double tap operation by a human.

A further listening test by the developer and later by the test reader is still needed. The correction changes neither voice quality nor cloud connectivity. PDF OCR, real devices and the microphone check stay outside this fix.
