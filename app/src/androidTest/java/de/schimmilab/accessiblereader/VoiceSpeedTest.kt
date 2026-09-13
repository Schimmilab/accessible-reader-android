package de.schimmilab.accessiblereader

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * The app times every piece it synthesizes. This checks that the measurement reaches the listener instead of
 * staying in the log: a voice that cannot keep up with listening decides whether a book plays through, and
 * nothing else on the screen shows it.
 */
class VoiceSpeedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel
    private val slowEngine = "com.CodeBySonu.VoxSherpa"
    private val stockEngine = "com.google.android.tts"

    @Test fun aSlowVoiceIsNamedAfterItHasBeenMeasured() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(60_000) { model.state.value.connected && model.state.value.engines.isNotEmpty() }
        assumeTrue("Needs the neural engine installed", model.state.value.engines.containsKey(slowEngine))
        compose.runOnIdle { model.engine(slowEngine) }
        compose.waitUntil(40_000) { model.state.value.voices.isNotEmpty() || model.state.value.error != null }
        assumeTrue("The neural engine reports no German voice", model.state.value.voices.isNotEmpty())
        val slow = model.state.value.voices.firstOrNull { it.id.contains("thorsten") } ?: model.state.value.voices.first()
        compose.runOnIdle { model.clearCache(); model.voice(slow.id); model.demo(); model.chapter(0); model.play() }
        compose.waitUntil(300_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.waitUntil(120_000) { model.state.value.voiceSpeed != null }
        compose.runOnIdle { model.pause() }

        val note = model.state.value.voiceSpeed
        Log.i("ReaderSpeedNote", "Hinweis zur Stimme ${slow.id}: $note")
        assertNotNull("A voice this slow has to be named", note)
        assertTrue("The note has to say how fast it speaks: $note", note!!.contains("Wörter je Minute"))
        assertTrue("And that it cannot keep ahead of listening: $note",
            note.contains("Hörzeit") || note.contains("Zuhören"))
    }

    @Test fun aFastVoiceIsDescribedButNotWarnedAbout() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(60_000) { model.state.value.connected && model.state.value.engines.isNotEmpty() }
        // The other test switches the engine and that choice is remembered, so say which one this needs.
        assumeTrue("Needs the stock engine", model.state.value.engines.containsKey(stockEngine))
        compose.runOnIdle { model.engine(stockEngine) }
        compose.waitUntil(40_000) { model.state.value.voices.isNotEmpty() || model.state.value.error != null }
        assumeTrue(model.state.value.voices.isNotEmpty())
        val fast = model.state.value.voices.first { !it.needsNetwork }
        compose.runOnIdle { model.clearCache(); model.voice(fast.id); model.demo(); model.chapter(0); model.play() }
        compose.waitUntil(200_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.waitUntil(60_000) { !model.state.value.preparing }
        compose.runOnIdle { model.pause() }
        val note = model.state.value.voiceSpeed
        Log.i("ReaderSpeedNote", "Schnelle Stimme ${fast.id}: $note")
        assertNotNull("Every measured voice says how fast it speaks", note)
        assertTrue("A voice that is far ahead of listening gets no warning: $note",
            note!!.contains("Wörter je Minute") && !note.contains("Zuhören") && !note.contains("Hörzeit"))
    }
}
