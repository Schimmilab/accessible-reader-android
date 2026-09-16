package de.schimmilab.accessiblereader

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * What one more part costs in playback.
 *
 * Reading a novel with two voices, one for the narration and one for what the characters say, means cutting a
 * section at every quotation mark. Measured on a real novel, that is 83 parts per section instead of eleven, and
 * every part is its own audio file. If the player falls silent between files, a conversation would stutter.
 *
 * Silent WAV files of a known length, so the only thing measured is the player.
 */
@RunWith(AndroidJUnit4::class)
class PartTransitionCostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var folder: File

    @Before fun setUp() { folder = File(context.cacheDir, "gap-test").apply { deleteRecursively(); mkdirs() } }
    @After fun tearDown() { folder.deleteRecursively() }

    @Test fun manyShortPartsPlayWithoutFallingSilentBetweenThem() = runBlocking {
        val parts = 20
        val partMs = 500L
        val items = (0 until parts).map { index ->
            val file = File(folder, "part-$index.wav")
            writeSilence(file, partMs)
            MediaItem.fromUri(Uri.fromFile(file))
        }
        val player = withContext(Dispatchers.Main) { ExoPlayer.Builder(context).build() }
        try {
            val elapsed = withContext(Dispatchers.Main) {
                player.volume = 0f
                player.setMediaItems(items)
                player.prepare()
                player.playWhenReady = true
                val began = System.currentTimeMillis()
                while (player.playbackState != androidx.media3.common.Player.STATE_ENDED &&
                    System.currentTimeMillis() - began < 60_000) {
                    delay(20)
                }
                System.currentTimeMillis() - began
            }
            val audio = parts * partMs
            val gap = elapsed - audio
            Log.i("ReaderGap", "$parts Teile à ${partMs}ms: ${elapsed}ms gespielt, ${gap}ms mehr als Audio, " +
                "${gap / (parts - 1)}ms je Übergang")
            assertTrue("The player needed ${elapsed}ms for ${audio}ms of audio, which is ${gap}ms of silence " +
                "across ${parts - 1} transitions. A conversation cut at every quotation mark would stutter.",
                gap < (parts - 1) * 50)
        } finally { withContext(Dispatchers.Main) { player.release() } }
    }

    private fun writeSilence(file: File, ms: Long) {
        val rate = 22_050
        val data = (rate * ms / 1000).toInt() * 2
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            out.write("RIFF".toByteArray()); out.writeIntLE(36 + data); out.write("WAVEfmt ".toByteArray())
            out.writeIntLE(16); out.writeShortLE(1); out.writeShortLE(1)
            out.writeIntLE(rate); out.writeIntLE(rate * 2); out.writeShortLE(2); out.writeShortLE(16)
            out.write("data".toByteArray()); out.writeIntLE(data)
            out.write(ByteArray(data))
        }
    }
    private fun RandomAccessFile.writeIntLE(value: Int) =
        write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
    private fun RandomAccessFile.writeShortLE(value: Int) =
        write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
}
