package de.schimmilab.accessiblereader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Can the reader use the network voices an installed speech engine already offers? Those would cost nothing, need
 * no API key, and need no INTERNET permission in this app, because the speech engine does the network access in
 * its own process. The reader currently hides them, and this measures whether that is the right call.
 */
@RunWith(AndroidJUnit4::class)
class NetworkVoiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun aNetworkVoiceOfTheEngineCanWriteAFile() {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val tts = TextToSpeech(context, { status = it; ready.countDown() }, "com.google.android.tts")
        assertTrue("Engine did not start", ready.await(20, TimeUnit.SECONDS))
        assumeTrue("Google engine not available", status == TextToSpeech.SUCCESS)
        try {
            val german = tts.voices.orEmpty().filter { it.locale.language == "de" }
            val network = german.filter { it.isNetworkConnectionRequired }
            Log.i("ReaderNet", "Deutsche Stimmen: ${german.size}, davon mit Netz: ${network.size}")
            network.forEach { Log.i("ReaderNet", "   ${it.name}, Qualität ${it.quality}, Merkmale ${it.features}") }
            assumeTrue("This engine offers no German network voice", network.isNotEmpty())

            val voice = network.maxByOrNull { it.quality }!!
            assertEquals("Voice could not be selected", TextToSpeech.SUCCESS, tts.setVoice(voice))

            val text = "Der Reader liest ein Buch vor, Abschnitt für Abschnitt, ohne dass man hinsehen muss. ".repeat(3)
            val target = File(context.cacheDir, "network-voice-test.wav")
            var failed = false
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { done.countDown() }
                @Deprecated("Legacy") override fun onError(id: String?) { failed = true; done.countDown() }
                override fun onError(id: String?, code: Int) { failed = true; done.countDown() }
            })
            val started = System.currentTimeMillis()
            assertEquals("synthesizeToFile refused the request", TextToSpeech.SUCCESS,
                tts.synthesizeToFile(text, Bundle(), target, "net-1"))
            val answered = done.await(120, TimeUnit.SECONDS)
            val ms = System.currentTimeMillis() - started

            Log.i("ReaderNet", "Stimme ${voice.name}: geantwortet=$answered, Fehler=$failed, ${ms}ms, " +
                "Datei ${if (target.isFile) target.length() else 0} Bytes")
            assertTrue("The engine never answered for the network voice", answered)
            assertFalse("The engine reported an error for the network voice", failed)
            assertTrue("No audio was written", target.isFile && target.length() > 44)
            target.delete()
        } finally { runCatching { tts.shutdown() } }
    }
}
