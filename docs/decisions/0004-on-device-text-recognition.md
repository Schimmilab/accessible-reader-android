# Decision 0004: Text recognition runs on the device

Date: 12 September 2026

Status: accepted

## Context

The test reader: „OCR wäre schon wichtig, weil die meisten Bücher eigentlich eingescannt sind." [OCR really would be important, because most books are actually scanned.]

A scanned book contains no text, it contains images of text. The reader could only refuse such books. That made the largest part of her library silent paper for it.

Three ways were available:

1. **A cloud service.** Google Vision, Azure, AWS. Best recognition, but every book leaves the device, it costs per page, and the app would need a network permission. That contradicts the core of the project.
2. **Text recognition on the device with ML Kit.** The model sits inside the package, it runs without a network.
3. **Do nothing at all** and say early on opening that it does not work.

## Measurement instead of opinion

A 30 page scan was produced from a real book by rendering the pages to images. Measured on the emulator, **with Wi-Fi switched off and mobile data switched off**:

| Quantity | Value |
| --- | --- |
| per page, render and recognize | around 1.2 seconds |
| 30 page scan, complete import | 28 seconds |
| extrapolated to 500 pages | about 10 minutes, once at import |

The recognized text was correct, umlauts included, and the existing text cleanup repairs the hyphens at the end of a line along the way.

The resolution was measured as well, not estimated. From 764 to 1200 pixels of page width the recognition delivered character for character the same text, while one page grew from 1.2 to 1.5 seconds. Rendering therefore uses what is necessary: at least twice the page box, at least 900 pixels wide.

A first measurement had produced 251 milliseconds per page and could not be repeated. It was too optimistic, and the number that stands in this decision is the one confirmed several times.

## Decision

Way 2. A page without a text layer is rendered and recognized.

The library brings `INTERNET` and `ACCESS_NETWORK_STATE` along in its own manifest. Both are removed again in the app's manifest with `tools:node="remove"`. This is verified on the built package with `aapt2 dump permissions`, not on the source code.

## Cost

- The package grows from 19 MB to around 60 MB. The model and the libraries for all processor architectures make the difference.
- Recognition makes mistakes. The note on the document says how many pages went through recognition.
- Two column scans will mix their columns just as two column text pages do.

## What follows from this

If the package size becomes a problem, the next step is separate packages per processor architecture. That concerns delivery, not the code.
