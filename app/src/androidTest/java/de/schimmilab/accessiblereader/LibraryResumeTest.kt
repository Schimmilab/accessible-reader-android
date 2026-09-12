package de.schimmilab.accessiblereader

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The reported symptom: a PDF played fine, then reopening it from the library said there was no prepared
 * audio. Covers the whole round trip through the library rather than the shortcut ResumePositionTest takes.
 */
class LibraryResumeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun fixture(pages: Int): File {
        val file = File.createTempFile("resume", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(pages) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                page.canvas.drawText(
                    "Seite ${index + 1}. Ein Satz zum Vorlesen, lang genug für mehrere Audioteile in diesem Abschnitt.",
                    40f, 80f, Paint().apply { textSize = 18f })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        return file
    }

    @Test fun aDocumentReopenedFromTheLibraryContinuesWhereItStopped() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())

        val file = fixture(3)
        val document = try { runBlocking { DocumentStore(context).importPdf(Uri.fromFile(file)) {} } } finally { file.delete() }
        try {
            // Listen to it, the way a user would.
            compose.runOnIdle { model.openFromLibrary(document.id) }
            compose.waitUntil(15_000) { model.state.value.document.id == document.id }
            compose.runOnIdle { model.chapter(0); model.play() }
            compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
            assertNull("Playing a freshly imported document must work", model.state.value.error)
            compose.waitUntil(240_000) { !model.state.value.preparing }
            compose.waitUntil(30_000) { model.state.value.positionMs > 4_000 }
            val stopped = model.state.value.positionMs
            compose.runOnIdle { model.pause() }
            Thread.sleep(1_500) // the service stores the position about once per second

            // Leave it and come back through the library, which is what the report described.
            compose.runOnIdle { model.demo() }
            compose.waitUntil(10_000) { model.state.value.document.id != document.id }
            compose.runOnIdle { model.library(true) }
            compose.waitUntil(15_000) { model.state.value.library.any { it.id == document.id } }

            val row = model.state.value.library.first { it.id == document.id }
            assertTrue("The library row has to show that this document was already heard, was '${row.label}'",
                row.label.contains("zuletzt bei"))

            compose.runOnIdle { model.openFromLibrary(document.id) }
            compose.waitUntil(15_000) { model.state.value.document.id == document.id && !model.state.value.showLibrary }
            compose.runOnIdle { model.play() }
            compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
            assertNull("Reopening from the library and playing must work", model.state.value.error)

            val resumed = model.state.value.positionMs
            assertTrue("Expected to continue near $stopped ms, but started at $resumed ms", abs(resumed - stopped) < 6_000)
        } finally {
            compose.runOnIdle { model.pause() }
            DocumentStore(context).remove(document.id)
        }
    }
}
