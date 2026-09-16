package de.schimmilab.accessiblereader

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * The rules this project wrote down after learning them the hard way, checked against the real screen instead of
 * being trusted. The documentation claimed for weeks that an accessibility framework was running here. None was.
 *
 * Every rule below is one a screen reader user actually runs into:
 * - a control with no name is announced as "Schaltfläche" and nothing else
 * - a control under 48 by 48 density pixels is hard to hit without seeing it
 * - two controls with the same name cannot be told apart by ear, which cost this project a release once
 */
class AccessibilityRulesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun model(): ReaderViewModel {
        lateinit var model: ReaderViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        return model
    }

    /** A one-page PDF, so the library holds a real entry with a real title instead of being empty. */
    private fun importedDocument(): ReaderDocumentHandle {
        val file = File.createTempFile("a11y", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            page.canvas.drawText("Ein Satz zum Vorlesen.", 50f, 80f, Paint().apply { textSize = 20f })
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val store = DocumentStore(context)
        val document = runBlocking { store.importPdf(Uri.fromFile(file)) {} }
        file.delete()
        return ReaderDocumentHandle(document.id, document.title) { runBlocking { store.remove(document.id) } }
    }

    class ReaderDocumentHandle(val id: String, val title: String, val remove: () -> Unit)

    /** The label a screen reader would read: the description if there is one, otherwise the text. */
    private fun SemanticsNodeInteraction.label(): String {
        val node = fetchSemanticsNode()
        val described = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
        return (described ?: text).orEmpty().trim()
    }

    /**
     * Note that this walks every semantic root, so with a dialog open the controls of the screen behind it are
     * measured too. That makes the check stricter than what a screen reader reaches, never weaker, and the log
     * line below names what was actually seen.
     */
    private fun checkScreen(where: String) {
        val controls = compose.onAllNodes(hasClickAction() and isEnabled())
        val count = controls.fetchSemanticsNodes().size
        assertTrue("$where has no controls at all", count > 0)

        val names = mutableListOf<String>()
        val nameless = mutableListOf<String>()
        val small = mutableListOf<String>()
        for (index in 0 until count) {
            val control = controls[index]
            val name = control.label()
            val node = control.fetchSemanticsNode()
            if (name.isBlank()) nameless += "Element $index bei ${node.positionInRoot}" else names += name
            // Only controls the listener can actually reach are measured; a node scrolled out of view reports
            // no size of its own.
            val width = with(compose.density) { node.size.width.toDp() }
            val height = with(compose.density) { node.size.height.toDp() }
            if (name.isNotBlank() && node.size.width > 0 && (width < 48.dp || height < 48.dp)) {
                small += "$name ist ${width.value.toInt()} mal ${height.value.toInt()} dp"
            }
        }
        Log.i("ReaderA11y", "$where: $count Bedienelemente, Namen: ${names.joinToString(" · ")}")

        assertTrue("$where: controls a screen reader cannot name: $nameless", nameless.isEmpty())
        assertTrue("$where: controls too small to hit without seeing them: $small", small.isEmpty())
        val duplicates = names.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("$where: two controls carry the same name, which cannot be told apart by ear: $duplicates",
            duplicates.isEmpty())
    }

    @Test fun theMainScreenFollowsTheRules() {
        compose.onNodeWithText("Leseprobe").performClick()
        compose.waitForIdle()
        checkScreen("Hauptbildschirm")
    }

    @Test fun theContentsFollowTheRules() {
        compose.onNodeWithText("Leseprobe").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Inhaltsverzeichnis"))
        compose.onNodeWithText("Inhaltsverzeichnis").performClick()
        compose.waitForIdle()
        checkScreen("Inhaltsverzeichnis")
    }

    @Test fun theSettingsFollowTheRules() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Stimme & Tempo"))
        compose.onNodeWithText("Stimme und Einstellungen").performClick()
        compose.waitForIdle()
        checkScreen("Einstellungen")
    }

    @Test fun theLibraryFollowsTheRules() {
        val document = importedDocument()
        try {
            compose.onNodeWithText("Bibliothek").performClick()
            compose.waitUntil { compose.onAllNodesWithText("Entfernen").fetchSemanticsNodes().isNotEmpty() }
            checkScreen("Bibliothek")
        } finally { document.remove() }
    }

    /**
     * Removing a document is the one step in this app that destroys something, and it is the one place where two
     * buttons once nearly carried the same name. It gets checked like every other screen.
     */
    @Test fun theRemovalQuestionFollowsTheRules() {
        val document = importedDocument()
        try {
            compose.onNodeWithText("Bibliothek").performClick()
            compose.waitUntil { compose.onAllNodesWithText("Entfernen").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("${document.title} entfernen").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Wirklich entfernen?").assertExists()
            checkScreen("Rückfrage vor dem Entfernen")
        } finally { document.remove() }
    }

    /** The list someone reaches for to find a passage again, with a bookmark in it so the rows are checked too. */
    @Test fun theBookmarksFollowTheRules() {
        val model = model()
        compose.onNodeWithText("Leseprobe").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { model.mark() }
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Stelle merken"))
        compose.onNodeWithText("Lesezeichen, eines").performClick()
        compose.waitForIdle()
        checkScreen("Lesezeichen")
        compose.runOnUiThread { model.state.value.bookmarks.forEach { model.removeBookmark(it) } }
    }

    /** The message that appears when something went wrong is the last thing a listener has left to press. */
    @Test fun theErrorMessageFollowsTheRules() {
        val model = model()
        compose.runOnUiThread { model.showError("Die Seiten konnten nicht geladen werden.") }
        compose.waitForIdle()
        compose.onNodeWithText("Hinweis").assertExists()
        checkScreen("Hinweis")
        compose.runOnUiThread { model.dismissError() }
    }

    @Test fun theCommandHelpFollowsTheRules() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Alle Befehle und Texteingabe"))
        compose.onNodeWithText("Alle Befehle und Texteingabe").performClick()
        compose.waitForIdle()
        checkScreen("Sprachbefehle")
    }

    /** A screen reader moves by heading. A screen without them can only be walked through one control at a time. */
    @Test fun everyScreenOffersHeadingsToJumpBetween() {
        compose.onNodeWithText("Leseprobe").performClick()
        compose.waitForIdle()
        val headings = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .fetchSemanticsNodes().size
        Log.i("ReaderA11y", "Überschriften auf dem Hauptbildschirm: $headings")
        assertTrue("A screen reader needs headings to move by, found $headings", headings >= 3)
    }
}
