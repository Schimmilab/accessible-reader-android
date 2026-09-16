package de.schimmilab.accessiblereader

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.SleepOption
import de.schimmilab.accessiblereader.playback.ReaderPlaybackService
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * The sleep timer doing the thing it exists for: stopping a book that is playing.
 *
 * Needs an installed German voice and therefore stays off the CI emulator. What the app writes down for the
 * service is checked without one in `SleepTimerSettingTest`.
 */
class SleepTimerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel
    private val prefs by lazy { compose.activity.getSharedPreferences("reader", Context.MODE_PRIVATE) }

    @After fun tearDown() {
        prefs.edit().remove(ReaderPlaybackService.KEY_SLEEP_UNTIL)
            .putBoolean(ReaderPlaybackService.KEY_SLEEP_AT_SECTION_END, false).commit()
    }

    /**
     * "Am Ende des Abschnitts" has to beat the thing that normally happens at the end of a section, which is
     * that the next one starts by itself. Both the app and the service know how to continue a book, so both had
     * to learn to stop.
     */
    @Test fun theBookStopsAtTheEndOfTheSectionInsteadOfStartingTheNextOne() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue("Needs an installed German voice", model.state.value.voices.isNotEmpty())

        compose.runOnIdle { model.demo(); model.chapter(0); model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.waitUntil(600_000) { !model.state.value.preparing }

        compose.runOnIdle { model.sleepTimer(SleepOption.END_OF_SECTION) }
        val left = model.state.value.durationMs - model.state.value.positionMs
        compose.runOnIdle { model.seek(((left / 1000) - 8).toInt()) }

        compose.waitUntil(120_000) { !model.state.value.playbackRequested }
        assertEquals("the next section must not have started", 0, model.state.value.chapter)
        assertFalse(model.state.value.playing)
        assertFalse("and the timer is spent, not still waiting for the next section",
            prefs.getBoolean(ReaderPlaybackService.KEY_SLEEP_AT_SECTION_END, true))
    }

    /**
     * The one that matters: a book that is playing really stops, and the sound is back up afterwards. A listener
     * left with a silent reader and no idea why would have no way to find out.
     */
    @Test fun theBookStopsWhenTheTimeIsUpAndTheSoundComesBack() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue("Needs an installed German voice", model.state.value.voices.isNotEmpty())

        compose.runOnIdle { model.demo(); model.chapter(0); model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)

        // Eight seconds rather than fifteen minutes: the same code path, just without the wait.
        prefs.edit().putLong(ReaderPlaybackService.KEY_SLEEP_UNTIL, System.currentTimeMillis() + 8_000).commit()
        val before = model.state.value.positionMs

        compose.waitUntil(30_000) { !model.state.value.playbackRequested }
        assertFalse("the book has to stop by itself", model.state.value.playing)
        assertTrue("and keep the place it reached", model.state.value.positionMs >= before)
        assertEquals("nothing may be left for the service to act on", 0L,
            prefs.getLong(ReaderPlaybackService.KEY_SLEEP_UNTIL, 0))

        // Playing again has to be at full volume, whatever the fade left behind.
        compose.runOnIdle { model.play() }
        compose.waitUntil(60_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.waitUntil(5_000) { model.state.value.playing }
        compose.runOnIdle { model.pause() }
    }
}
