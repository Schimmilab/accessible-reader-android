package de.schimmilab.accessiblereader

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = DocumentStore(context)
    private val directory = File(context.filesDir, "documents")

    @Before fun emptyLibrary() { directory.listFiles().orEmpty().forEach { it.delete() } }

    private fun fixture(name: String): File {
        val file = File.createTempFile(name, ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            page.canvas.drawText("Inhalt von $name zum Vorlesen.", 50f, 80f, Paint().apply { textSize = 20f })
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        return file
    }

    @Test fun importedDocumentsAreListedNewestFirstAndCanBeRemoved() = runBlocking {
        val first = fixture("erstes")
        val second = fixture("zweites")
        try {
            val a = store.importPdf(Uri.fromFile(first)) {}
            Thread.sleep(20) // distinct timestamps, so the ordering assertion means something
            val b = store.importPdf(Uri.fromFile(second)) {}
            assertNotEquals("Different content must yield different ids", a.id, b.id)

            val listed = store.library()
            assertEquals(2, listed.size)
            assertEquals("Newest import comes first", b.id, listed.first().id)
            assertEquals(b.title, listed.first().title)
            assertEquals(b.chapters.size, listed.first().chapters)

            store.remove(a.id)
            val afterRemoval = store.library()
            assertEquals(listOf(b.id), afterRemoval.map { it.id })
            assertNull("Removing must drop the extracted text too", store.load(a.id))
        } finally { first.delete(); second.delete() }
    }

    @Test fun documentsFromOlderVersionsWithoutSideFileAreStillListed() = runBlocking {
        val file = fixture("altbestand")
        try {
            val document = store.importPdf(Uri.fromFile(file)) {}
            assertTrue(File(directory, "${document.id}.meta.json").delete())

            val listed = store.library()
            assertEquals(listOf(document.id), listed.map { it.id })
            assertEquals(document.title, listed.first().title)
            assertTrue("The missing side file must be written back", File(directory, "${document.id}.meta.json").isFile)
        } finally { file.delete() }
    }

    @Test fun anEmptyLibraryIsNotAnError() = runBlocking {
        assertEquals(emptyList<String>(), store.library().map { it.id })
    }
}
