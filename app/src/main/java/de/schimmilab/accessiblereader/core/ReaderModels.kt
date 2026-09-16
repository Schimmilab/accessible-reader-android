package de.schimmilab.accessiblereader.core

import java.text.Normalizer
import java.util.Locale

data class Chapter(val title: String, val firstPage: Int, val lastPage: Int, val text: String)
/**
 * @param paged whether [Chapter.firstPage] means a printed page. A PDF has pages, an EPUB has a reading order,
 * and telling a listener "Seite 12" for the twelfth file in an archive would be an invention.
 */
data class ReaderDocument(val id: String, val title: String, val chapters: List<Chapter>, val notice: String = "",
                          val paged: Boolean = true)
data class AudioPosition(val item: Int, val offsetMs: Long)

/**
 * How much text one section should hold when a PDF brings no bookmarks of its own.
 *
 * One section per page looked tidy and listened badly: playback stops at the end of every section while the next
 * one is synthesized, which measured 8 to 12 seconds of silence. On a 400 page book that is a pause after every
 * page. Roughly ten thousand characters is about ten minutes of listening, so the silence arrives once per ten
 * minutes instead of once per minute, and the spoken section number stops interrupting every page.
 */
const val SECTION_TARGET_CHARACTERS = 10_000

/**
 * Groups pages into sections, given how many characters each page holds. Returns the index of the first page of
 * each section. A page that is longer than the target stands alone; a blank page never starts a section.
 */
fun groupPagesIntoSections(pageLengths: List<Int>, target: Int = SECTION_TARGET_CHARACTERS): List<Int> {
    if (pageLengths.isEmpty()) return emptyList()
    val starts = mutableListOf(0)
    var carried = 0
    pageLengths.forEachIndexed { index, length ->
        if (index > 0 && carried >= target) { starts += index; carried = 0 }
        carried += length
    }
    return starts
}

/**
 * Where a new section starts among blocks that carry their own titles.
 *
 * A book's own table of contents is not always a useful shape to listen to. One real book names 565 entries for
 * 754.000 characters, so every "next section" would move about two minutes and the spoken section number would
 * interrupt that often. A titled block therefore starts a new section only once the current one has grown to
 * roughly [target]; otherwise it is folded into what is being read.
 */
fun groupTitledSections(titles: List<String?>, lengths: List<Int>, target: Int = SECTION_TARGET_CHARACTERS): List<Int> {
    if (lengths.isEmpty()) return emptyList()
    val starts = mutableListOf(0)
    var carried = 0
    lengths.forEachIndexed { index, length ->
        if (index > 0 && titles.getOrNull(index) != null && carried >= target) { starts += index; carried = 0 }
        carried += length
    }
    return starts
}

private val NUMBER_ONLY = Regex("""[\d.,\s]+|[ivxlcdm]+\.?|[IVXLCDM]+\.?""")

/**
 * What a section is called. A title that is nothing but a number is a page label the book happens to carry in
 * its contents, and reading "Abschnitt 5: 217" out loud helps nobody.
 */
fun sectionTitle(raw: String?, index: Int): String {
    val clean = raw?.trim().orEmpty()
    return if (clean.isBlank() || NUMBER_ONLY.matches(clean)) "Teil ${index + 1}" else clean
}

/**
 * Cuts a section that nobody would want to sit through into parts a listener can navigate. One real book is a
 * single file of half a million characters, which is about nine hours in one piece with no way to move inside it.
 */
fun splitOversized(title: String, text: String, target: Int = SECTION_TARGET_CHARACTERS,
                   limit: Int = SECTION_TARGET_CHARACTERS * 3): List<Pair<String, String>> {
    if (text.length <= limit) return listOf(title to text)
    val parts = mutableListOf<Pair<String, String>>()
    var rest = text
    while (rest.isNotEmpty()) {
        if (rest.length <= limit) { parts += title to rest; break }
        // Cut at a paragraph if there is one nearby, otherwise at a sentence, never inside a word.
        val window = rest.substring(0, minOf(limit, rest.length))
        val cut = window.lastIndexOf("\n\n").takeIf { it > target / 2 }
            ?: window.lastIndexOfAny(charArrayOf('.', '!', '?')).takeIf { it > target / 2 }?.plus(1)
            ?: window.lastIndexOf(' ').takeIf { it > target / 2 }
            ?: window.length
        parts += title to rest.substring(0, cut).trim()
        rest = rest.substring(cut).trimStart()
    }
    return parts.mapIndexed { index, (name, body) ->
        (if (parts.size > 1) "$name, Teil ${index + 1}" else name) to body
    }
}

/** What a section of a book without bookmarks is called. */
fun pageRangeTitle(firstPage: Int, lastPage: Int): String =
    if (firstPage >= lastPage) "Seite $firstPage" else "Seiten $firstPage bis $lastPage"

object AudioTimeline {
    fun absolute(durations: List<Long>, item: Int, offsetMs: Long): Long =
        durations.take(item.coerceIn(0, durations.size)).sum() + offsetMs.coerceAtLeast(0)

    fun locate(durations: List<Long>, positionMs: Long): AudioPosition {
        if (durations.isEmpty()) return AudioPosition(0, 0)
        var remaining = positionMs.coerceIn(0, durations.sum())
        durations.forEachIndexed { index, duration ->
            if (remaining < duration || index == durations.lastIndex) return AudioPosition(index, remaining)
            remaining -= duration
        }
        return AudioPosition(0, 0)
    }
}

/** What was written down about a listener's place in a document, read back from the preferences. */
data class SavedPosition(val chapter: Int, val item: Int, val offsetMs: Long, val voiceId: String,
                         val finished: Boolean)

/**
 * Where a section starts when it is opened again.
 *
 * A section is cut into parts by its text alone, never by the voice, so the part someone was listening to still
 * means the same words in another voice. Only the seconds inside that part belong to the old voice: the same
 * millisecond is a different word once a voice reads half again as fast. So a changed voice keeps the part and
 * starts it from the beginning, which repeats at most one part instead of the whole section. Before this, a
 * listener who tried another voice was thrown back to the section heading, up to ten minutes of listening.
 */
fun resumePoint(saved: SavedPosition, chapterIndex: Int, voiceId: String, parts: Int): AudioPosition {
    if (parts <= 0 || saved.chapter != chapterIndex || saved.finished) return AudioPosition(0, 0)
    val item = saved.item.coerceIn(0, parts - 1)
    return AudioPosition(item, if (saved.voiceId == voiceId) saved.offsetMs.coerceAtLeast(0) else 0)
}

object TextChunks {
    /**
     * Turns one extracted page into speakable text. The order matters: the page number has to go before the
     * hyphens are repaired, otherwise it glues itself onto the word the page break cut in half.
     */
    fun clean(text: String): String = repairWraps(stripRunningNumbers(collapseWhitespace(text)))

    /**
     * Cleanup for a source that has no page furniture, such as one document of an EPUB. Dropping a lone number
     * here would eat a chapter heading that is simply "1".
     */
    fun cleanBlock(text: String): String = repairWraps(collapseWhitespace(text))

    private fun collapseWhitespace(text: String) = text
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex(" *\\r?\\n *"), "\n")
        .replace(Regex("\n{3,}"), "\n\n").trim()

    private fun repairWraps(text: String) = text
        // A soft hyphen at a line end is a wrapped word, never a spoken one. Books typeset for print are full of
        // them, and a listener hears "verste hen" where the page said "verstehen".
        .replace("\u00ad\n", "")
        .replace(Regex("(\\p{L})-\\n(\\p{Ll})"), "$1$2")
        // Older German typesetting maps its hyphen to U+00AC. Joining only before a lowercase letter keeps the
        // logical NOT sign of a technical text intact.
        .replace(Regex("(\\p{L})\u00ac\n(\\p{Ll})"), "$1$2")
        // Every soft hyphen but a trailing one, which joinPages still needs to repair a word split across pages.
        .replace(Regex("\u00ad(?=[\\s\\S])"), "")
        .trim()

    private val RUNNING_NUMBER = Regex("\\p{Nd}{1,4}|[ivxlcdm]{2,8}|[IVXLCDM]{2,8}")

    /**
     * Drops a page number that stands alone on a page's first or last line. Pages are read one by one and then
     * joined, so such a number lands in the middle of a sentence: "... sehr attraktive Frauen und zwanzig
     * Maenner, die ...". A page holding nothing but a number is left alone.
     */
    fun stripRunningNumbers(page: String): String {
        var lines = page.split("\n")
        if (lines.size >= 2 && RUNNING_NUMBER.matches(lines.first().trim())) lines = lines.drop(1)
        if (lines.size >= 2 && RUNNING_NUMBER.matches(lines.last().trim())) lines = lines.dropLast(1)
        return lines.joinToString("\n").trim()
    }

    /**
     * Joins the pages of a chapter and repairs words that the page break cut in half. A print book breaks a word
     * at the bottom of a page as readily as at the end of a line.
     */
    fun joinPages(pages: List<String>): String {
        val out = StringBuilder()
        for (page in pages) {
            val text = page.trim()
            if (text.isEmpty()) continue
            if (out.isNotEmpty()) {
                val last = out.last()
                val wrapped = last == '\u00ad' ||
                    ((last == '-' || last == '\u00ac') && text.first().isLowerCase())
                if (wrapped) out.deleteCharAt(out.length - 1) else out.append("\n\n")
            }
            out.append(text)
        }
        return out.toString().replace("\u00ad", "")
    }

    // Limit UTF-16 length as required by Android TTS; never split a surrogate pair.
    fun split(text: String, limit: Int = 1000): List<String> = split(text) { limit }

    /**
     * The sizes used for actual playback. The first pieces are short so the first sound arrives quickly, and
     * later ones are longer because a speech engine wastes less time per character on them.
     *
     * This matters only for slow voices, and it matters a lot. A local neural voice needed 30 seconds for a
     * 1000-character piece, so a listener waited over a minute in silence before a word came out, and long
     * pieces ran into the synthesis budget. The stock voices are so far ahead of playback that they never
     * noticed either way.
     */
    fun splitForPlayback(text: String, from: Int = 0): List<String> = split(text) { index ->
        when (index + from) { 0 -> 250; 1 -> 500; else -> 1000 }
    }

    private inline fun split(text: String, limitAt: (Int) -> Int): List<String> {
        val result = mutableListOf<String>()
        var rest = text.trim()
        while (rest.isNotEmpty()) {
            val limit = limitAt(result.size)
            require(limit >= 4)
            var end = minOf(limit, rest.length)
            if (end < rest.length) {
                val boundary = rest.substring(0, end).lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ' '))
                if (boundary >= limit / 2) end = boundary + 1
                if (rest[end - 1].isHighSurrogate()) end--
            }
            rest.substring(0, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            rest = rest.substring(end).trimStart()
        }
        return result
    }
}

/** One identical sample for every voice, with numbers, a date and abbreviations, as the listening test requires. */
const val VOICE_SAMPLE = "Kapitel 3, Seite 127. Am 14. März um 8 Uhr 30 verließ Dr. Meier das Haus. " +
    "Etwa 25 Prozent des Weges lagen im Schatten. Magst du diese Stimme auch nach einer halben Stunde noch?"

/** Spoken intro of every chapter, so listeners hear where they are without TalkBack. */
object ChapterAnnouncement {
    private val page = Regex("Seiten? \\d+( bis \\d+)?")
    fun text(index: Int, total: Int, title: String): String =
        if (page.matches(title.trim())) "${title.trim()}." else "Abschnitt ${index + 1} von $total: ${title.trim()}."
}

sealed interface ReaderCommand {
    data object Play : ReaderCommand
    data object Pause : ReaderCommand
    data object Next : ReaderCommand
    data object Previous : ReaderCommand
    data object Contents : ReaderCommand
    data object Library : ReaderCommand
    data object Position : ReaderCommand
    data class Seek(val seconds: Int) : ReaderCommand
    data class GoTo(val chapter: Int) : ReaderCommand
}

object CommandParser {
    fun parse(input: String): ReaderCommand? {
        val s = Normalizer.normalize(input.lowercase(Locale.GERMAN), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace("ß", "ss")
            .replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex(" +"), " ")
        return when (s) {
            "vorlesen", "weiter", "fortsetzen", "start", "starten", "abspielen" -> ReaderCommand.Play
            "pause", "stopp", "stop", "anhalten" -> ReaderCommand.Pause
            "nachstes kapitel", "nachste seite" -> ReaderCommand.Next
            "vorheriges kapitel", "letztes kapitel", "vorherige seite" -> ReaderCommand.Previous
            "inhaltsverzeichnis", "kapitel anzeigen", "inhaltsverzeichnis offnen" -> ReaderCommand.Contents
            "bibliothek", "meine bucher", "meine dokumente", "bibliothek offnen" -> ReaderCommand.Library
            "position", "wo bin ich", "aktuelle position" -> ReaderCommand.Position
            else -> {
                Regex("(?:kapitel|gehe zu kapitel) (\\d+)").matchEntire(s)?.let {
                    return it.groupValues[1].toIntOrNull()?.takeIf { n -> n > 0 }?.let(ReaderCommand::GoTo)
                }
                val match = Regex("(30|dreissig|10|zehn|60|sechzig) sekunden (zuruck|vor|vorspringen|zuruckspringen)").matchEntire(s)
                    ?: return null
                val n = when (match.groupValues[1]) { "dreissig" -> 30; "zehn" -> 10; "sechzig" -> 60; else -> match.groupValues[1].toInt() }
                ReaderCommand.Seek(if (match.groupValues[2].startsWith("zuruck")) -n else n)
            }
        }
    }
}

fun demoDocument() = ReaderDocument("demo-v1", "Ein Moment zum Zuhören", listOf(
    Chapter("Ankommen", 1, 1, """
        Willkommen bei Accessible Reader. Diese Leseprobe hilft dir, die Bedienung in Ruhe auszuprobieren.
        Draußen hat es aufgehört zu regnen. Auf der Fensterbank steht eine Tasse Tee. Der Raum ist still genug, um das Ticken der Uhr zu hören.
        Eine Leserin schlägt ein Buch auf. Sie möchte selbst entscheiden, wann eine Geschichte beginnt, wie schnell sie erzählt wird und welche Stelle sie noch einmal hören möchte.
        Genau dafür gibt es hier eine Pause, Sprünge um dreißig Sekunden und ein Inhaltsverzeichnis. Jede Taste trägt einen Namen, den TalkBack vorlesen kann.
        Du kannst die Wiedergabe unterbrechen und später fortsetzen. Auch wenn du die App verlässt, läuft das vorbereitete Kapitel weiter. Die letzte Hörposition wird gespeichert.
        Diese Stimme dient zunächst zum Testen. Später vergleichen wir natürlichere Stimmen gemeinsam. Entscheidend ist, welche Stimme du auch nach einer halben Stunde noch gerne hörst.
    """.trimIndent()),
    Chapter("Unterwegs", 2, 2, """
        Am nächsten Morgen geht es früh los. Um acht Uhr dreißig fährt der Bus. Die Fahrkarte kostet drei Euro und fünfzig Cent.
        Auf dem Weg zum Bahnhof erzählt eine Freundin von einem neuen Buch. Es handelt von einem Garten, einem alten Haus und einem Brief, der erst nach vielen Jahren gefunden wird.
        Du kannst jetzt dreißig Sekunden zurückspringen und dir den Anfang dieses Kapitels noch einmal anhören. Oder du öffnest das Inhaltsverzeichnis und wählst den nächsten Abschnitt.
        Mit der Taste Sprachbefehl pausiert die Wiedergabe. Sage zum Beispiel: nächstes Kapitel. Die App zeigt an, welchen Befehl sie erkannt hat. Sie hört nur zu, nachdem du die Taste betätigt hast.
    """.trimIndent()),
    Chapter("Dein eigenes Dokument", 3, 3, """
        Über PDF öffnen kannst du ein eigenes Dokument auswählen. Wenn es gespeicherte Kapitelmarken enthält, erscheinen sie im Inhaltsverzeichnis.
        Ohne Kapitelmarken wird jede Seite zu einem eigenen Abschnitt. So findest du dich auch in einer Anleitung oder einem längeren Brief zurecht.
        Eingescannte Seiten brauchen eine Texterkennung. Diese Funktion folgt später. Die erste Version weist darauf hin, wenn sie keinen lesbaren Text findet.
        Probiere die Tasten aus und merke dir, was sich umständlich anfühlt. Diese Rückmeldungen bestimmen, wie die nächste Version aussieht.
    """.trimIndent())
), "Leseprobe zum Testen. Die lokale Systemstimme ist noch keine Entscheidung über die spätere Hörqualität.")
