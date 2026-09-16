package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.*
import org.junit.Assert.*
import org.junit.Test

/** Finding a passage in a book you cannot leaf through. */
class SearchTest {
    private val book = ReaderDocument("such-test", "Ein Buch", listOf(
        Chapter("Erstes Kapitel", 1, 4, "Am Morgen fuhr der Bus über die Brücke. " +
            "Später stand sie am Fenster und sah dem Regen zu. " + "Ein Satz zum Auffüllen. ".repeat(60)),
        Chapter("Zweites Kapitel", 5, 9, "Ein Satz zum Auffüllen. ".repeat(60) +
            "Die Brücke war gesperrt, also nahmen sie den Umweg über den Markt."),
        Chapter("Drittes Kapitel", 10, 12, "Hier steht nichts von Bedeutung. ".repeat(20))))

    @Test fun aWordIsFoundWithTheSectionItStandsIn() {
        val hits = search(book, "Brücke")
        assertEquals(2, hits.size)
        assertEquals(listOf(0, 1), hits.map { it.chapter })
        assertEquals(listOf("Erstes Kapitel", "Zweites Kapitel"), hits.map { it.chapterTitle })
    }

    /**
     * Umlauts are written both ways in the real world, and speech recognition produces one or the other. A
     * search for "Bruecke" that finds nothing in a book full of the word would look broken.
     */
    @Test fun umlautsAreFoundHoweverTheyAreSpelled() {
        assertEquals(2, search(book, "Bruecke").size)
        assertEquals(2, search(book, "BRUECKE").size)
        assertEquals(2, search(book, "brücke").size)
    }

    /** Punctuation between the words must not matter: nobody dictates a comma. */
    @Test fun severalWordsAreFoundAcrossPunctuation() {
        assertEquals(1, search(book, "Bruecke war gesperrt").size)
        assertEquals("a line break between the words is still the same sentence",
            1, search(book, "Regen zu Ein Satz").size)
    }

    /** A hit has to be reachable: the part it names is the part playback starts at, heading counted as part 0. */
    @Test fun aHitNamesThePartItCanBeHeardIn() {
        val early = search(book, "Morgen").single()
        assertEquals("the first words of a section are in its first part of text", 1, early.item)
        val late = search(book, "Umweg").single()
        assertTrue("a hit at the end of a long section is in a later part, was ${late.item}", late.item > 1)
    }

    @Test fun theContextAroundAHitIsReadableAloud() {
        val hit = search(book, "gesperrt").single()
        assertTrue(hit.context.contains("gesperrt"))
        assertTrue("short enough for a list", hit.context.length <= CONTEXT_CHARACTERS + 12)
        assertFalse("no line breaks in something that gets read out", hit.context.contains("\n"))
    }

    @Test fun oneSectionCannotCrowdOutTheOthers() {
        val hits = search(book, "Auffüllen")
        assertTrue("at most three per section", hits.count { it.chapter == 0 } <= MOST_HITS_PER_SECTION)
        assertTrue("and both sections that hold it are named", hits.map { it.chapter }.distinct().size >= 2)
    }

    @Test fun nothingFoundIsSaidOutLoudToo() {
        assertEquals(emptyList<Hit>(), search(book, "Hubschrauber"))
        assertEquals("Keine Fundstelle für Hubschrauber.",
            searchResultAnnouncement("Hubschrauber", search(book, "Hubschrauber")))
        assertEquals("Eine Fundstelle für Umweg: Zweites Kapitel.",
            searchResultAnnouncement("Umweg", search(book, "Umweg")))
        assertEquals("2 Fundstellen für Brücke.", searchResultAnnouncement("Brücke", search(book, "Brücke")))
    }

    @Test fun anEmptySearchFindsNothingRatherThanEverything() {
        assertEquals(emptyList<Hit>(), search(book, "   "))
        assertEquals(emptyList<Hit>(), search(book, ",.;"))
    }

    /** With a second voice the section is cut differently, so a hit has to name the part of that cut. */
    @Test fun thePartOfAHitFollowsHowTheSectionIsCut() {
        val novel = ReaderDocument("roman", "Ein Roman", listOf(Chapter("Erstes Kapitel", 1, 2,
            "Ein Satz zum Auffüllen. ".repeat(40) + "»Und du meinst wirklich, es wird gehen?« fragte sie.")))
        val plain = search(novel, "wirklich", twoVoices = false).single()
        val voiced = search(novel, "wirklich", twoVoices = true).single()
        assertTrue(plain.item >= 1 && voiced.item >= 1)
        assertNotEquals("a novel cut at its quotation marks has more and shorter parts",
            plain.item, voiced.item)
    }

    /**
     * The words are handed on exactly as they were said. Every other command is folded down to compare it
     * against a fixed word, and that folding turns "Brücke" into "brucke", which is in no book.
     */
    @Test fun searchingIsReachableByVoiceAndKeepsTheWords() {
        assertEquals(ReaderCommand.Search("Brücke"), CommandParser.parse("Suche nach Brücke"))
        assertEquals(ReaderCommand.Search("der alte Garten"), CommandParser.parse("finde der alte Garten"))
        assertNull("a search for nothing is not a command", CommandParser.parse("suche nach"))
        assertEquals("and the other commands still work", ReaderCommand.Pause, CommandParser.parse("Pause"))
    }
}
