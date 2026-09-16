package de.schimmilab.accessiblereader.core

/** What the reader should do about someone falling asleep over the book. */
enum class SleepOption(val minutes: Int) {
    OFF(0),
    AFTER_15(15),
    AFTER_30(30),
    AFTER_45(45),
    AFTER_60(60),
    /** Stop where the section ends, however long that takes. A chapter is a better place to stop than a minute. */
    END_OF_SECTION(-1);

    val isTimed: Boolean get() = minutes > 0
}

/** How long the sound is faded before the reader stops, so nobody is startled awake by the silence. */
const val SLEEP_FADE_MS = 20_000L

fun sleepLabel(option: SleepOption): String = when (option) {
    SleepOption.OFF -> "Aus"
    SleepOption.END_OF_SECTION -> "Am Ende des Abschnitts"
    else -> "Nach ${option.minutes} Minuten"
}

/** Said out loud when it is set, because a screen reader never reads what changed by itself. */
fun sleepAnnouncement(option: SleepOption): String = when (option) {
    SleepOption.OFF -> "Einschlaftimer aus. Der Reader liest weiter."
    SleepOption.END_OF_SECTION -> "Einschlaftimer: Der Reader hört am Ende dieses Abschnitts auf."
    else -> "Einschlaftimer: ${option.minutes} Minuten. Danach hört der Reader auf."
}

/** What the timer button says, so its remaining time can be heard without opening anything. */
fun sleepButtonLabel(option: SleepOption, remainingMs: Long): String = when {
    option == SleepOption.OFF -> "Einschlaftimer"
    option == SleepOption.END_OF_SECTION -> "Einschlaftimer, Ende des Abschnitts"
    remainingMs <= 0 -> "Einschlaftimer"
    remainingMs < 60_000 -> "Einschlaftimer, noch ${remainingMs / 1000} Sekunden"
    else -> "Einschlaftimer, noch ${(remainingMs + 59_999) / 60_000} Minuten"
}

/**
 * How loud to play with [remainingMs] left.
 *
 * Full volume until the last [SLEEP_FADE_MS], then down to silence. Cutting the sound off mid-sentence wakes the
 * person it was meant to let fall asleep.
 */
fun sleepVolume(remainingMs: Long, fadeMs: Long = SLEEP_FADE_MS): Float = when {
    remainingMs >= fadeMs -> 1f
    remainingMs <= 0L -> 0f
    else -> (remainingMs.toFloat() / fadeMs).coerceIn(0f, 1f)
}
