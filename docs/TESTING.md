# Testen auf dem Entwicklungsrechner

Stand: 10. September 2026

## Ziel

Wir testen die App bereits auf dem Entwicklungsrechner als Audiooberflaeche. Ein sehender Entwickler kann damit viele Barrieren finden. Er kann die Erfahrung einer blinden Person aber nicht vollstaendig nachbilden. Die blinde Testnutzerin prueft deshalb jeden benutzbaren Meilenstein auf ihrem eigenen Smartphone.

## Aktueller Stand des Macs

Installiert sind Android Studio für Apple Silicon, SDK 36 und 37, Platform Tools und ein Pixel-8a-Emulator mit Android 17, API 37, Google Play und 16-KB-Seitengröße. Der Build verwendet das vorhandene Java-17-JDK. Ein echtes Android-Gerät ist noch nicht verbunden.

TalkBack ist im Emulator eingerichtet. Für Voice Access wurde ein englisches Offline-Sprachmodell installiert. Das bestätigt keine deutsche Spracheingabe. Die App prüft ihre eigene lokale Spracherkennung unabhängig von Voice Access. Das Weiterreichen des Mac-Mikrofons muss für einen vollständigen Sprachtest noch praktisch überprüft werden.

Android Studio ist fuer dieses Projekt die einfachste Arbeitsumgebung. Es bringt die Verwaltung von SDK, Emulatoren, Logausgabe und Bedienungstests zusammen.

## Einmalige Einrichtung

1. Android Studio installieren.
2. Das Android-SDK, Platform Tools und den Emulator installieren.
3. Ein virtuelles Pixel-Geraet mit einer Google-Play-Systemabbildung anlegen.
4. Im Emulator die Android Accessibility Suite und TalkBack aktualisieren.
5. Audioausgabe des Emulators ueber Lautsprecher oder Kopfhoerer pruefen.
6. In den erweiterten Emulator-Einstellungen die Nutzung des Mac-Mikrofons einschalten.
7. TalkBack aktivieren und einen schnellen Ein- und Ausschalter einrichten.

Eine Google-Play-Systemabbildung ist wichtig, weil reine AOSP-Abbilder nicht immer dieselben Google-Dienste und Accessibility-Komponenten enthalten wie ein verbreitetes Endgeraet.

## So wird ohne Sicht getestet

### Runde 1: TalkBack bei sichtbarem Bildschirm

Der Entwickler beobachtet Fokus, Beschriftungen und Reihenfolge. Geprueft wird:

- Wird jedes Bedienelement genau einmal angesagt?
- Sagt TalkBack Typ und Zustand korrekt an?
- Ist die Reihenfolge logisch?
- Gibt es unbenannte Symbole oder leere Fokusziele?
- Sind Aenderungen wie Pause, Kapitelwechsel und Kostenmodus hoerbar?
- Unterbricht oder ueberlagert TalkBack das Buchaudio unangenehm?

### Runde 2: Bildschirm nicht ansehen

Der Emulator bleibt geoeffnet, der Entwickler wendet den Blick jedoch ab oder deckt den Bildschirm ab. Die Aufgabe wird nur mit TalkBack, Tastatur und Ton erledigt.

Pflichtaufgaben:

1. App starten.
2. Ein Testdokument oeffnen.
3. Vorlesen beginnen.
4. 30 Sekunden zurueckspringen.
5. Zum naechsten Kapitel wechseln.
6. Inhaltsverzeichnis aufrufen.
7. Aktuelle Position ansagen lassen.
8. Ein Lesezeichen setzen.
9. App schliessen und an derselben Stelle fortsetzen.

Jede Sackgasse, unklare Ansage und unnoetige Wischbewegung wird als Fehler notiert.

### Runde 3: Externe Tastatur

TalkBack unterstuetzt eine externe Tastatur. Beim erweiterten Tastenlayout dient auf einer Mac-Tastatur die Befehlstaste als TalkBack-Taste. Damit lassen sich Fokusbewegung, Aktivierung, Ueberschriften und Medienwiedergabe ohne Maus testen.

Die im Emulator aktive TalkBack-Version ist massgeblich, weil Google die Tastenbelegung weiterentwickelt. Die eingebaute TalkBack-Tastaturhilfe zeigt die aktuelle Belegung.

### Runde 4: Spracheingabe

Vor jedem Sprachbefehl pausiert die App das Buchaudio. Der Emulator verwendet das Mikrofon des Macs. Geprueft werden:

- leise und normale Sprechlautstaerke,
- Hintergrundgeraeusche,
- Befehle waehrend TalkBack spricht,
- aehnliche Befehle wie "vor" und "vorlesen",
- unbekannte Befehle,
- fehlende Internetverbindung,
- sichere Wiederaufnahme der Wiedergabe.

Der Mikrofonzugriff ist im Android-Emulator standardmaessig ausgeschaltet. Er wird unter den erweiterten Einstellungen bei "Microphone" mit "Virtual microphone uses host audio input" aktiviert.

## Test auf einem echten Android-Smartphone

Der Emulator prueft die Logik, ersetzt aber kein echtes Geraet. Auf einem Smartphone unterscheiden sich TalkBack-Version, Herstelleroberflaeche, Akkumanagement, TTS-Dienste, Bluetooth und Mikrofonverhalten.

Der erste Prototyp wird als Debug-APK ueber USB oder WLAN installiert. Getestet wird mindestens auf:

- einem Google- oder weitgehend unveraenderten Android-Geraet,
- einem Samsung-Geraet, wenn eines verfuegbar ist,
- dem Smartphone der Testnutzerin.

Auf dem echten Geraet werden Bildschirmabschaltung, Kopfhoerer-Tasten, eingehende Benachrichtigungen, Telefonanrufe, Bluetooth-Unterbrechungen und laengere Laufzeiten geprueft.

## Automatische Tests

Jetpack Compose stellt fuer jede Oberflaeche einen Semantikbaum bereit. Tests koennen dadurch pruefen, ob ein Element als Schaltflaeche, Ueberschrift oder Status erkannt wird und ob eine passende Aktion vorhanden ist.

Fuer jede Kernfunktion entsteht mindestens ein Test:

- Play und Pause haben Beschriftung, Rolle und Zustand.
- Vor und Zurueck nennen die Sprungweite.
- Kapitel besitzen Ueberschriften-Semantik.
- Das Inhaltsverzeichnis hat eine feste Lesereihenfolge.
- Der Cloud-Modus nennt Kostenstatus und Limit.
- Fehler erscheinen als hoerbare Meldung und erhalten den Fokus.
- Keine Kernaktion ist nur ueber eine Geste erreichbar.

Zusaetzlich wird das Android Accessibility Test Framework aktiviert. Es erkennt unter anderem fehlende Beschriftungen, zu kleine Ziele, Kontrastprobleme und Teile der falschen Fokusreihenfolge.

## Hoertest fuer Stimmen

Alle Stimmen erhalten denselben Text und dieselben Vergleichsbedingungen. Dateinamen und gesprochene Einleitungen duerfen die Stimme nicht vorab positiv oder negativ bewerten.

Bewertungsskala von 1 bis 5:

- Natuerlichkeit
- Verstaendlichkeit
- Betonung
- Pausen
- Aussprache von Zahlen und Abkuerzungen
- Verhalten bei 1,25-facher und 1,5-facher Geschwindigkeit
- Anstrengung nach laengerem Hoeren

Die erste Auswahl erfolgt mit zwei bis fuenf Minuten langen Proben. Die Favoriten werden anschliessend mindestens 30 Minuten am Stueck gehoert.

## Grenzen des Tests durch Sehende

"Augen zu" ist ein Fehlerfindungsverfahren, keine Simulation von Blindheit. Sehende kennen den Bildschirmaufbau oft bereits und koennen sich Positionen merken. Blinde Menschen bringen ausserdem viel mehr Erfahrung mit TalkBack, Gesten, Braillezeilen und hohen Sprechgeschwindigkeiten mit.

Deshalb gilt:

- Wir beheben offensichtliche Fehler vor ihrem Test.
- Wir erklaeren ihr den Bedienweg nicht vorab, wenn wir dessen Auffindbarkeit pruefen.
- Wir beobachten nur mit ihrer Zustimmung.
- Ihre Rueckmeldung entscheidet bei widerspruechlichen Annahmen.

## Testprotokoll pro Version

Zu jeder Test-APK werden festgehalten:

- Versionsnummer und Commit
- Geraet, Android-Version und TalkBack-Version
- aktive Stimme und Geschwindigkeit
- getestetes Dokument
- bestandene und gescheiterte Aufgaben
- Zahl der noetigen Fokusbewegungen fuer Kernaufgaben
- Sprachfehler und Audioartefakte
- offene Fragen fuer die Testnutzerin

## Naechster technischer Schritt

Das Grundgerüst 0.3.0 spielt nach einem Mindestvorlauf, sagt Kapitel an, liest am Kapitelende weiter, bedient Kapitel über Medientasten, führt eine Bibliothek und kann seine eigene Sprachausgabe prüfen. Testnotizen stehen in [TEST-0.1.md](TEST-0.1.md), [TEST-0.1.1.md](TEST-0.1.1.md), [TEST-0.2.md](TEST-0.2.md), [TEST-0.2.1.md](TEST-0.2.1.md), [TEST-0.2.2.md](TEST-0.2.2.md) , [TEST-0.3.md](TEST-0.3.md) und [TEST-0.4.2.md](TEST-0.4.2.md).

Der nächste manuelle Durchgang prüft die Bedienung mit TalkBack ohne Blick auf den Bildschirm, echte Spracheingabe über das Mac-Mikrofon und anschließend die Bewertung der Testnutzerin auf einem Smartphone. Die Checkliste dafür steht in [TEST-0.2.md](TEST-0.2.md).

Hinweis für Gerätetests: Der Emulator erzeugt einen Audioteil in rund 30 Sekunden, also langsamer als die Wiedergabe. Tests, die ans Kapitelende springen, müssen zuerst auf das Ende der Aufbereitung warten. Ein Klick auf „Vorlesen“ öffnet bei frischer Installation erst den Benachrichtigungsdialog von Android 13 und neuer.

## TalkBack und automatische Tests schließen sich aus

⛔ Vor einem automatischen Testlauf muss TalkBack abgeschaltet sein.

Bei aktivem TalkBack liest es jede Änderung der Statuszeile vor und hält dafür den Audiofokus. Die Wiedergabetests warten dann korrekt, aber minutenlang, und laufen in ihre Zeitlimits. Gemessen am 12. September 2026: dieselben vier Prüfungen brauchen mit abgeschaltetem TalkBack 43 bis 79 Sekunden, mit aktivem TalkBack liefen zwei davon in die Zeitüberschreitung nach 240 Sekunden. Im Protokoll der Sprachausgabe klaffen dabei Lücken von 79 und 194 Sekunden, in denen die App gar keine Synthese anfordert.

Das ist kein Fehler, sondern genau das gewünschte Verhalten. Es macht die beiden Prüfarten nur unvereinbar.

```sh
# vor dem automatischen Lauf
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services null

# danach für den Handtest wieder einschalten
adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

Die Regel aus dem Abschnitt zur Regression bleibt damit bestehen und wird nur konkret: Während eines TalkBack-Durchgangs keine UI-Automation starten, und umgekehrt.

## Regression: TalkBack und Vorlesen

`PlaybackFocusTest` prüft mit echtem Media3-Player und lokalen deutschen Audiodaten:

- Vorübergehender Audiofokusverlust ändert die Pause-Taste nicht in eine Vorlesen-Taste.
- Nach Ende der Unterbrechung setzt der Player die Wiedergabe fort.
- Eine während der Unterbrechung gedrückte Pause bleibt erhalten.
- Dauerhafter Fokusverlust startet nach Freigabe nicht ungefragt wieder.
- Das Kapitelende bietet einen Neustart an.

Danach separat mit aktiviertem TalkBack testen: Vorlesen-Taste fokussieren, per Doppeltipp aktivieren, mindestens 20 Sekunden ohne weitere Eingabe zuhören, wieder per Doppeltipp pausieren und prüfen, dass die Pause erhalten bleibt. Die Taste muss während einer kurzen Menüansage „Pause“ bleiben. Ein normaler Compose-Klicktest ersetzt diesen Durchgang nicht. Während des Gestentests keine UI-Automation starten, die andere Bedienungshilfen unterdrückt.
