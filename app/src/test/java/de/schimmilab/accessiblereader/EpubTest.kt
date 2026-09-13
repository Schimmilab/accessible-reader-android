package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.Epub
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
        assertEquals("Erstes Kapitel", fromNav["OEBPS/text/kapitel1.xhtml"])
        assertEquals("The first label for a document wins", "Zweites Kapitel", fromNav["OEBPS/text/kapitel2.xhtml"])

        val ncx = """<navMap>
            <navPoint id="a" playOrder="1"><navLabel><text>Erstes Kapitel</text></navLabel>
              <content src="ch1.html"/></navPoint>
            <navPoint id="b" playOrder="2"><navLabel><text>Zweites Kapitel</text></navLabel>
              <content src="ch2.html"/></navPoint></navMap>"""
        val fromNcx = Epub.tableOfContents(ncx, "OEBPS/toc.ncx")
        assertEquals("Erstes Kapitel", fromNcx["OEBPS/ch1.html"])
        assertEquals("Zweites Kapitel", fromNcx["OEBPS/ch2.html"])
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
}
