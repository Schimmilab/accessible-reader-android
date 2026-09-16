package de.schimmilab.accessiblereader.core

/** Who is speaking a passage: the book itself, or one of its characters. */
enum class SpeakingRole { NARRATOR, DIALOGUE }

/** One stretch of text spoken by one [role]. */
data class Passage(val role: SpeakingRole, val text: String)

/**
 * The voices a section is read with: one for the narration, optionally a second one for what the characters say.
 *
 * The cast, not a single voice, is what identifies a prepared section. It is written into every media item and
 * into the saved position, so a section prepared with two voices is not mistaken for the same section prepared
 * with one. With no second voice the key is exactly the voice id, which is what every earlier version wrote, so
 * nothing already saved changes meaning.
 */
data class VoiceCast(val narrator: String, val dialogue: String = "") {
    val twoVoices: Boolean get() = dialogue.isNotBlank() && dialogue != narrator
    val key: String get() = if (twoVoices) "$narrator$SEPARATOR$dialogue" else narrator
    fun voiceFor(role: SpeakingRole): String =
        if (role == SpeakingRole.DIALOGUE && twoVoices) dialogue else narrator

    companion object {
        private const val SEPARATOR = '|'
        fun parse(key: String): VoiceCast {
            val at = key.indexOf(SEPARATOR)
            return if (at < 0) VoiceCast(key) else VoiceCast(key.take(at), key.substring(at + 1))
        }
    }
}

/**
 * Splits a text into what the narrator says and what the characters say, so a novel can be read with two voices.
 *
 * Nothing is guessed about *who* speaks. Measured on a real novel, Fontane's *Effi Briest*: four out of five
 * lines of dialogue are not followed by any "sagte er" or "sagte sie" at all, so a male and a female voice would
 * be assigned wrongly most of the time, and a wrong voice is worse than one voice. What the text does say
 * unmistakably is *that* someone is speaking: the quotation marks. That is what this uses.
 *
 * What the same novel says about the shape of the job, measured by `NarrationSurveyTest` over all 609,000 of its
 * characters:
 * - 51 % of them are direct speech, so a second voice carries half the book
 * - 45 passages per ten thousand characters, so a section becomes about 38 parts where it holds twelve today
 * - the median line of dialogue is 73 characters, the median piece of narration 126
 * - more than half of its dialogue runs across a line break, which is why only a blank line ends an unterminated
 *   quotation, never a newline; 23 of its 1890 quotations are never closed at all
 */
object Narration {
    /** A quotation left open this long is a typesetting accident, not a monologue. */
    const val LONGEST_DIALOGUE = 2000

    private const val GERMAN_CLOSERS = "“”\""

    /**
     * German books use »…«, French ones «…», and the two are the same two characters in the opposite order. The
     * direction is decided once per text by whichever comes first, instead of per character, where a single
     * stray guillemet would turn the rest of the book inside out.
     */
    private fun guillemetsAreFrench(text: String): Boolean {
        val german = text.indexOf('»')
        val french = text.indexOf('«')
        if (french < 0) return false
        return german < 0 || french < german
    }

    private fun closersFor(char: Char, french: Boolean): String? = when (char) {
        '„' -> GERMAN_CLOSERS            // „ opens, and the closer is set in a different height
        '»' -> if (french) null else "«"
        '«' -> if (french) "»" else null
        '“' -> "”"                  // an English opening quote, when no „ is waiting to be closed
        '"' -> "\""
        else -> null
    }

    fun passages(text: String): List<Passage> {
        val french = guillemetsAreFrench(text)
        val out = mutableListOf<Passage>()
        var start = 0
        var index = 0
        while (index < text.length) {
            val closers = closersFor(text[index], french)
            if (closers == null) { index++; continue }
            var end = index + 1
            while (end < text.length && text[end] !in closers &&
                !text.startsWith("\n\n", end) && end - index <= LONGEST_DIALOGUE) end++
            if (end < text.length && text[end] in closers) end++
            if (index > start) out += Passage(SpeakingRole.NARRATOR, text.substring(start, index))
            out += Passage(SpeakingRole.DIALOGUE, text.substring(index, end))
            start = end
            index = end
        }
        if (start < text.length) out += Passage(SpeakingRole.NARRATOR, text.substring(start))
        return tidy(out)
    }

    /**
     * The same passages, cut to the sizes playback needs. The ramp counts across the whole section, so the first
     * pieces stay short and the first sound arrives quickly however the section begins.
     */
    fun partsForPlayback(text: String): List<Passage> {
        val out = mutableListOf<Passage>()
        for (passage in passages(text)) {
            for (piece in TextChunks.splitForPlayback(passage.text, from = out.size)) {
                out += Passage(passage.role, piece)
            }
        }
        return out
    }

    /**
     * Trims, drops what is empty, and hands a passage without a single letter or digit to its neighbour: a lone
     * comma between two lines of dialogue is not worth a file of its own, and a voice would not say it anyway.
     */
    private fun tidy(passages: List<Passage>): List<Passage> {
        val out = mutableListOf<Passage>()
        for (passage in passages) {
            val text = passage.text.trim()
            if (text.isEmpty()) continue
            val silent = text.none { it.isLetterOrDigit() }
            val last = out.lastOrNull()
            // Only what nobody would hear is merged. Two replies that follow each other directly stay two
            // passages even though one voice reads both, because the boundary between two speakers is the one
            // thing a later version would need to give them different voices.
            if (last != null && silent) {
                out[out.lastIndex] = last.copy(text = "${last.text} $text")
            } else {
                out += Passage(passage.role, text)
            }
        }
        return out
    }
}
