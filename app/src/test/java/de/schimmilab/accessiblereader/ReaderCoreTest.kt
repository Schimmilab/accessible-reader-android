package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.*
import de.schimmilab.accessiblereader.speech.AndroidSpeechProvider
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
    @Test fun thePositionLineSaysWhatToDoInsteadOfWhatIsMissing() {
        // "noch nicht vorbereitet" reads as something in progress. A listener waited four minutes for it to
        // finish instead of pressing the button that starts it.
        assertEquals("0:00 / Vorlesen drücken", positionLabel("0:00", "0:00", 0, preparing = false))
        assertEquals("Hörposition 0:00. Für diesen Abschnitt ist noch kein Audio da. Vorlesen drücken, dann wird es erzeugt.",
            positionAnnouncement("0:00", "0:00", 0, preparing = false))
        assertEquals("0:12 / 1:40 bisher, wird noch vorbereitet", positionLabel("0:12", "1:40", 100_000, preparing = true))
        assertEquals("1:00 / 4:00", positionLabel("1:00", "4:00", 240_000, preparing = false))
        assertEquals("Hörposition 1:00. Kapitel enthält 4:00.", positionAnnouncement("1:00", "4:00", 240_000, false))
    }
    @Test fun theReportSaysWhichVoicesTheAppCanActuallyUse() {
        // One device reported thirteen offline voices while the app offered five, and nothing said why.
        val installed = VoiceInfo("de-de-x-star02-local", "de_DE", false, 400)
        val notInstalled = VoiceInfo("de-de-x-star04-local", "de_DE", false, 400, listOf("notInstalled"))
        val network = VoiceInfo("de-de-x-deb-network", "de_DE", true, 400)
        assertTrue(installed.offeredByTheReader)
        assertFalse("A voice whose data is missing cannot be used", notInstalled.offeredByTheReader)
        assertTrue("Since 0.7.0 an online voice is offered, marked as such", network.offeredByTheReader)

        val report = SpeechReport("0.6.2", "samsung SM-S931B", "16", 36, false, "com.google.android.tts",
            listOf(EngineReport("com.google.android.tts", "Google", true, true, "verfügbar",
                listOf(installed, notInstalled, network), "funktioniert"))).format()
        assertTrue(report.contains("Gemeldete deutsche Stimmen: 3, davon offline: 2"))
        assertTrue(report.contains("Davon bietet der Reader an: 2"))
        assertTrue(report.contains("im Reader nicht wählbar, Sprachdaten fehlen"))
        assertTrue("The report has to say what to do about missing voice data",
            report.contains("1 Stimmen sind nur angekündigt"))
        assertTrue(report.contains("im Reader wählbar, holt die Sprache aus dem Internet"))
    }
    @Test fun anOnlineVoiceIsOnlyUsedWhenTheConnectionAllowsIt() {
        assertTrue(mayUseOnlineVoice(OnlineVoicePolicy.WIFI_ONLY, NetworkKind.UNMETERED))
        assertFalse("Mobile data is not spent without being told to",
            mayUseOnlineVoice(OnlineVoicePolicy.WIFI_ONLY, NetworkKind.METERED))
        assertFalse(mayUseOnlineVoice(OnlineVoicePolicy.WIFI_ONLY, NetworkKind.NONE))
        assertTrue(mayUseOnlineVoice(OnlineVoicePolicy.ALWAYS, NetworkKind.METERED))
        assertFalse("Nothing works without a connection", mayUseOnlineVoice(OnlineVoicePolicy.ALWAYS, NetworkKind.NONE))
        assertFalse(mayUseOnlineVoice(OnlineVoicePolicy.NEVER, NetworkKind.UNMETERED))
    }
    @Test fun theRefusalSaysWhatToDoAboutIt() {
        assertTrue(onlineVoiceRefusal(OnlineVoicePolicy.WIFI_ONLY, NetworkKind.NONE).contains("nicht verbunden"))
        assertTrue(onlineVoiceRefusal(OnlineVoicePolicy.WIFI_ONLY, NetworkKind.NONE).contains("offline arbeitet"))
        assertTrue(onlineVoiceRefusal(OnlineVoicePolicy.WIFI_ONLY, NetworkKind.METERED).contains("mobile Daten erlauben"))
        assertTrue(onlineVoiceRefusal(OnlineVoicePolicy.NEVER, NetworkKind.UNMETERED).contains("ausgeschaltet"))
    }
    @Test fun theVoiceListSaysWhichVoicesNeedTheInternet() {
        assertEquals("Deutsch 1 · Deutschland", voiceLabel(0, "Deutschland", needsNetwork = false))
        assertEquals("Deutsch 3 · Deutschland · braucht Internet", voiceLabel(2, "Deutschland", needsNetwork = true))
        assertEquals("Deutsch 2 · lokal", voiceLabel(1, "", needsNetwork = false))
    }
    @Test fun libraryLabelSaysWhatItIsHowLongAndWhereYouLeftOff() {
        val entry = LibraryEntry("abc", "Der Garten", 12, 0)
        assertEquals("Der Garten, 12 Abschnitte, noch nicht gehört.", libraryLabel(entry, 0, started = false))
        assertEquals("Der Garten, 12 Abschnitte, zuletzt bei Abschnitt 4 von 12.", libraryLabel(entry, 3, started = true))
        assertEquals("Ein Brief, 1 Abschnitt, noch nicht gehört.", libraryLabel(LibraryEntry("d", "Ein Brief", 1, 0), 0, false))
        // A stale saved chapter from an older import must not produce a label beyond the document.
        assertEquals("Der Garten, 12 Abschnitte, zuletzt bei Abschnitt 12 von 12.", libraryLabel(entry, 99, started = true))
    }
    @Test fun aVoiceThatCannotKeepUpSaysSo() {
        // Measured on an emulator: the stock Google voice needs about 0.04 of the listening time, the local
        // neural one between 0.6 and 1.2. The second kind decides whether a book plays through.
        assertNull("A voice far ahead of listening needs no comment", voiceSpeedNote(0.04))
        assertNull(voiceSpeedNote(0.49))
        assertTrue(voiceSpeedNote(0.65)!!.contains("halbe Hörzeit"))
        val slow = voiceSpeedNote(1.05)!!
        assertTrue(slow.contains("so lange, wie das Zuhören dauert"))
        assertTrue("It has to name the way out", slow.contains("andere Stimme"))
    }
    @Test fun theVoiceListSaysWhichEngineItBelongsTo() {
        assertEquals("Stimmen von Google: 5", voicesHeading("Google", 5))
        assertEquals("Stimmen von Vocalizer TTS: 1", voicesHeading("Vocalizer TTS", 1))
        assertEquals("Stimmen von Samsung TTS: keine", voicesHeading("Samsung TTS", 0))
        assertEquals("Stimmen von dieser Sprachausgabe: 3", voicesHeading(null, 3))
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
        assertEquals("Seiten 7 bis 12.", ChapterAnnouncement.text(1, 40, "Seiten 7 bis 12"))
        assertEquals("Abschnitt 1 von 1: Seite 7 und 8.", ChapterAnnouncement.text(0, 1, "Seite 7 und 8"))
    }
    @Test fun aBookWithoutBookmarksGetsSectionsWorthListeningTo() {
        // One section per page stopped playback for 8 to 12 seconds at every page break, measured on the emulator.
        val pages = List(40) { 1_500 }
        val starts = groupPagesIntoSections(pages)
        assertEquals("40 pages of 1500 characters should become about six sections", 6, starts.size)
        assertEquals(0, starts.first())
        assertTrue("Sections must not start twice on the same page", starts == starts.distinct())
        assertTrue("Sections have to stay in order", starts == starts.sorted())

        // A single long page stands alone rather than being split.
        assertEquals(listOf(0, 1, 2), groupPagesIntoSections(listOf(30_000, 30_000, 30_000)))
        // Blank pages never start a section of their own.
        assertEquals(listOf(0), groupPagesIntoSections(listOf(0, 0, 0, 500)))
        assertEquals(emptyList<Int>(), groupPagesIntoSections(emptyList()))
        assertEquals("Seite 4", pageRangeTitle(4, 4))
        assertEquals("Seiten 4 bis 9", pageRangeTitle(4, 9))
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
    @Test fun theFirstPiecesAreShortSoTheFirstSoundArrivesQuickly() {
        // A local neural voice needs about as long as the audio itself. With one size for every piece a listener
        // waited over a minute before the first word.
        val text = "Ein Satz mit ordentlicher Länge, der sich immer wiederholt und dabei Text erzeugt. ".repeat(60)
        val pieces = TextChunks.splitForPlayback(text)
        assertTrue("First piece was ${pieces[0].length} characters", pieces[0].length <= 250)
        assertTrue("Second piece was ${pieces[1].length} characters", pieces[1].length in 251..500)
        assertTrue("Later pieces should be full size", pieces[3].length > 500)
        assertEquals("No text may be lost", text.trim(), pieces.joinToString(" "))
        // A short chapter still produces exactly one piece.
        assertEquals(listOf("Kurzer Abschnitt."), TextChunks.splitForPlayback("Kurzer Abschnitt."))
    }
    @Test fun theSynthesisBudgetFollowsTheLengthOfTheText() {
        // Measured worst case on an emulator: 115 ms per character while also speaking an announcement.
        assertEquals(60_000L, AndroidSpeechProvider.synthesisBudgetMs(0))
        assertEquals(60_000L, AndroidSpeechProvider.synthesisBudgetMs(200))
        assertEquals(75_000L, AndroidSpeechProvider.synthesisBudgetMs(250))
        assertEquals(300_000L, AndroidSpeechProvider.synthesisBudgetMs(1000))
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
