# Test protocol 0.10.2

Date: 16 September 2026

## What is new

**"Wo bin ich?" now answers for the whole book.**

It used to say the section and the seconds inside it. A listener has no scroll bar to feel and no thumb in the pages, so how much book is left was something the app knew and never said:

> Unterwegs, Abschnitt 2 von 3. 4 Minuten und 12 Sekunden. Du hast etwa ein Drittel des Buches gehört. Noch etwa 3 Stunden und 20 Minuten.

- The share is counted in **characters**, not in sections, because sections differ in length by a lot.
- It is said in words someone can picture — "ein Drittel", "die Hälfte" — and everything else is rounded to five percent. A percentage to the digit would sound like knowledge nobody has.
- The remaining time is only given **once the app has measured how fast this voice reads**, and the minutes are rounded to five. An estimate at a rate nobody measured would be a made-up number spoken with confidence.

## And a feature that was measured and then not built

Telling the characters of a novel apart by a male and a female voice. It was the obvious next step after two voices, and the measurement says no.

All of *Effi Briest*: in 323 of its 1,895 lines of dialogue the narration says who spoke. Those lines are the only ground truth there is, so each candidate rule had to predict them without being allowed to see the attribution it was judged against.

| Rule | Correct |
| --- | --- |
| The speakers take turns | **46 %** |
| The same person goes on speaking until the text says otherwise | **68 %** |
| Always the protagonist's gender | 63 % |

Taking turns is worse than a coin toss. The best rule is right two times in three, which means **every third line of dialogue in the wrong voice** — a man's voice saying Effi's lines, several times a chapter, to someone who cannot check it against the page. A wrong voice is worse than one voice, so one voice it stays. Written up in `docs/decisions/0006-not-guessing-who-speaks.md`.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.10.2, version code 33.

## Automated tests

| Run | Result |
| --- | --- |
| `ProgressTest` (unit) | 6 of 6 |
| `PositionAnnouncementTest` | 1 of 1, without a speech engine; runs in CI |
| All unit tests | 103, all passed |
| Lint | no errors |

## What stays open

- A table beside a paragraph still reads interleaved.
- The search finds words, not word stems.
- The interface is German only.
- Focus order and everything that only appears under a real screen reader still need the manual walk in `docs/TESTING.md`. Still nothing tested across manufacturers.
