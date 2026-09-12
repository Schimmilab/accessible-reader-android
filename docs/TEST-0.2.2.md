# Test protocol 0.2.2

Date: 11 September 2026

## Fault and correction

The spoken announcements of the app were silent while audio was being prepared in the background. Affected were „Inhaltsverzeichnis vorlesen“ and „Wo bin ich? Position vorlesen“.

Cause: `AndroidSpeechProvider` used a single TextToSpeech instance for two jobs. One instance cannot speak and write a file at the same time, so `say()` had a lock that discarded every announcement while a synthesis was running. Up to 0.1.1 this went unnoticed, because all buttons were locked during the preparation. Since the progressive preparation in 0.2.0 the buttons can be used, and that turned the lock into a visible fault: the user would have pressed a button and heard nothing.

Correction: a second TextToSpeech instance only for short feedback. It is independent of the file synthesis and speaks in the selected book voice.

Fixed in addition: with TalkBack running, the position announcement was emitted twice, once by TalkBack through the live region and once by the app. Now `screenReaderActive()` decides which of the two speaks.

## New: a voice preview for each voice

Until now the voices were only called „Deutsch 1 · Deutschland“. Without sight they could not be told apart that way. In the settings every voice now has a „Probe“ button that speaks the same text for all voices, with numbers, a date and abbreviations, as the listening test in [PLAN.md](PLAN.md) requires. Selection and voice preview are two separate focus targets, so that TalkBack does not get stuck inside a nested button.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64, Google Play image.
- TalkBack 17.0.0.889642762, local German Android voice.
- App 0.2.2, version code 5.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.

## Results of the automatic checks

| Check | Result |
| --- | --- |
| Build the debug app and the test app, Android Lint | passed, Lint without errors |
| Six core logic tests (JVM) | passed |
| SpeechAudioTest, two checks, new: an announcement starts while file synthesis is running | passed |
| ProgressivePreparationTest, three checks | passed |
| PlaybackFocusTest, five checks | passed |
| ReaderUiTest, MediaButtonTest, PdfImportTest, two checks each | passed |

The new test is a real regression check: it starts a long synthesis, speaks an announcement in parallel and demands that the announcement starts while the synthesis is demonstrably still running. With the old code it would be red.

## To be checked manually in addition

In addition to the checklist in [TEST-0.2.md](TEST-0.2.md):

| No. | Task | Expectation | Result |
| --- | --- | --- | --- |
| 13 | During the preparation, open „Inhaltsverzeichnis“ and use „Übersicht vorlesen“ | The list is spoken in the book voice, not silent | |
| 14 | With TalkBack, „Wo bin ich? Position vorlesen“ | The position is announced exactly once, not twice | |
| 15 | In the settings, „Probe“ for each voice | The same text in the respective voice, the button is named „Hörprobe für …“ | |

## Still open

- The whole manual run on a real Android device.
- The listening test by the test reader and the voice assessment that the voice preview now makes possible.
