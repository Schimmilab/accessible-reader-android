# Test protocol 0.9.7

Date: 16 September 2026

## What is new

**A novel can be read with two voices: one for the book, one for its people.**

Under the voice list in the settings there is now *Zweite Stimme für Gespräche*. Pick a second voice there and it speaks everything in quotation marks; the voice chosen above reads the rest.

The split is mechanical, at the quotation marks, and **who** is speaking is never guessed. That is a measurement, not caution: over all of Fontane's *Effi Briest*, four out of five lines of dialogue are followed by no "sagte er" and no "sagte sie" at all. A male and a female voice would therefore be assigned wrongly most of the time, and a wrong voice is worse than one voice.

## What the measurements said before any of it was built

Three numbers decided the shape of this, all taken before the feature existed.

| Question | Measured | Consequence |
| --- | --- | --- |
| How much of a novel is direct speech? | 51 % of 609,000 characters of *Effi Briest* | A second voice carries half the book, so it is worth doing |
| Does the player fall silent between parts? | 14 ms per transition, 20 parts of 500 ms | Cutting at every quotation mark does not make a conversation stutter |
| What does one more part cost the engine? | +14 % preparation for 37 parts instead of 4, stock Google voices, warmed up and measured in both orders | Affordable on a voice that keeps up; irrelevant on one that does not, because that one is already too slow |

Two more from the same survey shaped the code: more than half of the dialogue in that novel runs across a line break, so only a blank line ends an unterminated quotation and never a newline; and 23 of its 1890 quotations are never closed at all, which is why an open quotation also ends after 2000 characters.

## What it does not do

- It does not tell the characters apart. One voice speaks all of them.
- Both voices play at the speed set for the chosen voice. One speed per section on purpose: otherwise pressing *Langsamer* during a line of dialogue would change nothing audible, and a screen reader user would be left pressing a button that does nothing.
- Both voices have to come from the same speech engine, because one provider speaks to one engine. Changing the engine clears the second voice.
- The measured speed of a voice under the voice list is only recorded while one voice reads everything. With two, a piece cannot be attributed from there, and one average of both rates would describe neither.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64. Google engine with five installed German voices, VoxSherpa with one.
- App 0.9.7, version code 28.

## Automated tests

| Run | Result |
| --- | --- |
| `NarrationTest` (unit) | 12 of 12 |
| `NarrationSurveyTest` (unit) | ran over a whole novel, 609,000 characters, nothing lost or invented |
| `TwoVoicesTest` | 3 of 3, without a speech engine |
| `TwoRealVoicesTest` | passed on the Google engine, both voices real |
| `DialogueVoiceFlowTest` | 1 of 1, the whole way through the app with two real voices |
| `PartTransitionCostTest` | 1 of 1, 14 ms per transition |
| `AccessibilityRulesTest` | 8 of 8, including the new rows in the settings |
| `SectionResumeTest` | 4 of 4 |
| `ResumePositionTest`, `LibraryResumeTest` | 1 of 1 each, with a real voice |
| `ReaderCoreTest` and the rest of the unit tests | all passed |
| Lint | no errors |

`TwoVoicesTest` runs in CI. It uses `SilentVoices`, a speech provider of silent WAV files that records which voice was asked for which text, so the division of labour is checked on a machine that has no German voice at all.

## Also in this version

The contrast of every colour pair the app draws is now measured, at the strictest level the guidelines name. The one pair that fell short was the label of the two 30-second buttons on the card, at 6.44 to 1; the green of the app is one step darker for it, which lifts that pair to 7.21 and every other one with it.

## What stays open

- A table inside a PDF is still read row by row, interleaved with the paragraph beside it. That is the next thing.
- Telling the characters apart, where the text does say who speaks, would need the carry-over rule evaluated by someone actually listening. Four out of five lines carry no attribution at all.
- Focus order, and everything that only appears under a real screen reader, still need the manual walk in `docs/TESTING.md`. Still nothing tested across manufacturers.
