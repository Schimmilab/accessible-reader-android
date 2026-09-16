package de.schimmilab.accessiblereader.core

import java.text.Normalizer
import java.util.Locale

/**
 * One place in the book where the searched words stand.
 *
 * [item] is the part of the section playback would start at, counted the way the player counts: part 0 is the
 * spoken section heading, so the first piece of text is part 1. That is what makes a hit reachable at all —
 * jumping to it is the same machinery as a bookmark or as continuing where the listener stopped.
 */
data class Hit(val chapter: Int, val chapterTitle: String, val item: Int, val context: String)

/** More than this and the spoken list stops being something anyone can hold in their head. */
const val MOST_HITS = 20
/** At most this many per section, so one chapter full of a common word cannot crowd out all the others. */
const val MOST_HITS_PER_SECTION = 3
/** How much of the sentence around a hit is read out, in characters. */
const val CONTEXT_CHARACTERS = 90

/**
 * Folds a German text down to what two spellings of the same word have in common.
 *
 * Umlauts are written both ways in the real world — "für" in the book, "fuer" from a keyboard — and speech
 * recognition produces one or the other depending on the engine. Without this, a search for "fuer" would find
 * nothing in a book that is full of the word.
 */
fun searchable(text: String): String = Normalizer.normalize(text.lowercase(Locale.GERMAN), Normalizer.Form.NFC)
    .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
    .replace(Regex("\\p{M}"), "")
    .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
    .trim()

/**
 * Where a piece of text starts inside [whole], in characters, for each part playback would be cut into.
 *
 * The parts are cut from the text itself and their edges are trimmed, so their lengths do not add up to the
 * length of the text. Each one is therefore looked up from where the last one ended, which also keeps a part
 * that happens to repeat itself from matching too early.
 */
fun partStarts(whole: String, parts: List<String>): List<Int> {
    val starts = mutableListOf<Int>()
    var from = 0
    for (part in parts) {
        val at = whole.indexOf(part, from).takeIf { it >= 0 } ?: from
        starts += at
        from = at + part.length
    }
    return starts
}

/**
 * Finds [query] in a document and says where each hit can be listened to.
 *
 * Searching is done on the folded text of both sides, so case, umlaut spelling and punctuation between the
 * words do not matter. What is read back is the real text around the hit, not the folded one.
 */
fun search(document: ReaderDocument, query: String, twoVoices: Boolean = false): List<Hit> {
    val needle = searchable(query)
    if (needle.isBlank()) return emptyList()
    val hits = mutableListOf<Hit>()

    document.chapters.forEachIndexed { index, chapter ->
        if (hits.size >= MOST_HITS) return@forEachIndexed
        val parts = if (twoVoices) Narration.partsForPlayback(chapter.text).map { it.text }
        else TextChunks.splitForPlayback(chapter.text)
        val starts = partStarts(chapter.text, parts)

        // Folded character by character, so a position in the folded text can be carried back to the real one.
        val folded = StringBuilder()
        val original = mutableListOf<Int>()
        var lastWasSpace = true
        chapter.text.forEachIndexed { at, char ->
            val piece = searchable(char.toString())
            if (piece.isBlank()) {
                if (!lastWasSpace) { folded.append(' '); original += at; lastWasSpace = true }
            } else {
                piece.forEach { folded.append(it); original += at }
                lastWasSpace = false
            }
        }

        var found = 0
        var from = 0
        while (found < MOST_HITS_PER_SECTION && hits.size < MOST_HITS) {
            val at = folded.indexOf(needle, from)
            if (at < 0) break
            val real = original.getOrElse(at) { 0 }
            val part = starts.indexOfLast { it <= real }.coerceAtLeast(0)
            hits += Hit(index, chapter.title, part + 1, context(chapter.text, real, needle.length))
            found++
            from = at + needle.length
        }
    }
    return hits
}

/** The sentence around a hit, cut at word boundaries, short enough to be read out in a list. */
private fun context(text: String, at: Int, length: Int): String {
    val before = (at - CONTEXT_CHARACTERS / 2).coerceAtLeast(0)
    val after = (at + length + CONTEXT_CHARACTERS / 2).coerceAtMost(text.length)
    var snippet = text.substring(before, after).replace("\n", " ").replace(Regex(" +"), " ")
    if (before > 0) snippet = snippet.substringAfter(' ', snippet).let { "… $it" }
    if (after < text.length) snippet = snippet.substringBeforeLast(' ', snippet).let { "$it …" }
    return snippet.trim()
}

fun searchResultAnnouncement(query: String, hits: List<Hit>): String = when (hits.size) {
    0 -> "Keine Fundstelle für $query."
    1 -> "Eine Fundstelle für $query: ${hits.first().chapterTitle}."
    MOST_HITS -> "Mehr als ${MOST_HITS - 1} Fundstellen für $query. Die ersten $MOST_HITS stehen in der Liste."
    else -> "${hits.size} Fundstellen für $query."
}

/** What a hit is called in the list, section first so the listener knows where it would take them. */
fun hitLabel(hit: Hit): String = "${hit.chapterTitle}: ${hit.context}"
