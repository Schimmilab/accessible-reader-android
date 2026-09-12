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

/**
 * Measures how long each installed speech engine needs to turn text into a file, at the chunk sizes the reader
 * actually uses. A neural voice can be an order of magnitude slower than the stock one, and the reader's chunk
 * size and timeouts were never checked against that.
 */
@RunWith(AndroidJUnit4::class)
class SynthesisSpeedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val sentence = "Der Reader liest ein Buch vor, Abschnitt für Abschnitt, ohne dass man hinsehen muss. "

    @Test fun everyEngineIsTimedAtTheChunkSizesTheReaderUses() = runBlocking {
        val engines = TextToSpeech(context) {}.let { probe ->
            val list = runCatching { probe.engines.map { it.name to it.label } }.getOrDefault(emptyList())
            probe.shutdown(); list
        }
        Log.i("ReaderSpeed", "Sprachmaschinen: ${engines.joinToString { it.second + " (" + it.first + ")" }}")

        for ((packageName, label) in engines) {
            val provider = AndroidSpeechProvider(context, packageName)
            val voices = runCatching { provider.voices() }.getOrDefault(emptyList())
            if (voices.isEmpty()) {
                Log.i("ReaderSpeed", "$label: keine deutsche Stimme, übersprungen")
                provider.close(); continue
            }
            val voice = voices.first()
            for (length in listOf(150, 200, 300, 400, 600)) {
                val text = buildString { while (this.length < length) append(sentence) }.take(length)
                val started = System.currentTimeMillis()
                val result = runCatching { provider.synthesize(text, voice.id) }
                val ms = System.currentTimeMillis() - started
                result.onSuccess {
                    val ratio = if (it.durationMs > 0) ms.toDouble() / it.durationMs else -1.0
                    Log.i("ReaderSpeed", "$label, ${voice.id}, $length Zeichen: ${ms}ms für ${it.durationMs}ms Audio, " +
                        "Faktor ${"%.2f".format(ratio)}")
                }.onFailure {
                    Log.i("ReaderSpeed", "$label, ${voice.id}, $length Zeichen: FEHLER nach ${ms}ms, ${it.message}")
                }
            }
            provider.close()
        }
        assertTrue(engines.isNotEmpty())
    }
}
