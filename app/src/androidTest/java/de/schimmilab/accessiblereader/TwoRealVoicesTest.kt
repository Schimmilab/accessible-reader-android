package de.schimmilab.accessiblereader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.core.Chapter
import de.schimmilab.accessiblereader.core.Narration
import de.schimmilab.accessiblereader.core.ReaderDocument
import de.schimmilab.accessiblereader.core.SpeakingRole
import de.schimmilab.accessiblereader.core.TextChunks
import de.schimmilab.accessiblereader.core.VoiceCast
import de.schimmilab.accessiblereader.playback.SectionPreparer
import de.schimmilab.accessiblereader.playback.SectionProgress
import de.schimmilab.accessiblereader.speech.AndroidSpeechProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Two real voices on a real engine, and what the second one costs.
 *
 * The silent tests prove the division of labour. This one proves that a real engine will do it at all and puts a
 * number on the price, because a voice that cannot keep up with listening is the failure this project has
 * already had once: a listener was told after 25 pages that the voice had taken longer than a minute.
 *
 * Needs an engine with two German voices installed and skips otherwise.
 */
@RunWith(AndroidJUnit4::class)
class TwoRealVoicesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("reader-real-voices-test", Context.MODE_PRIVATE)

    /** Novel-shaped and every paragraph different, so no piece is answered out of the cache. */
    private fun novel(paragraphs: Int) = (1..paragraphs).joinToString("\n\n") { index ->
        "Am $index. Tag trat sie ans Fenster und sah in den Garten hinaus, wo der Abend zwischen den Bäumen stand. " +
        "»Und du meinst wirklich, dass es im $index. Jahr so gehen wird?« fragte sie, ohne sich umzuwenden. " +
        "»Gewiß«, sagte die Mutter, »warum sollte es im $index. Jahr nicht gehen?« " +
        "Sie schwieg und trat an den Tisch zurück, auf dem die Lampe brannte und die $index Briefe lagen."
    }

    @Test fun aRealEngineReadsTheDialogueInTheSecondVoiceAndSaysWhatItCosts() = runBlocking {
        val engines = TextToSpeech(context) {}.let { probe ->
            val list = runCatching { probe.engines.map { it.name to it.label } }.getOrDefault(emptyList())
            probe.shutdown(); list
        }
        var ran = false
        for ((packageName, label) in engines) {
            val provider = AndroidSpeechProvider(context, packageName)
            val voices = runCatching { provider.voices() }.getOrDefault(emptyList()).filter { !it.needsNetwork }
            if (voices.size < 2) { provider.close(); continue }
            val cast = VoiceCast(voices[0].id, voices[1].id)
            val text = novel(6)
            val document = ReaderDocument("real-voices", "Ein Roman", listOf(Chapter("Erstes Kapitel", 1, 4, text)))
            val parts = Narration.partsForPlayback(text)
            Log.i("ReaderTwoVoices", "$label: ${text.length} Zeichen, ${parts.size} Teile mit zwei Stimmen, " +
                "${TextChunks.splitForPlayback(text).size} mit einer, " +
                "${parts.count { it.role == SpeakingRole.DIALOGUE }} davon Rede")

            // Warmed up first and then measured in both orders: an engine that has just loaded its model would
            // otherwise make whichever ran second look free.
            prepare(provider, document, VoiceCast(voices[0].id))
            val one = listOf(prepare(provider, document, VoiceCast(voices[0].id)),
                prepare(provider, document, VoiceCast(voices[0].id)))
            val two = listOf(prepare(provider, document, cast), prepare(provider, document, cast))
            val withOne = one.min()
            val withTwo = two.min()
            Log.i("ReaderTwoVoices", "$label: eine Stimme $one ms, zwei Stimmen $two ms, " +
                "bestes gegen bestes ${"%+d".format((withTwo - withOne) * 100 / withOne)} Prozent")

            // The audio of the same words differs between the voices, which is the entire point.
            val byNarrator = provider.synthesize("»Und du meinst wirklich?«", cast.narrator)
            val byCharacter = provider.synthesize("»Und du meinst wirklich?«", cast.dialogue)
            assertNotEquals("both voices produced the same file", byNarrator.file, byCharacter.file)
            assertTrue(byNarrator.durationMs > 0 && byCharacter.durationMs > 0)
            provider.close()
            ran = true
        }
        assumeTrue("No engine with two installed German voices on this device", ran)
    }

    private suspend fun prepare(provider: AndroidSpeechProvider, document: ReaderDocument, cast: VoiceCast): Long {
        File(context.cacheDir, "speech").listFiles()?.forEach { it.delete() }
        return withContext(Dispatchers.Main) {
            val player = ExoPlayer.Builder(context).build()
            try {
                player.volume = 0f
                val began = System.currentTimeMillis()
                SectionPreparer(provider, prefs, SectionPreparer.MIN_LEAD_MS, SectionPreparer.CACHE_BUDGET_BYTES)
                    .prepare(player, document, 0, cast, 1.0f, object : SectionProgress {})
                System.currentTimeMillis() - began
            } finally { player.release() }
        }
    }
}
