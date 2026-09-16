package de.schimmilab.accessiblereader

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * What "Wo bin ich?" says. Needs no speech engine: the sentence is built from the document, not from audio.
 *
 * A listener has no scroll bar to feel and no thumb in the pages, so the only way to know how much book is left
 * is to be told. It used to say only where in the section someone was.
 */
class PositionAnnouncementTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    @Test fun thePositionSaysWhereInTheSectionAndWhereInTheBook() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.runOnIdle { model.demo(); model.chapter(0) }
        val atStart = model.positionText()
        assertTrue("the section is still named: $atStart", atStart.contains("Abschnitt 1 von 3"))
        assertTrue("and where in the book: $atStart", atStart.contains("Du bist ganz am Anfang des Buches."))

        compose.runOnIdle { model.chapter(1) }
        val middle = model.positionText()
        assertTrue("the middle of a three-section book is not its beginning: $middle",
            !middle.contains("Anfang des Buches"))
        assertTrue("it is said in words someone can picture: $middle",
            middle.contains("Prozent des Buches") || middle.contains("Drittel") || middle.contains("Hälfte") ||
                middle.contains("Viertel"))

        compose.runOnIdle { model.chapter(2) }
        val last = model.positionText()
        listOf(atStart, middle, last).forEach { Log.i("ReaderPosition", it) }
        assertTrue("the last section is further on than the middle one: $last", last != middle)
    }
}
