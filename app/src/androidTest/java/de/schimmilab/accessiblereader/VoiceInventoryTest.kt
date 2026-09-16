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

/** Lists what every installed engine offers, so a test machine can be told what it can and cannot prove. */
@RunWith(AndroidJUnit4::class)
class VoiceInventoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun everyEngineListsItsGermanVoices() = runBlocking {
        val engines = TextToSpeech(context) {}.let { probe ->
            val list = runCatching { probe.engines.map { it.name to it.label } }.getOrDefault(emptyList())
            probe.shutdown(); list
        }
        for ((packageName, label) in engines) {
            val provider = AndroidSpeechProvider(context, packageName)
            val voices = runCatching { provider.voices() }.getOrDefault(emptyList())
            Log.i("ReaderVoices", "$label ($packageName): ${voices.size} Stimmen" +
                if (voices.isEmpty()) "" else " – " + voices.joinToString { "${it.id}${if (it.needsNetwork) " [online]" else ""}" })
            provider.close()
        }
        assertTrue(engines.isNotEmpty())
    }
}
