package de.schimmilab.accessiblereader

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Reproduces what a listener reported with a neural voice: pressing read aloud makes the app churn for over a
 * minute and then give up. The stock engines are so much faster than playback that the design never met this.
 */
class SlowVoiceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel
    private val slowEngine = "com.CodeBySonu.VoxSherpa"

    @Test fun aNeuralVoiceReachesTheFirstSoundInReasonableTime() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected }
        assumeTrue("Needs the neural engine installed", model.state.value.engines.containsKey(slowEngine))

        compose.runOnIdle { model.engine(slowEngine) }
        compose.waitUntil(40_000) { model.state.value.voices.isNotEmpty() || model.state.value.error != null }
        assumeTrue("The neural engine reports no German voice", model.state.value.voices.isNotEmpty())
        Log.i("ReaderSlow", "Stimme: ${model.state.value.voiceId}")

        compose.runOnIdle { model.clearCache(); model.demo(); model.chapter(0) }
        val started = System.currentTimeMillis()
        compose.runOnIdle { model.play() }
        compose.waitUntil(300_000) { model.state.value.playing || model.state.value.error != null }
        val toFirstSound = System.currentTimeMillis() - started
        Log.i("ReaderSlow", "Bis zum ersten Ton: ${toFirstSound}ms, Fehler: ${model.state.value.error}")
        assertNull("Preparation failed: ${model.state.value.error}", model.state.value.error)

        // Now the part the listener actually hit: switching section while the voice is still slow.
        val switched = System.currentTimeMillis()
        compose.runOnIdle { model.chapter(1) }
        compose.waitUntil(300_000) { model.state.value.playing || model.state.value.error != null }
        Log.i("ReaderSlow", "Abschnittswechsel bis zum Ton: ${System.currentTimeMillis() - switched}ms, " +
            "Fehler: ${model.state.value.error}")
        assertNull("Switching section failed: ${model.state.value.error}", model.state.value.error)

        compose.waitUntil(300_000) { !model.state.value.preparing || model.state.value.error != null }
        Log.i("ReaderSlow", "Kapitel fertig vorbereitet, Fehler: ${model.state.value.error}, " +
            "spielt noch: ${model.state.value.playbackRequested}")
        assertNull("Background preparation failed: ${model.state.value.error}", model.state.value.error)
        compose.runOnIdle { model.pause() }

        assertTrue("First sound took ${toFirstSound / 1000} s, a listener gives up long before that",
            toFirstSound < 25_000)
    }

    /**
     * The same switch, but without the spoken announcement. If this one works while the announced switch hangs,
     * the cause is the second TextToSpeech instance talking to an engine that has only one synthesizer.
     */
    @Test fun aSilentSectionSwitchWorksWithTheSameVoice() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected }
        assumeTrue("Needs the neural engine installed", model.state.value.engines.containsKey(slowEngine))
        compose.runOnIdle { model.engine(slowEngine) }
        compose.waitUntil(40_000) { model.state.value.voices.isNotEmpty() || model.state.value.error != null }
        assumeTrue("The neural engine reports no German voice", model.state.value.voices.isNotEmpty())

        compose.runOnIdle { model.clearCache(); model.demo(); model.chapter(0, announce = false) }
        compose.runOnIdle { model.play() }
        compose.waitUntil(200_000) { model.state.value.playing || model.state.value.error != null }
        assertNull("First playback failed: ${model.state.value.error}", model.state.value.error)

        val switched = System.currentTimeMillis()
        compose.runOnIdle { model.chapter(1, announce = false) }
        compose.waitUntil(200_000) { model.state.value.playing || model.state.value.error != null }
        Log.i("ReaderSlow", "STILLER Wechsel bis zum Ton: ${System.currentTimeMillis() - switched}ms, " +
            "Fehler: ${model.state.value.error}")
        assertNull("Silent switch failed: ${model.state.value.error}", model.state.value.error)
        assertTrue("No sound after a silent section switch", model.state.value.playing)
        compose.runOnIdle { model.pause() }
    }
}
