package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.*
import de.schimmilab.accessiblereader.core.SpeakingRole.DIALOGUE
import de.schimmilab.accessiblereader.core.SpeakingRole.NARRATOR
import org.junit.Assert.*
import org.junit.Test

/**
 * Splitting a novel into what the book says and what its people say.
 *
 * The examples are written in the shapes a real novel uses, taken from what Fontane's *Effi Briest* does with
 * its 1880 pairs of guillemets: replies of three words, an inquit in the middle of a sentence, dialogue running
 * across line breaks, and the odd quotation mark that is never closed.
 */
class NarrationTest {
    private fun roles(text: String) = Narration.passages(text).map { it.role }
    private fun texts(text: String) = Narration.passages(text).map { it.text }

    @Test fun theBookAndItsPeopleAreToldApart() {
        val text = "Effi trat ans Fenster. »Und du meinst wirklich, es wird gehen?« fragte sie."
        assertEquals(listOf(NARRATOR, DIALOGUE, NARRATOR), roles(text))
        assertEquals(listOf("Effi trat ans Fenster.", "»Und du meinst wirklich, es wird gehen?«", "fragte sie."),
            texts(text))
    }

    /** The commonest shape of all: the inquit sits inside the sentence, between two halves of one reply. */
    @Test fun anInquitInTheMiddleIsNarration() {
        val text = "»Gewiß«, sagte die Mutter, »warum sollte es nicht gehen?«"
        assertEquals(listOf(DIALOGUE, NARRATOR, DIALOGUE), roles(text))
        assertEquals(", sagte die Mutter,", texts(text)[1])
    }

    @Test fun theOtherGermanQuotationMarksWorkToo() {
        assertEquals(listOf(NARRATOR, DIALOGUE), roles("Sie sagte: „Ich komme gleich.“"))
        assertEquals(listOf(NARRATOR, DIALOGUE), roles("She said: “I am coming.”"))
    }

    /** French books turn the guillemets around. The direction is decided once, by whichever comes first. */
    @Test fun theDirectionOfTheGuillemetsIsDecidedOncePerText() {
        assertEquals(listOf(NARRATOR, DIALOGUE), roles("Elle dit: «Je viens.»"))
        assertEquals(listOf(NARRATOR, DIALOGUE), roles("Sie sagte: »Ich komme.«"))
    }

    /**
     * More than half the dialogue in a real novel runs across a line break, because a page of print wraps where
     * the paper ends. A newline must therefore never end a quotation; only a blank line does.
     */
    @Test fun dialogueSurvivesALineBreak() {
        val text = "»Und du meinst wirklich,\ndass es so gehen wird,\nwie du es dir denkst?« fragte sie."
        assertEquals(listOf(DIALOGUE, NARRATOR), roles(text))
        assertTrue(texts(text)[0].contains("denkst?«"))
    }

    @Test fun aQuotationMarkThatIsNeverClosedEndsAtTheParagraph() {
        val text = "»Ich habe es nie gewollt.\n\nAm nächsten Morgen regnete es."
        assertEquals(listOf(DIALOGUE, NARRATOR), roles(text))
        assertEquals("Am nächsten Morgen regnete es.", texts(text)[1])
    }

    /**
     * The dash between two replies gets handed to the reply before it. Two replies stay two passages though: one
     * voice reads both today, but the boundary between two speakers is the one thing a version that tells them
     * apart would need, and merging throws it away.
     */
    @Test fun aLonePieceOfPunctuationDoesNotBecomeItsOwnPart() {
        val text = "»Ja.« — »Nein.«"
        assertEquals(listOf(DIALOGUE, DIALOGUE), roles(text))
        assertEquals(listOf("»Ja.« —", "»Nein.«"), texts(text))
    }

    /** The parts stay inside one role, and the ramp keeps counting across the whole section. */
    @Test fun thePartsNeverMixTheTwoVoices() {
        val narration = "Ein Satz, der vorgelesen wird. ".repeat(60)   // 1800 characters
        val parts = Narration.partsForPlayback("$narration»Ein kurzer Einwurf.« $narration")
        assertTrue("expected the long narration to be cut up, got ${parts.size} parts", parts.size >= 5)
        assertEquals(1, parts.count { it.role == DIALOGUE })
        assertTrue("the first piece is short so the first sound arrives quickly", parts[0].text.length <= 250)
        assertTrue(parts[1].text.length <= 500)
        for (part in parts) assertTrue(part.text.length <= 1000)
    }

    @Test fun aTextWithoutAnyDialogueIsOneRoleThroughout() {
        val parts = Narration.partsForPlayback("Ein Satz ohne jede Rede. ".repeat(100))
        assertTrue(parts.isNotEmpty())
        assertTrue(parts.all { it.role == NARRATOR })
    }

    /** The cast is what identifies a prepared section, and with one voice it has to look exactly as it always did. */
    @Test fun oneVoiceIsWrittenDownExactlyAsBefore() {
        assertEquals("de-DE-language", VoiceCast("de-DE-language").key)
        assertEquals("de-DE-language", VoiceCast("de-DE-language", "de-DE-language").key)
        assertEquals("de-DE-language", VoiceCast("de-DE-language", "").key)
        assertFalse(VoiceCast("de-DE-language").twoVoices)
    }

    @Test fun twoVoicesSurviveBeingWrittenDownAndReadBack() {
        val cast = VoiceCast("de-DE-language", "de_DE-thorsten-high.onnx")
        assertEquals(cast, VoiceCast.parse(cast.key))
        assertEquals("de-DE-language", cast.voiceFor(NARRATOR))
        assertEquals("de_DE-thorsten-high.onnx", cast.voiceFor(DIALOGUE))
        assertTrue(cast.twoVoices)
    }

    @Test fun withoutASecondVoiceEveryPassageGoesToTheNarrator() {
        val cast = VoiceCast("de-DE-language")
        assertEquals("de-DE-language", cast.voiceFor(DIALOGUE))
    }
}
