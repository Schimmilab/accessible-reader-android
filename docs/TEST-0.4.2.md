# Testprotokoll 0.4.2

Datum: 12. September 2026

## Was neu ist

1. Die Einfuhrgrenzen sind auf Buchgröße gebracht. Vorher 40 MB, 300 Seiten und eine Million Zeichen, jetzt 120 MB, 3.000 Seiten und sechs Millionen Zeichen. Anlass war der Versuch der Testnutzerin, einen Roman zu laden, der mit dem Hinweis auf 300 Seiten abgewiesen wurde.
2. Erzeugtes Audio wird bei 500 MB automatisch aufgeräumt, das am längsten nicht Gehörte zuerst. Vorher verweigerte die Wiedergabe an dieser Grenze den Dienst und bat, den Cache von Hand zu leeren. Ein Buch dieser Länge erzeugt ein Vielfaches davon; Die Nutzerin wäre mitten im Buch stehengeblieben.

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64, Google-Play-Abbild.
- App 0.4.2, Versionscode 10.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.
- ⛔ TalkBack für den automatischen Lauf abgeschaltet, siehe unten.

## Ergebnisse

| Prüfung | Ergebnis |
| --- | --- |
| Debug-App und Test-App bauen, Android Lint | bestanden, Lint ohne Fehler |
| Elf Kernlogiktests (JVM) | bestanden |
| PdfImportTest, drei Prüfungen, neu: Import eines erzeugten 600-Seiten-PDF | bestanden, Import in 4,0 Sekunden, 600 Abschnitte |
| SpeechAudioTest, sechs Prüfungen, neu: Cache räumt das am längsten nicht Gehörte weg, und lässt einen Cache im Budget in Ruhe | bestanden |
| ProgressivePreparationTest, ResumePositionTest, PlaybackFocusTest, ReaderUiTest, MediaButtonTest, LibraryTest, LibraryUiTest, DiagnosticsTest | bestanden |

Summe: 30 Gerätetests in zehn Klassen, keine übersprungen.

## Der Befund, der am meisten Zeit gekostet hat

Zwei Prüfungen aus `ProgressivePreparationTest` liefen wiederholt in ihre Zeitlimits. Der Verdacht lag zuerst beim knappen Arbeitsspeicher des Macs, dann bei den eigenen Änderungen. Beides war falsch.

Das Protokoll der Sprachausgabe zeigte Lücken von 79 und 194 Sekunden, in denen die App überhaupt keine Synthese anforderte. Ursache: Auf dem Emulator war **TalkBack aktiv**. Es liest jede Änderung der Statuszeile vor und hält dafür den Audiofokus; die Wiedergabe wartet dann korrekt, nur eben minutenlang.

Mit abgeschaltetem TalkBack brauchen dieselben vier Prüfungen 43 bis 79 Sekunden statt über 250. Die Regel steht jetzt in [TESTING.md](TESTING.md) samt den beiden Befehlen zum Ab- und Anschalten.

🎯 Die Lehre: Der automatische Test und der TalkBack-Handtest schließen sich aus. Das stand als Warnung schon im Protokoll zu 0.1.1, war aber nicht als Voraussetzung für jeden Lauf formuliert.

## Noch offen

- TalkBack ist auf dem Emulator derzeit abgeschaltet. Vor dem nächsten Handtest wieder einschalten.
- Der manuelle Durchgang auf einem echten Gerät. Die Testnutzerin benutzt den Reader inzwischen täglich, ein förmliches Protokoll gibt es dafür nicht.
- Ihre Stimmenwahl, siehe [VOICES.md](VOICES.md).
