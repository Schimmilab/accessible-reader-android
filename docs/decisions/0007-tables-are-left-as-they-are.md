# Decision 0007: Tables are read as they stand, and not taken apart

Date: 16 September 2026

Status: accepted

## Context

A table read aloud is a word soup. "UTF-8 8 1, 2, 3, or 4 UTF-16 16 1 or 2" tells a listener nothing about which number belongs to which name, where a sighted reader simply sees the grid. The obvious fix is to find the cells and read each one with its column heading, the way a screen reader reads a table in a web page.

That needs the cells to be found, reliably, in a PDF that says nothing about them. Whether they can be was measured before anything was built.

## What was measured

Eight real books, 332 pages, 14,000 lines: a product catalogue, a magazine, a book about serial ports, a government guide to nursing care, table tennis rules and three books of prose. Every line was cut into cells wherever its glyphs left a gap wider than two characters, or wherever the file padded with spaces, and runs of at least three consecutive lines whose cells start at the same places were counted as tables.

| | Blocks found | With three or more columns |
| --- | --- | --- |
| Magazine (44 pages) | 66, 238 lines | **0** |
| Table tennis rules (12 pages) | 11, 41 lines | **0** |
| Serial ports (60 pages) | 4, 12 lines | **0** |
| Catalogue, nursing guide, three prose books (216 pages) | 5, 22 lines | **0** |

**Not one block of three or more aligned columns exists in 332 pages.** And of the two-column blocks, the overwhelming majority are not tables at all. This is what the magazine's 66 "tables" look like:

> gründig gar nichts. Mich reg | bat, mir die Quellen für sei
> Schummeleien an einer Doktor | chen Behauptungen zum Thema

That is a page of two-column prose, seen through a detector looking for tables. Three genuine tables turned up in the whole sample, of three rows each:

> Rentenversicherung | 19,9 v.H.

## Decision

**No cell-by-cell reading of tables.** A table is read the way it stands, row by row, which is what text extraction produces and what the reader has always done.

## Why

Two reasons, and the second is the stronger one.

**There is almost nothing to gain.** Three small tables in 332 pages is about ten lines in fourteen thousand. Even perfect handling would change 0.07 % of the text.

**And a great deal to lose.** A table and a page of two-column prose have the same shape: aligned columns with a gap between them. In this sample the prose outnumbers the tables twenty to one, so a detector tuned to catch tables catches prose instead, and taking prose apart into cells would wreck whole pages to fix ten lines. This is not hypothetical: an earlier version of the column reading did split a table of contribution rates, and every figure was torn from the row it belonged to.

The same measurement also killed the idea of using the table shape to find two-column pages. On the magazine page where the interleaving is worst, **25 of 87 lines cross the narrowest gap in the page** — a third of them. One column boundary does not describe that page, and anything that pretends otherwise reorders a page it has not understood.

## What was done instead

`core/PageColumns.kt`, which reads a page one column at a time when the page really is in columns, and refuses when it is not. It changes ten pages of a catalogue in this sample and leaves the other 322 character for character as they were. It deliberately leaves the magazine alone, for the reason above.

## What would change this

A book whose tables actually matter — Maja's geopolitics book is the obvious candidate and is not available here. The harness that produced these numbers is kept as `TableSurveyTest`, so the same counting can be repeated on any collection of books in a few minutes. If tables turn out to be common and to have three or more columns in books that are really being listened to, this decision is worth taking out again.
