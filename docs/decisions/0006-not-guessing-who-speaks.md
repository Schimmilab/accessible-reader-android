# Decision 0006: The reader never guesses which character is speaking

Date: 16 September 2026

Status: accepted

## Context

Since 0.9.7 a novel can be read with two voices: one for the narration and one for everything in quotation marks. The obvious next wish, and the one that was asked for, is a male and a female voice for the characters themselves.

Nothing in an EPUB or a PDF says who is speaking. It would have to be guessed from the text, and the question is only whether a guess is right often enough to be worth hearing.

## What was measured

All of Theodor Fontane's *Effi Briest*, 609,000 characters, 1,895 lines of dialogue. In 323 of them — **17 %** — the narration right after the quotation says who spoke: "sagte er", "erwiderte Effi", "fragte die Mutter". Those 323 lines are the only ground truth available, and they were used as one: each candidate rule had to predict them without being allowed to look at the attribution it was being judged against.

| Rule | Predicted | Correct |
| --- | --- | --- |
| The speakers take turns (alternate from the last known one) | 71 % of the checkable lines | **46 %** |
| The same person goes on speaking until the text says otherwise | 71 % | **68 %** |
| Always the protagonist's gender (she says most of it) | 100 % | **63 %** |

## Decision

**No gendered voices.** One voice speaks all the characters, and where the text does not say who is speaking, nothing is guessed.

## Why

The intuition that a conversation alternates is not merely imperfect, it is **worse than a coin toss**: 46 %. Written dialogue is full of a person speaking twice in a row with narration in between, and of a third person joining.

The best rule, carrying the last known speaker forward, is right 68 % of the time. That sounds respectable until it is turned round: **roughly every third line of dialogue would be spoken in the wrong voice.** A listener would hear a man's voice saying Effi's lines, several times a chapter, with no way to tell which times. And the rule only beats "always guess the protagonist" by five points, which is another way of saying it has barely learned anything from the text.

A wrong voice is worse than one voice. One voice is merely neutral; a wrong one asserts something false about the book, repeatedly, to someone who cannot check it against the page.

## What would change this

Not a better heuristic. It would take knowing the characters of the book — a list of names with their genders, and resolving "der Baron", "die Mutter", "sie" against it. That is a different kind of work, and it would still have to be measured against the same 17 %, and it would still have to beat 68 % by enough to be worth the risk.

Until then, the honest version of this feature is the one that shipped: the characters get a voice of their own, and all of them share it.
