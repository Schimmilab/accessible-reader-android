package de.schimmilab.accessiblereader

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A survey before anything is built: are tables in real books detectable at all, and what do they look like?
 *
 * A table read out as one line of words is a word soup — "UTF-8 8 1, 2, 3, or 4 UTF-16 16 1 or 2" — and nobody
 * can tell which number belongs to what. Making that comprehensible means knowing where the cells are, which is
 * only worth attempting if the cells can be found reliably. This counts and prints, and decides nothing.
 *
 * Fill the app's cache folder the way `RealBooksTest` documents. Skips without books.
 */
@RunWith(AndroidJUnit4::class)
class TableSurveyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** One line, cut into cells wherever the glyphs leave a gap wider than a space. */
    private data class Row(val page: Int, val top: Float, val cells: List<Pair<Float, String>>)

    private class RowCollector(val rows: MutableList<Row>) : PDFTextStripper() {
        var page = 0
        override fun writeString(text: String, positions: List<TextPosition>) {
            if (positions.isEmpty() || text.isBlank()) return
            val widths = positions.map { it.widthDirAdj }.filter { it > 0f }.sorted()
            val typical = if (widths.isEmpty()) 0f else widths[widths.size / 2]
            val cells = mutableListOf<Pair<Float, String>>()
            val current = StringBuilder()
            var cellLeft = positions.first().xDirAdj
            for (index in positions.indices) {
                val here = positions[index]
                current.append(here.unicode)
                val next = positions.getOrNull(index + 1) ?: continue
                val gap = next.xDirAdj - (here.xDirAdj + here.widthDirAdj)
                // Two typical characters of empty space is more than any font puts between words.
                // Two signals, because a PDF pads its columns either with empty space or with real spaces,
                // and which one it uses is up to whoever made the file.
                val padded = here.unicode == " " && next.unicode == " "
                if ((typical > 0f && gap > typical * 2f) || padded) {
                    cells += cellLeft to current.toString().trim()
                    current.setLength(0)
                    cellLeft = next.xDirAdj
                }
            }
            if (current.isNotBlank()) cells += cellLeft to current.toString().trim()
            rows += Row(page, positions.first().yDirAdj, cells.filter { it.second.isNotBlank() })
        }
    }

    /**
     * A run of at least three consecutive lines whose cells start at the same places is a table.
     *
     * "The same places" has to allow for a column of numbers that is right-aligned and a heading that is not, so
     * two cells count as the same column when they start within a few points of each other.
     */
    private fun tableBlocks(rows: List<Row>): List<List<Row>> {
        val blocks = mutableListOf<List<Row>>()
        var current = mutableListOf<Row>()
        for (row in rows.sortedBy { it.top }) {
            val fits = row.cells.size >= 2 && (current.isEmpty() || sameColumns(current.last(), row))
            if (fits) current += row else {
                if (current.size >= 3) blocks += current.toList()
                current = if (row.cells.size >= 2) mutableListOf(row) else mutableListOf()
            }
        }
        if (current.size >= 3) blocks += current.toList()
        return blocks
    }

    private fun sameColumns(a: Row, b: Row): Boolean {
        if (a.cells.size != b.cells.size) return false
        return a.cells.indices.all { kotlin.math.abs(a.cells[it].first - b.cells[it].first) < 12f }
    }

    @Test fun realTablesAreCountedAndPrinted() {
        val folder = File(context.cacheDir, "books")
        val books = folder.listFiles { f -> f.isFile && f.name.endsWith(".pdf", true) }.orEmpty().sortedBy { it.name }
        assumeTrue("No PDFs in $folder", books.isNotEmpty())
        PDFBoxResourceLoader.init(context)

        for (book in books) {
            runCatching {
                PDDocument.load(book, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)).use { pdf ->
                    val pages = minOf(pdf.numberOfPages, 60)
                    var blocks = 0
                    var wide = 0
                    var rowsInBlocks = 0
                    var shown = 0
                    var lines = 0
                    for (page in 1..pages) {
                        val rows = mutableListOf<Row>()
                        val collector = RowCollector(rows).apply {
                            sortByPosition = true; this.page = page; startPage = page; endPage = page
                        }
                        collector.getText(pdf)
                        lines += rows.size
                        val found = tableBlocks(rows)
                        blocks += found.size
                        // Three columns or more is where a block stops being ambiguous. Two columns is the shape
                        // of a table and of a page of two-column prose alike, and the prose is far commoner.
                        val threeOrMore = found.filter { it.first().cells.size >= 3 }
                        wide += threeOrMore.size
                        rowsInBlocks += found.sumOf { it.size }
                        if (shown < 2 && threeOrMore.isNotEmpty()) {
                            shown++
                            val block = threeOrMore.first()
                            Log.i("ReaderTables", "${book.name} S.$page: ${block.size} Zeilen, " +
                                "${block.first().cells.size} Spalten")
                            block.take(4).forEach { row ->
                                Log.i("ReaderTables", "   | " + row.cells.joinToString(" | ") { it.second.take(28) })
                            }
                        }
                    }
                    Log.i("ReaderTables", "${book.name}: $pages Seiten, $lines Zeilen, $blocks Blöcke mit zwei " +
                        "oder mehr Spalten, davon $wide mit drei oder mehr, zusammen $rowsInBlocks Zeilen")
                }
            }.onFailure { Log.i("ReaderTables", "${book.name}: ${it::class.java.simpleName}: ${it.message}") }
        }
        assertTrue(books.isNotEmpty())
    }
}
