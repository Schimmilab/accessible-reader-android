package de.schimmilab.accessiblereader.speech

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import de.schimmilab.accessiblereader.core.voiceLabel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ReaderVoice(val id: String, val label: String, val needsNetwork: Boolean = false)
/** [fromCache] tells a measurement apart from a file that was already there; a cache hit costs no time. */
data class SpeechAudio(val file: File, val durationMs: Long, val fromCache: Boolean = false)

/** Providers return reusable audio. Cloud implementations must enforce consent and budget before synthesis. */
interface SpeechProvider {
    val providerId: String
    suspend fun voices(): List<ReaderVoice>
    suspend fun synthesize(text: String, voiceId: String): SpeechAudio
    /** Makes room before a section is prepared. A provider that keeps no files of its own has nothing to do. */
    fun trimCache(budgetBytes: Long) {}
    fun close()
}

/**
 * @param enginePackage the speech engine to use, or blank for the system default. The target device defaults to an
 * engine that reports no voices at all, so the choice has to be the user's and not the system's.
 */
class AndroidSpeechProvider(context: Context, private val enginePackage: String = "") : SpeechProvider {
    companion object {
        /** Stands for "whatever German voice this engine uses by default", for engines that report no voices. */
        const val ENGINE_DEFAULT_VOICE = "engine-default-de"

        /**
         * How long one piece of text may take. Measured on an emulator: the stock Google voice needs about
         * 3 milliseconds per character, a local neural voice 30, and up to 115 while it is also speaking an
         * announcement and audio is playing. 300 leaves room above the worst case that was measured without
         * making a genuinely stuck engine hang forever.
         */
        fun synthesisBudgetMs(characters: Int): Long = maxOf(60_000L, 300L * characters)
    }

    override val providerId = "android-local-v1"
    private val cache = File(context.cacheDir, "speech").apply { mkdirs() }
    private val ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val mutex = Mutex()
    private val tts = build(context) { status ->
        if (status == TextToSpeech.SUCCESS) ready.complete(Unit)
        else ready.completeExceptionally(IllegalStateException("Die Sprachausgabe konnte nicht gestartet werden."))
    }

    // A second engine for short spoken feedback. One engine cannot speak and write a file at the same time,
    // so sharing it silenced every announcement while a chapter was being prepared in the background.
    @Volatile private var announcerReady = false
    private val announcer = build(context) { status -> announcerReady = status == TextToSpeech.SUCCESS }

    private fun build(context: Context, listener: (Int) -> Unit) =
        if (enginePackage.isBlank()) TextToSpeech(context.applicationContext, listener)
        else TextToSpeech(context.applicationContext, listener, enginePackage)

    /** Identifies the engine actually in use, so cached audio of two engines can never collide. */
    private fun engineId(): String = enginePackage.ifBlank { runCatching { tts.defaultEngine }.getOrNull().orEmpty() }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { utteranceId?.let { pending.remove(it)?.complete(Unit) } }
            @Deprecated("Legacy callback")
            override fun onError(utteranceId: String?) { fail(utteranceId) }
            @Deprecated("Platform callback")
            override fun onError(utteranceId: String?, errorCode: Int) { fail(utteranceId) }
            private fun fail(id: String?) {
                id?.let { pending.remove(it)?.completeExceptionally(IllegalStateException("Die lokale Stimme konnte den Text nicht erzeugen. Bitte die Sprachdaten prüfen.")) }
            }
        })
    }

    /**
     * Named voices when the engine reports them. Engines that serve only the legacy language API report none,
     * and for those a single entry stands for their built-in German voice, rather than leaving the app unusable.
     */
    override suspend fun voices(): List<ReaderVoice> {
        withTimeout(20_000) { ready.await() }
        val named = runCatching {
            // Voices that fetch their audio from the internet are offered too, clearly marked. The engine does
            // that in its own process, so this app still has no internet permission; what it must not do is
            // spend mobile data unasked, which is why the caller checks the policy before using one.
            tts.voices.orEmpty().filter {
                it.locale.language == "de" &&
                    !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
                .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
                .mapIndexed { i, voice ->
                    ReaderVoice(voice.name,
                        voiceLabel(i, voice.locale.getDisplayCountry(Locale.GERMAN), voice.isNetworkConnectionRequired),
                        voice.isNetworkConnectionRequired)
                }
        }.getOrDefault(emptyList())
        if (named.isNotEmpty()) return named
        val available = runCatching { tts.isLanguageAvailable(Locale.GERMAN) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        return if (available >= TextToSpeech.LANG_AVAILABLE)
            listOf(ReaderVoice(ENGINE_DEFAULT_VOICE, "Standardstimme dieser Sprachausgabe")) else emptyList()
    }

    override suspend fun synthesize(text: String, voiceId: String): SpeechAudio = mutex.withLock {
        withTimeout(20_000) { ready.await() }
        val voice = runCatching { tts.voices.orEmpty().firstOrNull { it.name == voiceId } }.getOrNull()
        if (voice == null && voiceId != ENGINE_DEFAULT_VOICE) {
            error("Diese Stimme ist nicht mehr verfügbar. Bitte in den Einstellungen eine Stimme wählen.")
        }
        val key = MessageDigest.getInstance("SHA-256").digest("$providerId|${engineId()}|$voiceId|$text".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(cache, "$key.wav")
        if (file.isFile && file.length() > 44) {
            val duration = duration(file)
            // Touch it so trimCache treats recently heard chapters as recently used, not as old.
            if (duration > 0) {
                file.setLastModified(System.currentTimeMillis())
                return@withLock SpeechAudio(file, duration, fromCache = true)
            }
        }
        val temp = File(cache, "$key.part.wav")
        val id = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Unit>()
        try {
            if (voice != null) check(tts.setVoice(voice) == TextToSpeech.SUCCESS) { "Die gewählte Stimme ist nicht verfügbar." }
            else check(tts.setLanguage(Locale.GERMAN) >= TextToSpeech.LANG_AVAILABLE) { "Diese Sprachausgabe kann kein Deutsch." }
            tts.setSpeechRate(1f)
            pending[id] = completion
            check(tts.synthesizeToFile(text, Bundle(), temp, id) == TextToSpeech.SUCCESS) { "Sprachausgabe konnte nicht vorbereitet werden." }
            // A neural voice can need as long as the audio itself, and longer while it is also speaking an
            // announcement. A flat limit turned that into silence, so the budget follows the length of the text.
            // withTimeoutOrNull on purpose: withTimeout throws a CancellationException, which every caller has to
            // treat as "the user cancelled", and a swallowed timeout leaves a listener waiting forever.
            val budget = synthesisBudgetMs(text.length)
            withTimeoutOrNull(budget) { completion.await() }
                ?: error("Diese Stimme hat für diesen Abschnitt länger als ${budget / 1000} Sekunden gebraucht. " +
                    "Bitte eine andere Stimme wählen oder es noch einmal versuchen.")
            val duration = withContext(Dispatchers.IO) { duration(temp) }
            check(duration > 0 && temp.renameTo(file)) { "Die erzeugte Audiodatei ist unvollständig." }
            SpeechAudio(file, duration)
        } finally {
            pending.remove(id)
            if (!completion.isCompleted) tts.stop()
            temp.delete()
        }
    }

    /** Speaks short feedback. Works while a chapter is being prepared; [voiceId] keeps it in the book voice. */
    fun say(text: String, voiceId: String = "") {
        if (!announcerReady) return
        val voice = runCatching { announcer.voices.orEmpty().firstOrNull { it.name == voiceId && !it.isNetworkConnectionRequired } }.getOrNull()
        if (voice != null) announcer.setVoice(voice) else announcer.language = Locale.GERMAN
        announcer.speak(text, TextToSpeech.QUEUE_FLUSH, null, "status")
    }

    /** Installed engines as package name to label, for the engine picker and the diagnosis. */
    fun engines(): Map<String, String> =
        runCatching { tts.engines.associate { it.name to it.label } }.getOrDefault(emptyMap())

    fun defaultEngineName(): String = runCatching { tts.defaultEngine.orEmpty() }.getOrDefault("")

    fun isSaying(): Boolean = announcerReady && runCatching { announcer.isSpeaking }.getOrDefault(false)
    fun stopSaying() { if (announcerReady) announcer.stop() }

    private fun duration(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } finally { retriever.release() }
    }.getOrDefault(0L)

    fun cacheSize(): Long = cache.listFiles().orEmpty().sumOf { it.length() }

    /**
     * Frees space by deleting the least recently used audio, down to [keepBytes]. A book of several hundred
     * pages produces far more audio than any sane budget, so refusing to play once the cache is full would
     * strand a listener in the middle of a book. Deleted audio is simply synthesized again when needed.
     */
    override fun trimCache(budgetBytes: Long) = trimCache(budgetBytes, budgetBytes * 4 / 5)

    fun trimCache(budgetBytes: Long, keepBytes: Long) {
        val files = cache.listFiles().orEmpty().filter { it.isFile }
        var size = files.sumOf { it.length() }
        if (size <= budgetBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (size <= keepBytes) return
            val length = file.length()
            if (file.delete()) size -= length
        }
    }
    fun clearCache() { cache.listFiles().orEmpty().forEach { it.delete() } }
    override fun close() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        ready.cancel()
        tts.stop()
        tts.shutdown()
        announcer.stop()
        announcer.shutdown()
    }
}
