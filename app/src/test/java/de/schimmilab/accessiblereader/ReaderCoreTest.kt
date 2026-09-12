package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.*
import org.junit.Assert.*
import org.junit.Test

class ReaderCoreTest {
    @Test fun thirtySecondsCrossAudioFileBoundary() {
        val durations = listOf(12_000L, 25_000L, 50_000L)
        val now = AudioTimeline.absolute(durations, 1, 8_000L)
        assertEquals(AudioPosition(2, 13_000L), AudioTimeline.locate(durations, now + 30_000))
        assertEquals(AudioPosition(0, 0L), AudioTimeline.locate(durations, now - 30_000))
    }
    @Test fun exactEndAndEmptyAudioAreBounded() {
        assertEquals(AudioPosition(1, 20L), AudioTimeline.locate(listOf(10L, 20L), 1000))
        assertEquals(AudioPosition(1, 0L), AudioTimeline.locate(listOf(10L, 20L), 10))
        assertEquals(AudioPosition(0, 0L), AudioTimeline.locate(emptyList(), -10))
    }
    @Test fun germanCommandsHandleUmlautsAndDoNotGuess() {
        assertEquals(ReaderCommand.Seek(-30), CommandParser.parse("Dreißig Sekunden zurück!"))
        assertEquals(ReaderCommand.Next, CommandParser.parse("Nächstes Kapitel"))
        assertEquals(ReaderCommand.Play, CommandParser.parse("Vorlesen"))
        assertEquals(ReaderCommand.GoTo(2), CommandParser.parse("Gehe zu Kapitel 2"))
        assertNull(CommandParser.parse("nicht vorlesen"))
        assertNull(CommandParser.parse("Kapitel 999999999999999999"))
        assertNull(CommandParser.parse("lösche alles"))
    }
    @Test fun chunkingPreservesWordsAndSurrogatePairs() {
        val input = "Hallo Welt. " + "Ein langer Satz mit Zahlen 12,50 und Namen. ".repeat(100)
        val chunks = TextChunks.split(input, 100)
        assertTrue(chunks.all { it.length <= 100 && it.isNotBlank() })
        assertEquals(input.trim(), chunks.joinToString(" "))
        val unicode = TextChunks.split("😀".repeat(70), 11)
        assertEquals("😀".repeat(70), unicode.joinToString(""))
        assertTrue(unicode.all { !it.last().isHighSurrogate() })
    }
    @Test fun openingSaysThatTheListeningPositionWasKept() {
        // Without this the screen reads "noch nicht vorbereitet" and a listener concludes the app forgot.
        assertEquals("Der Garten geöffnet. 12 Abschnitte.", openedMessage("Der Garten", 12, 0, started = false))
        assertEquals("Der Garten geöffnet. 12 Abschnitte. Zuletzt bei Abschnitt 4. Vorlesen setzt dort fort.",
            openedMessage("Der Garten", 12, 3, started = true))
        assertEquals("Ein Brief geöffnet. 1 Abschnitt.", openedMessage("Ein Brief", 1, 0, started = false))
        assertEquals("Der Garten geöffnet. 12 Abschnitte. Zuletzt bei Abschnitt 12. Vorlesen setzt dort fort.",
            openedMessage("Der Garten", 12, 99, started = true))
    }
    @Test fun libraryLabelSaysWhatItIsHowLongAndWhereYouLeftOff() {
        val entry = LibraryEntry("abc", "Der Garten", 12, 0)
        assertEquals("Der Garten, 12 Abschnitte, noch nicht gehört.", libraryLabel(entry, 0, started = false))
        assertEquals("Der Garten, 12 Abschnitte, zuletzt bei Abschnitt 4 von 12.", libraryLabel(entry, 3, started = true))
        assertEquals("Ein Brief, 1 Abschnitt, noch nicht gehört.", libraryLabel(LibraryEntry("d", "Ein Brief", 1, 0), 0, false))
        // A stale saved chapter from an older import must not produce a label beyond the document.
        assertEquals("Der Garten, 12 Abschnitte, zuletzt bei Abschnitt 12 von 12.", libraryLabel(entry, 99, started = true))
    }
    @Test fun libraryIsReachableByVoice() {
        assertEquals(ReaderCommand.Library, CommandParser.parse("Bibliothek"))
        assertEquals(ReaderCommand.Library, CommandParser.parse("Meine Bücher"))
        assertEquals(ReaderCommand.Contents, CommandParser.parse("Inhaltsverzeichnis"))
        assertNull(CommandParser.parse("Bibliothek löschen"))
    }
    @Test fun chapterAnnouncementStaysShortForPageChapters() {
        assertEquals("Abschnitt 2 von 3: Unterwegs.", ChapterAnnouncement.text(1, 3, " Unterwegs "))
        assertEquals("Seite 7.", ChapterAnnouncement.text(6, 40, "Seite 7"))
        assertEquals("Abschnitt 1 von 1: Seite 7 und 8.", ChapterAnnouncement.text(0, 1, "Seite 7 und 8"))
    }
    @Test fun languageAvailabilityIsTranslatedFromTheLegacyCodes() {
        // The codes engines answer with when they report no voices at all.
        assertEquals("verfügbar", describeLanguageAvailability(2))
        assertEquals("verfügbar", describeLanguageAvailability(0))
        assertEquals("Sprachdaten fehlen", describeLanguageAvailability(-1))
        assertEquals("nicht unterstützt", describeLanguageAvailability(-2))
    }
    @Test fun speechReportComparesEveryInstalledEngine() {
        // Shaped after the real report from the target device: the default engine names no voice at all.
        val report = SpeechReport(appVersion = "0.4.0", device = "samsung SM-S931B", androidRelease = "16", sdk = 36,
            screenReader = false, defaultEngine = "es.codefactory.vocalizertts",
            engines = listOf(
                EngineReport("es.codefactory.vocalizertts", "Vocalizer TTS", isDefault = true, started = true,
                    germanAvailability = "verfügbar", germanVoices = emptyList(), fileSynthesis = "funktioniert, 1840 Millisekunden, 86 Kilobyte"),
                EngineReport("com.google.android.tts", "Google", isDefault = false, started = true,
                    germanAvailability = "verfügbar",
                    germanVoices = listOf(VoiceInfo("de-DE-local", "de_DE", false, 400), VoiceInfo("de-DE-network", "de_DE", true, 500)),
                    fileSynthesis = "funktioniert, 1500 Millisekunden, 70 Kilobyte"))).format()
        assertTrue(report.contains("Sprachmaschine 1: Vocalizer TTS (es.codefactory.vocalizertts) [Standard]"))
        assertTrue("An engine without named voices must still be reported as usable",
            report.contains("Gemeldete deutsche Stimmen: 0, davon offline: 0"))
        assertTrue(report.contains("Sprachmaschine 2: Google (com.google.android.tts)"))
        assertFalse("Only one engine is the default", report.contains("Google (com.google.android.tts) [Standard]"))
        assertTrue(report.contains("Gemeldete deutsche Stimmen: 2, davon offline: 1"))
        assertTrue(report.contains("de-DE-network, de_DE, braucht Netz"))
    }
    @Test fun speechReportStaysReadableWithoutAnyEngine() {
        val report = SpeechReport("0.4.0", "x", "16", 36, false, "", emptyList()).format()
        assertTrue(report.contains("Voreingestellte Sprachmaschine: unbekannt"))
        assertTrue(report.contains("Keine Sprachmaschine gefunden."))
    }
    @Test fun cleanupRemovesLineWrapHyphenButPreservesCompound() {
        assertEquals("Vorlesen\nAudio-\nPlayer", TextChunks.clean("Vor-\nlesen\nAudio-\nPlayer"))
    }
    @Test fun aSoftHyphenAtALineEndIsAWrappedWordNotASpokenOne() {
        // Print typesetting uses U+00AD, and a 500-page book carries thousands of them.
        assertEquals("verstehen konnten", TextChunks.clean("verste\u00ad\nhen konnten"))
        assertEquals("Rolle setzt", TextChunks.clean("Rolle\u00ad setzt"))
    }
    @Test fun theOldGermanHyphenSignIsTreatedAsAHyphen() {
        // One book in the test set carries 302 of these, all at a line end, none as a logical operator.
        assertEquals("desinfizierende Wirkung", TextChunks.clean("desinfizie\u00ac\nrende Wirkung"))
        assertEquals("Signal A\u00ac\nNicht B", TextChunks.clean("Signal A\u00ac\nNicht B"))
    }
    @Test fun aPageNumberOnItsOwnLineIsNotReadOut() {
        // It sits at the foot of every page and lands in the middle of a sentence when the pages are joined.
        assertEquals("attraktive Frauen und", TextChunks.clean("attraktive Frauen und\n20"))
        assertEquals("Erstes Kapitel", TextChunks.clean("xiv\nErstes Kapitel"))
        assertEquals("Im Jahr 1984", TextChunks.clean("Im Jahr 1984"))
        // A page that holds nothing else keeps its number, and a year inside a line is never a page number.
        assertEquals("20", TextChunks.clean("20"))
        assertEquals("Kapitel 20\nEin Satz", TextChunks.clean("Kapitel 20\nEin Satz"))
    }
    @Test fun aWordSplitByAPageBreakIsPutBackTogether() {
        assertEquals("Es ist ganz schön chaotisch.",
            TextChunks.joinPages(listOf(TextChunks.clean("Es ist ganz schön chao\u00ad\n21"), TextChunks.clean("tisch.\n22"))))
        assertEquals("Vorlesen", TextChunks.joinPages(listOf("Vor-", "lesen")))
        // A compound hyphen before a capital stays, and separate pages stay separate paragraphs.
        assertEquals("Audio-\n\nPlayer", TextChunks.joinPages(listOf("Audio-", "Player")))
        assertEquals("Erster Satz.\n\nZweiter Satz.", TextChunks.joinPages(listOf("Erster Satz.", "  ", "Zweiter Satz.")))
    }
}
