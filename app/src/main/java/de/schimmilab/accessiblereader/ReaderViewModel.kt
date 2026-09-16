package de.schimmilab.accessiblereader

import android.app.Application
import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.schimmilab.accessiblereader.core.*
import org.json.JSONArray
import org.json.JSONObject
import de.schimmilab.accessiblereader.data.DocumentStore
import de.schimmilab.accessiblereader.playback.ReaderPlaybackService
import de.schimmilab.accessiblereader.playback.SectionPreparer
import de.schimmilab.accessiblereader.playback.SectionProgress
import de.schimmilab.accessiblereader.speech.*
import de.schimmilab.accessiblereader.speech.SpeechProbe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ReaderState(
    val document: ReaderDocument = demoDocument(), val chapter: Int = 0,
    val busy: Boolean = false, val playing: Boolean = false, val connected: Boolean = false,
    // Unlike isPlaying, this stays true while TalkBack temporarily owns audio focus.
    val playbackRequested: Boolean = false,
    // True while later audio parts of the current chapter are still being synthesized in the background.
    val preparing: Boolean = false,
    val positionMs: Long = 0, val durationMs: Long = 0, val speed: Float = 1f,
    val voices: List<ReaderVoice> = emptyList(), val voiceId: String = "",
    /** The second voice, for what the characters of a novel say. Blank means one voice reads everything. */
    val dialogueVoiceId: String = "",
    val engines: Map<String, String> = emptyMap(), val engineId: String = "",
    val status: String = "Bereit für deine erste Leseprobe.", val error: String? = null,
    val showContents: Boolean = false, val showSettings: Boolean = false,
    val cacheBytes: Long = 0, val listening: Boolean = false,
    val report: String = "",
    val onlineVoices: OnlineVoicePolicy = OnlineVoicePolicy.WIFI_ONLY,
    /** What was measured about the speed of the chosen voice, or null while nothing has been measured yet. */
    val voiceSpeed: String? = null,
    // Separate from busy: a running diagnosis must not lock the screen someone is waiting in front of.
    val diagnosing: Boolean = false,
    val library: List<LibraryItem> = emptyList(), val showLibrary: Boolean = false, val pendingRemoval: String? = null,
    /** Places in the current document someone asked to come back to, newest first. */
    val bookmarks: List<Bookmark> = emptyList(), val showBookmarks: Boolean = false,
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        /** Minimum audio buffered ahead of the start position before playback begins, so the chapter intro cannot drain into silence. */
        const val MIN_LEAD_MS = SectionPreparer.MIN_LEAD_MS
        /** Upper bound for generated audio kept on the device; oldest is dropped first. */
        const val CACHE_BUDGET_BYTES = SectionPreparer.CACHE_BUDGET_BYTES
    }
    private val prefs = application.getSharedPreferences("reader", Application.MODE_PRIVATE)
    private val accessibility = application.getSystemService(Application.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val store = DocumentStore(application)
    // Replaced when the user picks another speech engine, so it cannot be a val.
    private var speech = AndroidSpeechProvider(application, application.getSharedPreferences("reader", Application.MODE_PRIVATE).getString("engine", "").orEmpty())
    private val mutable = MutableStateFlow(ReaderState(speed = storedSpeed(prefs.getString("voice", "").orEmpty())))
    val state = mutable.asStateFlow()
    private var player: MediaController? = null
    private var work: Job? = null
    private var prefetch: Job? = null
    private var preparedKey: String? = null
    private var durations: List<Long> = emptyList()
    private val controllerFuture = MediaController.Builder(application, SessionToken(application, ComponentName(application, ReaderPlaybackService::class.java)))
        .setListener(object : MediaController.Listener {
            // Media keys and notification buttons: the service turns next/previous into chapter requests.
            override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                when (command.customAction) {
                    ReaderPlaybackService.COMMAND_NEXT_CHAPTER -> chapter(state.value.chapter + 1)
                    ReaderPlaybackService.COMMAND_PREVIOUS_CHAPTER -> chapter(state.value.chapter - 1)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).buildAsync()

    init {
        // A fresh view model means nothing is being prepared; clears a flag left behind by a killed process.
        // The second flag tells the service that the app is here and will look after the next section itself.
        prefs.edit().putBoolean(ReaderPlaybackService.KEY_PREPARING, false)
            .putBoolean(ReaderPlaybackService.KEY_UI_ALIVE, true).apply()
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.onSuccess { controller ->
                player = controller
                controller.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { showError("Das Audio konnte nicht abgespielt werden. Bitte erneut starten.") }
                    override fun onEvents(player: Player, events: Player.Events) { syncPlayer() }
                })
                mutable.update { it.copy(connected = true) }
            }.onFailure { showError("Der Audioplayer konnte nicht verbunden werden.") }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch {
            val lastId = prefs.getString("document", null)
            val document = withContext(Dispatchers.IO) { lastId?.let(store::load) } ?: demoDocument()
            mutable.update { it.copy(document = document, bookmarks = readBookmarks(document.id),
                chapter = prefs.getInt("${document.id}.chapter", 0).coerceIn(document.chapters.indices)) }
            refreshVoices()
            while (isActive) {
                syncPlayer()
                delay(500)
            }
        }
    }

    private fun syncPlayer() {
        val p = player ?: return
        val extra = p.currentMediaItem?.mediaMetadata?.extras
        if (extra?.getString("document") == state.value.document.id) {
            durations = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaMetadata.extras?.getLong("duration") ?: 0 }
            val chapter = extra.getInt("chapter")
            val voice = extra.getString("voice").orEmpty()
            preparedKey = "${state.value.document.id}:$chapter:$voice"
            mutable.update { it.copy(chapter = chapter, playing = p.isPlaying, playbackRequested = p.wantsPlayback(),
                positionMs = AudioTimeline.absolute(durations, p.currentMediaItemIndex, p.currentPosition), durationMs = durations.sum()) }
            // A finished chapter continues with the next one; the last chapter stays at its end and offers a restart.
            val s = state.value
            if (p.playbackState == Player.STATE_ENDED && p.playWhenReady && !s.preparing && !s.busy && chapter + 1 in s.document.chapters.indices) {
                chapter(chapter + 1, announce = false)
                play(quiet = true)
            }
        } else mutable.update { it.copy(playing = false, playbackRequested = false) }
    }

    // Running out of prepared parts while more are coming is not a chapter end.
    private fun Player.wantsPlayback(): Boolean = playWhenReady && playbackState != Player.STATE_IDLE &&
        (playbackState != Player.STATE_ENDED || state.value.preparing)

    fun refreshVoices() { viewModelScope.launch {
        try {
            val voices = speech.voices()
            val engines = speech.engines()
            val chosen = prefs.getString("engine", "").orEmpty().ifBlank { speech.defaultEngineName() }
            val policy = runCatching { OnlineVoicePolicy.valueOf(prefs.getString("onlineVoices", "") ?: "") }
                .getOrDefault(OnlineVoicePolicy.WIFI_ONLY)
            // A second voice from another engine cannot be reached, so it is dropped when the engine changes.
            val dialogue = voices.firstOrNull { it.id == prefs.getString("voice.dialogue", null) }?.id.orEmpty()
            mutable.update { current -> current.copy(voices = voices, engines = engines, engineId = chosen, onlineVoices = policy,
                dialogueVoiceId = dialogue,
                voiceId = voices.firstOrNull { it.id == prefs.getString("voice", null) }?.id ?: voices.firstOrNull()?.id.orEmpty(),
                voiceSpeed = measuredSpeed(voices.firstOrNull { it.id == prefs.getString("voice", null) }?.id
                    ?: voices.firstOrNull()?.id.orEmpty()),
                speed = storedSpeed(voices.firstOrNull { it.id == prefs.getString("voice", null) }?.id
                    ?: voices.firstOrNull()?.id.orEmpty()),
                status = if (voices.isEmpty())
                    "Diese Sprachausgabe meldet keine deutsche Stimme. Bitte in Stimme und Einstellungen eine andere Sprachausgabe wählen."
                else current.status) }
        } catch (e: Exception) { if (e is CancellationException) throw e; showError(e.message ?: "Stimmen konnten nicht geladen werden.") }
    } }

    /** Switches the speech engine itself. Needed because the system default may report no voices at all. */
    fun engine(packageName: String) {
        if (state.value.busy || packageName == state.value.engineId) return
        stopPreparation()
        pause(); player?.stop(); player?.clearMediaItems(); preparedKey = null; durations = emptyList()
        prefs.edit().putString("engine", packageName).remove("voice").remove("voice.dialogue").apply()
        speech.close()
        speech = AndroidSpeechProvider(getApplication(), packageName)
        mutable.update { it.copy(engineId = packageName, voices = emptyList(), voiceId = "",
            durationMs = 0, positionMs = 0, status = "Sprachausgabe gewechselt. Stimmen werden geladen.") }
        refreshVoices()
    }

    fun importDocument(uri: Uri) {
        if (state.value.busy) return
        player?.pause()
        work?.cancel()
        work = viewModelScope.launch {
            mutable.update { it.copy(busy = true, preparing = false, error = null, status = "Öffne Datei …") }
            try {
                val document = store.import(uri) { message -> mutable.update { it.copy(status = message) } }
                open(document)
                refreshLibrary()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showError(if (e.javaClass.simpleName.contains("Password")) "Dieses PDF ist passwortgeschützt. Bitte eine entsperrte Kopie verwenden." else e.message ?: "PDF konnte nicht gelesen werden.")
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun demo() { if (!state.value.busy) { stopPreparation(); open(demoDocument()) } }
    /** Replaces the current document. Callers outside the import job must cancel a running preparation first. */
    fun open(document: ReaderDocument) {
        player?.stop(); player?.clearMediaItems()
        preparedKey = null; durations = emptyList()
        prefs.edit().putString("document", document.id).apply()
        val saved = prefs.getInt("${document.id}.chapter", 0).coerceIn(document.chapters.indices)
        val heard = prefs.contains("${document.id}.voice")
        mutable.update { it.copy(document = document, chapter = saved, durationMs = 0, positionMs = 0, error = null,
            bookmarks = readBookmarks(document.id), showBookmarks = false,
            status = openedMessage(document.title, document.chapters.size, saved, heard)) }
    }

    fun togglePlayback() { if (player?.wantsPlayback() == true) pause() else play() }
    fun pause() { player?.pause(); mutable.update { it.copy(playing = false, playbackRequested = false, status = "Pausiert.") } }
    /** [quiet] skips status updates so an automatic chapter change causes no TalkBack announcement. */
    fun play(quiet: Boolean = false) {
        val s = state.value
        if (s.busy || s.listening) return
        val p = player ?: return showError("Der Audioplayer verbindet sich noch.")
        if (s.voiceId.isBlank()) { settings(true); return showError("Bitte eine deutsche Offline-Stimme installieren und anschließend Stimmen neu laden.") }
        val chapter = s.document.chapters[s.chapter]
        if (chapter.text.isBlank()) return showError("Dieser Abschnitt enthält keinen lesbaren Text. Bitte einen anderen Abschnitt wählen.")
        // Checked before anything is prepared, so a voice that cannot work right now says so instead of failing
        // halfway through a chapter.
        // Both voices are checked: a section that starts in the narrator's voice and stops at the first line of
        // dialogue would be worse than not starting at all.
        if (s.voices.any { (it.id == s.voiceId || it.id == s.dialogueVoiceId) && it.needsNetwork }) {
            val network = networkKind()
            if (!mayUseOnlineVoice(s.onlineVoices, network)) return showError(onlineVoiceRefusal(s.onlineVoices, network))
        }
        val key = "${s.document.id}:${s.chapter}:${cast(s).key}"
        if (preparedKey == key && p.mediaItemCount > 0) {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0)
            p.prepare(); p.play(); syncPlayer(); mutable.update { it.copy(status = "Wiedergabe läuft.") }; return
        }
        work?.cancel()
        work = viewModelScope.launch {
            prefs.edit().putBoolean(ReaderPlaybackService.KEY_PREPARING, true).apply()
            mutable.update { it.copy(busy = true, preparing = true, error = null) }
            var started = false
            try {
                preparedKey = null; durations = emptyList()
                // The very preparer the playback service uses when the app is gone, so there is one loop and not
                // two copies of the subtleties it contains.
                SectionPreparer(speech, prefs, MIN_LEAD_MS, CACHE_BUDGET_BYTES)
                    .prepare(p, s.document, s.chapter, cast(s), state.value.speed, object : SectionProgress {
                        override fun onPreparing(index: Int, total: Int) {
                            if (!quiet) mutable.update { it.copy(status = "Bereite Audio vor: Teil ${index + 1} von $total.") }
                        }
                        override fun onSynthesized(workMs: Long, audioMs: Long, characters: Int, fromCache: Boolean) {
                            // Only real work counts. A cache hit costs nothing and would make every voice look instant.
                            // Only measured while one voice reads everything. With two, a piece cannot be told
                            // apart from here, and mixing both rates into one average would describe neither.
                            if (!fromCache && audioMs > 0 && !cast(s).twoVoices)
                                recordVoiceSpeed(s.voiceId, workMs, audioMs, characters)
                        }
                        override fun onStarted(key: String, pending: Int) {
                            preparedKey = key; started = true
                            syncPlayer()
                            val more = if (pending > 0) " Weitere Teile werden im Hintergrund vorbereitet." else ""
                            mutable.update { it.copy(busy = false,
                                status = if (quiet) it.status else "${chapter.title}. Wiedergabe läuft.$more") }
                        }
                        override fun onAppended() = syncPlayer()
                    })
                // The section is complete. Put the beginning of the next one into the audio cache while this one
                // is still being listened to, so the change of section does not fall silent.
                if (state.value.playbackRequested) prefetchNext(s.document, s.chapter + 1, cast(s))
            } catch (e: Exception) {
                // A timeout arrives as a CancellationException too. Rethrowing it silently was the worst bug this
                // app had: a slow voice simply stopped, with no sound, no message and nothing to press.
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (started) preparedKey = null // next Play retries the missing parts from the saved position
                showError(if (started) "Das restliche Audio dieses Kapitels konnte nicht vorbereitet werden. Bitte Vorlesen erneut starten." else e.message ?: "Audio konnte nicht vorbereitet werden.")
            } finally {
                prefs.edit().putBoolean(ReaderPlaybackService.KEY_PREPARING, false).apply()
                mutable.update { it.copy(busy = false, preparing = false, cacheBytes = speech.cacheSize()) }
            }
        }
    }

    /**
     * Synthesizes the opening of the next section into the audio cache while the current one plays.
     *
     * Measured before this existed: playback stopped for 8 to 12 seconds at every section change, because the
     * next section was only synthesized once the previous one had ended. Nothing here touches playback or the
     * state; the cache is keyed by provider, engine, voice and text, so the later `play()` simply finds the
     * pieces already there. In the worst case this is wasted work, never a wrong result.
     *
     * Deliberately outside `preparing`: that flag gates automatic continuation, and setting it here would stop
     * the very thing this exists to smooth.
     */
    private fun prefetchNext(document: ReaderDocument, index: Int, cast: VoiceCast) {
        prefetch?.cancel()
        val chapter = document.chapters.getOrNull(index) ?: return
        if (chapter.text.isBlank() || cast.narrator.isBlank()) return
        prefetch = viewModelScope.launch {
            runCatching {
                val texts = listOf(Passage(SpeakingRole.NARRATOR,
                    ChapterAnnouncement.text(index, document.chapters.size, chapter.title))) +
                    (if (cast.twoVoices) Narration.partsForPlayback(chapter.text)
                    else TextChunks.splitForPlayback(chapter.text).map { Passage(SpeakingRole.NARRATOR, it) })
                var ready = 0L
                for (passage in texts) {
                    ensureActive()
                    ready += speech.synthesize(passage.text, cast.voiceFor(passage.role)).durationMs
                    // Enough for playback to start on; the rest is prepared as usual once the section is open.
                    if (ready >= MIN_LEAD_MS) break
                }
            }
        }
    }

    private fun stopPreparation() {
        // Cancels the running synthesis only. Whatever the prefetch already wrote stays in the cache and is
        // exactly what the next section needs.
        prefetch?.cancel()
        work?.cancel()
        prefs.edit().putBoolean(ReaderPlaybackService.KEY_PREPARING, false).apply()
        mutable.update { it.copy(busy = false, preparing = false) }
    }
    fun cancelWork() { stopPreparation(); mutable.update { it.copy(status = "Vorgang abgebrochen.") } }
    /** [announce] false keeps the live-region status untouched, e.g. when a chapter continues automatically. */
    fun chapter(index: Int, announce: Boolean = true) {
        if (state.value.busy || index !in state.value.document.chapters.indices) return
        val wasPlaying = player?.wantsPlayback() == true
        stopPreparation()
        player?.stop(); player?.clearMediaItems(); preparedKey = null; durations = emptyList()
        val id = state.value.document.id
        prefs.edit().putInt("$id.chapter", index).putInt("$id.item", 0).putLong("$id.offset", 0).putBoolean("$id.finished", false).apply()
        mutable.update { it.copy(chapter = index, positionMs = 0, durationMs = 0, playing = false, playbackRequested = false, showContents = false,
            status = if (announce) "${it.document.chapters[index].title} ausgewählt." else it.status) }
        if (wasPlaying) play()
    }
    fun seek(seconds: Int) {
        val p = player ?: return
        if (durations.isEmpty() || state.value.busy) return showError("Bitte zuerst das Vorlesen starten, damit Audio vorbereitet wird.")
        val absolute = AudioTimeline.absolute(durations, p.currentMediaItemIndex, p.currentPosition)
        val target = AudioTimeline.locate(durations, absolute + seconds * 1000L)
        p.seekTo(target.item, target.offsetMs)
        syncPlayer()
        val end = if (state.value.preparing) "das bereits vorbereitete Audio" else "das Kapitelende"
        mutable.update { it.copy(status = if (seconds < 0) "${-seconds} Sekunden zurück, begrenzt auf den Kapitelanfang." else "$seconds Sekunden vor, begrenzt auf $end.") }
    }
    /**
     * The speed remembered for one voice. Voices differ in how fast they speak at their own natural rate, by a
     * lot: a listener had to take one neural voice down to keep up with it while the stock voices were fine at
     * normal. One number for all of them means changing the voice silently changes the speed.
     */
    private fun storedSpeed(voiceId: String): Float {
        if (voiceId.isBlank()) return prefs.getFloat("speed", 1f)
        // Falls back to whatever was set before voices had their own, so nobody's setting is lost.
        return prefs.getFloat("speed.rate.$voiceId", prefs.getFloat("speed", 1f))
    }

    fun speed(value: Float) {
        // Rounded to the step, so repeated presses cannot drift into 1.2000001 and make a screen reader read that.
        val speed = (Math.round(value / SPEED_STEP) * SPEED_STEP).coerceIn(SPEED_MIN, SPEED_MAX)
        player?.setPlaybackSpeed(speed)
        val voiceId = state.value.voiceId
        // Written per voice only. The old single value stays where it is and serves as the starting point for a
        // voice that has never been adjusted; writing it here would let one voice drag all the others along.
        if (voiceId.isNotBlank()) prefs.edit().putFloat("speed.rate.$voiceId", speed).apply()
        else prefs.edit().putFloat("speed", speed).apply()
        // Said out loud on purpose: the screen reader keeps its focus on the button that was pressed and would
        // never read the value that changed because of it.
        mutable.update { it.copy(speed = speed, status = speedAnnouncement(speed)) }
    }
    fun slower() = speed(state.value.speed - SPEED_STEP)
    fun faster() = speed(state.value.speed + SPEED_STEP)
    fun voice(id: String) {
        if (state.value.busy) return
        stopPreparation()
        pause(); player?.clearMediaItems(); preparedKey = null; durations = emptyList()
        // Picking the voice that was reading the dialogue leaves one voice for everything, which is what it is.
        if (id == state.value.dialogueVoiceId) prefs.edit().remove("voice.dialogue").apply()
        prefs.edit().putString("voice", id).apply()
        // Every voice keeps its own speed, because they do not speak at the same rate at all.
        val speed = storedSpeed(id)
        player?.setPlaybackSpeed(speed)
        mutable.update { it.copy(voiceId = id, speed = speed, durationMs = 0, positionMs = 0, voiceSpeed = measuredSpeed(id),
            dialogueVoiceId = if (id == it.dialogueVoiceId) "" else it.dialogueVoiceId,
            status = "Stimme gewechselt, ${speedLabel(speed)} fach. Das Vorlesen setzt beim nächsten Start kurz vor deiner Stelle wieder ein.") }
    }
    /** The voices the current selection reads with: the chosen one, plus a second one for direct speech. */
    private fun cast(s: ReaderState = state.value) = VoiceCast(s.voiceId, s.dialogueVoiceId)

    /**
     * Chooses the voice for what the characters say, or clears it with a blank id. Both voices come from the
     * same engine, because one provider speaks to one engine.
     */
    fun dialogueVoice(id: String) {
        if (state.value.busy || id == state.value.dialogueVoiceId) return
        stopPreparation()
        pause(); player?.clearMediaItems(); preparedKey = null; durations = emptyList()
        prefs.edit().putString("voice.dialogue", id).apply()
        val label = state.value.voices.firstOrNull { it.id == id }?.label
        mutable.update { it.copy(dialogueVoiceId = id, durationMs = 0, positionMs = 0,
            status = if (label == null) "Eine Stimme liest alles. Das Vorlesen beginnt beim nächsten Start neu."
            else "$label spricht ab jetzt, was die Figuren sagen. Das Vorlesen beginnt beim nächsten Start neu.") }
    }

    /**
     * Remembers the place being listened to.
     *
     * The three numbers are the ones the resume position uses, so coming back to a bookmark is the same
     * machinery as continuing after the app was closed, down to what happens when the voice has changed since.
     */
    fun mark() {
        val s = state.value
        val player = this.player
        val item = player?.currentMediaItemIndex ?: 0
        val offset = player?.currentPosition ?: 0
        val title = s.document.chapters.getOrNull(s.chapter)?.title.orEmpty()
        val bookmark = Bookmark(chapter = s.chapter, item = item, offsetMs = offset, positionMs = s.positionMs,
            voiceId = cast(s).key, label = bookmarkLabel(title, s.positionMs), createdAt = System.currentTimeMillis())
        val updated = addBookmark(s.bookmarks, bookmark)
        storeBookmarks(s.document.id, updated)
        mutable.update { it.copy(bookmarks = updated, status = bookmarkSetAnnouncement(bookmark.label)) }
    }

    fun bookmarks(open: Boolean) = mutable.update { it.copy(showBookmarks = open) }

    /** Goes back to a bookmark by handing it to the resume position and starting the section again. */
    fun goToBookmark(bookmark: Bookmark) {
        val s = state.value
        if (s.busy) return
        stopPreparation()
        pause(); player?.clearMediaItems(); preparedKey = null; durations = emptyList()
        prefs.edit()
            .putInt("${s.document.id}.chapter", bookmark.chapter)
            .putInt("${s.document.id}.item", bookmark.item)
            .putLong("${s.document.id}.offset", bookmark.offsetMs)
            .putString("${s.document.id}.voice", bookmark.voiceId)
            .putBoolean("${s.document.id}.finished", false)
            .apply()
        mutable.update { it.copy(chapter = bookmark.chapter.coerceIn(s.document.chapters.indices),
            showBookmarks = false, positionMs = 0, durationMs = 0, status = "Weiter bei ${bookmark.label}.") }
        play(quiet = true)
    }

    fun removeBookmark(bookmark: Bookmark) {
        val updated = state.value.bookmarks.filterNot { it.createdAt == bookmark.createdAt }
        storeBookmarks(state.value.document.id, updated)
        mutable.update { it.copy(bookmarks = updated, status = "Lesezeichen entfernt: ${bookmark.label}.") }
    }

    private fun storeBookmarks(documentId: String, list: List<Bookmark>) {
        val array = JSONArray()
        for (b in list) array.put(JSONObject().apply {
            put("chapter", b.chapter); put("item", b.item); put("offset", b.offsetMs)
            put("position", b.positionMs); put("voice", b.voiceId); put("label", b.label); put("created", b.createdAt)
        })
        prefs.edit().putString("$documentId.bookmarks", array.toString()).apply()
    }

    private fun readBookmarks(documentId: String): List<Bookmark> = runCatching {
        val array = JSONArray(prefs.getString("$documentId.bookmarks", "[]"))
        (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            Bookmark(o.getInt("chapter"), o.getInt("item"), o.getLong("offset"), o.getLong("position"),
                o.optString("voice"), o.optString("label"), o.getLong("created"))
        }
    }.getOrDefault(emptyList())

    fun library(open: Boolean) {
        mutable.update { it.copy(showLibrary = open) }
        if (open) refreshLibrary()
    }

    private fun refreshLibrary() {
        viewModelScope.launch {
            val entries = runCatching { store.library() }.getOrDefault(emptyList())
            val current = state.value.document.id
            mutable.update { s ->
                s.copy(library = entries.map { entry ->
                    LibraryItem(entry.id, entry.title,
                        libraryLabel(entry, prefs.getInt("${entry.id}.chapter", 0), prefs.contains("${entry.id}.voice")),
                        entry.id == current)
                })
            }
        }
    }

    fun openFromLibrary(id: String) {
        if (state.value.busy) return
        viewModelScope.launch {
            val document = withContext(Dispatchers.IO) { store.load(id) }
            if (document == null) {
                showError("Dieses Dokument konnte nicht geladen werden. Es wurde aus der Bibliothek entfernt.")
                withContext(Dispatchers.IO) { store.remove(id) }
                refreshLibrary()
                return@launch
            }
            stopPreparation()
            open(document)
            mutable.update { it.copy(showLibrary = false) }
        }
    }

    /** Removal is destructive and unreachable by accident: the screen asks first. */
    fun askRemoval(id: String?) { mutable.update { it.copy(pendingRemoval = id) } }

    fun confirmRemoval() {
        val id = state.value.pendingRemoval ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.remove(id) }
            prefs.edit().remove("$id.chapter").remove("$id.item").remove("$id.offset")
                .remove("$id.voice").remove("$id.finished").apply()
            if (state.value.document.id == id) { stopPreparation(); open(demoDocument()) }
            mutable.update { it.copy(pendingRemoval = null, status = "Dokument aus der Bibliothek entfernt.") }
            refreshLibrary()
        }
    }

    fun contents(open: Boolean) { mutable.update { it.copy(showContents = open) } }
    /**
     * Opening the settings asks the engine for its voices again. An engine can report only its built-in voices
     * right after starting and the downloaded ones a moment later, and a listener comparing the diagnosis with
     * this list would otherwise see two different numbers with no way to reconcile them.
     */
    /**
     * What kind of connection the device is on. Reading this needs ACCESS_NETWORK_STATE, which grants no network
     * access of its own; the app still cannot reach the internet, and the voices that can are served by the
     * speech engine in its own process.
     */
    private fun networkKind(): NetworkKind {
        val manager = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { manager?.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
            ?: return NetworkKind.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return NetworkKind.NONE
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) NetworkKind.UNMETERED
        else NetworkKind.METERED
    }

    /**
     * Keeps a running measurement of how long this voice needs per second of audio, and puts it in front of the
     * listener. Whether a voice can keep up decides whether a book plays through or keeps stopping, and it is
     * invisible from the outside: the neural voice sounds better and needs thirty times longer than the stock one.
     *
     * Averaged over everything measured so far for that voice, so one slow first piece does not label it.
     */
    private fun recordVoiceSpeed(voiceId: String, synthesisMs: Long, audioMs: Long, characters: Int) {
        val key = "speed.${voiceId}"
        val work = prefs.getLong("$key.work", 0) + synthesisMs
        val audio = prefs.getLong("$key.audio", 0) + audioMs
        val letters = prefs.getLong("$key.chars", 0) + characters
        prefs.edit().putLong("$key.work", work).putLong("$key.audio", audio).putLong("$key.chars", letters).apply()
        if (audio <= 0) return
        val note = voiceSpeedNote(work.toDouble() / audio, wordsPerMinute(letters.toInt(), audio))
        if (note != state.value.voiceSpeed) mutable.update { it.copy(voiceSpeed = note) }
    }

    /** Reads back what was measured for a voice earlier, so the note survives a restart. */
    private fun measuredSpeed(voiceId: String): String? {
        val audio = prefs.getLong("speed.$voiceId.audio", 0)
        if (audio <= 0) return null
        return voiceSpeedNote(prefs.getLong("speed.$voiceId.work", 0).toDouble() / audio,
            wordsPerMinute(prefs.getLong("speed.$voiceId.chars", 0).toInt(), audio))
    }

    fun onlineVoices(policy: OnlineVoicePolicy) {
        prefs.edit().putString("onlineVoices", policy.name).apply()
        mutable.update { it.copy(onlineVoices = policy, status = when (policy) {
            OnlineVoicePolicy.NEVER -> "Online-Stimmen sind aus. Es werden nur Stimmen verwendet, die offline arbeiten."
            OnlineVoicePolicy.WIFI_ONLY -> "Online-Stimmen nur im WLAN."
            OnlineVoicePolicy.ALWAYS -> "Online-Stimmen auch über mobile Daten. Das verbraucht dein Datenvolumen."
        }) }
    }

    fun settings(open: Boolean) {
        mutable.update { it.copy(showSettings = open, cacheBytes = speech.cacheSize()) }
        if (open) refreshVoices()
    }
    fun clearCache() {
        if (state.value.busy) return
        stopPreparation()
        pause(); player?.stop(); player?.clearMediaItems(); preparedKey = null; durations = emptyList()
        speech.clearCache(); mutable.update { it.copy(cacheBytes = 0, durationMs = 0, status = "Erzeugtes Audio gelöscht. Deine PDFs und Hörpositionen bleiben gespeichert.") }
    }
    fun positionText(): String {
        val s = state.value
        return "${s.document.chapters[s.chapter].title}, Abschnitt ${s.chapter + 1} von ${s.document.chapters.size}. ${s.positionMs / 60000} Minuten und ${(s.positionMs / 1000) % 60} Sekunden."
    }
    /** With TalkBack running the live region already speaks the status; the book voice would double every announcement. */
    private fun screenReaderActive(): Boolean =
        accessibility.isEnabled && accessibility.isTouchExplorationEnabled

    fun announcePosition() {
        pause()
        val text = positionText()
        mutable.update { it.copy(status = text) }
        if (!screenReaderActive()) speech.say(text, state.value.voiceId)
    }

    /**
     * Asks the device what its speech engine can do. Built because the target phone is a Samsung with the Vocalizer
     * engine, where file synthesis cannot be assumed, and no such device is available here.
     */
    fun runDiagnostics() {
        if (state.value.diagnosing) return
        viewModelScope.launch {
            mutable.update { it.copy(diagnosing = true, error = null, report = "", status = "Prüfung beginnt …") }
            try {
                val application = getApplication<Application>()
                val default = speech.defaultEngineName()
                // Every installed engine is probed, not just the default: on the target device the default reports nothing.
                val installed = speech.engines().entries.sortedBy { it.value }
                val engines = installed.mapIndexed { index, (packageName, label) ->
                    mutable.update { it.copy(status = "Prüfe ${index + 1} von ${installed.size}: $label …") }
                    SpeechProbe.probe(application, packageName, label, packageName == default)
                }
                val version = runCatching {
                    application.packageManager.getPackageInfo(application.packageName, 0).versionName
                }.getOrNull().orEmpty()
                val report = SpeechReport(
                    appVersion = version, device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidRelease = Build.VERSION.RELEASE, sdk = Build.VERSION.SDK_INT,
                    screenReader = screenReaderActive(), defaultEngine = default, engines = engines,
                )
                mutable.update { it.copy(report = report.format(), status = "Prüfung abgeschlossen. Der Bericht steht unter der Taste.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showError(e.message ?: "Die Prüfung der Sprachausgabe ist fehlgeschlagen.")
            } finally { mutable.update { it.copy(diagnosing = false, cacheBytes = speech.cacheSize()) } }
        }
    }

    /** Plays the same sample in the chosen voice, so voices can be told apart by ear instead of by label. */
    fun previewVoice(id: String) {
        if (state.value.busy) return
        if (state.value.playbackRequested) pause()
        speech.say(VOICE_SAMPLE, id)
    }

    fun readContents() {
        pause()
        // Always the book voice: hearing the list as continuous speech is the whole point of this button.
        speech.say(state.value.document.chapters.take(20).mapIndexed { i, c -> "Abschnitt ${i + 1}: ${c.title}." }.joinToString(" ") + if (state.value.document.chapters.size > 20) " Weitere Abschnitte stehen im Inhaltsverzeichnis." else "", state.value.voiceId)
    }
    fun listening(active: Boolean) { if (active) pause(); mutable.update { it.copy(listening = active, status = if (active) "Ich höre zu. Sage einen Befehl." else "Spracheingabe beendet.") } }
    fun command(text: String) {
        mutable.update { it.copy(listening = false) }
        when (val command = CommandParser.parse(text)) {
            ReaderCommand.Play -> play()
            ReaderCommand.Pause -> pause()
            ReaderCommand.Next -> chapter(state.value.chapter + 1)
            ReaderCommand.Previous -> chapter(state.value.chapter - 1)
            ReaderCommand.Contents -> contents(true)
            ReaderCommand.Library -> library(true)
            ReaderCommand.Position -> announcePosition()
            ReaderCommand.Mark -> mark()
            ReaderCommand.Bookmarks -> bookmarks(true)
            is ReaderCommand.Seek -> seek(command.seconds)
            is ReaderCommand.GoTo -> if (command.chapter in 1..state.value.document.chapters.size) chapter(command.chapter - 1) else showError("Diesen Abschnitt gibt es nicht.")
            null -> showError("Befehl nicht erkannt: $text. Beispiele: Vorlesen, Pause, 30 Sekunden zurück, nächstes Kapitel.")
        }
    }
    fun showError(message: String) { mutable.update { it.copy(error = message, status = message, listening = false) } }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    override fun onCleared() {
        // From here on the service continues the book on its own; nothing else would.
        prefs.edit().putBoolean(ReaderPlaybackService.KEY_UI_ALIVE, false).apply()
        work?.cancel(); prefetch?.cancel(); speech.close()
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
