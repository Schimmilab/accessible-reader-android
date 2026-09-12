package de.schimmilab.accessiblereader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.speech.AndroidSpeechProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The diagnosis report of one device listed 17 German voices for the Google engine, 13 of them offline, while
 * the voice list in the app offered four. This compares both paths on one device: what the engine reports, and
 * what the app makes of it.
 */
@RunWith(AndroidJUnit4::class)
class VoiceListTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = "com.google.android.tts"

    @Test fun theAppOffersEveryOfflineGermanVoiceTheEngineReports() = runBlocking {
        val ready = CompletableDeferred<Int>()
        val tts = TextToSpeech(context, { ready.complete(it) }, engine)
        val status = withTimeout(20_000) { ready.await() }
        assumeTrue("Google speech engine not installed on this device", status == TextToSpeech.SUCCESS)

        val all = runCatching { tts.voices.orEmpty().toList() }.getOrDefault(emptyList())
        val german = all.filter { it.locale.language == "de" }
        val offline = german.filter { !it.isNetworkConnectionRequired }
        val installed = offline.filterNot { it.features.orEmpty().contains("notInstalled") }
        Log.i("ReaderVoices", "Engine meldet: ${all.size} Stimmen, ${german.size} deutsch, " +
            "${offline.size} deutsch und offline, ${installed.size} davon installiert")
        offline.sortedBy { it.name }.forEach {
            Log.i("ReaderVoices", "   ${it.name}, ${it.locale}, Qualität ${it.quality}, Merkmale ${it.features}")
        }
        tts.shutdown()

        val offered = AndroidSpeechProvider(context, engine).let { provider ->
            provider.voices().also { provider.close() }
        }
        Log.i("ReaderVoices", "App bietet an: ${offered.size} Stimmen")
        offered.forEach { Log.i("ReaderVoices", "   ${it.id} als '${it.label}'") }

        assertEquals("The app must offer every offline German voice the engine reports",
            offline.size, offered.size)
    }
}
