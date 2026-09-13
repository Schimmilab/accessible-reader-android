package de.schimmilab.accessiblereader.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.content.SharedPreferences
import de.schimmilab.accessiblereader.MainActivity
import de.schimmilab.accessiblereader.core.AudioTimeline
import de.schimmilab.accessiblereader.data.DocumentStore
import de.schimmilab.accessiblereader.speech.AndroidSpeechProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
class ReaderPlaybackService : MediaSessionService() {
    companion object {
        /** Sent to connected controllers when a media key or notification button asks for a chapter change. */
        const val COMMAND_NEXT_CHAPTER = "de.schimmilab.accessiblereader.NEXT_CHAPTER"
        const val COMMAND_PREVIOUS_CHAPTER = "de.schimmilab.accessiblereader.PREVIOUS_CHAPTER"
        /** Set while a chapter is still being synthesized, so the service can tell an empty buffer from a real end. */
        const val KEY_PREPARING = "preparing"

        /**
         * Set by the ViewModel while the app itself is alive. When it is gone, the service prepares the next
         * section on its own, because nothing else will: playback of the current section already survives the
         * activity, but the section after it used to be nobody's job once the ViewModel had died with the
         * activity, and the book stopped wherever the listener happened to be.
         */
        const val KEY_UI_ALIVE = "uiAlive"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var continuation: Job? = null

    /**
     * Continues the book when the app is gone. Everything needed is already in the current media item: the
     * document id, the section and the voice. The text comes from the same store the app reads, so nothing has
     * to be handed across.
     */
    private fun continueWithoutTheApp(player: Player, preferences: SharedPreferences) {
        if (continuation?.isActive == true) return
        if (player.playbackState != Player.STATE_ENDED || !player.playWhenReady) return
        if (preferences.getBoolean(KEY_PREPARING, false)) return
        if (preferences.getBoolean(KEY_UI_ALIVE, false)) return
        val extra = player.currentMediaItem?.mediaMetadata?.extras ?: return
        val documentId = extra.getString("document") ?: return
        val next = extra.getInt("chapter") + 1
        val voice = extra.getString("voice").orEmpty()
        if (voice.isBlank()) return
        continuation = scope.launch {
            preferences.edit().putBoolean(KEY_PREPARING, true).apply()
            val speech = AndroidSpeechProvider(applicationContext, preferences.getString("engine", "").orEmpty())
            try {
                val document = withContext(Dispatchers.IO) { DocumentStore(this@ReaderPlaybackService).load(documentId) }
                    ?: return@launch
                if (next !in document.chapters.indices || document.chapters[next].text.isBlank()) return@launch
                SectionPreparer(speech, preferences, SectionPreparer.MIN_LEAD_MS, SectionPreparer.CACHE_BUDGET_BYTES)
                    .prepare(player, document, next, voice, player.playbackParameters.speed, object : SectionProgress {})
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("ReaderService", "Der naechste Abschnitt liess sich nicht vorbereiten", e)
            } finally {
                speech.close()
                preferences.edit().putBoolean(KEY_PREPARING, false).apply()
            }
        }
    }
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this).setSeekBackIncrementMs(30_000).setSeekForwardIncrementMs(30_000).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        val player = ChapterPlayer(exo) { command -> session?.broadcastCustomCommand(SessionCommand(command, Bundle.EMPTY), Bundle.EMPTY) }
        // Progress lives with playback, including while the activity is gone.
        val preferences = getSharedPreferences("reader", MODE_PRIVATE)
        val handler = android.os.Handler(mainLooper)
        val save = object : Runnable {
            override fun run() {
                player.currentMediaItem?.mediaMetadata?.extras?.let { extra ->
                    val document = extra.getString("document") ?: return@let
                    preferences.edit().putInt("$document.chapter", extra.getInt("chapter"))
                        .putInt("$document.item", player.currentMediaItemIndex)
                        .putLong("$document.offset", player.currentPosition)
                        .putString("$document.voice", extra.getString("voice"))
                        // Running out of not-yet-prepared parts also reports ENDED, and a chapter wrongly marked
                        // finished restarts from the beginning instead of resuming where the listener stopped.
                        .putBoolean("$document.finished",
                            player.playbackState == Player.STATE_ENDED && !preferences.getBoolean(KEY_PREPARING, false))
                        .apply()
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(save)
        progressHandler = handler
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = continueWithoutTheApp(player, preferences)
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
                continueWithoutTheApp(player, preferences)
        })
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val buttons = listOf(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_30).setPlayerCommand(Player.COMMAND_SEEK_BACK).setDisplayName("30 Sekunden zurück")
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW).build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30).setPlayerCommand(Player.COMMAND_SEEK_FORWARD).setDisplayName("30 Sekunden vor")
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW).build())
        session = MediaSession.Builder(this, player).setSessionActivity(activity).setMediaButtonPreferences(buttons).build()
    }
    private var progressHandler: android.os.Handler? = null
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.playWhenReady != true) stopSelf()
    }
    override fun onDestroy() {
        continuation?.cancel()
        scope.cancel()
        progressHandler?.removeCallbacksAndMessages(null)
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/**
 * Media keys, Bluetooth headsets and the notification talk to this player. A playlist is one chapter made of
 * audio parts, so "next/previous" means chapter, not part, and 30-second jumps run on the chapter timeline.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class ChapterPlayer(private val exo: Player, private val chapterCommand: (String) -> Unit) : ForwardingSimpleBasePlayer(exo) {
    private fun durations() = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaMetadata.extras?.getLong("duration") ?: 0L }
    private fun positionMs(durations: List<Long>) = AudioTimeline.absolute(durations, exo.currentMediaItemIndex, exo.currentPosition)

    // Keep next/previous/jump buttons available on every part. Repeat ALL is only advertised, never applied to
    // the wrapped player: it makes SimpleBasePlayer route "next" on the last part into handleSeek instead of ignoring it.
    override fun getState(): State {
        val state = super.getState()
        if (exo.mediaItemCount == 0) return state
        return state.buildUpon().setRepeatMode(Player.REPEAT_MODE_ALL)
            .setAvailableCommands(state.availableCommands.buildUpon().addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD).build())
            .build()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_BACK -> seekBy(-exo.seekBackIncrement)
            Player.COMMAND_SEEK_FORWARD -> seekBy(exo.seekForwardIncrement)
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> chapterCommand(ReaderPlaybackService.COMMAND_NEXT_CHAPTER)
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                // Same rule as Media3's own previous: far into the chapter it restarts it, near the start it goes back one.
                if (positionMs(durations()) > exo.maxSeekToPreviousPosition) exo.seekTo(0, 0)
                else chapterCommand(ReaderPlaybackService.COMMAND_PREVIOUS_CHAPTER)
            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        return Futures.immediateVoidFuture()
    }

    private fun seekBy(deltaMs: Long) {
        val durations = durations()
        val target = AudioTimeline.locate(durations, positionMs(durations) + deltaMs)
        exo.seekTo(target.item, target.offsetMs)
    }
}
