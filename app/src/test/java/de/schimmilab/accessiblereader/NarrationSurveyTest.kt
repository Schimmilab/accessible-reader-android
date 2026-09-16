package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.Narration
import de.schimmilab.accessiblereader.core.SpeakingRole
import de.schimmilab.accessiblereader.core.TextChunks
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * A harness, not a fixed test: it runs the split over a whole real novel and reports what it makes of it.
 * Invented examples say nothing about a book with two thousand pairs of quotation marks in it.
 *
 * Point it at a plain text novel and it reports; without one it skips, so it costs nothing in CI:
 *
 *     READER_NOVEL=/path/to/novel.txt ./gradlew :app:testDebugUnitTest --tests "*NarrationSurveyTest"
 *
 * The numbers quoted throughout this project come from Fontane's *Effi Briest*, Project Gutenberg, which is out
 * of copyright and uses »…« like most German novels.
 */
class NarrationSurveyTest {
    @Test fun aWholeNovelIsSplitAndReported() {
        val path = System.getenv("READER_NOVEL")
        assumeTrue("Set READER_NOVEL to a plain text novel to run this", path != null && File(path).isFile)
        val raw = File(path!!).readText()
        val text = TextChunks.cleanBlock(raw)

        val passages = Narration.passages(text)
        val dialogue = passages.filter { it.role == SpeakingRole.DIALOGUE }
        val narration = passages.filter { it.role == SpeakingRole.NARRATOR }
        val spoken = dialogue.sumOf { it.text.length }

        fun median(values: List<Int>) = values.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }

        println("Novel: ${text.length} characters")
        println("  passages ${passages.size}: ${dialogue.size} dialogue, ${narration.size} narration")
        println("  direct speech: ${"%.1f".format(spoken * 100.0 / text.length)} % of the characters")
        println("  median dialogue ${median(dialogue.map { it.text.length })}, " +
            "median narration ${median(narration.map { it.text.length })}")
        println("  passages per 10,000 characters: " +
            "${"%.1f".format(passages.size * 10_000.0 / text.length)}")
        val section = text.take(10_000)
        println("  a 10,000 character section becomes ${Narration.partsForPlayback(section).size} parts " +
            "instead of ${TextChunks.splitForPlayback(section).size}")
        println("  unterminated quotations: ${dialogue.count { it.text.last() !in "«»“”\"" }}")

        // Nothing may be lost or invented: the passages put back together are the text again.
        assertEquals("every character of the book has to survive the split",
            text.filter { !it.isWhitespace() }, passages.joinToString("") { it.text }.filter { !it.isWhitespace() })
    }
}
