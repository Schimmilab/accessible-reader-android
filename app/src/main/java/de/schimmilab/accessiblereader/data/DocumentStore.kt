package de.schimmilab.accessiblereader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import de.schimmilab.accessiblereader.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class DocumentStore(private val context: Context) {
    companion object {
        // Sized for real books. A 600-page novel was refused once, then a 3700-page one. A page holds roughly
        // 2.000 characters, so the three limits describe the same ceiling from three sides. Raising them is not
        // free: the text of a whole book is held in memory and written as one JSON file, so any change here
        // needs a measurement at the new size, see PdfImportTest.
        const val MAX_BYTES = 200L * 1024 * 1024
        const val MAX_PAGES = 10_000
        const val MAX_CHARACTERS = 20_000_000

        /** How many pages without a single character are enough to give up, recognition included. */
        const val SCAN_PROBE_PAGES = 25

        fun scanMessage(pagesChecked: Int): String =
            if (pagesChecked == 1) "Auf dieser Seite steht keine Schrift. Auch die Texterkennung hat nichts gefunden."
            else "In diesem PDF steht keine Schrift. Auch die Texterkennung hat auf den ersten $pagesChecked Seiten nichts gefunden. Wahrscheinlich sind es Fotos oder die Vorlage ist zu undeutlich."
    }

    private val directory = File(context.filesDir, "documents").apply { mkdirs() }
    init { PDFBoxResourceLoader.init(context) }

    fun load(id: String): ReaderDocument? = runCatching {
        val json = JSONObject(File(directory, "$id.json").readText())
        val array = json.getJSONArray("chapters")
        ReaderDocument(id, json.getString("title"), List(array.length()) { i ->
            val c = array.getJSONObject(i)
            Chapter(c.getString("title"), c.getInt("first"), c.getInt("last"), c.getString("text"))
        }, json.optString("notice"), json.optBoolean("paged", true))
    }.getOrNull()

    private fun metaFile(id: String) = File(directory, "$id.meta.json")

    private fun saveMeta(entry: LibraryEntry) {
        val json = JSONObject().put("title", entry.title).put("chapters", entry.chapters).put("addedAt", entry.addedAt)
        val temp = File(directory, "${entry.id}.meta.tmp")
        temp.writeText(json.toString())
        check(temp.renameTo(metaFile(entry.id))) { "Die Dokumentangaben konnten nicht gespeichert werden." }
    }

    private fun readMeta(id: String): LibraryEntry? = runCatching {
        val json = JSONObject(metaFile(id).readText())
        LibraryEntry(id, json.getString("title"), json.getInt("chapters"), json.getLong("addedAt"))
    }.getOrNull()

    /**
     * Lists imported documents from small side files, so opening the library never parses a whole book.
     * Documents from older versions have no side file yet and get one on first listing.
     */
    suspend fun library(): List<LibraryEntry> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty()
            .filter { it.name.endsWith(".json") && !it.name.endsWith(".meta.json") }
            .mapNotNull { file ->
                val id = file.name.removeSuffix(".json")
                readMeta(id) ?: load(id)?.let { document ->
                    LibraryEntry(id, document.title, document.chapters.size, file.lastModified())
                        .also { runCatching { saveMeta(it) } }
                }
            }
            .sortedByDescending { it.addedAt }
    }

    /** Removes the extracted text and the listing entry. The user's own PDF file is never touched. */
    fun remove(id: String) {
        File(directory, "$id.json").delete()
        metaFile(id).delete()
    }

    private fun save(document: ReaderDocument) {
        val chapters = JSONArray()
        document.chapters.forEach { c -> chapters.put(JSONObject().put("title", c.title)
            .put("first", c.firstPage).put("last", c.lastPage).put("text", c.text)) }
        val json = JSONObject().put("title", document.title).put("chapters", chapters)
            .put("notice", document.notice).put("paged", document.paged)
        val temp = File(directory, "${document.id}.tmp")
        temp.writeText(json.toString())
        check(temp.renameTo(File(directory, "${document.id}.json"))) { "Dokument konnte nicht gespeichert werden." }
    }

    /**
     * Reads whatever the listener handed over. An EPUB states its chapters, a PDF has to have them guessed, so
     * the two take different routes from here.
     */
    suspend fun import(uri: Uri, progress: (String) -> Unit): ReaderDocument =
        if (looksLikeEpub(uri)) importEpub(uri, progress) else importPdf(uri, progress)

    private fun looksLikeEpub(uri: Uri): Boolean {
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (type.contains("epub", ignoreCase = true)) return true
        if (type.contains("pdf", ignoreCase = true)) return false
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
        return name?.endsWith(".epub", ignoreCase = true) == true
    }

    /** Copies the chosen file to a temp file we can read at will, and returns it with the title and the id. */
    private fun fetch(uri: Uri, temp: File, suffix: String): Pair<String, String> {
        val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.removeSuffix(suffix) ?: "Dokument"
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "Diese Datei ist größer als ${MAX_BYTES / 1024 / 1024} MB." }
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("Die Datei konnte nicht geöffnet werden.")
        val digest = MessageDigest.getInstance("SHA-256")
        temp.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return title to digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * An EPUB is a ZIP of XHTML documents with a stated reading order and, almost always, a table of contents.
     * Everything the PDF route has to work out by hand is simply written down here.
     */
    suspend fun importEpub(uri: Uri, progress: (String) -> Unit): ReaderDocument = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("import-", ".epub", context.cacheDir)
        try {
            val (fileTitle, id) = fetch(uri, temp, ".epub")
            java.util.zip.ZipFile(temp).use { zip ->
                fun read(path: String): String? = zip.getEntry(path)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val container = read("META-INF/container.xml")
                    ?: error("Diese EPUB-Datei hat kein Inhaltsverzeichnis der Dateien. Sie ist wahrscheinlich beschädigt.")
                val packagePath = Epub.packagePath(container)
                    ?: error("Diese EPUB-Datei nennt keine Buchdatei. Sie ist wahrscheinlich beschädigt.")
                val book = Epub.readPackage(read(packagePath) ?: error("Die Buchdatei fehlt in diesem EPUB."), packagePath)
                require(book.spine.isNotEmpty()) { "Dieses EPUB nennt keine Lesereihenfolge und kann nicht vorgelesen werden." }
                require(book.spine.size <= MAX_PAGES) { "Dieses EPUB hat ${book.spine.size} Teile. Der Reader schafft bis zu $MAX_PAGES." }

                val contents = book.contentsPath?.let { path -> read(path)?.let { Epub.tableOfContents(it, path) } }
                    .orEmpty()
                val anchorsByPath = contents.filter { it.fragment != null }.groupBy({ it.path }, { it.fragment!! })
                val titleFor = contents.associateBy({ it.path + "#" + it.fragment.orEmpty() }, { it.title })

                // A book routinely puts fifty chapters into four files and tells them apart by the anchor alone,
                // so every document is cut where its own table of contents points into it.
                var characters = 0
                val blocks = mutableListOf<Pair<String?, String>>()
                book.spine.forEachIndexed { index, path ->
                    coroutineContext.ensureActive()
                    progress("Lese Teil ${index + 1} von ${book.spine.size} …")
                    Epub.split(read(path).orEmpty(), anchorsByPath[path].orEmpty()).forEach { piece ->
                        val text = TextChunks.cleanBlock(piece.second)
                        characters += text.length
                        require(characters <= MAX_CHARACTERS) { "Dieses Buch enthält mehr als ${MAX_CHARACTERS / 1_000_000} Millionen Zeichen." }
                        blocks += titleFor[path + "#" + piece.first.orEmpty()] to text
                    }
                }
                require(blocks.any { it.second.isNotBlank() }) { "In diesem EPUB steht kein Text, den der Reader vorlesen könnte." }

                // A stated table of contents beats any grouping. Without one, the parts are grouped by length the
                // way pages are, and a single huge part is cut down to something a listener can move around in.
                val hasTitles = blocks.any { it.first != null }
                val starts = if (hasTitles) groupTitledSections(blocks.map { it.first }, blocks.map { it.second.length })
                    else groupPagesIntoSections(blocks.map { it.second.length })
                val titled: List<Pair<String, String>> = starts.mapIndexed { index, first ->
                    val end = starts.getOrNull(index + 1) ?: blocks.size
                    sectionTitle(blocks[first].first, index) to
                        TextChunks.joinPages(blocks.subList(first, end).map { it.second })
                }
                val chapters = titled.flatMap { splitOversized(it.first, it.second) }
                    .filter { it.second.isNotBlank() }
                    .mapIndexed { index, piece -> Chapter(piece.first, index + 1, index + 1, piece.second) }
                require(chapters.isNotEmpty()) { "In diesem EPUB steht kein Text, den der Reader vorlesen könnte." }
                val titles = contents

                val notice = listOfNotNull(
                    if (titles.isEmpty()) "Dieses EPUB hat kein Inhaltsverzeichnis. Der Reader hat die Teile zu Abschnitten zusammengefasst."
                    else "Inhaltsverzeichnis des Buches übernommen.",
                    "EPUB-Bücher haben keine Seitenzahlen, deshalb nennt der Reader nur Abschnitte."
                ).joinToString(" ")
                ReaderDocument(id, book.title?.takeIf { it.isNotBlank() } ?: fileTitle, chapters, notice, paged = false)
                    .also {
                        save(it)
                        saveMeta(LibraryEntry(it.id, it.title, it.chapters.size, System.currentTimeMillis()))
                    }
            }
        } finally { temp.delete() }
    }

    suspend fun importPdf(uri: Uri, progress: (String) -> Unit): ReaderDocument = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("import-", ".pdf", context.cacheDir)
        try {
            val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.removeSuffix(".pdf") ?: "PDF-Dokument"
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "Diese PDF-Datei ist größer als ${MAX_BYTES / 1024 / 1024} MB. So große Dateien sind fast immer eingescannt, und eingescannte Seiten kann der Reader noch nicht lesen." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Die PDF-Datei konnte nicht geöffnet werden.")
            val digest = MessageDigest.getInstance("SHA-256")
            temp.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val id = digest.digest().joinToString("") { "%02x".format(it) }
            PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)).use { pdf ->
                require(pdf.numberOfPages in 1..MAX_PAGES) { "Dieses PDF hat ${pdf.numberOfPages} Seiten. Der Reader schafft bis zu $MAX_PAGES." }
                require(pdf.currentAccessPermission.canExtractContent() || pdf.currentAccessPermission.canExtractForAccessibility()) {
                    "Dieses PDF erlaubt keinen Textzugriff. Bitte verwende eine freigegebene Kopie."
                }
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                var characters = 0
                var recognized = 0
                // Opened only when a page turns out to be a scan, because it costs memory and a model.
                var ocr: PageOcr? = null
                val pages = try { (1..pdf.numberOfPages).map { page ->
                    coroutineContext.ensureActive()
                    progress("Lese Seite $page von ${pdf.numberOfPages} …")
                    stripper.startPage = page
                    stripper.endPage = page
                    var text = TextChunks.clean(stripper.getText(pdf))
                    if (text.isBlank()) {
                        progress("Seite $page von ${pdf.numberOfPages} ist ein Bild, Texterkennung läuft …")
                        if (ocr == null) ocr = runCatching { PageOcr(temp) }.getOrNull()
                        ocr?.let { reader ->
                            if (page - 1 < reader.pageCount) {
                                text = TextChunks.clean(reader.text(page - 1))
                                if (text.isNotBlank()) recognized++
                            }
                        }
                    }
                    text.also {
                        characters += it.length
                        require(characters <= MAX_CHARACTERS) { "Dieses PDF enthält mehr als ${MAX_CHARACTERS / 1_000_000} Millionen Zeichen. Das ist mehr Text, als der Reader auf einmal verarbeiten kann." }
                        // Give up after a sample instead of working through a 500-page book first. Text extraction
                        // and recognition have both failed on this many leading pages, and waiting minutes for
                        // that verdict is the worst part of it.
                        require(!(page >= SCAN_PROBE_PAGES && characters == 0)) { scanMessage(page) }
                    }
                } } finally { ocr?.close() }
                require(pages.any { it.isNotBlank() }) { scanMessage(pdf.numberOfPages) }
                val marks = mutableListOf<Pair<Int, String>>()
                fun collect(node: PDOutlineNode, depth: Int) {
                    if (depth > 12 || marks.size >= 1000) return
                    var item = node.firstChild
                    var visited = 0
                    while (item != null && visited++ < 1000 && marks.size < 1000) {
                        val current = item
                        runCatching { current.findDestinationPage(pdf) }.getOrNull()?.let { page ->
                            val index = pdf.pages.indexOf(page)
                            if (index in pages.indices) marks += index to (current.title?.take(200) ?: "Kapitel ${marks.size + 1}")
                        }
                        collect(current, depth + 1)
                        item = current.nextSibling
                    }
                }
                pdf.documentCatalog.documentOutline?.let { collect(it, 0) }
                val sorted = marks.sortedBy { it.first }.distinctBy { it.first }.toMutableList()
                val hasOutline = sorted.isNotEmpty()
                if (hasOutline && sorted.first().first > 0) sorted.add(0, 0 to "Anfang")
                // Without bookmarks, group pages instead of making every page a section. One section per page
                // meant playback stopped for eight to twelve seconds at every page break.
                val starts = if (hasOutline) sorted
                    else groupPagesIntoSections(pages.map { it.length }).map { it to "" }
                val chapters = starts.mapIndexed { index, (first, name) ->
                    val end = starts.getOrNull(index + 1)?.first ?: pages.size
                    Chapter(name.ifBlank { pageRangeTitle(first + 1, end) }, first + 1, end,
                        TextChunks.joinPages(pages.subList(first, end)))
                }
                val emptyPages = pages.count { it.isBlank() }
                val notice = listOfNotNull(
                    if (!hasOutline) "Keine Kapitelmarken gefunden. Der Reader hat die Seiten zu Abschnitten zusammengefasst." else "PDF-Kapitelmarken übernommen. Sprünge beginnen an der jeweiligen Seite.",
                    if (recognized > 0) "$recognized eingescannte Seiten wurden mit Texterkennung gelesen. Dabei können Lesefehler entstehen." else null,
                    if (emptyPages > 0) "$emptyPages Seiten ohne lesbaren Text, auch die Texterkennung fand dort nichts." else null,
                    "Bei Spalten, Tabellen und Fußnoten bitte die Lesereihenfolge prüfen."
                ).joinToString(" ")
                ReaderDocument(id, title, chapters, notice).also {
                    save(it)
                    saveMeta(LibraryEntry(it.id, it.title, it.chapters.size, System.currentTimeMillis()))
                }
            }
        } finally { temp.delete() }
    }
}
