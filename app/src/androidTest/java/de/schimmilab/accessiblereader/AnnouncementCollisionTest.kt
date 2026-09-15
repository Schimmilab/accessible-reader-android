package de.schimmilab.accessiblereader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.speech.AndroidSpeechProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app speaks short feedback over a second connection to the speech engine while the first one is turning the
 * book into audio. Google, Vocalizer and Acapela serve both at once. An engine with a single native synthesizer
 * does not, and then the book's audio waits behind a sentence of feedback.
 *
 * A listener hit exactly that: she pressed for the next section, heard the announcement, heard a little of the
 * book, and then it stopped with the message that the voice had taken more than sixty seconds.
 */
@RunWith(AndroidJUnit4::class)
class AnnouncementCollisionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val slowEngine = "com.CodeBySonu.VoxSherpa"

    @Test fun anAnnouncementDoesNotStallTheBook() = runBlocking {
        val engines = TextToSpeech(context) {}.let { probe ->
            val list = runCatching { probe.engines.map { it.name } }.getOrDefault(emptyList())
            probe.shutdown(); list
        }
        assumeTrue("Needs the single-synthesizer engine installed", engines.contains(slowEngine))

        val speech = AndroidSpeechProvider(context, slowEngine)
        try {
            val voice = speech.voices().firstOrNull { it.id.contains("thorsten") } ?: speech.voices().firstOrNull()
            assumeTrue("This engine reports no German voice", voice != null)
            speech.clearCache()

            // Long enough that the announcement lands in the middle of it.
            val text = "Ein Satz über das Haus, den Garten und einen Brief, der lange genug ist. ".repeat(6)
            val started = System.currentTimeMillis()
            val book = async(Dispatchers.Default) { speech.synthesize(text, voice!!.id) }
            delay(1_500)
            Log.i("ReaderCollision", "Ansage wird gesprochen, während das Buch erzeugt wird")
            speech.say("Nächster Abschnitt, Seite 25 bis 33.", voice!!.id)

            val audio = book.await()
            val took = System.currentTimeMillis() - started
            Log.i("ReaderCollision", "Buchaudio nach ${took}ms fertig, ${audio.durationMs}ms lang")
            assertTrue("The book's audio has to be produced", audio.durationMs > 0)
            // Without the announcement this text takes a few seconds on this engine. Sixty is the app's own
            // budget for a piece this size, and the listener ran into exactly that.
            assertTrue("The announcement stalled the book for ${took / 1000} s", took < 30_000)
        } finally { speech.close() }
    }
}
