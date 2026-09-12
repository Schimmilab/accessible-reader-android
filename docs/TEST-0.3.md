# Testprotokoll 0.3.0

Datum: 12. September 2026

## Was neu ist

Die Bibliothek. Bisher kannte die App nur das zuletzt geöffnete Dokument. Ein früher importiertes PDF war nicht mehr erreichbar, obwohl sein Text weiterhin im privaten Speicher lag. Die MVP-Liste in [PLAN.md](PLAN.md) verlangt ausdrücklich eine vollständig mit TalkBack bedienbare Bibliothek.

- Alle importierten Dokumente werden aufgelistet, neueste zuerst.
- Jede Zeile nennt Titel, Anzahl der Abschnitte und wo die Nutzerin aufgehört hat.
- Ein Dokument lässt sich öffnen und nach Rückfrage entfernen.
- Erreichbar über die Taste „Bibliothek“ und über den Sprachbefehl „Bibliothek“ oder „Meine Bücher“.

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64, Google-Play-Abbild.
- App 0.3.0, Versionscode 7.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.

## Ergebnisse der automatischen Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Debug-App und Test-App bauen, Android Lint | bestanden, Lint ohne Fehler |
| Zehn Kernlogiktests (JVM), neu: Beschriftung einer Bibliothekszeile, Sprachbefehl | bestanden |
| LibraryTest, drei Prüfungen: Reihenfolge, Entfernen, Altbestand ohne Begleitdatei, leere Bibliothek | bestanden |
| LibraryUiTest, eine Prüfung: benannte Zeilen, Rückfrage vor dem Entfernen, Abbrechen behält das Dokument | bestanden |
| ProgressivePreparationTest, PlaybackFocusTest, ReaderUiTest, MediaButtonTest, PdfImportTest, SpeechAudioTest | bestanden |

Summe: 21 Gerätetests in acht Klassen, keine übersprungen.

## Ein Befund aus dem Oberflächentest

Der erste Durchlauf von `LibraryUiTest` scheiterte, weil zwei Tasten gleichzeitig „Entfernen“ hießen, eine in der Liste und eine im Bestätigungsdialog. Für eine sehende Person ist das eindeutig, weil die Dialoge übereinander liegen. Beim Anhören ist es das nicht. Die bestätigende Taste heißt jetzt „Ja, entfernen“. Der Test wurde nicht angepasst, sondern die Oberfläche.

## Zusätzlich manuell zu prüfen

Ergänzend zu den Checklisten in [TEST-0.2.md](TEST-0.2.md) und [TEST-0.2.2.md](TEST-0.2.2.md):

| Nr. | Aufgabe | Erwartung | Ergebnis |
| --- | --- | --- | --- |
| 16 | Zwei PDFs nacheinander importieren, dann „Bibliothek“ | Beide erscheinen, das zuletzt importierte oben | |
| 17 | Erstes Dokument ein Stück hören, Bibliothek öffnen | Die Zeile nennt den zuletzt gehörten Abschnitt | |
| 18 | Aus der Bibliothek das andere Dokument öffnen | Wechsel ohne Neustart, Wiedergabe beginnt beim Vorlesen von vorn | |
| 19 | Ein Dokument entfernen, im Dialog „Abbrechen“ | Dokument bleibt in der Liste | |
| 20 | Dasselbe entfernen und mit „Ja, entfernen“ bestätigen | Verschwindet aus der Liste, Hörposition ist weg, die PDF-Datei auf dem Gerät existiert weiter | |
| 21 | Das gerade gehörte Dokument entfernen | App fällt auf die Leseprobe zurück, ohne Absturz | |
| 22 | Sprachbefehl „Bibliothek“ | Bibliothek öffnet sich | |

## Noch offen

- Der gesamte manuelle Durchgang auf einem echten Android-Gerät.
- Der Diagnosebericht vom Zielgerät. Er entscheidet, ob die Audioerzeugung so bleiben kann.
- Der Hörtest der Testnutzerin, siehe [VOICES.md](VOICES.md).
