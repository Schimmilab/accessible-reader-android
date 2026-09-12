package de.schimmilab.accessiblereader

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.speech.AndroidSpeechProvider
import de.schimmilab.accessiblereader.speech.SpeechProbe
import de.schimmilab.accessiblereader.core.VOICE_SAMPLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechAudioTest {
    @Test fun localGermanVoiceProducesPlayableCachedAudio() = runBlocking {
        withContext(Dispatchers.Main) {
            val provider = AndroidSpeechProvider(ApplicationProvider.getApplicationContext<Context>())
            try {
                val voices = provider.voices()
                Log.i("ReaderAudioTest", "German offline voices: $voices")
                assumeTrue("Requires German offline voice data on the test device", voices.isNotEmpty())
                val text = "Willkommen. Dies ist ein Test der lokalen Sprachausgabe. Dreißig Sekunden zurück."
                val first = provider.synthesize(text, voices.first().id)
                assertTrue(first.durationMs > 1000)
                assertTrue(first.file.length() > 1000)
                val length = first.file.length()
                val touchedBefore = first.file.lastModified()
                Thread.sleep(1_100) // file timestamps have second resolution on some filesystems

                val second = provider.synthesize(text, voices.first().id)
                assertEquals("The same cached file is reused", first.file, second.file)
                assertEquals("It is not synthesized again", length, second.file.length())
                assertEquals(first.durationMs, second.durationMs)
                // Deliberately touched on a cache hit, so trimCache can tell recently heard audio from old audio.
                assertTrue("A cache hit counts as recent use", second.file.lastModified() >= touchedBefore)
            } finally { provider.close() }
        }
    }

    /** A long book outgrows any cache budget, so the oldest audio has to give way instead of playback refusing. */
    @Test fun trimCacheDropsTheLeastRecentlyUsedAudioFirst() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = java.io.File(context.cacheDir, "speech").apply { mkdirs() }
        cache.listFiles()?.forEach { it.delete() }
        val provider = AndroidSpeechProvider(context)
        try {
            val oldest = java.io.File(cache, "a.wav").apply { writeBytes(ByteArray(300_000)); setLastModified(1_000_000) }
            val middle = java.io.File(cache, "b.wav").apply { writeBytes(ByteArray(300_000)); setLastModified(2_000_000) }
            val newest = java.io.File(cache, "c.wav").apply { writeBytes(ByteArray(300_000)); setLastModified(3_000_000) }
            assertEquals(900_000L, provider.cacheSize())

            provider.trimCache(budgetBytes = 700_000, keepBytes = 400_000)

            assertFalse("The oldest audio goes first", oldest.exists())
            assertFalse("And the next oldest, until the cache fits", middle.exists())
            assertTrue("What was heard most recently survives", newest.exists())
            assertTrue(provider.cacheSize() <= 400_000)
        } finally { provider.close(); cache.listFiles()?.forEach { it.delete() } }
    }

    @Test fun trimCacheLeavesACacheWithinBudgetAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = java.io.File(context.cacheDir, "speech").apply { mkdirs() }
        cache.listFiles()?.forEach { it.delete() }
        val provider = AndroidSpeechProvider(context)
        try {
            val kept = java.io.File(cache, "small.wav").apply { writeBytes(ByteArray(100_000)) }
            provider.trimCache(budgetBytes = 500_000)
            assertTrue("Nothing is deleted while there is room", kept.exists())
        } finally { provider.close(); cache.listFiles()?.forEach { it.delete() } }
    }

    @Test fun everyInstalledEngineIsProbedIndividually() = runBlocking {
        withContext(Dispatchers.Main) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val provider = AndroidSpeechProvider(context)
            val engines = provider.engines()
            val default = provider.defaultEngineName()
            provider.close()
            assertTrue("The diagnosis must find at least one engine", engines.isNotEmpty())

            val reports = engines.entries.map { (packageName, label) ->
                SpeechProbe.probe(context, packageName, label, packageName == default)
            }
            reports.forEach { Log.i("ReaderAudioTest", "probe: $it") }
            val working = reports.filter { it.started && it.fileSynthesis.startsWith("funktioniert") }
            assumeTrue("Requires at least one engine with German voice data", working.isNotEmpty())
            assertTrue("A working engine must report German as available", working.all { it.germanAvailability == "verfügbar" })
        }
    }

    @Test fun anUnknownEngineYieldsAWellFormedReportInsteadOfCrashing() = runBlocking {
        // Android quietly substitutes the default engine for an unknown package rather than failing. That is why
        // a stored engine choice cannot brick the app once that engine is uninstalled.
        val report = SpeechProbe.probe(ApplicationProvider.getApplicationContext(), "de.schimmilab.gibtesnicht", "Erfunden", false)
        assertEquals("Erfunden", report.label)
        assertEquals("de.schimmilab.gibtesnicht", report.packageName)
        assertFalse(report.isDefault)
        assertTrue("Availability must be one of the known phrases, never a raw code",
            report.germanAvailability in setOf("verfügbar", "Sprachdaten fehlen", "nicht unterstützt", "nicht geprüft"))
        assertTrue("The synthesis line must be readable German",
            report.fileSynthesis.startsWith("funktioniert") || report.fileSynthesis.startsWith("fehlgeschlagen") ||
                report.fileSynthesis.startsWith("nicht geprüft"))
    }

    @Test fun spokenFeedbackStartsWhileAFileIsBeingSynthesized() = runBlocking {
        withContext(Dispatchers.Main) {
            val provider = AndroidSpeechProvider(ApplicationProvider.getApplicationContext<Context>())
            try {
                val voices = provider.voices()
                assumeTrue("Requires German offline voice data on the test device", voices.isNotEmpty())
                val id = voices.first().id
                // Unique and long, so the cache cannot shortcut it and the synthesis is still running when we speak.
                val long = "Satz Nummer ${System.currentTimeMillis()}. " + "Ein langer Satz zum Vorlesen mit vielen Wörtern. ".repeat(70)
                val job = async { provider.synthesize(long, id) }
                provider.say(VOICE_SAMPLE, id)
                var spoke = false
                var stillSynthesizing = false
                repeat(100) {
                    if (!spoke && provider.isSaying()) { spoke = true; stillSynthesizing = job.isActive }
                    if (!spoke) delay(100)
                }
                assertTrue("Announcements must not be swallowed while a chapter is being prepared", spoke)
                assertTrue("The announcement has to start while the file synthesis is still running", stillSynthesizing)
                val audio = job.await()
                assertTrue("Synthesis must still succeed alongside the announcement", audio.durationMs > 1000)
            } finally { provider.close() }
        }
    }
}
