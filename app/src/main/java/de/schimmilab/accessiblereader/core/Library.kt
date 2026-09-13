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

/**
 * Names the engine whose voices are listed. One device reported 17 German voices for its Google engine while
 * the app offered the four of another engine, and the screen gave no way to tell the two apart.
 */
fun voicesHeading(engineLabel: String?, count: Int): String {
    val engine = engineLabel?.takeIf { it.isNotBlank() } ?: "dieser Sprachausgabe"
    if (count == 0) return "Stimmen von $engine: keine"
    return if (count == 1) "Stimmen von $engine: 1" else "Stimmen von $engine: $count"
}

/**
 * The position line when no audio exists yet. "Noch nicht vorbereitet" reads as something the app is busy with,
 * and a listener waited four minutes for it to finish instead of pressing the button that starts it. Say what to
 * do, not what is missing.
 */
fun positionLabel(position: String, duration: String, durationMs: Long, preparing: Boolean): String =
    "$position / " + when {
        durationMs <= 0 -> "Vorlesen drücken"
        preparing -> "$duration bisher, wird noch vorbereitet"
        else -> duration
    }

/** The same line for a screen reader, which gets the whole sentence rather than the shorthand. */
fun positionAnnouncement(position: String, duration: String, durationMs: Long, preparing: Boolean): String =
    "Hörposition $position. " + when {
        durationMs <= 0 -> "Für diesen Abschnitt ist noch kein Audio da. Vorlesen drücken, dann wird es erzeugt"
        preparing -> "Bisher $duration vorbereitet, weitere Teile folgen"
        else -> "Kapitel enthält $duration"
    } + "."
