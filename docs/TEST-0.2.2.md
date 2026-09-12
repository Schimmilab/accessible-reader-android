# Testprotokoll 0.2.2

Datum: 11. September 2026

## Fehler und Korrektur

Die gesprochenen Ansagen der App waren stumm, solange im Hintergrund Audio vorbereitet wurde. Betroffen waren „Inhaltsverzeichnis vorlesen“ und „Wo bin ich? Position vorlesen“.

Ursache: `AndroidSpeechProvider` benutzte eine einzige TextToSpeech-Instanz für zwei Aufgaben. Eine Instanz kann nicht gleichzeitig sprechen und eine Datei schreiben, deshalb hatte `say()` eine Sperre, die jede Ansage verwarf, solange eine Synthese lief. Bis 0.1.1 fiel das nicht auf, weil während der Aufbereitung alle Tasten gesperrt waren. Seit der fortlaufenden Aufbereitung in 0.2.0 sind die Tasten bedienbar, und damit wurde aus der Sperre ein sichtbarer Fehler: Die Nutzerin hätte eine Taste gedrückt und nichts gehört.

Korrektur: eine zweite TextToSpeech-Instanz nur für kurze Rückmeldungen. Sie ist von der Dateisynthese unabhängig und spricht in der gewählten Buchstimme.

Zusätzlich behoben: Bei laufendem TalkBack wurde die Positionsansage doppelt ausgegeben, einmal von TalkBack über die Live-Region und einmal von der App. Jetzt entscheidet `screenReaderActive()`, welcher der beiden spricht.

## Neu: Hörprobe je Stimme

Die Stimmen hießen bisher nur „Deutsch 1 · Deutschland“. Ohne Sicht waren sie damit nicht unterscheidbar. Jede Stimme hat jetzt in den Einstellungen eine Taste „Probe“, die bei allen Stimmen denselben Text spricht, mit Zahlen, Datum und Abkürzungen, wie es der Hörtest in [PLAN.md](PLAN.md) verlangt. Auswahl und Hörprobe sind zwei getrennte Fokusziele, damit TalkBack nicht in einer verschachtelten Schaltfläche hängen bleibt.

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64, Google-Play-Abbild.
- TalkBack 17.0.0.889642762, lokale deutsche Android-Stimme.
- App 0.2.2, Versionscode 5.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.

## Ergebnisse der automatischen Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Debug-App und Test-App bauen, Android Lint | bestanden, Lint ohne Fehler |
| Sechs Kernlogiktests (JVM) | bestanden |
| SpeechAudioTest, zwei Prüfungen, neu: Ansage startet während laufender Dateisynthese | bestanden |
| ProgressivePreparationTest, drei Prüfungen | bestanden |
| PlaybackFocusTest, fünf Prüfungen | bestanden |
| ReaderUiTest, MediaButtonTest, PdfImportTest, je zwei Prüfungen | bestanden |

Der neue Test ist eine echte Regressionsprüfung: Er startet eine lange Synthese, spricht parallel eine Ansage und verlangt, dass die Ansage beginnt, während die Synthese nachweislich noch läuft. Mit dem alten Code wäre er rot.

## Zusätzlich manuell zu prüfen

Ergänzend zur Checkliste in [TEST-0.2.md](TEST-0.2.md):

| Nr. | Aufgabe | Erwartung | Ergebnis |
| --- | --- | --- | --- |
| 13 | Während der Aufbereitung „Inhaltsverzeichnis“ öffnen und „Übersicht vorlesen“ | Liste wird in der Buchstimme gesprochen, nicht stumm | |
| 14 | Mit TalkBack „Wo bin ich? Position vorlesen“ | Position wird genau einmal angesagt, nicht doppelt | |
| 15 | In den Einstellungen je Stimme „Probe“ | Derselbe Text in der jeweiligen Stimme, Taste ist als „Hörprobe für …“ benannt | |

## Noch offen

- Der gesamte manuelle Durchgang auf einem echten Android-Gerät.
- Der Hörtest der Testnutzerin und die Stimmenbewertung, die die Hörprobe jetzt ermöglicht.
