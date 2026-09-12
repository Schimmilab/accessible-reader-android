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
        // Sized for real books after a 600-page novel was refused. A page holds roughly 2.000 characters,
        // so the three limits describe the same ceiling from three sides.
        const val MAX_BYTES = 120L * 1024 * 1024
        const val MAX_PAGES = 3000
        const val MAX_CHARACTERS = 6_000_000

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
        }, json.optString("notice"))
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
        val json = JSONObject().put("title", document.title).put("chapters", chapters).put("notice", document.notice)
        val temp = File(directory, "${document.id}.tmp")
        temp.writeText(json.toString())
        check(temp.renameTo(File(directory, "${document.id}.json"))) { "Dokument konnte nicht gespeichert werden." }
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
                val starts = if (hasOutline) sorted else pages.indices.map { it to "Seite ${it + 1}" }
                val chapters = starts.mapIndexed { index, (first, name) ->
                    val end = starts.getOrNull(index + 1)?.first ?: pages.size
                    Chapter(name, first + 1, end, TextChunks.joinPages(pages.subList(first, end)))
                }
                val emptyPages = pages.count { it.isBlank() }
                val notice = listOfNotNull(
                    if (!hasOutline) "Keine Kapitelmarken gefunden. Das Inhaltsverzeichnis listet die Seiten." else "PDF-Kapitelmarken übernommen. Sprünge beginnen an der jeweiligen Seite.",
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
