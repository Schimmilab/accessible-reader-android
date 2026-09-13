# Decision 0005: Online voices are offered, the app still never goes online

Date: 13 September 2026

Status: accepted

## Context

The blind test reader wants a voice that sounds like the ones she hears from an assistant. The stock offline voices sound synthetic, and the free local neural voice, Piper Thorsten through VoxSherpa, needs about as long to synthesize as the audio it produces, which makes a book a waiting game.

Her own question was the useful one: could the app tell whether the phone is on Wi-Fi, and offer better online voices when it is?

## What was measured

On an emulator, with the Google speech engine.

| Question | Answer |
| --- | --- |
| Does the engine expose German voices that fetch their audio from the internet? | Yes, four of nine |
| Can `synthesizeToFile` use one? | Yes. 1.4 MB of audio in 3.4 seconds |
| Does the app need an `INTERNET` permission for that? | **No.** The speech engine does the network access in its own process |
| Can the app tell Wi-Fi from mobile data? | Yes, but only with `ACCESS_NETWORK_STATE` |
| Without that permission? | `getNetworkCapabilities` returns null, measured |

The cloud services were compared as well, per 800,000 characters, which is one 500-page book.

| Option | Cost per book | Key needed | Text leaves the device |
| --- | --- | --- | --- |
| Network voices of the installed engine | nothing | none | to the engine vendor |
| Google Chirp 3 HD | about 24 dollars, 1 million characters free per month | Google Cloud project | yes, no EU endpoint |
| Azure Neural | about 12 dollars | Azure subscription | yes, EU region available |
| ElevenLabs | about 80 dollars | yes | yes, and training is on by default below the enterprise tier |

## Decision

Offer the network voices the installed speech engine already exposes. Do not integrate any cloud provider.

- The list marks them: „braucht Internet" [needs internet]. Nobody picks one without knowing.
- A setting decides when they may be used, defaulting to Wi-Fi only: only on an unmetered connection, also on mobile data, or never.
- If a chosen voice cannot be used right now, the app says so before it prepares anything, and the sentence names what to change.
- `ACCESS_NETWORK_STATE` is added to the manifest. It reports the state of the connection and grants no access to it. The app still has no `INTERNET` permission, and the check in `docs/RELEASING.md` proves it for every build.

## Why no cloud provider

Three reasons, in order of weight.

1. **The key.** Google, Azure and AWS all require a sighted person to create a project, attach billing and generate credentials. For a blind end user that is not a setup, it is a barrier. The alternative is the developer's own key, which makes one private individual both the payer and, under the GDPR, the controller for someone else's reading.
2. **The text.** A cloud voice means the whole book goes to a third party. ElevenLabs trains on submitted content by default below its enterprise tier, which is incompatible with what this project promises.
3. **The money.** There is no budget. A free tier that covers one book a month is not a foundation for a daily reader.

None of that is an argument against a cloud voice forever. It is an argument for not adding one before the free option has been heard and judged.

## Consequences

- The audio cache key already contains the voice id, so switching between an online and an offline voice cannot mix them up.
- A network voice fails differently from a local one: it can stop mid-chapter when the connection drops. The synthesis error path already covers that, and the message names the voice.
- Whether these voices actually sound better than the local ones is a listening question, not a technical one. That is for the test reader to answer.
