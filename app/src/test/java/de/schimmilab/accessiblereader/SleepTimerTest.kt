package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.*
import org.junit.Assert.*
import org.junit.Test

/** Falling asleep over a book without the reader either stopping mid-sentence or reading all night. */
class SleepTimerTest {
    @Test fun everyChoiceCanBeSaidOutLoud() {
        assertEquals("Aus", sleepLabel(SleepOption.OFF))
        assertEquals("Nach 30 Minuten", sleepLabel(SleepOption.AFTER_30))
        assertEquals("Am Ende des Abschnitts", sleepLabel(SleepOption.END_OF_SECTION))
    }

    @Test fun settingItSaysWhatWillHappen() {
        assertEquals("Einschlaftimer: 45 Minuten. Danach hört der Reader auf.",
            sleepAnnouncement(SleepOption.AFTER_45))
        assertEquals("Einschlaftimer: Der Reader hört am Ende dieses Abschnitts auf.",
            sleepAnnouncement(SleepOption.END_OF_SECTION))
        assertEquals("Einschlaftimer aus. Der Reader liest weiter.", sleepAnnouncement(SleepOption.OFF))
    }

    /** The remaining time has to be hearable without opening anything, so the button carries it. */
    @Test fun theButtonCarriesTheRemainingTime() {
        assertEquals("Einschlaftimer", sleepButtonLabel(SleepOption.OFF, 0))
        assertEquals("Einschlaftimer, noch 24 Minuten", sleepButtonLabel(SleepOption.AFTER_30, 23 * 60_000L + 30_000))
        assertEquals("the last minute is counted in seconds, or it would say zero minutes",
            "Einschlaftimer, noch 40 Sekunden", sleepButtonLabel(SleepOption.AFTER_30, 40_000))
        assertEquals("Einschlaftimer, Ende des Abschnitts", sleepButtonLabel(SleepOption.END_OF_SECTION, 0))
    }

    /**
     * The sound is faded rather than cut. Silence arriving mid-sentence wakes the person the timer was set for,
     * which is the one thing it must not do.
     */
    @Test fun theSoundIsFadedBeforeItStops() {
        assertEquals(1f, sleepVolume(10 * 60_000L), 0.001f)
        assertEquals(1f, sleepVolume(SLEEP_FADE_MS), 0.001f)
        assertEquals(0.5f, sleepVolume(SLEEP_FADE_MS / 2), 0.01f)
        assertEquals(0f, sleepVolume(0), 0.001f)
        assertEquals("never negative, whatever the clock does", 0f, sleepVolume(-5_000), 0.001f)
    }

    @Test fun aTimedChoiceKnowsItIsTimed() {
        assertTrue(SleepOption.AFTER_15.isTimed)
        assertFalse(SleepOption.OFF.isTimed)
        assertFalse("the end of a section is not a number of minutes", SleepOption.END_OF_SECTION.isTimed)
    }

    @Test fun theTimerIsReachableByVoice() {
        assertEquals(ReaderCommand.Sleep, CommandParser.parse("Einschlaftimer"))
        assertEquals(ReaderCommand.Sleep, CommandParser.parse("Timer"))
        assertEquals(ReaderCommand.Sleep, CommandParser.parse("einschlafen"))
    }
}
