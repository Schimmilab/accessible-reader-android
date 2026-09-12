package de.schimmilab.accessiblereader

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.demoDocument
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * The blind test user named exactly one requirement: the reader has to come back where it stopped, even after closing it.
 * She assumed that goes without saying, which is why it is worth an explicit test.
 */
class ResumePositionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    @Test fun theReaderComesBackWhereItStopped() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())
        compose.runOnIdle { model.demo(); model.chapter(0); model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.waitUntil(240_000) { !model.state.value.preparing }

        compose.runOnIdle { model.seek(20) }
        compose.waitUntil(10_000) { model.state.value.positionMs > 18_000 }
        val stopped = model.state.value.positionMs
        compose.runOnIdle { model.pause() }
        Thread.sleep(1_500) // the playback service stores the position about once per second

        val prefs = compose.activity.getSharedPreferences("reader", Context.MODE_PRIVATE)
        val id = model.state.value.document.id
        assertEquals("The document has to be remembered", id, prefs.getString("document", ""))
        assertEquals("The chapter has to be remembered", 0, prefs.getInt("$id.chapter", -1))
        assertFalse("A paused chapter is not a finished one", prefs.getBoolean("$id.finished", false))
        val storedItem = prefs.getInt("$id.item", -1)
        assertTrue("The audio part has to be remembered", storedItem >= 0)

        // Drops everything held in memory, exactly as closing the app would; only the stored position survives.
        compose.runOnIdle { model.open(demoDocument()) }
        assertEquals(0L, model.state.value.positionMs)
        compose.runOnIdle { model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)

        val resumed = model.state.value.positionMs
        assertTrue("Expected to continue near $stopped ms, but started at $resumed ms", abs(resumed - stopped) < 5_000)
        compose.runOnIdle { model.pause() }
    }
}
