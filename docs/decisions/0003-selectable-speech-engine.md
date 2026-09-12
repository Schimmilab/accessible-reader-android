# Decision 0003: The speech engine is selectable, and voices are optional

Date: 12 September 2026

Status: accepted

## Context

The diagnosis report from the test reader's Galaxy S25 (0.2.3, 12 September 2026):

```
Voreingestellte Sprachmaschine: es.codefactory.vocalizertts
Installierte Sprachmaschinen: Google (com.google.android.tts),
  Acapela TTS (com.acapelagroup.android.tts), Vocalizer TTS (es.codefactory.vocalizertts)
Deutsche Stimmen: 0, davon offline nutzbar: 0
```

Zero voices, although the user listens to Vocalizer in German with TalkBack every day. The app was therefore unusable on her device: without a voice, reading aloud refuses to work.

The cause lay in two assumptions that held only on the development emulator with Google TTS:

1. The app always took the speech engine preset by the system.
2. The app queried voices exclusively through `TextToSpeech.getVoices()`, which has existed since API 21.

Licensed speech engines such as Vocalizer by Code Factory and Acapela often serve only the older interface through `isLanguageAvailable` and `setLanguage`. They report no individual voices, but they do handle the language perfectly well.

## Decision

- The speech engine is selected in the app, not taken over from the system. All installed engines are available for selection, and the selection is stored.
- If an engine reports no individual German voices, the app checks the older language query. If German is available, it offers an entry „Standardstimme dieser Sprachausgabe" and sets the language instead of a voice during synthesis.
- The cache key contains the engine actually in use, so that audio from two engines is not mixed up.
- The self diagnosis checks every installed engine individually: startup, availability of German, reported voices and whether audio can be written to a file.

## Consequences

The app no longer depends on the preset engine being the one that serves the modern interface. It works with an engine that offers only a single nameless German voice.

A welcome side effect: if the user later buys a better voice as its own speech engine app, that voice appears in the selection automatically. She stated exactly that as a wish, because Acapela becomes exhausting after listening for a longer time.

It remains open which of the three engines on her device is allowed to write audio to a file. Only the next report answers that. If none of them can do it, the reader needs a second playback path that speaks directly instead of producing files; in that case, however, precise jumps and resuming at the listening position in their present form are lost.

## What there was to learn from this

The emulator had exactly one speech engine installed, and it was the one that supports everything. Every assumption about the speech engine that was checked only there is unproven. The same applies analogously to the TalkBack version, Bluetooth and power management, see [0002](0002-target-device-samsung-s25.md).
