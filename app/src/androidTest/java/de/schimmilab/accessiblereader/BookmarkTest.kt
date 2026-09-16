package de.schimmilab.accessiblereader

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.demoDocument
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONArray

/**
 * Remembering a place in a book and finding it again, all the way through the app.
 *
 * Needs no speech engine: marking a place and keeping it is not playback. What is checked is that the mark
 * survives the app being closed, because a listener who marks a passage and comes back tomorrow is the only
 * reason this exists.
 */
class BookmarkTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    @Test fun aMarkedPlaceIsKeptAndSaidOutLoud() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        val prefs = compose.activity.getSharedPreferences("reader", Context.MODE_PRIVATE)
        val id = demoDocument().id
        prefs.edit().remove("$id.bookmarks").commit()

        compose.runOnIdle { model.demo() }
        compose.waitUntil(10_000) { model.state.value.document.id == id }
        assertTrue("a fresh document has no bookmarks", model.state.value.bookmarks.isEmpty())

        compose.runOnIdle { model.chapter(1); model.mark() }
        val bookmarks = model.state.value.bookmarks
        assertEquals(1, bookmarks.size)
        assertEquals(1, bookmarks.first().chapter)
        assertTrue("the listener has to be told it worked, a screen reader does not say it by itself",
            model.state.value.status.startsWith("Lesezeichen gesetzt:"))
        assertTrue("the bookmark names where it is", bookmarks.first().label.isNotBlank())

        // Written down, not just held in memory: this has to survive the app being closed.
        val stored = JSONArray(prefs.getString("$id.bookmarks", "[]"))
        assertEquals(1, stored.length())
        assertEquals(1, stored.getJSONObject(0).getInt("chapter"))

        // Pressing again in the same place, because you could not tell whether it worked.
        compose.runOnIdle { model.mark() }
        assertEquals("the same place twice is one bookmark", 1, model.state.value.bookmarks.size)

        compose.runOnIdle { model.chapter(2); model.mark() }
        assertEquals(2, model.state.value.bookmarks.size)
        assertEquals("the newest comes first", 2, model.state.value.bookmarks.first().chapter)

        // A fresh model, as after closing the app, reads them back.
        compose.runOnIdle { model.open(demoDocument()) }
        assertEquals(2, model.state.value.bookmarks.size)

        compose.runOnIdle { model.removeBookmark(model.state.value.bookmarks.first()) }
        assertEquals(1, model.state.value.bookmarks.size)
        assertEquals(1, JSONArray(prefs.getString("$id.bookmarks", "[]")).length())
        prefs.edit().remove("$id.bookmarks").commit()
    }

    @Test fun theBookmarkListIsReachableByVoice() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.runOnIdle { model.demo(); model.command("Lesezeichen") }
        assertTrue(model.state.value.showBookmarks)
        compose.runOnIdle { model.bookmarks(false); model.command("Stelle merken") }
        assertEquals(1, model.state.value.bookmarks.size)
        compose.runOnIdle {
            model.state.value.bookmarks.forEach { model.removeBookmark(it) }
        }
    }
}
