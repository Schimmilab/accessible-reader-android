package de.schimmilab.accessiblereader.core

data class VoiceInfo(val name: String, val locale: String, val networkRequired: Boolean, val quality: Int,
                    val features: List<String> = emptyList()) {
    /** Voice data that the engine names but has not downloaded. Such a voice cannot speak a word. */
    val dataMissing: Boolean get() = features.contains("notInstalled")

    /**
     * Whether the reader would put this voice in its list. This has to follow the same rule as
     * `AndroidSpeechProvider.voices`, or the report and the app contradict each other, which is what this line
     * was added to prevent. Since 0.7.0 a voice that needs the internet is offered too, marked as such.
     */
    val offeredByTheReader: Boolean get() = !dataMissing
}

/** What one installed speech engine can actually do. Probed one engine at a time, not just the default one. */
data class EngineReport(
    val packageName: String,
    val label: String,
    val isDefault: Boolean,
    val started: Boolean,
    val germanAvailability: String,
    val germanVoices: List<VoiceInfo>,
    val fileSynthesis: String,
)

data class SpeechReport(
    val appVersion: String,
    val device: String,
    val androidRelease: String,
    val sdk: Int,
    val screenReader: Boolean,
    val defaultEngine: String,
    val engines: List<EngineReport>,
)

/**
 * The legacy language check, which engines support even when they report no voices at all.
 * Codes come from TextToSpeech: 2, 1, 0 mean available, -1 missing data, -2 not supported.
 */
fun describeLanguageAvailability(code: Int): String = when {
    code >= 0 -> "verfügbar"
    code == -1 -> "Sprachdaten fehlen"
    else -> "nicht unterstützt"
}

/** Plain German, meant to be read aloud by a screen reader and to be pasted into a message. */
fun SpeechReport.format(): String = buildString {
    appendLine("Bericht zur Sprachausgabe")
    appendLine("App $appVersion")
    appendLine("Gerät $device, Android $androidRelease, API $sdk")
    appendLine("Screenreader aktiv: ${if (screenReader) "ja" else "nein"}")
    appendLine("Voreingestellte Sprachmaschine: ${defaultEngine.ifBlank { "unbekannt" }}")
    if (engines.isEmpty()) append("Keine Sprachmaschine gefunden.")
    engines.forEachIndexed { index, engine ->
        appendLine()
        appendLine("Sprachmaschine ${index + 1}: ${engine.label} (${engine.packageName})${if (engine.isDefault) " [Standard]" else ""}")
        appendLine("  Start: ${if (engine.started) "ok" else "lässt sich nicht starten"}")
        appendLine("  Deutsch: ${engine.germanAvailability}")
        appendLine("  Gemeldete deutsche Stimmen: ${engine.germanVoices.size}, davon offline: ${engine.germanVoices.count { !it.networkRequired }}")
        appendLine("  Davon bietet der Reader an: ${engine.germanVoices.count { it.offeredByTheReader }}")
        val missing = engine.germanVoices.count { it.dataMissing }
        if (missing > 0) appendLine("  $missing Stimmen sind nur angekündigt, ihre Sprachdaten fehlen. " +
            "Sie lassen sich in den Android-Einstellungen unter Sprachausgabe nachladen.")
        engine.germanVoices.forEach {
            val extra = if (it.features.isEmpty()) "" else ", Merkmale ${it.features.sorted().joinToString(" ")}"
            val verdict = when {
                it.dataMissing -> "im Reader nicht wählbar, Sprachdaten fehlen"
                it.networkRequired -> "im Reader wählbar, holt die Sprache aus dem Internet"
                else -> "im Reader wählbar"
            }
            appendLine("    ${it.name}, ${it.locale}, ${if (it.networkRequired) "braucht Netz" else "offline"}, " +
                "Qualität ${it.quality}, $verdict$extra")
        }
        append("  Audio in eine Datei schreiben: ${engine.fileSynthesis}")
        if (index < engines.lastIndex) appendLine()
    }
}
