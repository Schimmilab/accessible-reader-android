# Testing on the development machine

> German strings quoted in this document are the app's own wording. The README section "Another language" translates the ones that recur.

Status: 10 September 2026

## Goal

We already test the app on the development machine as an audio interface. A sighted developer can find many barriers that way. He cannot fully reproduce the experience of a blind person, though. The blind test user therefore checks every usable milestone on her own smartphone.

## Current state of the Mac

Installed are Android Studio for Apple Silicon, SDK 36 and 37, Platform Tools and a Pixel 8a emulator with Android 17, API 37, Google Play and 16 KB page size. The build uses the existing Java 17 JDK. A real Android device is not connected yet.

TalkBack is set up in the emulator. An English offline speech model was installed for Voice Access. That does not confirm German speech input. The app checks its own local speech recognition independently of Voice Access. Passing the Mac microphone through still has to be verified in practice for a complete speech test.

Android Studio is the simplest working environment for this project. It brings together the management of SDK, emulators, log output and accessibility testing.

## One-time setup

1. Install Android Studio.
2. Install the Android SDK, Platform Tools and the emulator.
3. Create a virtual Pixel device with a Google Play system image.
4. Update the Android Accessibility Suite and TalkBack in the emulator.
5. Check the emulator audio output through speakers or headphones.
6. Switch on the use of the Mac microphone in the extended emulator settings.
7. Activate TalkBack and set up a quick on and off switch.

A Google Play system image matters because pure AOSP images do not always contain the same Google services and accessibility components as a widely used end device.

## How testing without sight works

### Round 1: TalkBack with a visible screen

The developer watches focus, labels and order. The checks are:

- Is every control announced exactly once?
- Does TalkBack announce type and state correctly?
- Is the order logical?
- Are there unnamed icons or empty focus targets?
- Are changes such as pause, chapter change and cost mode audible?
- Does TalkBack interrupt or overlay the book audio unpleasantly?

### Round 2: Do not look at the screen

The emulator stays open, but the developer looks away or covers the screen. The task is completed with TalkBack, keyboard and sound only.

Mandatory tasks:

1. Start the app.
2. Open a test document.
3. Start reading aloud.
4. Jump back 30 seconds.
5. Switch to the next chapter.
6. Open the table of contents.
7. Have the current position announced.
8. Set a bookmark.
9. Close the app and resume at the same place.

Every dead end, unclear announcement and unnecessary swipe gesture is noted as a defect.

### Round 3: External keyboard

TalkBack supports an external keyboard. With the extended key layout the command key acts as the TalkBack key on a Mac keyboard. That makes it possible to test focus movement, activation, headings and media playback without a mouse.

The TalkBack version active in the emulator is what counts, because Google keeps developing the key assignment. The built-in TalkBack keyboard help shows the current assignment.

### Round 4: Speech input

The app pauses the book audio before every voice command. The emulator uses the microphone of the Mac. The checks are:

- quiet and normal speaking volume,
- background noise,
- commands while TalkBack is speaking,
- similar commands such as "vor" and "vorlesen" [forward and read aloud],
- unknown commands,
- missing internet connection,
- safe resumption of playback.

Microphone access is switched off by default in the Android emulator. It is enabled under the extended settings at "Microphone" with "Virtual microphone uses host audio input".

## Test on a real Android smartphone

The emulator checks the logic, but it does not replace a real device. On a smartphone the TalkBack version, the manufacturer interface, battery management, TTS services, Bluetooth and microphone behaviour all differ.

The first prototype is installed as a debug APK over USB or Wi-Fi. Testing happens at least on:

- a Google device or a largely unmodified Android device,
- a Samsung device, if one is available,
- the smartphone of the test user.

On the real device, screen off, headphone keys, incoming notifications, phone calls, Bluetooth interruptions and longer run times are checked.

## Automated tests

Jetpack Compose provides a semantics tree for every interface. Tests can therefore check whether an element is recognized as a button, a heading or a status, and whether a matching action exists.

At least one test is created for every core function:

- Play and pause have a label, a role and a state.
- Forward and back name the jump distance.
- Chapters have heading semantics.
- The table of contents has a fixed reading order.
- The cloud mode names cost status and limit.
- Errors appear as an audible message and receive the focus.
- No core action is reachable only by a gesture.

In addition the Android Accessibility Test Framework is enabled. It detects, among other things, missing labels, targets that are too small, contrast problems and parts of the wrong focus order.

## Listening test for voices

All voices get the same text and the same comparison conditions. File names and spoken introductions must not judge the voice positively or negatively in advance.

Rating scale from 1 to 5:

- Naturalness
- Intelligibility
- Emphasis
- Pauses
- Pronunciation of numbers and abbreviations
- Behaviour at 1.25 times and 1.5 times speed
- Effort after longer listening

The first selection is made with sample readings of two to five minutes. The favourites are then listened to for at least 30 minutes in one go.

## Limits of testing by sighted people

"Eyes closed" is a defect finding method, not a simulation of blindness. Sighted people often already know the screen layout and can memorize positions. Blind people also bring much more experience with TalkBack, gestures, braille displays and high speaking rates.

Therefore the following applies:

- We fix obvious defects before her test.
- We do not explain the operating path to her in advance when we are testing how findable it is.
- We observe only with her consent.
- Her feedback decides when assumptions contradict each other.

## Test protocol per version

The following is recorded for every test APK:

- Version number and commit
- Device, Android version and TalkBack version
- Active voice and speed
- Tested document
- Passed and failed tasks
- Number of focus movements needed for core tasks
- Speech errors and audio artefacts
- Open questions for the test user

## Next technical step

The 0.3.0 scaffold plays after a minimum lead, announces chapters, keeps reading at the end of a chapter, controls chapters through media keys, keeps a library and can check its own speech engine. Test notes are in [TEST-0.1.md](TEST-0.1.md), [TEST-0.1.1.md](TEST-0.1.1.md), [TEST-0.2.md](TEST-0.2.md), [TEST-0.2.1.md](TEST-0.2.1.md), [TEST-0.2.2.md](TEST-0.2.2.md), [TEST-0.3.md](TEST-0.3.md) and [TEST-0.4.2.md](TEST-0.4.2.md).

The next manual round checks operation with TalkBack without looking at the screen, real speech input through the Mac microphone, and after that the assessment by the test user on a smartphone. The checklist for it is in [TEST-0.2.md](TEST-0.2.md).

Note for device tests: The emulator produces one audio part in roughly 30 seconds, so slower than playback. Tests that jump to the end of a chapter have to wait for the end of the preparation first. On a fresh installation, a click on "Vorlesen" [read aloud] first opens the notification dialog of Android 13 and newer.

## TalkBack and automated tests are mutually exclusive

⛔ TalkBack must be switched off before an automated test run.

With TalkBack active it reads out every change of the status line and holds the audio focus for it. The playback tests then wait correctly, but for minutes, and run into their time limits. Measured on 12 September 2026: the same four checks take 43 to 79 seconds with TalkBack switched off, while with TalkBack active two of them ran into the timeout after 240 seconds. In the log of the speech engine there are gaps of 79 and 194 seconds in which the app requests no synthesis at all.

That is not a defect, it is exactly the intended behaviour. It only makes the two kinds of check incompatible.

```sh
# before an automated run
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services null

# afterwards, switch it back on for manual testing
adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

The rule from the section on regression therefore stands and only becomes concrete: do not start UI automation during a TalkBack round, and the other way around.

## Regression: TalkBack and reading aloud

`PlaybackFocusTest` checks with a real Media3 player and local German audio data:

- A transient loss of audio focus does not turn the pause button into a read aloud button.
- After the end of the interruption the player resumes playback.
- A pause pressed during the interruption is kept.
- A permanent loss of focus does not restart on its own after the focus is released.
- The end of a chapter offers a restart.

After that, test separately with TalkBack enabled: focus the "Vorlesen" button, activate it with a double tap, listen for at least 20 seconds without further input, pause again with a double tap and check that the pause is kept. The button has to stay "Pause" during a short menu announcement. A normal Compose click test does not replace this round. Do not start UI automation during the gesture test, because it suppresses other accessibility services.
