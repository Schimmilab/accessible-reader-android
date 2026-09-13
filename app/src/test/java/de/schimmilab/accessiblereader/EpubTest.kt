package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.Epub
import de.schimmilab.accessiblereader.core.groupTitledSections
import de.schimmilab.accessiblereader.core.sectionTitle
import de.schimmilab.accessiblereader.core.splitOversized
import org.junit.Assert.*
import org.junit.Test

/**
 * An EPUB states its chapters instead of leaving them to be guessed, which is the whole reason for supporting
 * it. These tests use the two shapes that exist in the wild: EPUB 3 with a navigation document, and EPUB 2
 * with an NCX.
 */
class EpubTest {
    @Test fun theContainerNamesThePackageDocument() {
        val container = """<?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf"
                media-type="application/oebps-package+xml"/></rootfiles></container>"""
        assertEquals("OEBPS/content.opf", Epub.packagePath(container))
        assertNull(Epub.packagePath("<container></container>"))
    }

    @Test fun theReadingOrderComesFromTheSpine() {
        val opf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata><dc:title>Der Garten</dc:title></metadata>
              <manifest>
                <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="c1" href="text/kapitel1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="text/kapitel2.xhtml" media-type="application/xhtml+xml"/>
                <item id="pic" href="bild.jpg" media-type="image/jpeg"/>
              </manifest>
              <spine>
                <itemref idref="cover" linear="no"/>
                <itemref idref="c1"/>
                <itemref idref="c2"/>
              </spine></package>"""
        val book = Epub.readPackage(opf, "OEBPS/content.opf")
        assertEquals("Der Garten", book.title)
        assertEquals("A cover marked linear=no is not part of the reading flow",
            listOf("OEBPS/text/kapitel1.xhtml", "OEBPS/text/kapitel2.xhtml"), book.spine)
        assertEquals("OEBPS/nav.xhtml", book.contentsPath)
    }

    @Test fun anOlderBookPointsAtItsNcx() {
        val opf = """<package version="2.0">
              <metadata><dc:title>Altes Buch</dc:title></metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="c1" href="ch1.html" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx"><itemref idref="c1"/></spine></package>"""
        val book = Epub.readPackage(opf, "content.opf")
        assertEquals("toc.ncx", book.contentsPath)
        assertEquals(listOf("ch1.html"), book.spine)
    }

    @Test fun chapterTitlesComeFromEitherKindOfContents() {
        val nav = """<nav epub:type="toc"><ol>
            <li><a href="text/kapitel1.xhtml">Erstes Kapitel</a></li>
            <li><a href="text/kapitel2.xhtml#start">Zweites Kapitel</a></li>
            <li><a href="text/kapitel2.xhtml#spaeter">Ein Abschnitt darin</a></li></ol></nav>"""
        val fromNav = Epub.tableOfContents(nav, "OEBPS/nav.xhtml")
        assertEquals(3, fromNav.size)
        assertEquals("Erstes Kapitel", fromNav[0].title)
        assertNull(fromNav[0].fragment)
        assertEquals("OEBPS/text/kapitel2.xhtml", fromNav[1].path)
        assertEquals("A chapter inside a file is kept, anchor and all", "start", fromNav[1].fragment)
        assertEquals("spaeter", fromNav[2].fragment)
        assertEquals("Ein Abschnitt darin", fromNav[2].title)

        val ncx = """<navMap>
            <navPoint id="a" playOrder="1"><navLabel><text>Erstes Kapitel</text></navLabel>
              <content src="ch1.html"/></navPoint>
            <navPoint id="b" playOrder="2"><navLabel><text>Zweites Kapitel</text></navLabel>
              <content src="ch2.html"/></navPoint></navMap>"""
        val fromNcx = Epub.tableOfContents(ncx, "OEBPS/toc.ncx")
        assertEquals(listOf("Erstes Kapitel", "Zweites Kapitel"), fromNcx.map { it.title })
        assertEquals(listOf("OEBPS/ch1.html", "OEBPS/ch2.html"), fromNcx.map { it.path })
    }

    @Test fun relativePathsAreResolvedAgainstWhereTheyWereWritten() {
        assertEquals("OEBPS/text/a.xhtml", Epub.resolve("OEBPS", "text/a.xhtml"))
        assertEquals("OEBPS/a.xhtml", Epub.resolve("OEBPS/text", "../a.xhtml"))
        assertEquals("a.xhtml", Epub.resolve("OEBPS/text", "../../a.xhtml"))
        assertEquals("OEBPS/text/a.xhtml", Epub.resolve("OEBPS/text", "./a.xhtml"))
        assertEquals("a.xhtml", Epub.resolve("", "a.xhtml"))
    }

    @Test fun theTextOfAChapterIsReadable() {
        val html = """<html><head><title>weg</title><style>p{color:red}</style></head><body>
            <h1>Erstes Kapitel</h1>
            <p>Der Garten war still. Ein Satz mit &auml;, &Auml; und &szlig;.</p>
            <p>Zweiter Absatz<br/>mit Umbruch.</p>
            <!-- ein Kommentar --><script>alert(1)</script>
            </body></html>"""
        val text = Epub.text(html)
        assertFalse("A style block is not read out", text.contains("color"))
        assertFalse("A script is not read out", text.contains("alert"))
        assertFalse("A comment is not read out", text.contains("Kommentar"))
        assertFalse("The head is not read out", text.contains("weg"))
        assertTrue(text.contains("Der Garten war still."))
        assertTrue("Entities have to become letters", text.contains("ä, Ä und ß"))
        assertTrue("A heading must not run into the paragraph below it",
            Regex("Erstes Kapitel\\s*\\n").containsMatchIn(text))
        assertTrue("A line break inside a paragraph is a line break",
            Regex("Zweiter Absatz\\s*\\n\\s*mit Umbruch").containsMatchIn(text))
    }

    @Test fun aDocumentIsCutWhereItsContentsPointIntoIt() {
        // One real book puts fifty chapters into four files and tells them apart by the anchor alone.
        val html = """<html><body><p>Vorspann</p>
            <h2 id="k1">Erstes Kapitel</h2><p>Der Garten war still.</p>
            <h2 id="k2">Zweites Kapitel</h2><p>Der Brief lag auf dem Tisch.</p></body></html>"""
        val pieces = Epub.split(html, listOf("k1", "k2"))
        assertEquals(3, pieces.size)
        assertNull("What comes before the first anchor belongs to whatever was read before", pieces[0].first)
        assertTrue(pieces[0].second.contains("Vorspann"))
        assertEquals("k1", pieces[1].first)
        assertTrue(pieces[1].second.contains("Der Garten war still"))
        assertFalse("A piece must not contain the next one", pieces[1].second.contains("Der Brief"))
        assertEquals("k2", pieces[2].first)

        // An anchor the document does not actually have must not lose the text.
        val whole = Epub.split(html, listOf("gibtsnicht"))
        assertEquals(1, whole.size)
        assertTrue(whole[0].second.contains("Der Brief lag auf dem Tisch"))
        assertEquals(1, Epub.split(html, emptyList()).size)
    }

    @Test fun aSectionNobodyCouldSitThroughIsCutDown() {
        // One real book is a single file of half a million characters: about nine hours with no way to move.
        val long = "Ein Satz über den Garten und den Brief, der lange genug ist. ".repeat(2_000)
        val parts = splitOversized("Das ganze Buch", long)
        assertTrue("120.000 characters have to become several parts, got ${parts.size}", parts.size >= 4)
        assertEquals("Das ganze Buch, Teil 1", parts.first().first)
        // Nothing may be lost. Only the whitespace at each cut disappears, so at most one character per part.
        val kept = parts.sumOf { it.second.length }
        assertTrue("Lost text: $kept of ${long.trim().length}", kept >= long.trim().length - parts.size)
        assertTrue(kept <= long.trim().length)
        assertTrue("No part may be empty", parts.all { it.second.isNotBlank() })
        assertTrue("No part may stay oversized", parts.all { it.second.length <= 30_000 })
        // A normal section is left exactly as it is.
        assertEquals(listOf("Kapitel" to "kurzer Text"), splitOversized("Kapitel", "kurzer Text"))
    }

    @Test fun anOverGranularContentsIsFoldedIntoSectionsWorthListeningTo() {
        // One real book names 565 entries for 754.000 characters, so every "next section" would move two minutes.
        val titles = List(30) { "Kapitel ${it + 1}" }
        val short = List(30) { 1_000 }
        val folded = groupTitledSections(titles, short, target = 10_000)
        assertEquals("Thirty short entries have to become three sections", 3, folded.size)
        assertEquals(listOf(0, 10, 20), folded)

        // A book whose chapters are long enough keeps every one of them.
        val long = List(5) { 20_000 }
        assertEquals(listOf(0, 1, 2, 3, 4), groupTitledSections(List(5) { "Kapitel" }, long, target = 10_000))

        // Untitled blocks never start a section, they belong to what is being read.
        assertEquals(listOf(0), groupTitledSections(listOf("Anfang", null, null), listOf(9_000, 9_000, 9_000), 10_000))
    }

    @Test fun aTitleThatIsOnlyANumberIsNotRead() {
        // One real book carries page labels in its contents, and "Abschnitt 5: 217" helps nobody.
        assertEquals("Teil 3", sectionTitle("217", 2))
        assertEquals("Teil 1", sectionTitle(null, 0))
        assertEquals("Teil 2", sectionTitle("  ", 1))
        assertEquals("Teil 4", sectionTitle("IV.", 3))
        assertEquals("Kapitel 3", sectionTitle("Kapitel 3", 5))
        assertEquals("1 Introduction", sectionTitle("1 Introduction", 0))
    }
}