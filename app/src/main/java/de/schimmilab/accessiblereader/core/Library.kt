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

/** What the app knows about the connection. Nothing here reaches the network; it only reads its state. */
enum class NetworkKind { NONE, METERED, UNMETERED }

/** What the listener allows for voices that fetch their audio from the internet. */
enum class OnlineVoicePolicy { NEVER, WIFI_ONLY, ALWAYS }

/**
 * Whether a voice that needs the internet may be used right now. The app itself never goes online: the speech
 * engine fetches the audio in its own process, which is why this needs no internet permission. What it must not
 * do is spend someone's mobile data without being told to.
 */
fun mayUseOnlineVoice(policy: OnlineVoicePolicy, network: NetworkKind): Boolean = when (policy) {
    OnlineVoicePolicy.NEVER -> false
    OnlineVoicePolicy.WIFI_ONLY -> network == NetworkKind.UNMETERED
    OnlineVoicePolicy.ALWAYS -> network != NetworkKind.NONE
}

/** Why an online voice cannot be used right now, in words a listener can act on. */
fun onlineVoiceRefusal(policy: OnlineVoicePolicy, network: NetworkKind): String = when {
    network == NetworkKind.NONE ->
        "Diese Stimme holt ihre Sprache aus dem Internet, und das Gerät ist gerade nicht verbunden. " +
            "Bitte eine Stimme wählen, die offline arbeitet."
    policy == OnlineVoicePolicy.NEVER ->
        "Diese Stimme holt ihre Sprache aus dem Internet. In den Einstellungen sind Online-Stimmen ausgeschaltet."
    else ->
        "Diese Stimme holt ihre Sprache aus dem Internet, und das Gerät ist im Mobilfunknetz. " +
            "In den Einstellungen kannst du Online-Stimmen auch für mobile Daten erlauben."
}

/** How the voice list describes a voice, so nobody picks an online voice without knowing it is one. */
fun voiceLabel(index: Int, country: String, needsNetwork: Boolean): String {
    val place = country.ifBlank { "lokal" }
    // Says what the voice does, not how it sounds. 0.10.4 called these "meist natürlicher", on the assumption
    // that a voice fetched from the internet is the better one. The listener tried them and reported the
    // opposite: on her phone the online voices of the Google engine are the older ones and sound worse than
    // the offline voices already installed. An app that recommends by assumption sends someone down a path
    // that costs them their data and their evening, so it says what it knows instead.
    return if (needsNetwork) "Deutsch ${index + 1} · $place · braucht Internet"
    else "Deutsch ${index + 1} · $place"
}

/**
 * Roughly how fast a voice speaks. A German word runs to about six and a half characters with its space, which
 * is close enough for a number meant to help someone choose between two voices.
 *
 * This is the unit the listener herself used: she asked for a voice to be taken down to eighty words a minute.
 */
fun wordsPerMinute(characters: Int, audioMs: Long): Int {
    if (characters <= 0 || audioMs <= 0) return 0
    return Math.round(characters / 6.5 / (audioMs / 60_000.0)).toInt()
}

/**
 * How far into the book someone is, in words rather than in a number they have to picture.
 *
 * A listener has no scroll bar and no thumb in the pages. "Abschnitt 3 von 15" says nothing about how much book
 * is left when the sections differ in length, which they do, so this counts characters and rounds hard: nobody
 * needs to hear a percentage to the digit, and a false precision would only sound like the app knows more than
 * it does.
 */
fun bookProgressLabel(fraction: Float): String {
    val percent = (fraction.coerceIn(0f, 1f) * 100).toInt()
    val how = when {
        percent < 3 -> return "Du bist ganz am Anfang des Buches."
        percent > 97 -> return "Du bist fast am Ende des Buches."
        percent in 23..27 -> "ein Viertel"
        percent in 31..35 -> "ein Drittel"
        percent in 48..52 -> "die Hälfte"
        percent in 64..69 -> "zwei Drittel"
        percent in 73..77 -> "drei Viertel"
        else -> "${(percent / 5) * 5} Prozent"
    }
    return "Du hast etwa $how des Buches gehört."
}

/**
 * Roughly how much listening is left, or null while the app has not yet measured how fast this voice speaks.
 *
 * An estimate is worth having here — "noch etwa drei Stunden" is what someone decides an evening by — but only
 * an honest one, so it is named as an estimate, rounded to half hours above an hour, and left out entirely
 * rather than guessed at a rate nobody has measured.
 */
fun remainingTimeLabel(charactersLeft: Int, wordsPerMinute: Int): String? {
    if (charactersLeft <= 0 || wordsPerMinute <= 0) return null
    val minutes = (charactersLeft / 6.5 / wordsPerMinute).toInt()
    if (minutes < 1) return null
    if (minutes < 60) return "Noch etwa $minutes Minuten."
    val hours = minutes / 60
    val rest = ((minutes % 60) / 5) * 5      // rounded to five: the estimate is not worth a single minute
    val hoursSaid = if (hours == 1) "eine Stunde" else "$hours Stunden"
    return if (rest == 0) "Noch etwa $hoursSaid." else "Noch etwa $hoursSaid und $rest Minuten."
}

/**
 * What to tell a listener about a voice, once the app has measured it while reading.
 *
 * Two things matter and neither can be heard from a short sample. How fast it speaks: measured on one emulator,
 * a local neural voice read at about 205 words a minute where the stock voice read at 132, which is why a
 * listener had to take that voice down to follow it. And whether it can keep ahead of playback, [ratio] being
 * the time the engine needs divided by the length of the audio it produces, so 0.03 is thirty times faster than
 * listening and 1.0 is exactly as slow. That second one decides whether a book runs through or keeps stopping.
 */
fun voiceSpeedNote(ratio: Double, wordsPerMinute: Int): String {
    val rate = if (wordsPerMinute > 0) "Diese Stimme spricht etwa $wordsPerMinute Wörter je Minute." else null
    val keepsUp = when {
        ratio < 0.5 -> null
        ratio < 0.9 -> "Zum Erzeugen braucht sie etwa die halbe Hörzeit, der Anfang kommt also etwas später."
        else -> "Zum Erzeugen braucht sie ungefähr so lange, wie das Zuhören dauert. Der Anfang kommt später, " +
            "und bei langen Abschnitten kann es kurze Pausen geben. Eine andere Stimme liest flüssiger."
    }
    return listOfNotNull(rate, keepsUp).joinToString(" ")
}


/**
 * What to say when a voice took longer than it was given.
 *
 * The old message was "Bitte eine andere Stimme wählen oder es noch einmal versuchen", and the listener it was
 * written for answered: "Ich glaube, ich bin blöd." Which other one? Trying again with the same voice is the
 * one thing that cannot help. So this names the voices that have kept up so far — the app has measured every
 * voice it has ever read with — and says where to find them.
 */
fun slowVoiceMessage(seconds: Long, faster: List<String>): String {
    val what = "Diese Stimme hat für ein Stück Text länger als $seconds Sekunden gebraucht und ist für dieses " +
        "Buch zu langsam."
    val how = when {
        faster.isEmpty() -> "Unter „Stimme und Einstellungen“ kannst du eine andere wählen. Dort steht bei " +
            "jeder Stimme, die schon einmal gelesen hat, wie schnell sie ist."
        faster.size == 1 -> "Gut mitgekommen ist bisher: ${faster.first()}. Du findest sie unter " +
            "„Stimme und Einstellungen“."
        else -> "Gut mitgekommen sind bisher: ${faster.take(3).joinToString(", ")}. Du findest sie unter " +
            "„Stimme und Einstellungen“."
    }
    return "$what $how"
}

/** A voice that needs less than half the listening time to produce its audio keeps up comfortably. */
const val KEEPS_UP_RATIO = 0.5

/**
 * How much one press changes the reading speed. A quarter was too coarse to be usable: from normal speed the
 * next step down was noticeably slow and the next one up noticeably fast, with nothing in between, and a
 * listener working the buttons through a screen reader has no way to land on what she wants.
 */
const val SPEED_STEP = 0.1f
const val SPEED_MIN = 0.5f
const val SPEED_MAX = 2.0f

/** The speed as German decimal, without trailing noise from floating point arithmetic. */
fun speedLabel(speed: Float): String = String.format(java.util.Locale.GERMAN, "%.1f", speed)

/** Spoken after a press, because a screen reader keeps its focus on the button and never reads the new value. */
fun speedAnnouncement(speed: Float): String = when {
    speed <= SPEED_MIN -> "Geschwindigkeit ${speedLabel(speed)} fach, langsamste Stufe."
    speed >= SPEED_MAX -> "Geschwindigkeit ${speedLabel(speed)} fach, schnellste Stufe."
    speed == 1f -> "Geschwindigkeit normal."
    else -> "Geschwindigkeit ${speedLabel(speed)} fach."
}
