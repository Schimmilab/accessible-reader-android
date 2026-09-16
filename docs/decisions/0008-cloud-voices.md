# Decision 0008: A cloud voice, and what it would cost

Date: 16 September 2026

Status: **proposed** — this one is not mine to decide. It costs money and it changes the promise the app is built on.

## Context

The listener has asked for the same thing twice, in her own words: *"Ich wäre schon glücklich, hätte ich trotzdem ein bisschen menschlichere Stimmen."* [I would be happy with somewhat more human voices.] It is the only wish of hers that is still open, and it is the one thing on the plan's own MVP list that has never been built: "one optional Google cloud voice" and "a cost limit", with acceptance criteria 9 and 10 written around them.

Everything else this reader does, it now does. So this is the fork in the road.

## The free option, which may already be enough

Before any of what follows: the speech engine on her phone already offers voices that fetch their audio from the internet, and those are the natural-sounding ones. The app has offered them since 0.9.x, gated to Wi-Fi by default.

**The app itself stays offline either way.** The engine does the fetching in its own process, which is why this needs no internet permission here, no account and no key, and costs nothing at all.

Until 0.10.4 those voices were labelled "braucht Internet" — a warning, not a recommendation — in a list reading "Deutsch 1" to "Deutsch 8". There was no reason to try number 6 rather than number 2. They now say what they are: *"aus dem Internet, meist natürlicher"*.

**So the first step is not a decision but a question: are they good enough for her?** It costs one message and a few spoken samples. If the answer is yes, everything below is unnecessary.

## What a real cloud voice would cost

Google Cloud Text-to-Speech, the plan's own candidate, as of September 2026:

| | Price | Free each month |
| --- | --- | --- |
| Chirp 3: HD | **$30 per million characters** | 1 million characters |
| Standard / WaveNet | markedly cheaper | up to 4 million characters |

What that means in books rather than characters, at about 2,500 characters a page and 6.5 characters a word:

| | Characters | Cost with Chirp 3 HD |
| --- | --- | --- |
| A 400-page book | about 1 million | **the free tier, once a month** |
| The same book again in the same month | about 1 million | **about $30** |
| An hour of listening | about 50,000 | about $1.50 |

So the free tier is roughly **one book, or twenty hours of listening, per month**. A second book in the same month costs about as much as a hardback.

## What it would take besides money

- **The `INTERNET` permission.** The app has never had it. That it has never had it is checked after every dependency change, and it is the promise the whole project rests on: her books cannot leave the phone because the app cannot send them anywhere. A cloud voice sends every sentence of every book to Google.
- **An account, a key and a bill.** Somebody's Google Cloud project, with a key on her phone or a small service in between.
- **Consent that is real.** She has to be told, in the app, what leaves the device and what it costs, before the first sentence is sent.
- **A cost limit that holds**, counted in characters before synthesis, not after the invoice.
- **A fallback.** No network, no money left, no key: the book has to keep reading in a local voice rather than stop.

## Options, as I see them

1. **Ask her first.** Have her try the engine's online voices and say whether they are enough. Costs nothing, needs no decision, and might end the question.
2. **Build it for one book at a time.** A cloud voice that she switches on per book, with the free million characters as the natural limit and a hard stop when it is used up. Honest, affordable, and the permission still has to be added.
3. **Leave it.** The app stays offline, the voices stay what the phone offers.

## Recommendation

Option 1, now. Option 2 only if she says the free voices are not enough — and then as a deliberate change of the project's promise, written down as such, not as a feature that quietly grew an internet permission.

## Sources

- Google Cloud Text-to-Speech pricing, September 2026: https://cloud.google.com/text-to-speech/pricing
- Summary of the current tiers: https://costbench.com/software/ai-voice-tools/google-cloud-text-to-speech/
