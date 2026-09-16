package de.schimmilab.accessiblereader.playback

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import de.schimmilab.accessiblereader.core.ChapterAnnouncement
import de.schimmilab.accessiblereader.core.ReaderDocument
import de.schimmilab.accessiblereader.core.SavedPosition
import de.schimmilab.accessiblereader.core.TextChunks
import de.schimmilab.accessiblereader.core.resumePoint
import de.schimmilab.accessiblereader.speech.SpeechProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** What a caller wants to know while a section is being turned into audio. Every callback runs on the main thread. */
interface SectionProgress {
    /** About to synthesize part [index] of [total]. Playback has not started yet. */
    fun onPreparing(index: Int, total: Int) {}
    /** Enough audio exists and playback has begun. [pending] parts are still to come. */
    fun onStarted(key: String, pending: Int) {}
    /** A further part was appended while the listener was already hearing the section. */
    fun onAppended() {}
    /**
     * One piece of text was turned into audio. [fromCache] means it cost no time and says nothing about how fast
     * the engine works; [characters] is what the voice actually spoke, which gives its speaking rate.
     */
    fun onSynthesized(workMs: Long, audioMs: Long, characters: Int, fromCache: Boolean) {}
}

/**
 * Turns one section of a book into audio and hands it to the player, starting playback as soon as enough is
 * buffered and appending the rest while it plays.
 *
 * This lives apart from the ViewModel because the playback service needs exactly the same behaviour when the app
 * is gone: a listener who swipes the app away should keep hearing the book, and the section after the current one
 * has to be prepared by someone. Two copies of this loop would be two sets of the subtleties it contains, and
 * those have cost this project several releases already.
 *
 * Must be called on the main thread; Media3 requires it.
 */
class SectionPreparer(
    private val speech: SpeechProvider,
    private val prefs: SharedPreferences,
    private val minLeadMs: Long,
    private val cacheBudgetBytes: Long,
) {
    companion object {
        /** How much audio has to lie ahead before playback starts. The ~3 s intro alone drains into silence. */
        const val MIN_LEAD_MS = 12_000L
        /** Generated audio is trimmed down to fit this, never refused once it is full. */
        const val CACHE_BUDGET_BYTES = 500L * 1024 * 1024
    }

    suspend fun prepare(
        player: Player,
        document: ReaderDocument,
        chapterIndex: Int,
        voiceId: String,
        speed: Float,
        listener: SectionProgress,
    ) {
        val chapter = document.chapters[chapterIndex]
        val key = "${document.id}:$chapterIndex:$voiceId"
        player.stop(); player.clearMediaItems()
        // Makes room instead of refusing: a long book outgrows any budget, and stopping mid-book is worse than
        // synthesizing an old section again should the listener return to it.
        withContext(Dispatchers.IO) { speech.trimCache(cacheBudgetBytes) }
        // Part 0 is always the spoken section intro, so saved item indexes stay stable.
        val texts = listOf(ChapterAnnouncement.text(chapterIndex, document.chapters.size, chapter.title)) +
            TextChunks.splitForPlayback(chapter.text)
        val start = resumePoint(
            SavedPosition(
                chapter = prefs.getInt("${document.id}.chapter", 0),
                item = prefs.getInt("${document.id}.item", 0),
                offsetMs = prefs.getLong("${document.id}.offset", 0),
                voiceId = prefs.getString("${document.id}.voice", "").orEmpty(),
                finished = prefs.getBoolean("${document.id}.finished", false)),
            chapterIndex, voiceId, texts.size)
        val startItem = start.item

        val items = mutableListOf<MediaItem>()
        var leadMs = 0L          // audio buffered from the start position; playback waits until it clears minLeadMs
        var startOffset = 0L
        var started = false
        texts.forEachIndexed { i, text ->
            if (!started) listener.onPreparing(i, texts.size)
            val began = System.currentTimeMillis()
            val part = speech.synthesize(text, voiceId)
            coroutineContext.ensureActive()
            listener.onSynthesized(System.currentTimeMillis() - began, part.durationMs, text.length, part.fromCache)
            val extra = Bundle().apply {
                putString("document", document.id); putInt("chapter", chapterIndex); putString("voice", voiceId)
                putLong("duration", part.durationMs)
            }
            val item = MediaItem.Builder().setMediaId("$key:$i").setUri(Uri.fromFile(part.file))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(chapter.title).setArtist(document.title)
                    .setExtras(extra).build()).build()
            if (started) {
                player.addMediaItem(item)
                // The player ran out of parts before this one arrived; continue unless the listener paused.
                if (player.playWhenReady && player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(player.mediaItemCount - 1, 0); player.prepare(); player.play()
                }
                listener.onAppended()
            } else {
                items += item
                if (i == startItem) startOffset = start.offsetMs.coerceIn(0, part.durationMs)
                if (i >= startItem) leadMs += part.durationMs
                // Start only once enough audio lies ahead, so the short intro cannot drain before the first text
                // part is ready.
                if (i >= startItem && (leadMs - startOffset >= minLeadMs || i == texts.lastIndex)) {
                    player.setMediaItems(items, startItem, startOffset)
                    player.setPlaybackSpeed(speed); player.prepare(); player.play()
                    started = true
                    listener.onStarted(key, texts.size - items.size)
                }
            }
        }
    }
}
