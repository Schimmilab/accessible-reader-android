package de.schimmilab.accessiblereader

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.Chapter
import de.schimmilab.accessiblereader.core.ReaderDocument
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * A book without PDF bookmarks becomes one section per page. A listener reported having to work through such a
 * book page by page, so this checks what actually happens at the end of a page: does the next one start on its
 * own, and how long is the silence in between.
 */
class PageSectionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    private val document = ReaderDocument("page-sections-test", "Buch ohne Kapitelmarken",
        (1..4).map { page -> Chapter("Seite $page", page, page,
            "Das ist der Text von Seite $page. Er ist kurz, damit die Seite schnell zu Ende ist.") })

    @Test fun theNextPageStartsWithoutBeingAskedTo() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(30_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())

        compose.runOnIdle { model.clearCache(); model.open(document); model.chapter(0); model.play() }
        compose.waitUntil(120_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        assertEquals("Should start on the first page", 0, model.state.value.chapter)

        // Nothing is pressed from here on. Every further page has to arrive by itself.
        var lastChapter = 0
        var lastChange = System.currentTimeMillis()
        val gaps = mutableListOf<Long>()
        compose.waitUntil(300_000) {
            val s = model.state.value
            if (s.chapter != lastChapter) {
                gaps += System.currentTimeMillis() - lastChange
                Log.i("ReaderPages", "Seite ${lastChapter + 1} -> ${s.chapter + 1} nach ${gaps.last()}ms, " +
                    "spielt=${s.playing}, angefordert=${s.playbackRequested}")
                lastChapter = s.chapter
                lastChange = System.currentTimeMillis()
            }
            s.chapter == document.chapters.lastIndex || s.error != null
        }
        Log.i("ReaderPages", "Endstand: Abschnitt ${model.state.value.chapter + 1} von ${document.chapters.size}, " +
            "Fehler ${model.state.value.error}, Pausen ${gaps}")
        compose.runOnIdle { model.pause() }

        assertNull("Reading a page-per-section book failed: ${model.state.value.error}", model.state.value.error)
        assertEquals("The book has to run to its last page without anyone pressing anything",
            document.chapters.lastIndex, model.state.value.chapter)
    }
}
