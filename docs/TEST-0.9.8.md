# Test protocol 0.9.8

Date: 16 September 2026

## What is new

**A page that really has columns is read one column at a time.**

Text extraction sees a page as lines across its full width. Where a page has two columns, that splices the right one into the sentences of the left, word by word. On page 4 of a real product catalogue the reader said:

> Der Funkalarm Rodger ® ist eine komplette Lösung, um Ihr Kind mit Einnässen zu helfen Es ist **Boxer** mit Unterwäsche verwendet

"Boxer" is a heading from the next column. Read column by column, both halves come out whole.

## What was measured, on eight real books

332 pages of eight books from the shelf rather than fixtures: a product catalogue, a magazine, a book about serial ports, a government guide to nursing care, table tennis rules, and three books of plain prose.

| | Pages | Read by column |
| --- | --- | --- |
| Product catalogue | 18 | **10** |
| Magazine, government guide, serial ports, rules, three prose books | 314 | 0 |

**The 322 untouched pages come out character for character as they did before.** `PageLayoutSurveyTest` asserts exactly that, and it is the assertion that matters most here: reordering a page that was already right is the one way this feature can make a book worse.

## What it refuses to do, and why

Every rule below was put there by a page that broke without it.

- **Not one line may touch the gutter.** Allowing even five per cent of them to cross let a table of contribution rates be taken for two columns: "Renten- und Arbeitslosenversicherung – West 66.000 5.500" became "Renten- und Arbeitslosenversicherung – West, Renten- und Arbeitslosenversicherung – Ost, …" followed by the numbers on their own. Every figure torn from its row.
- **Each column needs at least eight rows of its own.** A table of three character encodings, five rows a side, was otherwise split into "UTF-8, UTF-16, UTF-32" and then "8, 16, 32".
- **The columns have to run at least 40 % of the page height.** Short stretches of two columns in a magazine were split without the interleaving going away, because their real boundary is crossed somewhere and the boundary found instead was a different one. A change with no benefit is not worth its risk.
- **A band of lines that spans the full width stays whole**, so a headline across two columns is read first and does not stop the split underneath it.

The price is honest and worth naming: **a two-column page whose gutter is crossed anywhere is left alone.** The magazine in this sample is not recognised at all. A table is never split, so a table beside a paragraph still reads interleaved, exactly as before.

## What it costs

Nothing measurable. The geometry of every line is written down during the ordinary extraction pass, so recognising a two-column page needs no second reading of the page; only a page that really has columns is read again, by area. Importing all eight books took the same time as before, and the whole survey of 332 pages runs in about 15 seconds on an emulator.

## Environment

- Mac mini M1, Pixel 8a emulator, Android 17, API 37, ARM64.
- App 0.9.8, version code 29.

## Automated tests

| Run | Result |
| --- | --- |
| `PageColumnsTest` (unit) | 14 of 14, runs in CI |
| `PageLayoutSurveyTest` | 8 books, 332 pages, 0 unexpected deviations |
| `RealBooksTest` | 8 of 8 books readable |
| `ReaderCoreTest`, `NarrationTest`, `ContrastTest` and the rest of the unit tests | all passed |
| Lint | no errors |

## What stays open

- A table beside a paragraph still reads interleaved. Reading a table aloud so it can be followed — "Spalte eins, Spalte zwei" — is a separate job and needs a listener to judge it.
- A two-column page with a caption across the gutter is left alone.
- Telling the characters of a novel apart by voice, where the text says who speaks.
- Focus order and everything that only appears under a real screen reader still need the manual walk in `docs/TESTING.md`. Still nothing tested across manufacturers.
