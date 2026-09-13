package de.schimmilab.accessiblereader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A harness rather than a fixed test: it imports whatever PDFs have been placed in the app's own external
 * folder and reports what the app makes of them. Takes PDF and EPUB alike. Generated fixtures say nothing about real books, which carry
 * odd fonts, encodings and column layouts. Skips itself when no PDFs are present.
 *
 * Fill the folder before the run. A debug build reaches its own cache through run-as, and unlike the
 * external app directory that path is readable by the app itself:
 *   adb push book.pdf /data/local/tmp/
 *   adb shell 'cat /data/local/tmp/book.pdf | run-as de.schimmilab.accessiblereader \
 *     sh -c "mkdir -p /data/data/de.schimmilab.accessiblereader/cache/books; \
 *            cat > /data/data/de.schimmilab.accessiblereader/cache/books/book.pdf"'
 * Note that Gradle uninstalls the app after a connected run and takes the folder with it, so install the
 * two APKs with adb and start the run with am instrument.
 */
@RunWith(AndroidJUnit4::class)
class RealBooksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun realBooksAreReadableOrHonestlyRefused() = runBlocking {
        val folder = File(context.cacheDir, "books")
        val pdfs = folder.listFiles { f ->
            f.isFile && (f.name.endsWith(".pdf", ignoreCase = true) || f.name.endsWith(".epub", ignoreCase = true))
        }.orEmpty().sortedBy { it.name }
        assumeTrue("No books in $folder to check", pdfs.isNotEmpty())

        val store = DocumentStore(context)
        val failures = mutableListOf<String>()
        for (file in pdfs) {
            val started = System.currentTimeMillis()
            val result = runCatching { store.import(Uri.fromFile(file)) {} }
            val seconds = (System.currentTimeMillis() - started) / 1000.0
            result.onSuccess { document ->
                val characters = document.chapters.sumOf { it.text.length }
                val empty = document.chapters.count { it.text.isBlank() }
                Log.i("ReaderBooks", "OK       ${file.name}: ${document.chapters.size} Abschnitte, " +
                    "$characters Zeichen, $empty leer, ${seconds}s, Titel '${document.title}'" +
                    (if (!document.paged) ", ohne Seiten" else ""))
                Log.i("ReaderBooks", "   Kapitel: " + document.chapters.take(4).joinToString { "'${it.title}'" })
                assertTrue("${file.name} imported but holds no text", characters > 0)
                assertEquals("${file.name} still carries soft hyphens, words would be spoken in halves",
                    0, document.chapters.sumOf { c -> c.text.count { it == '\u00ad' } })
                val middle = document.chapters[document.chapters.size / 2]
                Log.i("ReaderBooks", "   Probe aus '${middle.title}': " +
                    middle.text.replace("\n", " ⏎ ").take(420))
                store.remove(document.id)
            }.onFailure {
                Log.i("ReaderBooks", "ABGELEHNT ${file.name}: ${it.message} (${seconds}s)")
                failures += "${file.name}: ${it.message}"
            }
        }
        Log.i("ReaderBooks", "Ergebnis: ${pdfs.size - failures.size} von ${pdfs.size} lesbar")
        // Rejections are a legitimate outcome for a scan; the log above is the point of this test.
        assertTrue("Every single PDF was rejected, which points at the importer rather than the files:\n" +
            failures.joinToString("\n"), failures.size < pdfs.size)
    }
}
