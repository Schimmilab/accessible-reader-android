package de.schimmilab.accessiblereader.speech

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import de.schimmilab.accessiblereader.core.EngineReport
import de.schimmilab.accessiblereader.core.VoiceInfo
import de.schimmilab.accessiblereader.core.describeLanguageAvailability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Asks one installed engine what it can do, instead of assuming the system default behaves like Google's.
 * The target device reported zero voices because its default engine serves only the legacy language API.
 */
object SpeechProbe {
    // Short on purpose: a whole diagnosis runs while someone waits and watches. An engine that needs longer
    // than this to answer is not usable for reading a book anyway.
    private const val INIT_TIMEOUT_MS = 12_000L
    private const val SYNTHESIS_TIMEOUT_MS = 25_000L

    suspend fun probe(context: Context, packageName: String, label: String, isDefault: Boolean): EngineReport {
        val ready = CompletableDeferred<Int>()
        var tts: TextToSpeech? = null
        try {
            tts = TextToSpeech(context.applicationContext, { status -> ready.complete(status) }, packageName)
            val status = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() }
            val engine = tts
            if (status != TextToSpeech.SUCCESS) {
                return EngineReport(packageName, label, isDefault, started = false,
                    germanAvailability = "nicht geprüft", germanVoices = emptyList(), fileSynthesis = "nicht geprüft")
            }
            val availability = runCatching { engine.isLanguageAvailable(Locale.GERMAN) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            val voices = runCatching {
                engine.voices.orEmpty().filter { it.locale.language == "de" }.sortedBy { it.name }
                    .map { VoiceInfo(it.name, it.locale.toString(), it.isNetworkConnectionRequired, it.quality, it.features.orEmpty().toList()) }
            }.getOrDefault(emptyList())
            val synthesis = when {
                availability < 0 && voices.isEmpty() -> "nicht geprüft, diese Maschine kann kein Deutsch"
                else -> writeSample(context, engine, voices.firstOrNull { !it.networkRequired }?.name)
            }
            return EngineReport(packageName, label, isDefault, started = true,
                germanAvailability = describeLanguageAvailability(availability), germanVoices = voices, fileSynthesis = synthesis)
        } finally {
            runCatching { tts?.stop(); tts?.shutdown() }
        }
    }

    /** The decisive test: can this engine write audio to a file, which the whole reader is built on. */
    private suspend fun writeSample(context: Context, engine: TextToSpeech, voiceName: String?): String {
        val file = File(context.cacheDir, "probe-${UUID.randomUUID()}.wav")
        val done = CompletableDeferred<Boolean>()
        val id = UUID.randomUUID().toString()
        return try {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete(true) }
                @Deprecated("Legacy callback")
                override fun onError(utteranceId: String?) { if (utteranceId == id) done.complete(false) }
                override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id) done.complete(false) }
            })
            val voice = voiceName?.let { name -> runCatching { engine.voices.orEmpty().firstOrNull { it.name == name } }.getOrNull() }
            if (voice != null) engine.setVoice(voice) else engine.language = Locale.GERMAN
            val queued = engine.synthesizeToFile("Prüfung der Dateisynthese, ${System.currentTimeMillis()}.", Bundle(), file, id)
            if (queued != TextToSpeech.SUCCESS) return "fehlgeschlagen, die Maschine nimmt den Auftrag nicht an"
            val finished = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) { done.await() }
            when {
                finished == null -> "fehlgeschlagen, keine Antwort innerhalb von ${SYNTHESIS_TIMEOUT_MS / 1000} Sekunden"
                !finished -> "fehlgeschlagen, die Maschine meldet einen Fehler"
                !file.isFile || file.length() <= 44L -> "fehlgeschlagen, die Datei bleibt leer"
                else -> {
                    val duration = withContext(Dispatchers.IO) { duration(file) }
                    if (duration <= 0) "fehlgeschlagen, die Datei enthält kein abspielbares Audio"
                    else "funktioniert, $duration Millisekunden, ${file.length() / 1024} Kilobyte"
                }
            }
        } catch (e: Exception) {
            "fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            file.delete()
        }
    }

    private fun duration(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } finally { retriever.release() }
    }.getOrDefault(0L)
}
