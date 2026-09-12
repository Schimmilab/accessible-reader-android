# Decision 0002: The reference device is a Samsung Galaxy S25

Date: 12 September 2026

Status: accepted

## Context

So far development happened exclusively on a Pixel 8a emulator with the Google speech engine and Google TalkBack. The actual device of the test reader was ascertained through her sighted husband, who set the phone up:

- Samsung Galaxy S25, Android 16, One UI 8.5, kernel 6.6.98, nine months old
- TalkBack 16.2.00.12, that is Samsung's own TalkBack branch, not Google's 17 series
- Speech engine: Vocalizer
- Listens mostly through a Bluetooth speaker, rarely through wired headphones

## Decision

The Samsung Galaxy S25 is the reference device. The Pixel emulator remains the fast development environment, but it no longer decides any question on its own.

It follows that:

- No assumption about the speech engine counts as verified as long as it was verified only on Google TTS. In particular it is open whether Vocalizer supports `synthesizeToFile`. The whole audio generation depends on it.
- The app must be able to report its own environment, because no Samsung device is available here. The self diagnosis in the settings exists for that.
- Bluetooth is the normal case, not the special case. Transitions between audio parts, the delay when pausing and the buttons on the speaker are measured against it.
- Samsung's power management shuts down background services aggressively. The known weakness that chapter changes and automatic continued reading need a live ViewModel weighs heavier on this device than on a Pixel.
- A second test device for testing myself should also be a Samsung, not a Pixel.

## Consequences

The emulator can still check logic, accessibility semantics and regressions. It can no longer prove that a feature works on the target device.

⭐ Addendum of 12 September 2026: The diagnosis report is available. All three speech engines installed on the target device, Acapela, Google and Vocalizer, can write audio to files. The file based design is thereby confirmed and a second playback path is not needed. The original reservation read:

It remains open whether Vocalizer is allowed to write audio to files. If the answer turns out negative, the app needs either a selection of the speech engine so that a different engine can be used, or a second playback path without intermediate files. Both will be decided only once the diagnosis report from the target device is available.
