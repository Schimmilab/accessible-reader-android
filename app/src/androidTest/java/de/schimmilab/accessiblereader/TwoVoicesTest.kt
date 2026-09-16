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
import de.schimmilab.accessiblereader.playback.SectionProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Reading a novel with a narrator and a second voice for what its people say.
 *
 * Runs on silence, so it needs no speech engine and covers in CI what could otherwise only be heard on one
 * machine. What it checks is the division of labour: that every line in quotation marks went to the second voice
 * and everything else to the first, that a section prepared with one voice is still cut exactly as before, and
 * that a media item says which cast it belongs to, which is what the saved position is compared against.
 */
@RunWith(AndroidJUnit4::class)
class TwoVoicesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("reader-two-voices-test", Context.MODE_PRIVATE)

    private val novel = "Effi trat ans Fenster und sah hinaus. »Und du meinst wirklich, es wird gehen?« " +
        "fragte sie, ohne sich umzuwenden. »Gewiß«, sagte die Mutter, »warum sollte es nicht gehen?« " +
        "Sie schwieg und trat an den Tisch zurück, auf dem die Lampe brannte."
    private val document = ReaderDocument("two-voices", "Ein Roman",
        listOf(Chapter("Erstes Kapitel", 1, 4, novel)))

    private lateinit var folder: File
    @Before fun setUp() {
        folder = File(context.cacheDir, "two-voices-test").apply { deleteRecursively(); mkdirs() }
        prefs.edit().clear().commit()
    }
    @After fun tearDown() { folder.deleteRecursively(); prefs.edit().clear().commit() }

    @Test fun theCharactersGetTheSecondVoiceAndTheBookKeepsTheFirst() = prepared(VoiceCast("erzaehler", "figur")) { speech, _ ->
        val dialogue = speech.textsFor("figur")
        val narration = speech.textsFor("erzaehler")

        assertEquals(listOf(
            "»Und du meinst wirklich, es wird gehen?«",
            "»Gewiß«",
            "»warum sollte es nicht gehen?«"), dialogue)
        assertTrue("the narration keeps the first voice", narration.any { it.startsWith("Effi trat ans Fenster") })
        assertTrue("the inquit between two replies is narration too",
            narration.any { it.contains("sagte die Mutter") })
        assertTrue("no quotation mark may reach the narrator",
            narration.none { it.contains('»') || it.contains('«') })
        // Part 0 is the spoken section intro and always belongs to the narrator.
        assertTrue(narration.first().contains("Erstes Kapitel"))
    }

    /** With one voice nothing may change: the same pieces, so the same cached audio as every earlier version. */
    @Test fun oneVoiceIsCutExactlyAsBefore() = prepared(VoiceCast("erzaehler")) { speech, _ ->
        val expected = TextChunks.splitForPlayback(novel)
        assertEquals(expected, speech.textsFor("erzaehler").drop(1))
        assertTrue(speech.textsFor("figur").isEmpty())
    }

    @Test fun everyPartSaysWhichCastItBelongsToAndWhoSpokeIt() = prepared(VoiceCast("erzaehler", "figur")) { _, player ->
        val extras = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaMetadata.extras!! }
        assertTrue("the cast identifies the section", extras.all { it.getString("voice") == "erzaehler|figur" })
        assertEquals(setOf("erzaehler", "figur"), extras.mapNotNull { it.getString("partVoice") }.toSet())
    }

    private fun prepared(cast: VoiceCast, check: (SilentVoices, ExoPlayer) -> Unit) = runBlocking(Dispatchers.Main) {
        val speech = SilentVoices(folder, mapOf("erzaehler" to 700L, "figur" to 700L))
        val player = ExoPlayer.Builder(context).build()
        try {
            player.volume = 0f
            SectionPreparer(speech, prefs, SectionPreparer.MIN_LEAD_MS, SectionPreparer.CACHE_BUDGET_BYTES)
                .prepare(player, document, 0, cast, 1.0f, object : SectionProgress {})
            check(speech, player)
        } finally { player.release(); speech.close() }
    }
}
