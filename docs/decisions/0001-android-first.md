# Decision 0001: Android first

Date: 9 September 2026

Status: accepted

## Context

In the long run the reader should also be interesting for iPhone and iPad. TalkBack and VoiceOver differ, however, in focus, gestures, feedback, media control and testing. A shared user interface would slow down the start and could lead to compromises in accessibility.

## Decision

Android is the lead platform.

- The first usable product is a native Android app.
- Product decisions are checked first with TalkBack and with the way the test reader uses the app.
- The Android MVP is not aimed at a shared iOS codebase.
- Android uses Kotlin, Jetpack Compose, Media3 and the Android accessibility features.
- An iOS version follows later as its own native app with SwiftUI, VoiceOver, AVFoundation and PDFKit.
- Cloud interfaces, test texts, test PDFs and documented operating rules may be shared by both platforms.
- Shared program code is introduced only once a concrete benefit is proven and no platform becomes harder to operate because of it.

## Consequences

The first prototype reaches the test reader earlier. TalkBack can be implemented cleanly without regard for a shared user interface. Android specific features such as MediaSession, local speech recognition and background services can be used directly.

A later iOS version needs its own user interface and media interfaces. That means more development work. In return it can use VoiceOver and the strengths of iOS without regard for Android conventions.

For now the repository keeps the name `accessible-reader-android`. If an iOS app is created later, it can be organised in its own repository or in a parent project.
