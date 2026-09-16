package de.schimmilab.accessiblereader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.data.DocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * An EPUB states its chapters instead of leaving them to be guessed, which is why it is worth supporting: no page
 * numbers to strip, no hyphens to repair across page breaks, no recognition, and a real table of contents.
 */
@RunWith(AndroidJUnit4::class)
class EpubImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun epub(withContents: Boolean): File {
        val file = File.createTempFile("reader-test", ".epub", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<?xml version="1.0"?>
                <container version="1.0"><rootfiles><rootfile full-path="OEBPS/buch.opf"
                  media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("OEBPS/buch.opf", """<?xml version="1.0"?>
                <package version="3.0"><metadata><dc:title>Der Garten und der Brief</dc:title></metadata>
                <manifest>
                  <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                  ${if (withContents) """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" else ""}
                  <item id="c1" href="text/eins.xhtml" media-type="application/xhtml+xml"/>
                  <item id="c2" href="text/zwei.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine><itemref idref="cover" linear="no"/><itemref idref="c1"/><itemref idref="c2"/></spine></package>""")
            if (withContents) put("OEBPS/nav.xhtml", """<html><body><nav epub:type="toc"><ol>
                <li><a href="text/eins.xhtml">Der Garten</a></li>
                <li><a href="text/zwei.xhtml">Der Brief</a></li></ol></nav></body></html>""")
            put("OEBPS/cover.xhtml", "<html><body><p>Umschlag</p></body></html>")
            // Chapters of a real length. Short ones are folded together on purpose, which the other test covers.
            val filler = "<p>Ein Satz über das Haus, den Garten und den Brief, der lang genug ist. </p>".repeat(200)
            put("OEBPS/text/eins.xhtml", """<html><head><style>p{}</style></head><body>
                <h1>Der Garten</h1><p>Der Garten war still. Ein Satz mit &auml; und &szlig;.</p>
                $filler</body></html>""")
            put("OEBPS/text/zwei.xhtml", """<html><body>
                <h1>Der Brief</h1><p>Der Brief lag auf dem Tisch und niemand las ihn.</p>
                $filler</body></html>""")
        }
        return file
    }

    @Test fun anEpubBringsItsOwnChapters() = runBlocking {
        val file = epub(withContents = true)
        try {
            val document = DocumentStore(context).import(Uri.fromFile(file)) {}
            Log.i("ReaderEpub", "${document.title}: ${document.chapters.size} Abschnitte, " +
                document.chapters.joinToString { "'${it.title}' ${it.text.length} Zeichen" })

            assertEquals("The book's own title is better than the file name", "Der Garten und der Brief", document.title)
            assertEquals(2, document.chapters.size)
            assertEquals("Der Garten", document.chapters[0].title)
            assertEquals("Der Brief", document.chapters[1].title)
            assertFalse("An EPUB has no pages and must not pretend otherwise", document.paged)
            assertTrue(document.chapters[0].text.contains("Der Garten war still."))
            assertTrue("Entities have to become letters", document.chapters[0].text.contains("ä und ß"))
            assertFalse("A cover marked linear=no is not part of the reading flow",
                document.chapters.any { it.text.contains("Umschlag") })
            assertFalse("A style block is not read out", document.chapters[0].text.contains("p{}"))
            assertEquals(document, DocumentStore(context).load(document.id))
            DocumentStore(context).remove(document.id)
        } finally { file.delete() }
    }

    /** A book whose stated chapters are a few sentences each is folded into something worth listening to. */
    @Test fun tinyChaptersAreFoldedTogether() = runBlocking {
        val file = File.createTempFile("reader-tiny", ".epub", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="b.opf"
                media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("b.opf", """<package version="3.0"><metadata><dc:title>Winziges Buch</dc:title></metadata>
                <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
                <item id="b" href="b.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="a"/><itemref idref="b"/></spine></package>""")
            put("nav.xhtml", """<nav epub:type="toc"><ol><li><a href="a.xhtml">Eins</a></li>
                <li><a href="b.xhtml">Zwei</a></li></ol></nav>""")
            put("a.xhtml", "<html><body><p>Ein kurzer erster Teil.</p></body></html>")
            put("b.xhtml", "<html><body><p>Ein kurzer zweiter Teil.</p></body></html>")
        }
        try {
            val document = DocumentStore(context).import(Uri.fromFile(file)) {}
            assertEquals("Two sentences do not make two sections", 1, document.chapters.size)
            assertEquals("Eins", document.chapters[0].title)
            assertTrue(document.chapters[0].text.contains("zweiter Teil"))
            DocumentStore(context).remove(document.id)
        } finally { file.delete() }
    }

    @Test fun anEpubWithoutContentsIsStillReadable() = runBlocking {
        val file = epub(withContents = false)
        try {
            val document = DocumentStore(context).import(Uri.fromFile(file)) {}
            Log.i("ReaderEpub", "ohne Inhaltsverzeichnis: ${document.chapters.size} Abschnitte, " +
                document.chapters.joinToString { "'${it.title}'" })
            assertTrue("Parts are grouped, as pages are", document.chapters.size in 1..2)
            assertTrue(document.chapters.first().title.startsWith("Teil"))
            assertTrue(document.chapters.joinToString { it.text }.contains("Der Brief lag auf dem Tisch"))
            assertTrue(document.notice.contains("kein Inhaltsverzeichnis"))
            DocumentStore(context).remove(document.id)
        } finally { file.delete() }
    }

    @Test fun aBrokenEpubSaysWhatIsWrong() = runBlocking {
        val file = File.createTempFile("reader-broken", ".epub", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype")); zip.write("application/epub+zip".toByteArray()); zip.closeEntry()
        }
        try {
            val error = runCatching { DocumentStore(context).import(Uri.fromFile(file)) {} }.exceptionOrNull()
            assertNotNull("A file without a package has to be refused", error)
            Log.i("ReaderEpub", "kaputtes EPUB: ${error!!.message}")
            assertTrue(error.message!!, error.message!!.contains("beschädigt"))
        } finally { file.delete() }
    }

    /** A locked book has to be called locked, not damaged. The difference decides where someone looks next. */
    @Test fun aCopyProtectedEpubSaysSoInsteadOfClaimingDamage() = runBlocking {
        val file = File.createTempFile("reader-drm", ".epub", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/buch.opf"
                media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("META-INF/encryption.xml", """<encryption xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
                  <enc:CipherData><enc:CipherReference URI="OEBPS/text/eins.xhtml"/></enc:CipherData>
                </enc:EncryptedData></encryption>""")
            put("OEBPS/buch.opf", """<package version="3.0"><metadata><dc:title>Gekauftes Buch</dc:title></metadata>
                <manifest><item id="c1" href="text/eins.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="c1"/></spine></package>""")
            put("OEBPS/text/eins.xhtml", "\u0001\u0002 verschluesselter Unsinn")
        }
        try {
            val error = runCatching { DocumentStore(context).import(Uri.fromFile(file)) {} }.exceptionOrNull()
            assertNotNull("A locked book has to be refused", error)
            Log.i("ReaderEpub", "kopiergeschuetzt: ${error!!.message}")
            assertTrue(error.message!!, error.message!!.contains("kopiergeschützt"))
            assertFalse("It is not damaged and must not be called that",
                error.message!!.contains("beschädigt"))
        } finally { file.delete() }
    }

    /** The two books in the collection that scramble only their fonts still have to read. */
    @Test fun aBookThatOnlyScramblesItsFontsStillReads() = runBlocking {
        val file = File.createTempFile("reader-fonts", ".epub", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/buch.opf"
                media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("META-INF/encryption.xml", """<encryption xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/>
                  <enc:CipherData><enc:CipherReference URI="OEBPS/Fonts/Baskerville.otf"/></enc:CipherData>
                </enc:EncryptedData></encryption>""")
            put("OEBPS/buch.opf", """<package version="3.0"><metadata><dc:title>Schoen gesetzt</dc:title></metadata>
                <manifest><item id="c1" href="text/eins.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="c1"/></spine></package>""")
            put("OEBPS/text/eins.xhtml", "<html><body><p>Der Garten war still und der Brief lag auf dem Tisch.</p></body></html>")
        }
        try {
            val document = DocumentStore(context).import(Uri.fromFile(file)) {}
            assertTrue(document.chapters.first().text.contains("Der Garten war still"))
            DocumentStore(context).remove(document.id)
        } finally { file.delete() }
    }
}