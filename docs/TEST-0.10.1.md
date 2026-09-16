# Test protocol 0.10.1

Date: 16 September 2026

## What is new

**Searching inside a book.** Under the contents there is now *Im Buch suchen*.

A hit is not a page number, which would be useless here. It is a place playback can start at: it names its section, reads back the words around it, and starting it plays from that passage. Reaching a hit is the very machinery a bookmark uses — section, part, and a zero for the seconds — which is why it works at all.

- Umlauts may be spelled either way. "Brücke" and "Bruecke" find the same passage, because both sides are folded the same way before being compared. Without that, a search for "fuer" would find nothing in a book full of the word.
- Punctuation between the words does not matter: nobody dictates a comma.
- At most twenty hits, at most three per section, so one chapter full of a common word cannot crowd out the rest. A list read out loud has to stay small enough to hold in your head.
- The number of hits is spoken at once, because a screen reader reads the list only once the listener has found their way to it.
- By voice: "Suche nach Brücke".

## What the tests caught

**The voice search would have found nothing at all.** Every other voice command is folded down to be compared against a fixed word, and that folding turns "Brücke" into "brucke" — a spelling that stands in no German book. A spoken query is therefore handed on exactly as it was said, and only the search does its own folding.

A test caught it. A listener would have concluded that the search is broken.

**"Suche nach" on its own was a search for the word "nach".** Speech recognition cuts a sentence short often enough for that to be worth handling.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.10.1, version code 32.

## Automated tests

| Run | Result |
| --- | --- |
| `SearchTest` (unit) | 10 of 10 |
| `SearchFlowTest` | 3 of 3, without a speech engine; runs in CI |
| `AccessibilityRulesTest` | 11 of 11, the search added to the nine screens already checked |
| All unit tests | 97, all passed |
| Lint | no errors |

What a screen reader reads in the result list, from the run: *"Fundstelle 1: Ankommen: … Pause, Sprünge um dreißig Sekunden und ein Inhaltsverzeichnis. Jede Taste trägt einen Namen, den TalkBack …"*

## What stays open

- A table beside a paragraph still reads interleaved.
- Telling the characters of a novel apart by voice.
- The search finds words, not word stems: "Brücken" does not find "Brücke".
- Focus order and everything that only appears under a real screen reader still need the manual walk in `docs/TESTING.md`. Still nothing tested across manufacturers.
