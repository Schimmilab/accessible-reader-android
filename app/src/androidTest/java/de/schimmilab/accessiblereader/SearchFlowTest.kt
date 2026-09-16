package de.schimmilab.accessiblereader

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.demoDocument
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Searching a book from inside the app, and what the listener is told about it.
 *
 * Needs no speech engine: finding a passage is not playback. Starting to read at one does need a voice and is
 * covered where the other playback tests are.
 */
class SearchFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    @Test fun aPassageIsFoundAndTheCountIsSaidOutLoud() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.runOnIdle { model.demo() }
        compose.waitUntil(10_000) { model.state.value.document.id == demoDocument().id }

        compose.runOnIdle { model.search("Inhaltsverzeichnis") }
        val hits = model.state.value.hits
        assertTrue("the sample text says the word more than once, found ${hits.size}", hits.isNotEmpty())
        assertTrue("a screen reader reads the list only once someone has found it, so the count is spoken",
            model.state.value.status.contains("Fundstelle"))
        assertTrue("every hit names the section it is in", hits.all { it.chapterTitle.isNotBlank() })
        assertTrue("and the part it can be heard in", hits.all { it.item >= 1 })
        assertTrue("and reads back the words around it", hits.all { it.context.contains("nhaltsverzeichnis") })

        // Spelling an umlaut the other way has to find the same thing.
        compose.runOnIdle { model.search("Fuer") }
        val other = model.state.value.hits
        compose.runOnIdle { model.search("Für") }
        assertEquals(other.size, model.state.value.hits.size)

        compose.runOnIdle { model.search("Hubschrauber") }
        assertTrue(model.state.value.hits.isEmpty())
        assertEquals("Keine Fundstelle für Hubschrauber.", model.state.value.status)
    }

    @Test fun searchingIsReachableByVoiceWithTheWordsAsSpoken() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.runOnIdle { model.demo(); model.command("Suche nach Inhaltsverzeichnis") }
        assertTrue(model.state.value.showSearch)
        assertEquals("Inhaltsverzeichnis", model.state.value.query)
        assertTrue(model.state.value.hits.isNotEmpty())
        compose.runOnIdle { model.searchDialog(false) }
        assertTrue("closing the dialog clears the search", model.state.value.hits.isEmpty())
    }

    /** A hit hands the same three numbers to the same place a bookmark does, so it is really reachable. */
    @Test fun aHitIsHandedToTheResumePosition() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        val prefs = compose.activity.getSharedPreferences("reader", Context.MODE_PRIVATE)
        compose.runOnIdle { model.demo() }
        compose.waitUntil(10_000) { model.state.value.document.id == demoDocument().id }
        compose.runOnIdle { model.search("Inhaltsverzeichnis") }
        val hit = model.state.value.hits.last()

        compose.runOnIdle { model.goToHit(hit) }
        val id = demoDocument().id
        assertEquals(hit.chapter, prefs.getInt("$id.chapter", -1))
        assertEquals(hit.item, prefs.getInt("$id.item", -1))
        assertEquals(0L, prefs.getLong("$id.offset", -1))
        assertFalse("a passage someone jumped to is not a finished section",
            prefs.getBoolean("$id.finished", true))
        assertEquals(hit.chapter, model.state.value.chapter)
        compose.runOnIdle { model.pause() }
    }
}
