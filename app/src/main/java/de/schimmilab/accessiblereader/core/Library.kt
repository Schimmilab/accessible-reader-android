package de.schimmilab.accessiblereader.core

/** What the store knows about an imported document without reading its text. */
data class LibraryEntry(val id: String, val title: String, val chapters: Int, val addedAt: Long)

/** One row of the library as the screen renders and TalkBack reads it. */
data class LibraryItem(val id: String, val title: String, val label: String, val current: Boolean)

/**
 * The spoken label of a library row. Everything a listener needs before opening it: what it is, how long,
 * and whether they left off somewhere.
 */
/**
 * Spoken when a document is opened. It has to say that the listening position was kept, because the screen
 * otherwise reads "noch nicht vorbereitet" with the jump buttons greyed out, and a listener reasonably
 * concludes the app forgot where they were.
 */
fun openedMessage(title: String, chapters: Int, savedChapter: Int, started: Boolean): String {
    val count = if (chapters == 1) "1 Abschnitt" else "$chapters Abschnitte"
    if (!started) return "$title geöffnet. $count."
    val chapter = (savedChapter + 1).coerceIn(1, maxOf(chapters, 1))
    return "$title geöffnet. $count. Zuletzt bei Abschnitt $chapter. Vorlesen setzt dort fort."
}

fun libraryLabel(entry: LibraryEntry, savedChapter: Int, started: Boolean): String {
    val count = if (entry.chapters == 1) "1 Abschnitt" else "${entry.chapters} Abschnitte"
    val chapter = (savedChapter + 1).coerceIn(1, maxOf(entry.chapters, 1))
    val progress = if (started) "zuletzt bei Abschnitt $chapter von ${entry.chapters}" else "noch nicht gehört"
    return "${entry.title}, $count, $progress."
}
