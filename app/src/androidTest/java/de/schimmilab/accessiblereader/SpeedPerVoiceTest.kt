package de.schimmilab.accessiblereader

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Voices do not speak at the same rate at their own natural speed. A listener had to take a neural voice down
 * noticeably to keep up with it, while the stock voices were right at normal. One speed for all of them means
 * changing the voice silently changes how fast the book is read.
 */
class SpeedPerVoiceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    @Test fun everyVoiceKeepsItsOwnSpeed() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(60_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        val voices = model.state.value.voices
        assumeTrue("Needs two voices to tell them apart", voices.size >= 2)
        val first = voices[0].id
        val second = voices[1].id

        compose.runOnIdle { model.voice(first); model.speed(0.8f) }
        compose.waitUntil(10_000) { model.state.value.voiceId == first }
        assertEquals(0.8f, model.state.value.speed, 0.001f)

        compose.runOnIdle { model.voice(second) }
        compose.waitUntil(10_000) { model.state.value.voiceId == second }
        Log.i("ReaderSpeedVoice", "zweite Stimme startet bei ${model.state.value.speed}")
        assertEquals("A voice that was never slowed down stays at its own speed",
            1f, model.state.value.speed, 0.001f)
        compose.runOnIdle { model.speed(1.3f) }
        assertEquals(1.3f, model.state.value.speed, 0.001f)

        compose.runOnIdle { model.voice(first) }
        compose.waitUntil(10_000) { model.state.value.voiceId == first }
        Log.i("ReaderSpeedVoice", "zurueck bei der ersten Stimme: ${model.state.value.speed}")
        assertEquals("Going back to a voice brings its speed back", 0.8f, model.state.value.speed, 0.001f)
        assertTrue("The change has to be said out loud: ${model.state.value.status}",
            model.state.value.status.contains("0,8 fach"))

        compose.runOnIdle { model.voice(second) }
        compose.waitUntil(10_000) { model.state.value.voiceId == second }
        assertEquals(1.3f, model.state.value.speed, 0.001f)
    }
}
