# Test protocol 0.10.4

Date: 16 September 2026

## What the listener reported, and what it settles

She tested 0.9.3 with a Google voice, not the version sent today.

**The book runs.** Over half an hour of her China book, fluent, no stalls and no losing the place. That is the failure this project has been chasing for two days — the abort after 25 pages with the message about the sixty seconds — and with the announcement fix of 0.9.2 plus a voice that keeps up, it is gone.

Three things she said without being asked, all of them worth keeping:

- **The speed.** *"Die Tempoeinstellung gefällt mir jetzt richtig gut, weil du da einfach nicht mehr so viel schieben musst. Bei dem anderen musste ich schieben, dass das wirklich dann so gepasst hat."* [The speed setting is much better now because you don't have to nudge it so much. With the other one I had to nudge it until it fitted.] That is the change from quarter steps to steps of 0.1, which came out of her own earlier complaint. She noticed it unprompted.
- **It stops and pauses quickly**, and "Wo bin ich?" does what it says.
- *"Da quakt mir nicht irgendwelche andere Benachrichtigung dazwischen."* [No other notification butts in.] Her purchased reader lets TalkBack read a Telegram notification over the middle of a book. This one does not.

That last one is a strength nobody designed, and it is now a constraint: **every new announcement, live region or notification is a risk to it.** It is cheaper to keep than to win back.

## What changed in this version

One string, from her one remaining wish: *"Ich wäre schon glücklich, hätte ich trotzdem ein bisschen menschlichere Stimmen."* [I would be happy with somewhat more human voices.]

The voices that sound that way were already in her list. The Google engine's network voices are the natural-sounding ones, and the app offered them marked "braucht Internet" — a warning, not a recommendation. In a list reading "Deutsch 1" to "Deutsch 8" there is no reason to try number 6 rather than number 2, and trying one costs a spoken sample each.

They are now named by what they are good for: **"Deutsch 6 · Deutschland · aus dem Internet, meist natürlicher"**. The policy around them is unchanged: Wi-Fi only by default, the app itself still has no internet permission, the engine fetches the audio in its own process.

Her own reading of the voice question is correct, by the way: the Gemini voices cannot be bound into an Android app, and a VoiceOver user on iOS is in the same position. Android TTS offers what a speech engine exposes as an engine, and nothing else.

## Environment

- Reported from the target device, Samsung Galaxy S25, Google speech engine, one book of several hundred pages.
- App 0.10.4, version code 35.

## Automated tests

| Run | Result |
| --- | --- |
| All unit tests | 109, all passed |
| Lint | no errors |

## What stays open

- She has not tried 0.10.3 yet — sleep timer, bookmarks, search, the second voice. She expects to have time towards the weekend.
- More human voices beyond what the engine offers would need a cloud provider, an internet permission, consent and a cost limit. Nothing about that has changed.
