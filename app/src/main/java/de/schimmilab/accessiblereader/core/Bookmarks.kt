package de.schimmilab.accessiblereader.core

/**
 * A place in a book someone asked to come back to.
 *
 * It keeps the same three numbers the resume position keeps, so returning to a bookmark is the same machinery as
 * continuing after closing the app: the section, the part inside it, and the seconds inside that part. [voiceId]
 * is the cast the part was made with, so a bookmark set with one voice and returned to with another lands at the
 * start of its part instead of at a second that means a different word now.
 */
data class Bookmark(
    val chapter: Int,
    val item: Int,
    val offsetMs: Long,
    /** Where this is in the section, for saying it out loud. */
    val positionMs: Long,
    val voiceId: String,
    val label: String,
    val createdAt: Long,
)

/** Two bookmarks this close together are the same place, and the second one replaces the first. */
const val SAME_PLACE_MS = 20_000L

/** More than this and the list stops being something anyone can find their way through by ear. */
const val MOST_BOOKMARKS = 20

/**
 * Adds a bookmark, newest first.
 *
 * Setting one twice in the same passage is the commonest thing that happens — a listener presses the button,
 * is not sure it worked, presses again — so a new bookmark within [SAME_PLACE_MS] of an existing one in the same
 * section replaces it instead of filling the list with near-identical entries.
 */
fun addBookmark(existing: List<Bookmark>, bookmark: Bookmark): List<Bookmark> {
    val rest = existing.filterNot {
        it.chapter == bookmark.chapter && kotlin.math.abs(it.positionMs - bookmark.positionMs) < SAME_PLACE_MS
    }
    return (listOf(bookmark) + rest).take(MOST_BOOKMARKS)
}

/** What a bookmark is called in the list and in the spoken confirmation. */
fun bookmarkLabel(sectionTitle: String, positionMs: Long): String {
    val minutes = positionMs / 60_000
    val seconds = (positionMs / 1000) % 60
    val place = if (minutes > 0) "Minute $minutes" else "Sekunde $seconds"
    return "${sectionTitle.trim()}, $place"
}

/** Said out loud when a bookmark is set, because a screen reader never reads what changed by itself. */
fun bookmarkSetAnnouncement(label: String) = "Lesezeichen gesetzt: $label."

/** Said when the list is empty, so pressing the button never leads to silence. */
const val NO_BOOKMARKS = "Noch keine Lesezeichen. Mit „Stelle merken\" merkst du dir, wo du gerade bist."

fun bookmarksHeading(count: Int) = when (count) {
    0 -> "Lesezeichen"
    1 -> "Lesezeichen, eines"
    else -> "Lesezeichen, $count"
}
