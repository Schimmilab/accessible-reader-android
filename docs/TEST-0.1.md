# Test protocol 0.1.0

Date: 10 September 2026

## Environment

Mac mini M1, Pixel 8a emulator with Android 17 and API 37, ARM64 and 16 KB page size. Built with Java 17, Gradle 8.13 and Android SDK 36.

The emulator had to be cold started with `-gpu host`. With the software rendering that had been selected automatically before, ADB and the device reacted very slowly at times. The cold start preserved the installed packages and data.

## Successful checks

| Check | Result |
| --- | --- |
| Build the debug APK | successful |
| Android Lint | no errors, remaining hints about library versions, dependencies and style |
| Core logic, five JUnit tests | passed |
| Import a text PDF with two pages and load it again | passed |
| Report a PDF without text as an OCR case that is not supported yet | passed |
| Open the table of contents and select the second section | passed |
| Find labelled play and jump buttons in the Compose semantics tree | passed |
| Generate German offline audio and reuse it unchanged from the cache | passed |
| Start real playback, pause it, jump to 30 seconds and back | passed, observed target 30,000 ms at a chapter duration of 63,357 ms |
| Recreate the app window and keep operating the player | passed |
| Check the start screen visually in the emulator | passed |

Five instrumented tests ran. The first run found an Espresso test library that was too old and a test that triggered several asynchronous player commands one after another without waiting. Espresso 3.7.0 fixes the Android 17 incompatibility. The playback test now waits for the paused start position before it triggers the next jump. Both user interface tests passed in the corrected run. The other three instrumented tests had already passed.

The emulator offered five local German voices. The audio test used `de-DE-language`. That proves that local audio generation works, not that the sound quality is good enough for the test reader.

## Still open

- Audibility, focus order and interruptions in a practical TalkBack listening test.
- German voice input through the actual Mac microphone. Command recognition from text is tested; microphone and recognition service not yet together.
- Process termination and device start with resume, long-term operation, real headphones and Bluetooth.
- PDF bookmarks with complex hierarchies and different multi-column documents.
- Large font, landscape orientation and several real Android devices.
- Cloud voices, OCR, automatic chapter transitions, library and bookmarks.
- Review of the app license and of all third-party licenses before release.

The version number describes a development prototype. The test reader has not approved it yet.
