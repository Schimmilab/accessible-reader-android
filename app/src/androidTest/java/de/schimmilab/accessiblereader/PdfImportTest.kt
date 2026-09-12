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
     * A scanned book used to be read to the very last page before the app admitted it was useless.
     * For a 500-page scan that is minutes of waiting for a no.
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

            var read = 0
            val error = runCatching { DocumentStore(context).importPdf(Uri.fromFile(file)) { read++ } }.exceptionOrNull()

            assertNotNull("A scan has to be refused", error)
            assertTrue(error!!.message!!, error.message!!.contains("keinen Text"))
            assertTrue("The message has to name text recognition as what is missing",
                error.message!!.contains("Texterkennung"))
            assertTrue("Read $read of $pages pages before giving up, that is too many",
                read <= DocumentStore.SCAN_PROBE_PAGES + 2)
        } finally { runCatching { pdf.close() }; file.delete() }
    }

    @Test fun imageOnlyPdfReportsMissingOcr() = runBlocking {
        val file = fixture(true)
        try {
            val error = runCatching { DocumentStore(context).importPdf(Uri.fromFile(file)) {} }.exceptionOrNull()
            assertNotNull(error)
            // A short scan cannot be caught by the sample, so the check at the end has to say the same thing.
            assertTrue(error!!.message!!, error.message!!.contains("keinen Text"))
            assertTrue(error.message!!.contains("Texterkennung"))
        } finally { file.delete() }
    }
}
