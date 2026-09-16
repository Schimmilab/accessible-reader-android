package de.schimmilab.accessiblereader.core

/** One extracted line of a page, as far as its geometry matters here. */
data class TextLine(val left: Float, val right: Float, val top: Float = 0f, val bottom: Float = 0f)

/** A part of a page to be read as a whole, before the next one. */
data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Finds the parts of a page that have to be read one after another, so a page with columns is not read across.
 *
 * Text extraction sees a page as lines across its full width. Where a page really has columns, that splices the
 * right column into the sentences of the left one, word by word. Measured on a real product catalogue: ten of
 * its eighteen pages are genuinely multi-column, and the reader turned one of them into "Der Funkalarm Rodger
 * ist eine komplette Lösung, um Ihr Kind mit Einnässen zu helfen Es ist **Boxer** mit Unterwäsche verwendet",
 * where "Boxer" is a heading from the next column. Read column by column, both halves come out whole.
 *
 * A page is first cut into horizontal bands at every line that runs the full width, because a headline across
 * two columns is the commonest shape of all and would otherwise stop the split; then each band is examined for
 * columns of its own.
 *
 * Everything here is deliberately reluctant. Reordering a page that was already right is worse than leaving one
 * alone, so a column boundary has to be a band that no line crosses, wide enough to be a gutter rather than a
 * word space, inside the text rather than in a margin, with real text on both sides. Measured over 332 pages of
 * eight real books, this changes the catalogue's ten pages and not one single page of the other seven.
 */
object PageColumns {
    /** A gutter is at least this much of the page width. Narrower than that, it is the space between words. */
    private const val NARROWEST_GUTTER = 0.04f
    /** A boundary this far out is a ragged margin, not a column. */
    private const val OUTER_MARGIN = 0.15f
    /** Each column carries at least this share of its band's lines, and never fewer than three. */
    private const val SMALLEST_COLUMN = 0.25f
    private const val FEWEST_LINES = 8
    /**
     * Each column has to carry at least this many lines of its own.
     *
     * This is what tells a column apart from a table. Both have a gutter no text crosses, but a table is read
     * across, row by row, and a column is read down: reading "UTF-8 UTF-16 UTF-32" and then "8 16 32" separately
     * throws away which number belongs to which name. Measured on a real book about serial ports, its table of
     * three encodings has five rows a side, while the columns of a magazine page have thirty and more.
     */
    private const val FEWEST_ROWS_PER_COLUMN = 8
    /** Two lines within this many points of each other sit on the same row of the page. */
    private const val SAME_ROW = 3f
    /** A line reaching this far across the text area belongs to no column: it is a headline or a rule. */
    private const val FULL_WIDTH = 0.8f
    /** Two splits, so a three-column catalogue page comes out in order without inviting deeper guesswork. */
    private const val DEEPEST_SPLIT = 2
    /**
     * Columns have to run this much of the page's height before they are treated as columns.
     *
     * A short stretch of two columns is where this stops being worth it and starts being a risk: measured on a
     * magazine, the short stretches were split without the interleaving going away, because their real column
     * boundary is crossed somewhere and the boundary found instead was another one. Where the columns run the
     * page, as in a catalogue, the interleaving disappears.
     */
    private const val TALLEST_SHORT_BAND = 0.4f

    /**
     * The parts of one page in reading order, or an empty list when the page is a single column and should be
     * read exactly as before. Nothing to do is said by saying nothing.
     */
    fun regions(lines: List<TextLine>, pageWidth: Float, pageHeight: Float): List<Region> {
        if (pageWidth <= 0f || pageHeight <= 0f || lines.size < FEWEST_LINES) return emptyList()
        val sorted = lines.sortedBy { it.top }
        val usedLeft = sorted.minOf { it.left }
        val usedRight = sorted.maxOf { it.right }
        if (usedRight <= usedLeft) return emptyList()
        val full = (usedRight - usedLeft) * FULL_WIDTH

        // Bands: runs of lines that stay inside a column, separated by lines that run the full width.
        val bands = mutableListOf<MutableList<TextLine>>()
        var current = mutableListOf<TextLine>()
        var wide = false
        for (line in sorted) {
            val spansEverything = line.right - line.left >= full
            if (spansEverything != wide && current.isNotEmpty()) { bands += current; current = mutableListOf() }
            wide = spansEverything
            current += line
        }
        if (current.isNotEmpty()) bands += current

        // Vertical boundaries between bands, halfway between the last line of one and the first of the next, so
        // no line can fall outside the part it belongs to.
        val tops = mutableListOf(0f)
        for (index in 1 until bands.size) {
            val above = bands[index - 1].maxOf { maxOf(it.bottom, it.top) }
            val below = bands[index].minOf { minOf(it.top, it.bottom) }
            tops += if (below > above) (above + below) / 2f else below
        }

        val out = mutableListOf<Region>()
        var anyBandHasColumns = false
        bands.forEachIndexed { index, band ->
            val top = tops[index]
            val bottom = if (index + 1 < tops.size) tops[index + 1] else pageHeight
            val tall = band.maxOf { maxOf(it.top, it.bottom) } - band.minOf { minOf(it.top, it.bottom) } >=
                pageHeight * TALLEST_SHORT_BAND
            val columns = if (tall) split(band, 0f, pageWidth, pageWidth, DEEPEST_SPLIT) else listOf(0f to pageWidth)
            if (columns.size > 1) anyBandHasColumns = true
            for (column in columns) out += Region(column.first, top, column.second, bottom)
        }
        // Bands alone change nothing: they are read top to bottom, which is the order the page already had. Only
        // a band that really holds columns is a reason to read the page differently at all.
        return if (!anyBandHasColumns) emptyList() else out
    }

    private fun split(lines: List<TextLine>, from: Float, to: Float, pageWidth: Float, depth: Int): List<Pair<Float, Float>> {
        val here = listOf(from to to)
        if (depth <= 0) return here
        val gutter = gutter(lines, pageWidth) ?: return here
        val middle = (gutter.first + gutter.second) / 2f
        val left = lines.filter { it.right <= middle }
        val right = lines.filter { it.left >= middle }
        return split(left, from, middle, pageWidth, depth - 1) + split(right, middle, to, pageWidth, depth - 1)
    }

    /** How many rows of the page these lines occupy, counting everything on one baseline once. */
    private fun rows(lines: List<TextLine>): Int {
        val tops = lines.map { it.top }.sorted()
        var rows = 0
        var last = Float.NEGATIVE_INFINITY
        for (top in tops) if (top - last > SAME_ROW) { rows++; last = top }
        return rows
    }

    /** The widest band of the page that no line of [lines] touches, if it qualifies as a column boundary. */
    private fun gutter(lines: List<TextLine>, pageWidth: Float): Pair<Float, Float>? {
        if (lines.size < FEWEST_LINES) return null
        val buckets = 240
        val step = pageWidth / buckets
        val counts = IntArray(buckets)
        for (line in lines) {
            val first = (line.left / step).toInt().coerceIn(0, buckets - 1)
            val last = (line.right / step).toInt().coerceIn(0, buckets - 1)
            for (b in first..last) counts[b]++
        }
        // Not one line may touch the band. Allowing even a few lines to cross let a table of contribution rates
        // be taken for two columns, and its numbers were torn from the rows they belong to.
        val covered = BooleanArray(buckets) { counts[it] > 0 }
        val firstUsed = covered.indexOfFirst { it }
        val lastUsed = covered.indexOfLast { it }
        if (firstUsed < 0 || lastUsed <= firstUsed) return null
        val used = (lastUsed - firstUsed + 1) * step
        val inside = firstUsed * step + used * OUTER_MARGIN..lastUsed * step - used * OUTER_MARGIN

        var best: IntRange? = null
        var start = -1
        for (b in firstUsed..lastUsed + 1) {
            val empty = b <= lastUsed && !covered[b]
            if (empty) { if (start < 0) start = b } else if (start >= 0) {
                val band = start until b
                if (best == null || band.count() > best.count()) best = band
                start = -1
            }
        }
        val band = best ?: return null
        if (band.count() * step < pageWidth * NARROWEST_GUTTER) return null

        val middle = (band.first + band.last + 1) / 2f * step
        if (middle !in inside) return null
        // Both sides have to carry text of their own; a single indented block is not a column.
        val left = lines.filter { it.right <= middle }
        val right = lines.filter { it.left >= middle }
        val smallest = maxOf(3, (lines.size * SMALLEST_COLUMN).toInt())
        if (left.size < smallest || right.size < smallest) return null
        // And each side has to be a column's worth of rows, or this is a table and belongs read across.
        if (rows(left) < FEWEST_ROWS_PER_COLUMN || rows(right) < FEWEST_ROWS_PER_COLUMN) return null
        return band.first * step to (band.last + 1) * step
    }
}
