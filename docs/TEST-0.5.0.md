# Test protocol 0.5.0

Date: 12 September 2026

## What is new

Only the delivery, no behaviour. For the first time a release build signed with our own key instead of a debug build.

- Signing key created, RSA 4096, valid until January 2054. It lies outside the repository, the procedure is in [RELEASING.md](RELEASING.md).
- Signature schemes v2 and v3. v3 keeps a later key rotation open.
- Minification deliberately stays off. Media3 and PDFBox work with reflection, and without verified keep rules shrinking would break playback in a build that users cannot debug.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.5.0, version code 11, release build.
- TalkBack turned off for the automated runs.

## Package checks

| Check | Result |
| --- | --- |
| Signature verified | passed, scheme v2 and v3 |
| Certificate matches the keystore | passed, SHA-256 `8ec34b04…32c800d8` |
| `application-debuggable` | not present, as it must be |
| Version in the package | 0.5.0, code 11 |
| Size | 19.1 MB compared to 24.5 MB for the debug build |

## Smoke test of the release build on the device

A release build is a different build type and had never been on a device. So it was checked by hand, not only built:

| Step | Result |
| --- | --- |
| Install and start | passed, no crashes in the log |
| Notification permission on the first tap of „Vorlesen" | appears as expected, then playback starts |
| Open the sample reading | passed, three sections |
| Generate and play audio | passed, six syntheses, the position kept counting up |
| Automatic continuation across all three sections | passed, ended on „Abschnitt 3 von 3" |
| The last chapter stops and offers a restart | passed, the button shows „Vorlesen" |

⚠️ While watching, the status line was misleading at first. It still said „Ankommen. Wiedergabe läuft.", while the reader was long since in the third section. That is intentional: during automatic continuation the status is not changed, because every change of the live region makes TalkBack interrupt the book. What counts is the line „Abschnitt x von y", and that one was correct.

For debugging this means the status text is not a reliable indicator of the current chapter.

## Still open

- ⛔ The signing key exists on only one machine. Without a backup in a second place, losing it makes an update over an existing installation impossible, and every user loses their library when reinstalling.
- 0.5.0 cannot be installed over 0.4.2, because the signature differs. Anyone who has 0.4.2 must uninstall it.
- Minification, see above. It needs its own run on a device.
- Multiple languages for the user interface.
