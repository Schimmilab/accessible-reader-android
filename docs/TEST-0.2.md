# Testprotokoll 0.2.0

Datum: 11. September 2026

## Änderungen gegenüber 0.1.1

1. Vorlesen beginnt nach dem ersten Audioteil. Die restlichen Teile des Kapitels werden während des Hörens vorbereitet und angehängt, ohne Statusansagen für TalkBack.
2. Jedes Kapitel beginnt mit einer gesprochenen Ansage in der Buchstimme. Am Kapitelende liest die App im nächsten Kapitel weiter, ohne Eingabe und ohne TalkBack-Ansage. Das letzte Kapitel bleibt stehen und bietet den Neustart an.
3. Kopfhörer, Bluetooth und Medienbenachrichtigung: „Weiter“ wechselt das Kapitel, „Zurück“ springt an den Kapitelanfang und danach ins vorherige Kapitel. Die Benachrichtigung hat Tasten für 30 Sekunden vor und zurück, die über alle Audioteile rechnen.

Commits: bdf91e0 (Build-Upgrade), fe35dae (Punkt 1 und 2), 4fe1732 (Punkt 3), dazu der Versionscommit.

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64, Google-Play-Abbild.
- TalkBack 17.0.0.889642762, Google-Sprachausgabe 20260511.02, lokale deutsche Stimme.
- App 0.2.0, Versionscode 3, Leseprobe mit drei Abschnitten bei einfachem Tempo.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.

## Ergebnisse der automatischen Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Debug-App und Test-App bauen, Android Lint | bestanden, Lint ohne Fehler |
| Sechs Kernlogiktests (JVM), darunter neu die Kapitelansage | bestanden |
| PlaybackFocusTest, fünf Prüfungen, neu: Weiterlesen ohne Statusansage, Neustart nur im letzten Kapitel | bestanden |
| ProgressivePreparationTest, zwei Prüfungen: Wiedergabe startet vor Ende der Aufbereitung, Kapitelwechsel bricht sie ab | bestanden, rund 3 Minuten wegen langsamer Emulator-Synthese |
| MediaButtonTest, zwei Prüfungen über einen zweiten MediaController wie bei Kopfhörern | bestanden |
| ReaderUiTest, zwei Prüfungen | bestanden |
| SpeechAudioTest, eine Prüfung | bestanden, deutsche Offline-Stimme vorhanden |
| PdfImportTest, zwei Prüfungen | erster Lauf fehlgeschlagen, ohne verwertbare Ausgabe, weil der folgende Lauf die Ergebnisdatei überschrieb; Wiederholung allein bestanden. Ursache nicht ermittelt, der Importcode wurde in 0.2.0 nicht geändert. Bei erneutem Auftreten beobachten |

Der Emulator erzeugt einen Audioteil in rund 30 Sekunden und ist damit langsamer als die Wiedergabe. Der Aufbereitungstest hat deshalb vermutlich auch den Fall durchlaufen, dass der Player vor dem nächsten Teil leerläuft und nach dem Anhängen weiterspielt. Auf einem echten Gerät ist die Synthese deutlich schneller als Echtzeit.

## Manueller Durchgang mit TalkBack (offen)

Ohne Blick auf den Bildschirm, nur TalkBack, Ton und Doppeltipp. Kein ADB, keine UI-Automation während dieses Durchgangs.

| Nr. | Aufgabe | Erwartung | Ergebnis |
| --- | --- | --- | --- |
| 1 | App starten, „Leseprobe“ aktivieren | Titel und drei Abschnitte werden angesagt | |
| 2 | „Vorlesen“ per Doppeltipp | Innerhalb weniger Sekunden beginnt „Abschnitt 1 von 3: Ankommen.“, dann der Text | |
| 3 | 20 Sekunden zuhören, dann Menü-Ansage durch TalkBack auslösen (Fokus bewegen) | Buch setzt nach der Ansage fort, Taste bleibt „Pause“ | |
| 4 | „Pause“ per Doppeltipp, TalkBack-Ansage abwarten | Bleibt pausiert, Taste zeigt „Vorlesen“ | |
| 5 | „30 Sekunden zurück“ | Ansage der Sprungweite, Wiedergabe springt korrekt, auch über die Kapitelansage hinweg | |
| 6 | Bis zum Ende von „Ankommen“ hören | „Abschnitt 2 von 3: Unterwegs.“ folgt ohne Eingabe und ohne TalkBack-Ansage | |
| 7 | Bildschirm ausschalten, Kopfhörer: Weiter-Taste bzw. Doppelklick | Nächstes Kapitel wird angesagt und gespielt | |
| 8 | Kopfhörer: Zurück-Taste zweimal kurz nacheinander | Erst Kapitelanfang, dann vorheriges Kapitel | |
| 9 | Benachrichtigung öffnen | Fünf Tasten: Zurück, 30 s zurück, Pause, 30 s vor, Weiter, jede mit TalkBack benannt | |
| 10 | Letztes Kapitel zu Ende hören | Wiedergabe endet, Taste „Vorlesen“, erneutes Aktivieren startet das Kapitel von vorn | |
| 11 | App schließen und neu öffnen, „Vorlesen“ | Fortsetzung an der letzten Stelle | |
| 12 | App aus der Übersicht entfernen, während sie spielt | Aktuelles Kapitel spielt zu Ende, kein Weiterlesen (bekannte Grenze) | |

Jede Sackgasse, unklare Ansage und unnötige Wischbewegung wird notiert.

## Noch offen

- Der manuelle TalkBack-Durchgang oben und die Spracheingabe über das Mac-Mikrofon.
- Ein echtes Android-Gerät. Dort zusätzlich: Bluetooth-Kopfhörer, Bildschirmabschaltung, Telefonanruf, Akkumanagement.
- Der Hörtest der Testnutzerin. Erst danach lohnen Stimmenvergleich und OCR.
- Bekannte Grenze: Nach dem Entfernen der App aus der Übersicht laufen Kapitelwechsel per Medientaste und automatisches Weiterlesen nicht mehr, weil beides im ViewModel liegt. Dokument und Audioerzeugung in den Dienst zu verlegen ist der nächste größere Umbau.
