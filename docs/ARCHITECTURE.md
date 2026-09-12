# Grundgerüst 0.4

Stand: 12. September 2026 (wählbare Sprachmaschine, Bibliothek, Selbstdiagnose, getrennte Ansagestimme)

## Zuständigkeiten

| Teil | Aufgabe |
| --- | --- |
| `core/ReaderModels.kt` | Dokument und Kapitel, Textaufteilung, deutsche Befehle, Zeitpositionen über Audiodateien hinweg |
| `core/Library.kt` | Bibliothekseintrag und seine gesprochene Beschriftung |
| `core/SpeechReport.kt` | Befund der Selbstdiagnose und dessen Textfassung |
| `data/DocumentStore.kt` | PDF-Import, Seiten und Lesezeichen, private lokale JSON-Dateien, Bibliotheksliste |
| `speech/SpeechProvider.kt` | Austauschbare Audioerzeugung, lokale Android-TTS-Implementierung, Cache |
| `speech/SpeechProbe.kt` | Prüft eine einzelne installierte Sprachmaschine für die Selbstdiagnose |
| `playback/ReaderPlaybackService.kt` | Media3-Wiedergabe, Audiofokus, Medienbenachrichtigung, Speichern während Hintergrundwiedergabe, Kapitel-Semantik für Medientasten (`ChapterPlayer`) |
| `ReaderViewModel.kt` | Import- und Wiedergabezustand, Vorbereitung eines Kapitels, Navigation |
| `MainActivity.kt` | Dateiauswahl, Teilen-Intents, Berechtigungen, lokale Spracherkennung |
| `ui/ReaderScreen.kt` | Compose-Oberfläche und TalkBack-Semantik |

## Weg vom PDF zum Audio

Die Dateiauswahl gibt einen URI frei. Der Import kopiert den Inhalt in eine temporäre Datei, begrenzt die Dateigröße und extrahiert den Text seitenweise mit PDFBox-Android. Die temporäre Originaldatei wird anschließend entfernt. Der extrahierte Text bleibt im privaten App-Speicher. Die App fordert keine pauschale Speicherfreigabe an.

Lesezeichen liefern Abschnittsanfänge. Ohne Lesezeichen werden Seiten zu Abschnitten. Die App rät keine Überschriften. Seiten ohne Text werden gemeldet, Passwortschutz und nicht erlaubte Textextraktion führen zu einer verständlichen Fehlermeldung.

Der Player teilt ein Kapitel in höchstens 1.000 UTF-16-Zeichen lange Stücke, vorzugsweise an Satz- oder Wortgrenzen. `SpeechProvider` erzeugt eine Audiodatei pro Stück. Android-TTS darf hier nur Stimmen verwenden, die keinen Netzwerkzugriff verlangen. Das erzeugte Audio wird auf eine positive Dauer geprüft und erst danach unter seinem endgültigen Cache-Namen abgelegt.

Die Wiedergabe beginnt, sobald ab der Startposition ein Mindestvorlauf von `MIN_LEAD_MS` (12 Sekunden) Audio bereitliegt oder alle Teile fertig sind. Da die Kapitelansage (Teil 0) nur rund drei Sekunden dauert, wird so vor dem Start immer mindestens der erste Textteil mitvorbereitet. Ohne diesen Vorlauf lief der Player die kurze Ansage leer, bevor der Textteil synthetisiert war, und erzeugte eine hörbare Pause. Die restlichen Teile werden im selben Job erzeugt und mit `addMediaItem` angehängt. `ReaderState.preparing` bleibt so lange gesetzt; `busy` endet mit dem Start der Wiedergabe, damit alle Tasten bedienbar bleiben. Während der Hintergrundaufbereitung wird der Status nicht aktualisiert, weil jede Live-Region-Ansage von TalkBack das Buch unterbrechen würde. Läuft der Player vor dem nächsten Teil leer, zählt das nicht als Kapitelende: Die Taste bleibt auf Pause und der neue Teil wird nach dem Anhängen abgespielt, sofern nicht bewusst pausiert wurde. Kapitel-, Stimmen- und Dokumentwechsel sowie das Leeren des Caches brechen die laufende Aufbereitung ab.

Teil 0 jedes Kapitels ist immer die gesprochene Kapitelansage aus `ChapterAnnouncement`. Sie ist ein normaler Audioteil in der Buchstimme, damit gespeicherte Teilindizes stabil bleiben und die Ansage auch ohne TalkBack, bei ausgeschaltetem Bildschirm und über Kopfhörer zu hören ist. Erreicht der Player das Ende eines vollständig vorbereiteten Kapitels bei aktivem `playWhenReady`, wählt `syncPlayer` das nächste Kapitel ohne Statusänderung (`chapter(index, announce = false)`, `play(quiet = true)`) und startet es. Im letzten Kapitel bleibt das bisherige Verhalten: Ende, Taste „Vorlesen“, Neustart von vorn.

Media3 erhält die vorbereiteten Dateien und ihre Metadaten. Die App berechnet Sprünge aus der Summe der tatsächlichen Audiodauern. Ein 30-Sekunden-Sprung ist unabhängig von Textlänge und Sprechtempo. Er meint 30 Sekunden auf der Original-Audiozeitleiste.

### Medientasten und Benachrichtigung

Die MediaSession bekommt nicht den ExoPlayer direkt, sondern `ChapterPlayer`, einen `ForwardingSimpleBasePlayer`. Für Medientasten, Bluetooth und die Benachrichtigung ist damit eine Playlist ein Kapitel: `seekBack`/`seekForward` rechnen mit `AudioTimeline` über alle Teile, „Weiter“ und „Zurück“ werden in `handleSeek` abgefangen. „Zurück“ folgt der Media3-Regel: mehr als drei Sekunden im Kapitel springt an den Kapitelanfang, sonst ins vorherige Kapitel. Der Dienst kennt keine Kapitel und kann kein Audio erzeugen; er sendet deshalb `COMMAND_NEXT_CHAPTER` bzw. `COMMAND_PREVIOUS_CHAPTER` als Custom Command an die verbundenen Controller, und das ViewModel führt `chapter()` aus. Damit „Weiter“ auch auf dem letzten Teil verfügbar bleibt, meldet `getState()` die Befehle immer und gibt nach außen `REPEAT_MODE_ALL` an; der ExoPlayer selbst wiederholt nicht. Die Benachrichtigung zeigt über `setMediaButtonPreferences` zusätzlich 30 Sekunden vor und zurück.

Folge: Kapitelwechsel per Medientaste und das automatische Weiterlesen setzen ein lebendes ViewModel voraus. Nach dem Entfernen der App aus der Übersicht läuft nur das aktuelle Kapitel zu Ende. Die saubere Lösung, Dokument und Audioerzeugung in den Dienst zu verlegen, ist eine spätere Ausbaustufe.

Ein leerer Puffer meldet denselben Playerzustand wie ein echtes Kapitelende. Der Dienst darf daraus also nicht auf „fertig" schließen, denn ein so markiertes Kapitel beginnt beim nächsten Start von vorn statt fortzusetzen. Das ViewModel setzt deshalb während der Aufbereitung das Kennzeichen `KEY_PREPARING` in den Einstellungen, und der Dienst schreibt `finished` nur, wenn es nicht gesetzt ist. Ein frisch erzeugtes ViewModel löscht das Kennzeichen, falls ein beendeter Prozess es stehen ließ.

Der Dienst speichert Dokument, Kapitel, Audiodatei, Offset und Stimme etwa einmal pro Sekunde. Er arbeitet auch weiter, wenn die Activity geschlossen wird. Nach einem Prozessneustart wird beim nächsten Start des Vorlesens der Cache aufgebaut oder wiedergefunden und die letzte Position wiederhergestellt.

## Bibliothek

Jedes importierte Dokument liegt als `<id>.json` mit dem vollständigen Text im privaten Speicher. Die Bibliothek liest diese Dateien nicht, sondern je eine kleine Begleitdatei `<id>.meta.json` mit Titel, Anzahl der Abschnitte und Zeitpunkt des Imports. Sonst müsste das Öffnen der Liste bei zehn Büchern zehn vollständige Texte einlesen, bis zu einer Million Zeichen je Dokument. Dokumente aus älteren Versionen haben noch keine Begleitdatei; sie wird beim ersten Auflisten einmalig nachgeschrieben.

Die Hörposition steht weiterhin in den Einstellungen unter `<id>.chapter` und Geschwistern. Ob ein Dokument schon gehört wurde, erkennt die App daran, dass der Dienst `<id>.voice` geschrieben hat. `libraryLabel` baut daraus die gesprochene Zeile: Titel, Anzahl der Abschnitte, und wo die Nutzerin aufgehört hat.

Entfernen löscht den extrahierten Text, die Begleitdatei und die Hörposition. Die PDF-Datei der Nutzerin bleibt unangetastet, die App hat sie nie besessen. Erzeugtes Audio bleibt im Cache, weil dieser nach Text und Stimme adressiert ist und nicht nach Dokument; er wird über „Erzeugtes Audio löschen“ oder die 500-MB-Grenze abgeräumt.

Weil Entfernen nicht rückgängig zu machen ist, fragt die App vorher nach. Die bestätigende Taste heißt „Ja, entfernen“ und nicht noch einmal „Entfernen“, damit sie sich beim Anhören von der Taste in der Liste unterscheidet.

## Sprache und Datenschutz

### TalkBack und Wiedergabezustand

`ReaderState.playing` beschreibt tatsächlich laufendes Audio. `playbackRequested` beschreibt dagegen, ob der Player auf Wunsch des Nutzers abspielen soll. Die Vorlesen/Pause-Taste und die Kapitelwechsel richten sich nach `playWhenReady`, außer im Leerlauf und am Kapitelende. Player-Ereignisse aktualisieren den Zustand sofort; die halbe Sekunde Abfrageintervall bleibt für die Zeitposition bestehen.

Media3 darf bei einer TalkBack-Ansage den Audiostrom vorübergehend anhalten. Dadurch darf sich die fokussierte Taste nicht zwischen „Vorlesen“ und „Pause“ ändern, sonst liest TalkBack den neuen Zustand erneut vor und unterbricht das Buch wieder. Es gibt keine selbst gebaute Wiederanlauf-Zeitsteuerung. Media3 verwaltet den Audiofokus weiterhin, einschließlich dauerhaften Fokusverlusts und bewusstem Pausieren während einer Ansage.

Grundlage: [Media3-Player-Ereignisse und Unterschied zwischen playWhenReady und isPlaying](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).

### Gesprochene Ansagen der App

`AndroidSpeechProvider` hält zwei TextToSpeech-Instanzen. Die erste schreibt Kapitelaudio in Dateien, die zweite spricht kurze Rückmeldungen. Eine einzelne Instanz kann nicht gleichzeitig sprechen und eine Datei schreiben; solange beide sich eine teilten, verschluckte die App jede Ansage, während im Hintergrund ein Kapitel vorbereitet wurde. Seit der fortlaufenden Aufbereitung sind die Tasten in dieser Zeit bedienbar, der Fehler wurde dadurch sichtbar.

Ob eine Ansage gesprochen oder nur in die Live-Region geschrieben wird, entscheidet `screenReaderActive()` über `AccessibilityManager.isTouchExplorationEnabled`. Läuft TalkBack, liest es den Statustext ohnehin vor; zusätzlich in der Buchstimme zu sprechen würde jede Meldung verdoppeln. „Inhaltsverzeichnis vorlesen“ und die Hörprobe sprechen dagegen immer in der Buchstimme, weil genau das ihr Zweck ist, und setzen keinen Status.

### Wahl der Sprachmaschine

Die App nimmt nicht die vom System voreingestellte Sprachmaschine, sondern eine gespeicherte Auswahl unter `engine`; ist keine gesetzt, gilt die Voreinstellung. Grund ist der Befund vom Zielgerät, siehe [Entscheidung 0003](decisions/0003-sprachmaschine-waehlbar.md): Die dort voreingestellte Maschine meldet über `getVoices()` keine einzige Stimme, obwohl sie fließend Deutsch spricht.

`AndroidSpeechProvider.voices()` fragt deshalb zweistufig. Liefert `getVoices()` deutsche Offline-Stimmen, werden diese benannt angeboten. Andernfalls entscheidet `isLanguageAvailable(Locale.GERMAN)`, und bei Verfügbarkeit erscheint ein einzelner Eintrag `ENGINE_DEFAULT_VOICE` mit der Beschriftung „Standardstimme dieser Sprachausgabe“. Beim Erzeugen wird dann `setLanguage` statt `setVoice` benutzt.

Der Cache-Schlüssel enthält die tatsächlich benutzte Maschine, nicht mehr die Systemvoreinstellung. Sonst würde nach einem Wechsel der Maschine das Audio der alten weiterverwendet.

`SpeechProbe` prüft für die Diagnose jede installierte Maschine einzeln, jeweils mit eigener kurzlebiger TextToSpeech-Instanz: Startet sie, kann sie Deutsch, welche Stimmen meldet sie, und schreibt sie Audio in eine Datei. Android ersetzt ein unbekanntes Maschinen-Paket stillschweigend durch die Voreinstellung, statt zu scheitern; deshalb kann eine gespeicherte Auswahl die App auch nach Deinstallation dieser Maschine nicht unbrauchbar machen.

### Spracheingabe und lokale Daten

`SpeechRecognizer.createOnDeviceSpeechRecognizer` wird ab Android 12 benutzt. Die Sprachbefehle werden lokal auf bekannte Aktionen abgebildet. Die Aufnahme beginnt nur auf Tastendruck und pausiert das Buch. Bei fehlenden Sprachdaten werden die beschrifteten Tasten angeboten. Es gibt keine Abhängigkeit von der systemweiten Voice-Access-App.

Die Haupt-App hat keine Internetberechtigung. Cloud-Anbieter sind ausschließlich als spätere Implementierungen der Anbieterschnittstelle vorgesehen. Vor deren Integration müssen Zustimmung, Kostenlimit und Zugriffsschlüssel separat umgesetzt werden.

## Bewusste Zwischenlösungen

JSON und private Einstellungen reichen für den ersten Reader. Room wird nötig, sobald Bibliothek, Suche und Lesezeichen hinzukommen. WorkManager und fortlaufende Audioaufbereitung folgen für große Dokumente. Die aktuelle Version spielt jeweils ein vorbereitetes Kapitel ab.

## Abhängigkeiten

- Android Gradle Plugin 9.4.0, Gradle 9.6.0 und Java 17. Der Upgrade-Assistent von Android Studio hat in `gradle.properties` Kompatibilitäts-Flags für das alte DSL-Verhalten gesetzt (`android.newDsl=false`, `android.builtInKotlin=false` u. a.); sie bleiben, bis das Projekt bewusst auf das neue DSL umgestellt wird.
- Kotlin 2.2.21, Compose BOM 2026.01.00, Activity 1.12.3 und Lifecycle 2.10.0.
- [Media3](https://developer.android.com/jetpack/androidx/releases/media3) 1.9.2 für Audio und MediaSession.
- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) 2.0.27.0 für den prototypischen Textimport.

PDFBox-Android hat einen älteren Release-Stand. Vor einer öffentlichen Version werden Parser und transitive Kryptografie-Abhängigkeiten erneut bewertet. Die Abhängigkeits- und App-Lizenzprüfung steht vor der Veröffentlichung noch aus. Es werden keine Stimmenmodelle mitgeliefert.
