package de.schimmilab.accessiblereader

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The library must be usable without sight: named rows, and no document lost by a stray tap. */
class LibraryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun fixture(): File {
        val file = File.createTempFile("bibliothek", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            page.canvas.drawText("Ein Text zum Vorlesen, ${System.currentTimeMillis()}.", 50f, 80f, Paint().apply { textSize = 20f })
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        return file
    }

    @Test fun libraryNamesEveryRowAndAsksBeforeRemoving() {
        lateinit var model: ReaderViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        val file = fixture()
        val document = try { runBlocking { DocumentStore(context).importPdf(Uri.fromFile(file)) {} } } finally { file.delete() }
        try {
            compose.runOnIdle { model.library(true) }
            compose.waitUntil(10_000) { model.state.value.library.any { it.id == document.id } }

            val row = model.state.value.library.first { it.id == document.id }
            assertTrue("The row must say how long it is and where the listener left off", row.label.contains("noch nicht gehört"))
            compose.onNodeWithText(row.label).assertExists()

            // Every remove button carries its own document name, otherwise they are indistinguishable by ear.
            compose.onNodeWithContentDescription("${document.title} entfernen").performScrollTo().performClick()
            compose.onNodeWithText("Wirklich entfernen?").assertIsDisplayed()
            compose.onNodeWithText("Abbrechen").performClick()

            compose.runOnIdle { assertNull(model.state.value.pendingRemoval) }
            assertTrue("Cancelling must keep the document", model.state.value.library.any { it.id == document.id })

            compose.onNodeWithContentDescription("${document.title} entfernen").performScrollTo().performClick()
            compose.onNodeWithText("Ja, entfernen").performClick()
            compose.waitUntil(10_000) { model.state.value.library.none { it.id == document.id } }
        } finally {
            compose.runOnIdle { model.askRemoval(null); model.library(false) }
            DocumentStore(context).remove(document.id)
        }
    }
}
