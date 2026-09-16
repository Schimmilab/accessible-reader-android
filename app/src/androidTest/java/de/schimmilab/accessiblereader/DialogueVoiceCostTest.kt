package de.schimmilab.accessiblereader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.speech.AndroidSpeechProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What a second voice for the dialogue would cost.
 *
 * Reading a novel with a narrator and a second voice for what the characters say means cutting the text at every
 * quotation mark. Measured on Effi Briest, that is 83 parts per section where the reader uses eleven, and the
 * median line of dialogue is nineteen characters. Every part is one call to the speech engine, and a call has a
 * fixed cost on top of the text it speaks.
 *
 * This measures that fixed cost: the same passage once as the reader cuts it today, and once cut at the quotation
 * marks. Needs a German voice, so it stays off the CI emulator.
 */
@RunWith(AndroidJUnit4::class)
class DialogueVoiceCostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A page of a novel in the shape Fontane writes it: narration, short replies, an inquit in between. */
    private val passages = listOf(
        "Effi war ans Fenster getreten und sah in den Garten hinaus, wo der Abend schon zwischen den Bäumen stand.",
        "»Und du meinst wirklich, es wird gehen?«",
        "fragte sie, ohne sich umzuwenden.",
        "»Gewiß«,",
        "sagte die Mutter,",
        "»warum sollte es nicht gehen?«",
        "»Weil ich es nicht weiß. Man kann doch nicht immer alles wissen.«",
        "Sie schwieg eine Weile und trat dann an den Tisch zurück, auf dem die Lampe brannte und die Briefe lagen.",
        "»Nein«,",
        "sagte die Mutter ruhig,",
        "»alles weiß man nie.«")

    @Test fun oneCallPerQuotationMarkIsTimedAgainstOneCallPerThousandCharacters() = runBlocking {
        val engines = TextToSpeech(context) {}.let { probe ->
            val list = runCatching { probe.engines.map { it.name to it.label } }.getOrDefault(emptyList())
            probe.shutdown(); list
        }
        val whole = passages.joinToString(" ")
        Log.i("ReaderDialogue", "Probe: ${whole.length} Zeichen, ${passages.size} Teile, " +
            "kürzester ${passages.minOf { it.length }}, Median ${passages.map { it.length }.sorted()[passages.size / 2]}")

        var measured = 0
        for ((packageName, label) in engines) {
            val provider = AndroidSpeechProvider(context, packageName)
            val voices = runCatching { provider.voices() }.getOrDefault(emptyList())
            if (voices.isEmpty()) { provider.close(); continue }
            val voice = voices.first()
            // A neural engine loads its model on the first call. Without this warm-up the first measurement
            // carries 60 seconds of model loading and the comparison is worthless; measured exactly that.
            val warmBegan = System.currentTimeMillis()
            runCatching { provider.synthesize("Ein Satz zum Aufwärmen der Sprachmaschine.", voice.id) }
            Log.i("ReaderDialogue", "$label: Aufwärmen ${System.currentTimeMillis() - warmBegan}ms")

            clearCache()
            val oneBegan = System.currentTimeMillis()
            val oneAudio = runCatching { provider.synthesize(whole, voice.id).durationMs }.getOrDefault(0L)
            val oneMs = System.currentTimeMillis() - oneBegan

            clearCache()
            val manyBegan = System.currentTimeMillis()
            var manyAudio = 0L
            val failed = passages.count { text ->
                runCatching { manyAudio += provider.synthesize(text, voice.id).durationMs }
                    .onFailure { Log.i("ReaderDialogue", "  Teil «$text» scheiterte: ${it.message}") }.isFailure
            }
            val manyMs = System.currentTimeMillis() - manyBegan

            val overhead = if (passages.size > 1) (manyMs - oneMs) / (passages.size - 1) else 0
            Log.i("ReaderDialogue", "$label, ${voice.id}: am Stück ${oneMs}ms für ${oneAudio}ms Audio · " +
                "in ${passages.size} Teilen ${manyMs}ms für ${manyAudio}ms Audio · " +
                "${overhead}ms Aufschlag je zusätzlichem Teil · $failed Fehler")
            provider.close()
            measured++
        }
        assertTrue("No engine with a German voice on this device", measured > 0)
    }

    /** Only the files: removing the folder itself makes every later write fail. */
    private fun clearCache() { File(context.cacheDir, "speech").listFiles()?.forEach { it.delete() } }
}
