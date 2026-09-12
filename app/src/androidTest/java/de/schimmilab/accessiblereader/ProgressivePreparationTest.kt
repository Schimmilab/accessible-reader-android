package de.schimmilab.accessiblereader

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.Chapter
import de.schimmilab.accessiblereader.core.ReaderDocument
import de.schimmilab.accessiblereader.core.TextChunks
import de.schimmilab.accessiblereader.playback.ReaderPlaybackService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Playback must start after the first audio part; the rest is prepared while listening. */
class ProgressivePreparationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    private val longText = buildString {
        repeat(40) { append("Satz Nummer ${it + 1} dieser langen Leseprobe erzählt weiter von einem alten Haus, einem Garten und einem Brief. ") }
    }
    private val document = ReaderDocument("progressive-test", "Langes Kapitel", listOf(
        Chapter("Erster Teil", 1, 1, longText), Chapter("Zweiter Teil", 2, 2, "Kurzer zweiter Teil.")))

    private fun open() {
        assertTrue("Test needs several parts", TextChunks.split(longText).size >= 4)
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())
        compose.runOnIdle { model.clearCache(); model.open(document); model.chapter(0) }
    }

    @Test fun playbackStartsBeforeAllPartsArePrepared() {
        open()
        compose.runOnIdle { model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        val started = model.state.value
        assertNull(started.error)
        assertTrue("Playback should start while later parts are still being prepared", started.preparing)
        assertFalse("Controls must be usable during background preparation", started.busy)
        compose.waitUntil(240_000) { !model.state.value.preparing || model.state.value.error != null }
        val finished = model.state.value
        assertNull(finished.error)
        assertTrue("Duration grows as parts are appended", finished.durationMs > started.durationMs)
        assertTrue("Still playing after preparation", finished.playbackRequested)
        compose.runOnIdle { model.pause() }
    }

    @Test fun playbackDoesNotStartOnTheChapterIntroAlone() {
        // Demo chapter 0 = spoken intro (~3 s) + one text part. Starting on the intro alone caused a silent gap.
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())
        compose.runOnIdle { model.clearCache(); model.demo(); model.chapter(0); model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        assertTrue("Playback must not start on the ~3 s intro alone; a real text part has to be buffered first (was ${model.state.value.durationMs} ms)",
            model.state.value.durationMs >= ReaderViewModel.MIN_LEAD_MS)
        compose.runOnIdle { model.pause() }
    }

    /**
     * An empty buffer reports the same player state as a real chapter end, and a chapter wrongly marked finished
     * restarts from the beginning instead of resuming. Since the minimum lead the emulator rarely drains at all,
     * so this guards the contract rather than reproducing the failure: while preparing, finished must stay false.
     */
    @Test fun theFinishedFlagStaysFalseWhileAChapterIsStillBeingPrepared() {
        open()
        val prefs = compose.activity.getSharedPreferences("reader", Context.MODE_PRIVATE)
        compose.runOnIdle { prefs.edit().putBoolean("${document.id}.finished", false).apply(); model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)

        var sawFinishedFlag = false
        var sawPreparingFlag = false
        val deadline = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < deadline && model.state.value.preparing) {
            if (prefs.getBoolean("${document.id}.finished", false)) sawFinishedFlag = true
            if (prefs.getBoolean(ReaderPlaybackService.KEY_PREPARING, false)) sawPreparingFlag = true
            Thread.sleep(100)
        }
        compose.runOnIdle { model.pause() }
        assertTrue("The service has to be told that a preparation is running", sawPreparingFlag)
        assertFalse("A gap between prepared parts is not the end of the chapter", sawFinishedFlag)
        assertFalse("The flag must be cleared once preparation ends", prefs.getBoolean(ReaderPlaybackService.KEY_PREPARING, true))
    }

    @Test fun chapterChangeStopsBackgroundPreparation() {
        open()
        compose.runOnIdle { model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.runOnIdle { model.chapter(1) }
        compose.waitUntil(120_000) { model.state.value.chapter == 1 && model.state.value.playing && !model.state.value.preparing }
        assertEquals("Zweiter Teil", model.state.value.document.chapters[model.state.value.chapter].title)
        compose.runOnIdle { model.pause() }
    }
}
