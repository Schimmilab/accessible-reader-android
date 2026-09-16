package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.*
import org.junit.Assert.*
import org.junit.Test

/** Remembering a place in a book, for someone who cannot put a finger in it. */
class BookmarksTest {
    private fun mark(chapter: Int, positionMs: Long, at: Long = 0) = Bookmark(
        chapter = chapter, item = 2, offsetMs = 1_000, positionMs = positionMs,
        voiceId = "de-DE-language", label = bookmarkLabel("Abschnitt $chapter", positionMs), createdAt = at)

    @Test fun theNewestBookmarkComesFirst() {
        val list = addBookmark(addBookmark(emptyList(), mark(1, 60_000)), mark(4, 120_000))
        assertEquals(listOf(4, 1), list.map { it.chapter })
    }

    /**
     * Pressing the button twice because you could not tell whether it worked is the commonest thing that
     * happens without sight. The second press must not leave two entries a listener then has to tell apart.
     */
    @Test fun markingTheSamePlaceTwiceLeavesOneBookmark() {
        val list = addBookmark(addBookmark(emptyList(), mark(3, 300_000)), mark(3, 305_000))
        assertEquals(1, list.size)
        assertEquals(305_000, list.first().positionMs)
    }

    @Test fun theSameMinuteInAnotherSectionIsAnotherPlace() {
        val list = addBookmark(addBookmark(emptyList(), mark(3, 300_000)), mark(4, 300_000))
        assertEquals(2, list.size)
    }

    @Test fun theListStaysShortEnoughToFindYourWayThroughByEar() {
        var list = emptyList<Bookmark>()
        repeat(30) { list = addBookmark(list, mark(it, it * 60_000L)) }
        assertEquals(MOST_BOOKMARKS, list.size)
        assertEquals("the oldest fall off the end", 29, list.first().chapter)
    }

    @Test fun aBookmarkSaysWhereItIsInWordsSomeoneCanHear() {
        assertEquals("Kapitel 3, Minute 12", bookmarkLabel("Kapitel 3", 12 * 60_000L + 4_000))
        assertEquals("the first minute is named in seconds, or it would say minute zero",
            "Seiten 5 bis 8, Sekunde 42", bookmarkLabel("Seiten 5 bis 8", 42_000))
    }

    @Test fun settingOneIsSaidOutLoud() {
        assertEquals("Lesezeichen gesetzt: Kapitel 3, Minute 12.", bookmarkSetAnnouncement("Kapitel 3, Minute 12"))
    }

    @Test fun theHeadingCountsThem() {
        assertEquals("Lesezeichen", bookmarksHeading(0))
        assertEquals("Lesezeichen, eines", bookmarksHeading(1))
        assertEquals("Lesezeichen, 4", bookmarksHeading(4))
    }

    @Test fun bookmarksAreReachableByVoice() {
        assertEquals(ReaderCommand.Mark, CommandParser.parse("Stelle merken"))
        assertEquals(ReaderCommand.Mark, CommandParser.parse("Lesezeichen setzen"))
        assertEquals(ReaderCommand.Bookmarks, CommandParser.parse("Lesezeichen"))
        assertEquals(ReaderCommand.Bookmarks, CommandParser.parse("meine Lesezeichen"))
    }
}
