package de.schimmilab.accessiblereader

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A book the size of the one that prompted the limits to be raised: 3700 pages with a realistic amount of text
 * on each, about seven million characters in total.
 *
 * Raising a number in a constant is free. Surviving it is not, because the text of the whole book is held in
 * memory and written as one JSON file. This measures what that actually costs instead of assuming it works.
 */
@RunWith(AndroidJUnit4::class)
class LargeBookTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun aBookOfSeveralThousandPagesSurvivesTheImport() = runBlocking {
        val pages = 3_700
        val file = File.createTempFile("reader-large", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        val built = System.currentTimeMillis()
        try {
            repeat(pages) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                val ink = Paint().apply { textSize = 9f }
                var y = 20f
                var written = 0
                while (written < 1_900 && y < 820f) {
                    val line = "Seite ${index + 1}: Ein Satz über ein Haus, einen Garten und einen Brief, " +
                        "der lang genug ist, um eine Zeile zu füllen."
                    page.canvas.drawText(line, 10f, y, ink)
                    written += line.length
                    y += 20f
                }
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
            pdf.close()
            Log.i("ReaderLarge", "Prüfbuch gebaut in ${(System.currentTimeMillis() - built) / 1000}s, " +
                "${file.length() / 1024 / 1024} MB")

            val runtime = Runtime.getRuntime()
            val started = System.currentTimeMillis()
            val document = DocumentStore(context).importPdf(Uri.fromFile(file)) {}
            val seconds = (System.currentTimeMillis() - started) / 1000
            val characters = document.chapters.sumOf { it.text.length }
            Log.i("ReaderLarge", "$pages Seiten in ${seconds}s, ${document.chapters.size} Abschnitte, " +
                "$characters Zeichen, ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024} MB belegt, " +
                "Obergrenze ${runtime.maxMemory() / 1024 / 1024} MB")

            assertTrue("A book this size has to hold real text, got $characters characters", characters > 5_000_000)
            assertTrue("The last page has to be readable", document.chapters.last().text.contains("Seite $pages"))
            val reloaded = DocumentStore(context).load(document.id)
            assertNotNull("A book this size has to survive being written and read back", reloaded)
            assertEquals(document.chapters.size, reloaded!!.chapters.size)
            DocumentStore(context).remove(document.id)
        } finally { runCatching { pdf.close() }; file.delete() }
    }
}
