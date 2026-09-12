package de.schimmilab.accessiblereader.core

import java.text.Normalizer
import java.util.Locale

data class Chapter(val title: String, val firstPage: Int, val lastPage: Int, val text: String)
data class ReaderDocument(val id: String, val title: String, val chapters: List<Chapter>, val notice: String = "")
data class AudioPosition(val item: Int, val offsetMs: Long)

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

object TextChunks {
    fun clean(text: String): String = text.replace("\u00ad", "")
        .replace(Regex("(\\p{L})-\\r?\\n(\\p{Ll})"), "$1$2")
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex(" *\\r?\\n *"), "\n")
        .replace(Regex("\n{3,}"), "\n\n").trim()

    // Limit UTF-16 length as required by Android TTS; never split a surrogate pair.
    fun split(text: String, limit: Int = 1000): List<String> {
        require(limit >= 4)
        val result = mutableListOf<String>()
        var rest = text.trim()
        while (rest.isNotEmpty()) {
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
    private val page = Regex("Seite \\d+")
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
