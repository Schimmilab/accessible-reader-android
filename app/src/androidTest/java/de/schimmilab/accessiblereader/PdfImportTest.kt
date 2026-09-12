package de.schimmilab.accessiblereader

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfImportTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun fixture(blank: Boolean): File {
        val file = File.createTempFile("reader-test", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(2) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                if (!blank) page.canvas.drawText("Testseite ${index + 1}. Ein Text zum Vorlesen.", 50f, 80f, Paint().apply { textSize = 20f })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        return file
    }
    @Test fun extractsTextAndUsesPagesWithoutInventingChapters() = runBlocking {
        val file = fixture(false)
        try {
            val store = DocumentStore(context)
            val document = store.importPdf(Uri.fromFile(file)) {}
            assertEquals(2, document.chapters.size)
            assertEquals("Seite 1", document.chapters[0].title)
            assertTrue(document.chapters[1].text.contains("Testseite 2"))
            assertEquals(document, store.load(document.id))
        } finally { file.delete() }
    }
    /** A 600-page novel was refused by the old 300-page limit. Real books have to go through. */
    @Test fun aBookSizedPdfIsImported() = runBlocking {
        val pages = 600
        val file = File.createTempFile("reader-book", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(pages) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                page.canvas.drawText("Seite ${index + 1}. Ein Satz zum Vorlesen aus einem langen Roman.",
                    50f, 80f, Paint().apply { textSize = 20f })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
            pdf.close()

            val started = System.currentTimeMillis()
            val document = DocumentStore(context).importPdf(Uri.fromFile(file)) {}
            val seconds = (System.currentTimeMillis() - started) / 1000.0
            android.util.Log.i("ReaderImportTest", "$pages Seiten in $seconds s, ${document.chapters.size} Abschnitte")

            assertEquals(pages, document.chapters.size)
            assertTrue("Last page must be readable", document.chapters.last().text.contains("Seite $pages"))
            assertEquals(document, DocumentStore(context).load(document.id))
        } finally { runCatching { pdf.close() }; file.delete() }
    }

    /**
     * Blank pages carry nothing for either text extraction or recognition. The app used to work through to the
     * very last page before admitting it, and for a 500-page book that is minutes of waiting for a no.
     */
    @Test fun aScannedBookIsRejectedAfterASampleRatherThanAtTheEnd() = runBlocking {
        val pages = 200
        val file = File.createTempFile("reader-scan", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(pages) { index ->
                pdf.finishPage(pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create()))
            }
            file.outputStream().use(pdf::writeTo)
            pdf.close()

            // Counted from the message, not from the number of callbacks: a scanned page reports twice, once for
            // reading it and once for recognizing it.
            var read = 0
            val number = Regex("Seite (\\d+) von")
            val error = runCatching {
                DocumentStore(context).importPdf(Uri.fromFile(file)) { message ->
                    number.find(message)?.groupValues?.get(1)?.toInt()?.let { read = maxOf(read, it) }
                }
            }.exceptionOrNull()

            assertNotNull("A book without any writing has to be refused", error)
            assertTrue(error!!.message!!, error.message!!.contains("keine Schrift"))
            assertTrue("The message has to say that recognition was tried too",
                error.message!!.contains("Texterkennung"))
            assertTrue("Read $read of $pages pages before giving up, that is too many",
                read <= DocumentStore.SCAN_PROBE_PAGES + 2)
        } finally { runCatching { pdf.close() }; file.delete() }
    }

    @Test fun aPdfWithoutAnyWritingIsRefused() = runBlocking {
        val file = fixture(true)
        try {
            val error = runCatching { DocumentStore(context).importPdf(Uri.fromFile(file)) {} }.exceptionOrNull()
            assertNotNull(error)
            // Too short to be caught by the sample, so the check at the end has to say the same thing.
            assertTrue(error!!.message!!, error.message!!.contains("keine Schrift"))
            assertTrue(error.message!!.contains("Texterkennung"))
        } finally { file.delete() }
    }

    /**
     * The point of the whole exercise: most books our test reader owns are scans, and a scan holds no text
     * objects at all. Here the words exist only as pixels, exactly as they do in a scanned book.
     */
    @Test fun aScannedPageIsReadByTextRecognition() = runBlocking {
        val file = File.createTempFile("reader-scanned", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(2) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(1240, 1754, index + 1).create())
                // Painted into a bitmap first: that leaves pixels behind instead of text objects.
                val paper = android.graphics.Bitmap.createBitmap(1240, 1754, android.graphics.Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(paper).apply {
                    drawColor(android.graphics.Color.WHITE)
                    val ink = Paint().apply { color = android.graphics.Color.BLACK; textSize = 54f; isAntiAlias = true }
                    drawText("Der Garten war still.", 90f, 300f, ink)
                    drawText("Seite ${index + 1} wurde erkannt.", 90f, 420f, ink)
                }
                page.canvas.drawBitmap(paper, 0f, 0f, null)
                paper.recycle()
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
            pdf.close()

            val started = System.currentTimeMillis()
            val document = DocumentStore(context).importPdf(Uri.fromFile(file)) {}
            val seconds = (System.currentTimeMillis() - started) / 1000.0
            val text = document.chapters.joinToString("\n") { it.text }
            android.util.Log.i("ReaderImportTest", "Scan erkannt in $seconds s: ${text.replace("\n", " ⏎ ")}")

            assertTrue("Recognition missed the sentence entirely: $text", text.contains("Der Garten war still"))
            assertTrue("Recognition did not reach the second page: $text", text.contains("Seite 2"))
            assertTrue("The notice has to admit that recognition was used",
                document.notice.contains("Texterkennung"))
        } finally { runCatching { pdf.close() }; file.delete() }
    }
}
