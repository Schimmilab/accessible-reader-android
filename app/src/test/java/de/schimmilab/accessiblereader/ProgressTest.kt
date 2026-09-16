package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.bookProgressLabel
import de.schimmilab.accessiblereader.core.remainingTimeLabel
import org.junit.Assert.*
import org.junit.Test

/** Saying how far into a book someone is, to someone who has no scroll bar and no thumb in the pages. */
class ProgressTest {
    @Test fun theBeginningAndTheEndAreNamedRatherThanCounted() {
        assertEquals("Du bist ganz am Anfang des Buches.", bookProgressLabel(0f))
        assertEquals("Du bist ganz am Anfang des Buches.", bookProgressLabel(0.02f))
        assertEquals("Du bist fast am Ende des Buches.", bookProgressLabel(0.99f))
        assertEquals("Du bist fast am Ende des Buches.", bookProgressLabel(1f))
    }

    /** A fraction is easier to picture than a number, where one is close enough to be honest. */
    @Test fun theUsualFractionsAreSaidAsFractions() {
        assertEquals("Du hast etwa ein Viertel des Buches gehört.", bookProgressLabel(0.25f))
        assertEquals("Du hast etwa ein Drittel des Buches gehört.", bookProgressLabel(0.33f))
        assertEquals("Du hast etwa die Hälfte des Buches gehört.", bookProgressLabel(0.5f))
        assertEquals("Du hast etwa zwei Drittel des Buches gehört.", bookProgressLabel(0.66f))
        assertEquals("Du hast etwa drei Viertel des Buches gehört.", bookProgressLabel(0.75f))
    }

    /** Everything else is rounded to five, because a percentage to the digit sounds like knowledge nobody has. */
    @Test fun everythingElseIsRoundedHard() {
        assertEquals("Du hast etwa 10 Prozent des Buches gehört.", bookProgressLabel(0.12f))
        assertEquals("Du hast etwa 40 Prozent des Buches gehört.", bookProgressLabel(0.43f))
        assertEquals("Du hast etwa 85 Prozent des Buches gehört.", bookProgressLabel(0.88f))
    }

    @Test fun aFractionOutsideTheBookIsStillSaidSensibly() {
        assertEquals("Du bist ganz am Anfang des Buches.", bookProgressLabel(-1f))
        assertEquals("Du bist fast am Ende des Buches.", bookProgressLabel(2f))
    }

    /** The remaining time is what someone decides an evening by, so it is worth estimating — but only honestly. */
    @Test fun theRemainingTimeIsAnEstimateAndSaysSo() {
        fun charactersFor(minutes: Int, wpm: Int) = (minutes * 6.5 * wpm).toInt()
        assertEquals("Noch etwa 15 Minuten.", remainingTimeLabel(charactersFor(15, 130), 130))
        assertEquals("Noch etwa eine Stunde.", remainingTimeLabel(charactersFor(60, 130), 130))
        assertEquals("Noch etwa eine Stunde und 35 Minuten.", remainingTimeLabel(charactersFor(95, 130), 130))
        assertEquals("Noch etwa 3 Stunden und 20 Minuten.", remainingTimeLabel(charactersFor(200, 130), 130))
        assertEquals("the minutes are rounded to five, because the estimate is not worth a single one",
            "Noch etwa 2 Stunden und 5 Minuten.", remainingTimeLabel(charactersFor(127, 130), 130))
    }

    /** Without a measured voice there is no honest estimate, so nothing is said at all. */
    @Test fun nothingIsSaidAboutAVoiceNobodyHasMeasured() {
        assertNull(remainingTimeLabel(500_000, 0))
        assertNull(remainingTimeLabel(0, 130))
        assertNull("under a minute is not worth a sentence", remainingTimeLabel(100, 130))
    }
}
