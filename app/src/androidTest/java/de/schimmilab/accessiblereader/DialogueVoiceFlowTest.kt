package de.schimmilab.accessiblereader

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.Chapter
import de.schimmilab.accessiblereader.core.ReaderDocument
import de.schimmilab.accessiblereader.core.VoiceCast
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * The whole way through the app, not just the preparer: choosing a second voice in the settings and pressing
 * Vorlesen. Needs an engine with two installed German voices and skips otherwise.
 */
class DialogueVoiceFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    private val novel = "Sie trat ans Fenster und sah hinaus. »Und du meinst wirklich, es wird gehen?« fragte sie. " +
        "»Gewiß«, sagte die Mutter, »warum sollte es nicht gehen?« Dann wurde es still im Zimmer."

    @Test fun aSecondVoiceReachesThePlayer() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        val offline = model.state.value.voices.filter { !it.needsNetwork }
        assumeTrue("Needs two installed German voices", offline.size >= 2)

        compose.runOnIdle {
            model.open(ReaderDocument("dialogue-flow", "Ein Roman", listOf(Chapter("Erstes Kapitel", 1, 2, novel))))
            model.voice(offline[0].id)
            model.dialogueVoice(offline[1].id)
        }
        assertEquals(offline[1].id, model.state.value.dialogueVoiceId)

        compose.runOnIdle { model.play() }
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.waitUntil(240_000) { !model.state.value.preparing }

        val cast = VoiceCast(offline[0].id, offline[1].id)
        Thread.sleep(1_500) // the service writes the position about once per second
        val saved = compose.activity.getSharedPreferences("reader", android.content.Context.MODE_PRIVATE)
        assertEquals("the saved position has to name the whole cast, or resuming would rebuild the wrong section",
            cast.key, saved.getString("dialogue-flow.voice", ""))

        compose.runOnIdle { model.pause() }
        // Back to one voice, and the section is prepared again from scratch.
        compose.runOnIdle { model.dialogueVoice("") }
        assertEquals("", model.state.value.dialogueVoiceId)
        assertEquals(0L, model.state.value.positionMs)
    }
}
