# Test protocol 0.9.6

Date: 16 September 2026

## What is new

**Trying a different voice no longer throws away your place in the section.**

Until now, changing the voice started the current section again from its spoken heading. With sections of about ten thousand characters, that is up to ten minutes of listening to hear a second time — and the app said so out loud, which made it a reason not to try another voice at all. Someone who wanted to know whether a different voice was easier to follow had to pay for the question.

A section is cut into parts by its text alone. The voice decides how many seconds a part lasts, never where the parts begin. So the part someone had reached still means the same words in any voice, and only the seconds inside that part belong to the old voice: the same millisecond is a different word once a voice reads half again as fast.

`core/resumePoint` now keeps the part and gives up only the seconds inside it. At most one part is repeated, which is at most a thousand characters, instead of the whole section.

| | Before | Now |
| --- | --- | --- |
| Changing the voice mid-section | back to the section heading, up to ten minutes | back to the start of the current part, at most about one minute |
| Same voice, app restarted | to the second | unchanged, to the second |
| Section already finished | back to the heading | unchanged, back to the heading |

The spoken message changed with it, from "Dieses Kapitel beginnt beim nächsten Start von vorne" to "Das Vorlesen setzt beim nächsten Start kurz vor deiner Stelle wieder ein".

## Also in this version

The accessibility rules check from 0.9.5 was extended from three screens to seven: it now also walks the library, the question asked before a document is removed, the command list and the error message. **None of the four held a violation**, so nothing in the app changed for it. The error message matters most of the four: it is the last thing left to press when something has gone wrong.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.6, version code 27.

## Automated tests

| Run | Result |
| --- | --- |
| `SectionResumeTest` | 4 of 4, and the decisive one failed before the change |
| `ReaderCoreTest` (unit) | 32 of 32, three of them new |
| `AccessibilityRulesTest` | 8 of 8 |
| `ResumePositionTest` | 1 of 1, with a real voice |
| `LibraryResumeTest` | 1 of 1, with a real voice |
| Lint | no errors |

`SectionResumeTest` needs no speech engine: its voices are silent WAV files of a fixed length, one of them half again as long as the other for the same words, which is the gap measured between a local neural voice and the stock one. That is exactly the property under test, so it runs in CI with the import checks.

## What stays open

- The seconds inside the part are given up, not converted. Scaling them by the ratio of the two durations would land closer, but it can land *after* where the listener was, and missing words is worse than hearing a few again.
- Contrast and focus order are still unchecked, and anything that only appears under a real screen reader still needs the manual walk in `docs/TESTING.md`.
- Still nothing tested across manufacturers; the reference device is a Samsung Galaxy S25 with Vocalizer, and everything here was measured on a Pixel emulator.
