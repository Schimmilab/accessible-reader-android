# How this was built, and what stays private

This reader was not built for blind users. It was built with one.

## The arrangement

A friend of the author has been blind since birth. She reads with a screen reader every day, at speeds a sighted person finds unintelligible, and she has tried the commercial readers. She tests every version of this app on her own phone and says what annoys her.

Her sighted husband set up her phone and helps with anything that needs sight, such as installing a build or reading a diagnosis report aloud.

That arrangement decided the direction of this project repeatedly, and more than once against the author's assumption. Some examples that are visible in the code today:

- The play button must not change its label while a screen reader speaks. Sighted testing never surfaces this, because the eye does not mind. The ear does: the label change makes the screen reader announce it, which interrupts the book again.
- A licensed speech engine can report no voices at all and still speak perfectly. The app asked the modern way only, found nothing, and was unusable on her phone. Nobody would have found that on a Pixel emulator.
- Two buttons on screen must never carry the same name. Sighted people tell them apart by position.
- A page limit of 300 seemed generous until someone tried to load an actual novel.
- Playback refusing to start because a cache was full would have stranded her in the middle of a book.

The general lesson is uncomfortable and worth writing down: closing your eyes finds many bugs, but not the ones that matter. Someone who works with a screen reader all day finds those in minutes.

## What is not in this repository

Her name is not here, and neither is his. Also absent:

- the messages exchanged with them
- the verbatim feedback, as collected in private notes
- the full diagnosis reports from her phone, including which engines and voices she has

Those are personal. They are kept outside this repository.

## How this repository refers to them

- **the test user** for the blind user the app is built for
- **her sighted husband** for the person who sets up her phone and records the diagnosis reports
- **the target device** for her phone, named technically as a Samsung Galaxy S25 running Android 16, because that detail is needed for almost every decision

No real names, no quotes from private messages, nothing that identifies a person.

## What is in this repository

Everything that should be reproducible by someone else:

- the technical findings, anonymised, in the test protocols `TEST-*.md`
- the reasoning in `decisions/`
- the design in `ARCHITECTURE.md`
- the product plan in `PLAN.md`

A finding keeps its value without naming its source. That a licensed speech engine reports no voices through `getVoices()`, and that an app therefore needs a second lookup path, is useful to anyone building this kind of software. Whose phone it was is not.

## The history of this repository

It starts with a single commit. Development happened alongside her over several weeks, and that history contained her name, her device and her words throughout. Rather than rewrite it and hope nothing slipped through, the public history begins clean. The private history is archived outside this repository.
