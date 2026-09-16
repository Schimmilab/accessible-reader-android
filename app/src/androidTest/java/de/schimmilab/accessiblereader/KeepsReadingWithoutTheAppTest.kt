package de.schimmilab.accessiblereader

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import de.schimmilab.accessiblereader.playback.ReaderPlaybackService
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * A book has to keep reading when the app is swiped out of the recents list. Playback of the current section
 * already survives, because the service outlives the activity, but nothing used to prepare the section after it:
 * the ViewModel owned that, and the ViewModel dies with the activity. So the book stopped at the end of the
 * section a listener happened to be in.
 *
 * The test destroys the activity, which is what swiping away does to the ViewModel, and then watches the
 * progress the service writes once a second to see whether the next section starts on its own.
 */
class KeepsReadingWithoutTheAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Two pages, each long enough to become a section of its own once the pages are grouped. */
    private fun twoSectionPdf(): File {
        val file = File.createTempFile("reader-two-sections", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(2) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                val ink = Paint().apply { textSize = 4f }
                var written = 0
                var y = 8f
                while (written < 16_000 && y < 835f) {
                    val line = "Abschnitt ${index + 1}, Zeile ${(y / 4).toInt()}, ein Satz über ein Haus, einen Garten " +
                        "und einen Brief, der lange genug ist. "
                    page.canvas.drawText(line, 4f, y, ink)
                    written += line.length
                    y += 4.3f
                }
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { runCatching { pdf.close() } }
        return file
    }

    @Test fun theBookContinuesAfterTheAppIsGone() {
        val file = twoSectionPdf()
        val document = try { runBlocking { DocumentStore(context).importPdf(Uri.fromFile(file)) {} } }
            finally { file.delete() }
        Log.i("ReaderAlone", "Prüfbuch: ${document.chapters.size} Abschnitte, " +
            document.chapters.joinToString { "${it.text.length} Zeichen" })
        assertEquals("The fixture has to produce two sections, got ${document.chapters.size} with " +
            document.chapters.joinToString { "${it.text.length} characters" }, 2, document.chapters.size)

        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(60_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())

        val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
        prefs.edit().remove("${document.id}.chapter").remove("${document.id}.item")
            .remove("${document.id}.offset").remove("${document.id}.finished").apply()

        compose.runOnIdle { model.clearCache(); model.open(document); model.chapter(0); model.play() }
        compose.waitUntil(300_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        // Wait for the whole section, otherwise seeking to its end lands in audio that does not exist yet.
        compose.waitUntil(600_000) { !model.state.value.preparing || model.state.value.error != null }
        assertNull(model.state.value.error)

        val remaining = model.state.value.durationMs - model.state.value.positionMs
        Log.i("ReaderAlone", "Abschnitt 1 vorbereitet, ${remaining / 1000}s verbleiben, springe ans Ende")
        compose.runOnIdle { model.seek((remaining / 1000 - 8).toInt()) }
        compose.waitUntil(30_000) { model.state.value.durationMs - model.state.value.positionMs < 15_000 }

        // This is what swiping the app away does to the ViewModel.
        Log.i("ReaderAlone", "Aktivität wird beendet, Abschnitt laut Speicher: ${prefs.getInt("${document.id}.chapter", -1)}")
        compose.activityRule.scenario.close()

        val deadline = System.currentTimeMillis() + 180_000
        var reached = -1
        while (System.currentTimeMillis() < deadline) {
            reached = prefs.getInt("${document.id}.chapter", -1)
            if (reached >= 1) break
            Thread.sleep(1000)
        }
        Log.i("ReaderAlone", "Nach dem Ende der App steht der Speicher bei Abschnitt ${reached + 1}")
        assertEquals("The book has to reach the second section without the app", 1, reached)
    }

    /**
     * The headset button for the next section has to work when the app is gone too. Until now it asked the app,
     * which is not there, so the buttons went nowhere on a book that was otherwise still reading.
     */
    @Test fun theHeadsetButtonStillChangesSectionWithoutTheApp() {
        val file = twoSectionPdf()
        val document = try { runBlocking { DocumentStore(context).importPdf(Uri.fromFile(file)) {} } }
            finally { file.delete() }
        assertEquals(2, document.chapters.size)

        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(60_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())

        val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
        prefs.edit().remove("${document.id}.chapter").remove("${document.id}.item")
            .remove("${document.id}.offset").remove("${document.id}.finished").apply()

        compose.runOnIdle { model.clearCache(); model.open(document); model.chapter(0); model.play() }
        compose.waitUntil(300_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.activityRule.scenario.close()

        // A headset speaks to the session, not to the app. This is the same route.
        val controller = MediaController.Builder(context,
            SessionToken(context, ComponentName(context, ReaderPlaybackService::class.java))).buildAsync()
        val remote = controller.get(30, TimeUnit.SECONDS)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { remote.seekToNext() }
            val deadline = System.currentTimeMillis() + 180_000
            var reached = -1
            while (System.currentTimeMillis() < deadline) {
                reached = prefs.getInt("${document.id}.chapter", -1)
                if (reached >= 1) break
                Thread.sleep(1000)
            }
            Log.i("ReaderAlone", "Nach dem Knopfdruck ohne App steht der Speicher bei Abschnitt ${reached + 1}")
            assertEquals("The button has to reach the second section without the app", 1, reached)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { remote.release() }
        }
    }
}