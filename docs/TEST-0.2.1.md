# Test protocol 0.2.1

Date: 11 September 2026

## Fault and correction

In the first manual run with TalkBack on the emulator an audible pause appeared after the spoken chapter announcement, before the actual text started. The logcat of the run documents it for the sample reading „Ankommen": playback from 21:50:46, the announcement ending at position 3048 ms and state STOPPED at 21:50:49.6, then 2.2 seconds of silence, the text part from 21:50:52.

The cause was the start mechanism from 0.2.0: playback started as soon as part 0 was available. Part 0 is, however, the chapter announcement, which is only about three seconds long. The player played it before the first text part was synthesized, ran dry and had to wait.

Correction: playback only starts once at least `MIN_LEAD_MS` (12 seconds) of audio is available from the start position, or all parts are finished. That way at least the first text part is always prepared before the start. The remaining parts are still appended while the listener is listening.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64, Google Play image.
- TalkBack 17.0.0.889642762, local German Android voice.
- App 0.2.1, version code 4, sample reading with three sections.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.

## Results of the automatic checks

| Check | Result |
| --- | --- |
| Build the debug app and the test app, Android Lint | passed, Lint without errors |
| Six core logic tests (JVM) | passed |
| ProgressivePreparationTest, three checks, new: no start on the announcement alone (the duration at the start is above the minimum lead) | passed |
| PlaybackFocusTest, ReaderUiTest, MediaButtonTest | see below, in the regression run |

The new test checks the correction directly: at the transition into the state "playing", more than the minimum lead must already be prepared, so more than the three seconds of the announcement. On the emulator the synthesis of one part takes about 30 seconds, which is why the start is still slow there. That is emulator behaviour, not an app fault; on real hardware the synthesis is faster than playback.

## Limits of the emulator

The first run also showed a slow cold start and stuttering operation. The logcat attributes that to the ARM-translated emulator: „Skipped frames", „too much work on main thread" and an app start of 16 seconds. These points can only be judged on a real Android device.

## Still open

- The manual TalkBack run from [TEST-0.2.md](TEST-0.2.md) on a real Android device. Only there can the start speed, the fluidity of pause and resume and the behaviour of the headphone buttons be judged fairly.
- Check whether the sluggishness when pausing and resuming disappears after the correction.
- The listening test by the test reader.
