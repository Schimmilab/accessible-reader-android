package de.schimmilab.accessiblereader

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.Chapter
import de.schimmilab.accessiblereader.core.ReaderDocument
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * A book without PDF bookmarks becomes one section per page. A listener reported having to work through such a
 * book page by page, so this checks what actually happens at the end of a page: does the next one start on its
 * own, and how long is the silence in between.
 */
class PageSectionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel
    private val slowEngine = "com.CodeBySonu.VoxSherpa"

    // Long enough that preparing the next section has time to finish while this one plays, which is what a real
    // section of about ten minutes looks like. With seven second sections the measurement says nothing.
    private val document = ReaderDocument("page-sections-test", "Buch ohne Kapitelmarken",
        (1..3).map { page -> Chapter("Seite $page", page, page,
            "Das ist der Text von Seite $page. " + "Er erzählt weiter von einem Haus, einem Garten und " +
                "einem Brief, damit dieser Abschnitt lange genug dauert. ".repeat(6)) })

    @Test fun theNextPageStartsWithoutBeingAskedTo() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(30_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())

        compose.runOnIdle { model.clearCache(); model.open(document); model.chapter(0); model.play() }
        compose.waitUntil(120_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        assertEquals("Should start on the first page", 0, model.state.value.chapter)

        // Nothing is pressed from here on. Every further page has to arrive by itself, and the interesting
        // number is not the distance between page changes, which contains the audio itself, but how long the
        // sound actually stops.
        var lastChapter = 0
        var silenceStarted = 0L
        val silences = mutableListOf<Long>()
        var wasPlaying = true
        compose.waitUntil(300_000) {
            val s = model.state.value
            if (wasPlaying && !s.playing) { silenceStarted = System.currentTimeMillis(); wasPlaying = false }
            if (!wasPlaying && s.playing) {
                silences += System.currentTimeMillis() - silenceStarted
                Log.i("ReaderPages", "Ton war ${silences.last()}ms still, jetzt Abschnitt ${s.chapter + 1}")
                wasPlaying = true
            }
            if (s.chapter != lastChapter) lastChapter = s.chapter
            s.chapter == document.chapters.lastIndex || s.error != null
        }
        // The last page is still playing when the loop ends; only the changes in between count.
        Log.i("ReaderPages", "Endstand: Abschnitt ${model.state.value.chapter + 1} von ${document.chapters.size}, " +
            "Fehler ${model.state.value.error}, Stille zwischen den Abschnitten ${silences}")
        compose.runOnIdle { model.pause() }

        // With the stock voice, which synthesizes far faster than playback, preparing the next section ahead
        // works every time. Measured: 9011 ms of silence without it, 307 to 1019 ms with it.
        assertTrue("The sound stopped for ${silences.maxOrNull()} ms at a section change",
            silences.all { it < 2_500 })

        assertNull("Reading a page-per-section book failed: ${model.state.value.error}", model.state.value.error)
        assertEquals("The book has to run to its last page without anyone pressing anything",
            document.chapters.lastIndex, model.state.value.chapter)
    }

    /**
     * The same measurement with a slow voice. The stock voice synthesizes far faster than playback, so the gap
     * at a section change is under a second either way. A local neural voice needs about as long as the audio
     * itself, and that is where preparing the next section ahead of time has to earn its place.
     */
    @Test fun theSilenceStaysShortWithASlowVoice() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        // The engine list arrives with the first voice query, which can lag behind the player connection.
        compose.waitUntil(60_000) { model.state.value.connected && model.state.value.engines.isNotEmpty() }
        assumeTrue("Needs the neural engine installed, found ${model.state.value.engines.keys}",
            model.state.value.engines.containsKey(slowEngine))
        compose.runOnIdle { model.engine(slowEngine) }
        compose.waitUntil(40_000) { model.state.value.voices.isNotEmpty() || model.state.value.error != null }
        assumeTrue("The neural engine reports no German voice", model.state.value.voices.isNotEmpty())
        val slow = model.state.value.voices.firstOrNull { it.id.contains("thorsten") } ?: model.state.value.voices.first()
        compose.runOnIdle { model.voice(slow.id) }
        compose.waitUntil(10_000) { model.state.value.voiceId == slow.id }
        Log.i("ReaderPages", "Langsame Stimme: ${slow.id}")

        compose.runOnIdle { model.clearCache(); model.open(document); model.chapter(0); model.play() }
        compose.waitUntil(300_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)

        var silenceStarted = 0L
        val silences = mutableListOf<Long>()
        var wasPlaying = true
        compose.waitUntil(600_000) {
            val s = model.state.value
            if (wasPlaying && !s.playing) { silenceStarted = System.currentTimeMillis(); wasPlaying = false }
            if (!wasPlaying && s.playing) {
                silences += System.currentTimeMillis() - silenceStarted
                Log.i("ReaderPages", "LANGSAM: Ton war ${silences.last()}ms still, jetzt Abschnitt ${s.chapter + 1}")
                wasPlaying = true
            }
            s.chapter == document.chapters.lastIndex || s.error != null
        }
        Log.i("ReaderPages", "LANGSAM Endstand: Abschnitt ${model.state.value.chapter + 1}, Stille ${silences}")
        compose.runOnIdle { model.pause() }
        assertNull("Reading with a slow voice failed: ${model.state.value.error}", model.state.value.error)
        // A voice that synthesizes at about the speed of playback has no spare capacity to work ahead, so this
        // one stays a measurement rather than a promise. Measured on an emulator with Piper Thorsten: 21181 ms
        // without preparing the next section ahead, and between 145 and 8581 ms with it, depending on how much
        // room the current section leaves. The bound only catches a total regression.
        assertTrue("A slow voice fell silent for ${silences.maxOrNull()} ms between sections",
            silences.all { it < 15_000 })
    }
}
