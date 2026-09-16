package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.slowVoiceMessage
import de.schimmilab.accessiblereader.speech.VoiceTooSlowException
import org.junit.Assert.*
import org.junit.Test

/**
 * The message a listener gets when the voice cannot keep up.
 *
 * The old one said "Bitte eine andere Stimme wählen oder es noch einmal versuchen". The listener it reached
 * answered: "Ich glaube, ich bin blöd." Which other voice? And trying again with the same one is the single
 * thing that cannot help.
 */
class SlowVoiceMessageTest {
    @Test fun itNamesTheVoicesThatHaveKeptUp() {
        val message = slowVoiceMessage(60, listOf("Stimme 2", "Stimme 3"))
        assertTrue(message.contains("zu langsam"))
        assertTrue(message.contains("Stimme 2, Stimme 3"))
        assertTrue("and says where to find them", message.contains("Stimme und Einstellungen"))
    }

    @Test fun oneCandidateIsNamedInTheSingular() {
        assertTrue(slowVoiceMessage(60, listOf("Stimme 2")).contains("Gut mitgekommen ist bisher: Stimme 2."))
    }

    /** At most three, because this is read out and a list of nine voices helps nobody. */
    @Test fun theListStaysShortEnoughToHear() {
        val message = slowVoiceMessage(60, listOf("Anna", "Bernd", "Clara", "Dieter", "Emil"))
        assertTrue(message.contains("Anna, Bernd, Clara."))
        assertFalse(message.contains("Dieter"))
        assertFalse(message.contains("Emil"))
    }

    /** Nothing measured yet: say where to look instead of naming a voice nobody has heard read. */
    @Test fun withoutAnythingMeasuredItSaysWhereToLook() {
        val message = slowVoiceMessage(60, emptyList())
        assertTrue(message.contains("Stimme und Einstellungen"))
        assertFalse("never invent a recommendation", message.contains("Gut mitgekommen"))
    }

    @Test fun itSaysHowLongTheWaitWas() {
        assertTrue(slowVoiceMessage(300, emptyList()).contains("300 Sekunden"))
    }

    /**
     * A timeout is an IllegalStateException like every other failure in the speech layer, which is exactly why
     * it needs a kind of its own: the retry that catches IllegalStateException would otherwise make a listener
     * who is already waiting wait the whole budget a second time. Written down as a runnable fact, because this
     * project has been caught by the same shape of trap twice already.
     */
    @Test fun aVoiceTooSlowIsAnIllegalStateExceptionAndThereforeNeedsItsOwnKind() {
        val failure = VoiceTooSlowException(60_000, "Diese Stimme hat länger als 60 Sekunden gebraucht.")
        assertTrue(failure is IllegalStateException)
        assertEquals(60_000L, failure.budgetMs)
        assertTrue(failure.message!!.contains("60 Sekunden"))
    }
}
