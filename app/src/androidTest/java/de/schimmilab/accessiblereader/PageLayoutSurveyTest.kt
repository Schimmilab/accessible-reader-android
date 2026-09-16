package de.schimmilab.accessiblereader

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import de.schimmilab.accessiblereader.core.PageColumns
import de.schimmilab.accessiblereader.data.ColumnAwareStripper
import de.schimmilab.accessiblereader.data.readByColumn
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What reading a page column by column changes, on real books rather than on a fixture.
 *
 * Two things have to hold and both are checked here: a page that has no columns must come out exactly as it did
 * before, character for character, and a page that has them must come out differently. The first matters more.
 * Reordering a page that was already right would be a regression nobody would notice until a listener did.
 *
 * Fill the app's cache folder the way `RealBooksTest` documents. Skips without books.
 */
@RunWith(AndroidJUnit4::class)
class PageLayoutSurveyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun words(text: String) = text.split(Regex("\\s+")).filter { it.isNotBlank() }

    @Test fun columnsChangeOnlyThePagesThatHaveThem() {
        val folder = File(context.cacheDir, "books")
        val books = folder.listFiles { f -> f.isFile && f.name.endsWith(".pdf", true) }.orEmpty().sortedBy { it.name }
        assumeTrue("No PDFs in $folder", books.isNotEmpty())
        PDFBoxResourceLoader.init(context)

        for (book in books) {
            runCatching {
                PDDocument.load(book, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)).use { pdf ->
                    val pages = minOf(pdf.numberOfPages, 60)
                    val plain = PDFTextStripper().apply { sortByPosition = true }
                    val aware = ColumnAwareStripper().apply { sortByPosition = true }
                    var split = 0
                    var sameOrder = 0
                    var reordered = 0
                    var shown = 0
                    var untouchedDiffered = 0
                    val began = System.currentTimeMillis()
                    for (page in 1..pages) {
                        plain.startPage = page; plain.endPage = page
                        val before = plain.getText(pdf)
                        aware.startPage = page; aware.endPage = page; aware.lines.clear()
                        val again = aware.getText(pdf)
                        val box = pdf.getPage(page - 1).mediaBox
                        val columns = PageColumns.regions(aware.lines, box.width, box.height)
                        if (columns.isEmpty()) {
                            // The guarantee: without columns, nothing about the page changes.
                            if (before != again) untouchedDiffered++
                            continue
                        }
                        split++
                        val after = readByColumn(pdf, page, columns)
                        assertNotNull("page $page of ${book.name} was split but produced nothing", after)
                        // Only a different order of the words is a real change. Different line breaks are not,
                        // and counting them as one would hide how often this reorders a page.
                        val wordsBefore = words(before)
                        val wordsAfter = words(after!!)
                        if (wordsBefore == wordsAfter) { sameOrder++; continue }
                        reordered++
                        // How much of its own width each part is actually filled with text. A column of prose
                        // fills nearly all of it; a column of numbers in a table fills a fraction.
                        val fill = columns.joinToString(" ") { region ->
                            val width = region.right - region.left
                            val inside = aware.lines.filter {
                                it.left >= region.left - 1f && it.right <= region.right + 1f &&
                                    it.top >= region.top - 1f && it.bottom <= region.bottom + 1f
                            }
                            if (inside.isEmpty() || width <= 0f) "-" else {
                                val rows = inside.map { it.top }.sorted().fold(mutableListOf<Float>()) { acc, top ->
                                    if (acc.isEmpty() || top - acc.last() > 3f) acc.add(top); acc
                                }.size
                                "%.1f".format(inside.size.toFloat() / maxOf(1, rows))
                            }
                        }
                        Log.i("ReaderColumns", "${book.name} S.$page: ${columns.size} Teile, Wörter je Zeile $fill")
                        if (shown < 2) {
                            shown++
                            Log.i("ReaderColumns", "${book.name} Seite $page, ${columns.size} Teile, andere Reihenfolge")
                            Log.i("ReaderColumns", "  VORHER: " + before.replace("\n", " ⏎ ").take(240))
                            Log.i("ReaderColumns", "  NACHHER: " + after.replace("\n", " ⏎ ").take(240))
                            if (wordsBefore.sorted() != wordsAfter.sorted()) {
                                val lost = wordsBefore.toMutableList().also { l -> wordsAfter.forEach { l.remove(it) } }
                                Log.i("ReaderColumns", "  VERLOREN (${lost.size} Wörter): " + lost.take(12))
                            }
                        }
                    }
                    val ms = System.currentTimeMillis() - began
                    Log.i("ReaderColumns", "${book.name}: $pages Seiten · $split geteilt, davon $reordered mit " +
                        "anderer Wortfolge und $sameOrder ohne jede Änderung · $untouchedDiffered unerwartete " +
                        "Abweichungen · ${ms}ms für beide Durchläufe")
                    assertEquals("a page without columns has to come out exactly as before, in ${book.name}",
                        0, untouchedDiffered)
                }
            }.onFailure { Log.i("ReaderColumns", "${book.name}: ${it::class.java.simpleName}: ${it.message}") }
        }
        assertTrue(books.isNotEmpty())
    }
}
