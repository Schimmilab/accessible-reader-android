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
import de.schimmilab.accessiblereader.MainActivity
import de.schimmilab.accessiblereader.core.AudioTimeline

@androidx.annotation.OptIn(UnstableApi::class)
class ReaderPlaybackService : MediaSessionService() {
    companion object {
        /** Sent to connected controllers when a media key or notification button asks for a chapter change. */
        const val COMMAND_NEXT_CHAPTER = "de.schimmilab.accessiblereader.NEXT_CHAPTER"
        const val COMMAND_PREVIOUS_CHAPTER = "de.schimmilab.accessiblereader.PREVIOUS_CHAPTER"
        /** Set while a chapter is still being synthesized, so the service can tell an empty buffer from a real end. */
        const val KEY_PREPARING = "preparing"
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
