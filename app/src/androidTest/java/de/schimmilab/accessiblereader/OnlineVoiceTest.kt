package de.schimmilab.accessiblereader

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.OnlineVoicePolicy
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Voices that fetch their audio from the internet are offered, marked as such, and refused with a sentence a
 * listener can act on when the connection does not allow them. The app itself never goes online; the speech
 * engine does, in its own process.
 */
class OnlineVoiceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    private fun ready() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(30_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())
    }

    @Test fun onlineVoicesAreOfferedAndMarked() {
        ready()
        val online = model.state.value.voices.filter { it.needsNetwork }
        Log.i("ReaderOnline", "Stimmen gesamt ${model.state.value.voices.size}, davon online ${online.size}")
        assumeTrue("This engine offers no network voice", online.isNotEmpty())
        assertTrue("An online voice has to say so in its label: ${online.first().label}",
            online.first().label.contains("braucht Internet"))
        assertFalse("Offline voices must not be marked",
            model.state.value.voices.first { !it.needsNetwork }.label.contains("Internet"))
    }

    @Test fun anOnlineVoiceIsRefusedWithAReasonWhenItIsSwitchedOff() {
        ready()
        val online = model.state.value.voices.filter { it.needsNetwork }
        assumeTrue("This engine offers no network voice", online.isNotEmpty())

        compose.runOnIdle {
            model.onlineVoices(OnlineVoicePolicy.NEVER)
            model.demo(); model.voice(online.first().id)
        }
        compose.waitUntil(10_000) { model.state.value.voiceId == online.first().id }
        compose.runOnIdle { model.play() }
        compose.waitUntil(30_000) { model.state.value.error != null || model.state.value.playing }

        val error = model.state.value.error
        Log.i("ReaderOnline", "Antwort bei abgeschalteten Online-Stimmen: $error")
        assertNotNull("Playing must be refused, not attempted", error)
        assertTrue("The refusal has to name the setting: $error", error!!.contains("Einstellungen"))
        assertFalse("Nothing may play", model.state.value.playing)
        compose.runOnIdle { model.onlineVoices(OnlineVoicePolicy.WIFI_ONLY) }
    }
}
