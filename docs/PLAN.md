# Project plan

As of 9 September 2026

## Implementation status as of 10 September 2026

The native 0.1.0 scaffold is implemented and installed as a debug APK on the Pixel 8a emulator. It combines text PDF import, PDF bookmarks or page navigation, local Android TTS audio generation with a cache, Media3 playback, 30 second jumps, a table of contents, speed and saved listening positions. The app has TalkBack semantics and its own interface for local German voice commands.

Five logic tests and five device tests cover the core functions. The device test generated audio with a German offline voice, reused the cache, and ran chapter selection and the 30 second jumps. The current scope and the limits are described in the README. The next steps are a practical microphone and TalkBack listening test, a comparison of more natural voices, continuous playback across several chapters and OCR.

The product plan below still describes the full target. It is not a list of features that are already finished.

## 1. Goal

The app is an audio reader for blind people. It opens PDF documents, recognizes their structure and reads them aloud with a voice that is as natural as possible. The whole app can be operated with TalkBack and additionally through voice commands.

The app is meant to be published as an open source project on GitHub. A free local voice provides the basic service. Users can optionally switch on high quality cloud voices.

Android takes precedence. The complete first reader is developed for Android, tested there and improved together with the blind test user. An iOS version is a separate later stage. Only those parts are shared that do not force a compromise on TalkBack, VoiceOver or media control.

## 2. Product principles

### Audio first

The visible interface is not the starting point. Every function is first described as a flow using TalkBack, voice input and headphone buttons. Only after that does the visual presentation follow.

### Test with blind people

The test user should test the prototype while it is still being developed. Automated accessibility tests are not enough. The number of swipe gestures, the order of the elements, understandable feedback and the behaviour while book audio is playing at the same time matter most.

### Listening quality over a long time

A voice is not chosen from a five second sample. The listening test lasts at least two to five minutes and contains:

- narrative passages and dialogue,
- short and long sentences,
- numbers, times, dates and abbreviations,
- foreign language names,
- different speeds from 1.0 up to at least 1.5.

A long run test of at least 30 minutes follows later. It rates emphasis, pauses, pronunciation errors, monotony and listening fatigue.

### Costs stay predictable

The app generates cloud audio section by section and stores it locally. Sections that have already been generated are not computed again. An adjustable monthly limit stops further cloud requests and activates the local voice.

### No mandatory account for the basic function

PDF import, navigation, the local voice and saved positions work without registration. A cloud login is only required when a cloud voice is used.

## 3. Operating concept

### TalkBack

TalkBack reads menus, buttons, states and error messages. For that the app provides clean roles, labels, headings, states and a fixed focus order. Complex gestures get equivalent named actions.

There are no purely graphical buttons and no function that can only be reached by dragging or by a hidden gesture.

### Voice input

Voice input starts from a large button. A later version can support headphone buttons or an optional activation phrase. Permanent listening is not planned for the first version.

The book pauses when a command starts. The app briefly confirms the recognized action and then resumes playback.

Planned commands:

- read aloud, pause and resume
- 30 seconds back or forward
- a freely spoken amount of time back or forward
- next or previous chapter
- open or read aloud the table of contents
- go to a chapter or a page
- announce the current position
- change the reading speed
- set and recall bookmarks
- correct pronunciation
- set a sleep timer

The first version recognizes fixed intents locally. A large language model is not required for that.

### Media control

Android Media3 and MediaSession control playback, pause, jumps and chapter changes. That also makes the lock screen, the notification area, Bluetooth headphones and physical media keys work.

## 4. Document processing

The app builds an internal document model from every PDF:

- title and author, where available
- chapters and subchapters
- paragraphs
- source page and text position
- reading progress and bookmarks
- the link between a text section and the generated audio

Processing happens in this order:

1. Take over existing PDF bookmarks and structure information.
2. Extract embedded text in a sensible reading order.
3. Clean up headers, footers, repeated page numbers and hyphens.
4. Detect missing headings from font size, position and text characteristics.
5. For image PDFs, process the pages locally with OCR.
6. Mark uncertain results clearly instead of presenting an invented table of contents.

Multi column pages, tables, footnotes, formulas and scanned documents are test cases of their own. The app must announce such content or make it skippable.

## 5. Voices

The app gets an interchangeable provider interface. The player and the document processing must not depend directly on Google or on one particular local model.

### Local candidates

#### Kikiri German

Kikiri German is based on the Kokoro architecture. At the moment there is the German male voice Martin and the female voice Victoria. The models are licensed under Apache 2.0. An ONNX build of Martin is about 327 MB.

Before a decision, startup time, memory use, battery load, speed and sound are measured on a mid range Android device.

- Project: https://github.com/semidark/kikiri-tts
- ONNX model: https://huggingface.co/Godelaune/Kokoro-82M-ONNX-German-Martin
- Victoria: https://huggingface.co/kikiri-tts/kikiri-german-victoria

#### Piper

Piper is smaller and faster. The German Thorsten voice needs about 60 to 114 MB depending on the quality level. Piper works as an offline fallback when Kikiri is too slow on a device.

- Engine: https://github.com/OHF-Voice/piper1-gpl
- Thorsten: https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/high

The GPL license of the current Piper engine is reviewed before it is integrated directly. Voice models and engine have separate licenses.

### Cloud candidates

#### Google Chirp 3 HD

Chirp 3 HD is the preferred quality mode. Google offers numerous German voices. Under the current pricing model up to one million characters per month are free, after that usage costs 30 US dollars per million characters. Prices and free quotas can change.

#### Google Neural2

Neural2 is the economy cloud mode. Under the current pricing model up to one million characters per month are free, after that usage costs 16 US dollars per million characters.

- Prices: https://cloud.google.com/text-to-speech/pricing?hl=de
- Voices: https://docs.cloud.google.com/text-to-speech/docs/list-voices-and-types?hl=de

### Provider access

A shared secret API key must never sit inside the Android app. These routes are being examined for the first open source version:

- the user's own account,
- a freely configurable compatible server,
- later a community funded service with daily and monthly limits.

A public community service needs a login, abuse protection, understandable quotas and a clear privacy statement. It is not part of the first prototype.

## 6. Audio generation and cache

The app splits text into short sections at paragraph and sentence boundaries. It generates the current section and prefetches a few following sections.

The cache key takes these into account:

- the cleaned text,
- the voice provider and the voice id,
- speed and further speech parameters,
- the model version.

The cache can be cleared per document or completely. The app shows storage use and estimated cloud costs accessibly.

Jumps in time refer to the audio position. Chapter changes refer to the internal document model. The text position is kept when the voice changes.

## 7. Proposed Android technology

- Kotlin
- Jetpack Compose with complete semantics for TalkBack
- Android Media3 and MediaSession
- Room for the library, progress, chapters and bookmarks
- WorkManager for longer OCR and preparation work
- Android SpeechRecognizer for voice commands, preferably local from Android 12 on
- ML Kit Text Recognition for local OCR
- ONNX Runtime for suitable local voice models

The actual PDF library is chosen after tests with structured, multi column and scanned PDFs.

## 8. MVP

The first usable prototype covers:

- installation as a private APK
- opening a PDF through the file picker and the Android share menu
- a library that can be operated entirely with TalkBack
- play, pause and resume
- 30 seconds back and forward
- next and previous chapter
- showing and reading aloud the table of contents
- announcing the current position
- saving the last position automatically
- setting the speed
- simple voice input for all the functions listed
- one local test voice
- one optional Google cloud voice
- a local audio cache and a cost limit

## 9. Acceptance criteria for the first prototype

The test user can do the following without sighted help:

1. open a PDF file from another app,
2. start and pause reading aloud,
3. jump 30 seconds,
4. change chapter,
5. open the table of contents,
6. find out her current position,
7. give a voice command,
8. close the app and later continue at the same place,
9. tell whether the local voice or a paid voice is currently active,
10. understand and change the cloud cost limit.

No path through the app may end in a dead end for TalkBack.

## 10. Development phases

### Phase 0: Listening test and operating interview

- create identical longer samples of all voices
- the test user rates voices at several speeds
- record the readers she has used so far and her preferred gestures
- document must have functions and behaviour that gets in the way

### Phase 1: Accessible audio player

- use a static test book
- implement TalkBack navigation and MediaSession
- test jumps, chapter changes, position and voice input

### Phase 2: PDF processing

- import text PDFs
- build the document structure
- add OCR for image PDFs
- collect difficult layouts and keep them as regression tests

### Phase 3: Voice providers

- measure the local model candidate on Android
- connect Google Chirp 3 HD and Neural2
- implement the cache, the cost display and hard limits

### Phase 4: Public test version

- decide on a license
- privacy and security review
- reproducible builds and automated tests
- GitHub documentation and accessible bug reports
- APK test distribution, then a decision about the Play Store

## 11. Quality assurance

- manual tests with TalkBack on at least one Google and one Samsung device
- tests with the screen switched off
- tests with Bluetooth headphones and a wired headset
- Accessibility Scanner and automated Compose semantics tests
- listening tests at several speeds
- airplane mode, a poor connection and an exhausted cloud limit
- large PDFs and tight device storage
- resuming after the app is killed and after a restart

## 12. Open decisions

- the final project name
- the target Android versions and the reference devices
- the preferred voices after her listening test
- the license for the app and for a possible community server
- the first PDF library
- the user's own cloud account or a later community service
- APK distribution or the Google Play Store
- the scope of EPUB, Word documents and web pages after the PDF MVP
- the timing and scope of a later native iOS version

## 13. Next concrete steps

1. Generate two to five minute listening samples with identical text.
2. Have the test user rate voices, speed and pronunciation.
3. Record her smartphone model and Android version.
4. Build a small TalkBack capable audio player with voice commands.
5. Go through the first test together without sight.
