package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.speech.ReaderVoice
import de.schimmilab.accessiblereader.speech.SpeechAudio
import de.schimmilab.accessiblereader.speech.SpeechProvider
import java.io.File
import java.io.RandomAccessFile

/**
 * A speech engine made of silence, for the tests that are about everything except how a voice sounds.
 *
 * It writes real WAV files, so a real ExoPlayer can open them, of a length that can be set per voice — two real
 * voices differ by half again at their natural rate. It also records what was asked of which voice, which is how
 * a test can tell whether the dialogue went to the second voice. A fresh CI emulator has no German voice, so
 * everything these tests cover would otherwise only ever run on one machine.
 */
class SilentVoices(private val folder: File, private val msPerPart: Map<String, Long>, private val fallbackMs: Long = 600) :
    SpeechProvider {
    /** Every piece of text handed over, in order, with the voice it was meant for. */
    val spoken = mutableListOf<Pair<String, String>>()

    override val providerId = "silent"
    override suspend fun voices() = msPerPart.keys.map { ReaderVoice(it, it) }
    override suspend fun synthesize(text: String, voiceId: String): SpeechAudio {
        spoken += voiceId to text
        val ms = msPerPart[voiceId] ?: fallbackMs
        val file = File(folder, "$voiceId-${text.hashCode()}.wav")
        if (!file.exists()) writeSilence(file, ms)
        return SpeechAudio(file, ms)
    }
    override fun close() {}

    fun textsFor(voiceId: String) = spoken.filter { it.first == voiceId }.map { it.second }

    private fun writeSilence(file: File, ms: Long) {
        val rate = 8_000
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
