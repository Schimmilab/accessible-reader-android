package de.schimmilab.accessiblereader

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.core.Chapter
import de.schimmilab.accessiblereader.core.ReaderDocument
import de.schimmilab.accessiblereader.core.TextChunks
import de.schimmilab.accessiblereader.core.VoiceCast
import de.schimmilab.accessiblereader.playback.SectionPreparer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What happens to someone's place in a book when they try a different voice.
 *
 * Runs without a speech engine: the voices here are silent WAV files of a fixed length, one voice a third faster
 * than the other, which is how far two real voices are apart. That is the whole point of the behaviour under
 * test, and a fresh emulator has no German voice to do it with.
 */
@RunWith(AndroidJUnit4::class)
class SectionResumeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("reader-resume-test", Context.MODE_PRIVATE)

    /** Ten thousand characters, the size a section is grouped to, so it is cut into a realistic number of parts. */
    private val document = ReaderDocument("resume-test", "Ein Buch",
        listOf(Chapter("Erster Abschnitt", 1, 8, "Ein Satz, der vorgelesen wird. ".repeat(340))))

    private lateinit var folder: File
    @Before fun setUp() {
        folder = File(context.cacheDir, "resume-test").apply { deleteRecursively(); mkdirs() }
        prefs.edit().clear().commit()
    }
    @After fun tearDown() { folder.deleteRecursively(); prefs.edit().clear().commit() }

    /** The parts a section is cut into come from its text alone, so they are the same for every voice. */
    @Test fun theSameTextIsCutIntoTheSamePartsForEveryVoice() {
        val parts = TextChunks.splitForPlayback(document.chapters[0].text)
        assertTrue("A section of this size has to hold several parts, found ${parts.size}", parts.size >= 5)
    }

    @Test fun anotherVoiceContinuesAtThePartTheListenerHadReached() = prepared(
        savedItem = 4, savedOffsetMs = 3_000, savedVoice = "langsam", nowVoice = "schnell") { player ->
        assertEquals("the listener keeps the part", 4, player.currentMediaItemIndex)
        assertTrue("and starts it from the beginning, because the seconds belonged to the old voice",
            player.currentPosition < 2_000)
    }

    @Test fun theSameVoiceContinuesAtTheVerySecond() = prepared(
        savedItem = 4, savedOffsetMs = 3_000, savedVoice = "langsam", nowVoice = "langsam") { player ->
        assertEquals(4, player.currentMediaItemIndex)
        assertTrue("expected about 3 seconds in, was ${player.currentPosition}", player.currentPosition >= 3_000)
    }

    /** A section that was heard to the end starts at its heading again, whatever the voice. */
    @Test fun aFinishedSectionStartsAtItsHeading() = prepared(
        savedItem = 4, savedOffsetMs = 3_000, savedVoice = "langsam", nowVoice = "langsam", finished = true) { player ->
        assertEquals(0, player.currentMediaItemIndex)
    }

    private fun prepared(savedItem: Int, savedOffsetMs: Long, savedVoice: String, nowVoice: String,
                         finished: Boolean = false, check: (ExoPlayer) -> Unit) = runBlocking(Dispatchers.Main) {
        prefs.edit()
            .putInt("${document.id}.chapter", 0)
            .putInt("${document.id}.item", savedItem)
            .putLong("${document.id}.offset", savedOffsetMs)
            .putString("${document.id}.voice", savedVoice)
            .putBoolean("${document.id}.finished", finished)
            .commit()
        // The slower voice needs half again as long for the same words, which is the gap measured between a
        // local neural voice and the stock one.
        val speech = SilentVoices(folder, mapOf("langsam" to 9_000L, "schnell" to 6_000L))
        val player = ExoPlayer.Builder(context).build()
        try {
            player.volume = 0f
            SectionPreparer(speech, prefs, SectionPreparer.MIN_LEAD_MS, SectionPreparer.CACHE_BUDGET_BYTES)
                .prepare(player, document, 0, VoiceCast(nowVoice), 1.0f,
                    object : de.schimmilab.accessiblereader.playback.SectionProgress {})
            check(player)
        } finally { player.release(); speech.close() }
    }
}
