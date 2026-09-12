# Testprotokoll 0.6.0

Datum: 12. September 2026

## Was neu ist

**Eingescannte Bücher werden gelesen.** Eine Seite ohne Textebene wird gerendert und durch die Texterkennung geschickt. Alles auf dem Gerät, das Erkennungsmodell liegt im Paket. Die Begründung und die Messungen stehen in [Entscheidung 0004](decisions/0004-texterkennung-auf-dem-geraet.md).

**Die Stimmenliste sagt, zu welcher Sprachmaschine sie gehört.** Ein Diagnosebericht nannte 17 deutsche Stimmen für die eine Sprachmaschine, während die App die vier einer anderen anbot, und nichts auf dem Bildschirm unterschied die beiden. Die Überschrift heißt jetzt zum Beispiel „Stimmen von Google: 5".

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64.
- App 0.6.0, Versionscode 13, Release-Build.
- TalkBack für die automatischen Läufe abgeschaltet.
- **WLAN und Mobilfunk am Gerät abgeschaltet** für alle Läufe zur Texterkennung.

## Prüfung des Pakets

| Prüfung | Ergebnis |
| --- | --- |
| Signatur verifiziert | bestanden, Schema v2 und v3 |
| Zertifikat stimmt mit dem Keystore überein | bestanden, SHA-256 `8ec34b04…32c800d8` |
| `INTERNET` im Paket | nicht vorhanden, obwohl die Erkennungsbibliothek sie mitbringt |
| `ACCESS_NETWORK_STATE` im Paket | nicht vorhanden |
| `application-debuggable` | nicht vorhanden |
| Version im Paket | 0.6.0, Code 13 |
| Größe | 60,2 MB gegenüber 19,1 MB bei 0.5.1 |

Die Größe ist der Preis für das Erkennungsmodell und die Bibliotheken für alle vier Prozessorarchitekturen. Wird sie zum Problem, sind getrennte Pakete je Architektur der nächste Schritt.

## Texterkennung

| Messung | Wert |
| --- | --- |
| pro Seite, rendern und erkennen | 1180 ms |
| 30-seitiger Scan, vollständiger Import | 28 Sekunden |
| erkannte Zeichen auf 10 Seiten | 16.949 |
| hochgerechnet auf 500 Seiten | etwa 10 Minuten, einmalig beim Import |

Der Renderfaktor wurde gemessen statt geschätzt. Bei 764, 900, 1000 und 1200 Pixeln Seitenbreite lieferte die Erkennung praktisch denselben Text, während die Zeit je Seite stieg. Gerendert wird deshalb mit dem Nötigen.

Eine erste Messung hatte 251 ms je Seite ergeben. Sie ließ sich nicht wiederholen und ist verworfen.

## Echte Bücher

Sieben Dateien durch den Importer, darunter erstmals ein echter Scan.

| Datei | Seiten | Abschnitte | Zeichen | Dauer |
| --- | --- | --- | --- | --- |
| Scan, Bilder ohne jede Textebene | 30 | 30 | 48.384 | 28,4 s |
| Sachbuch, groß | 524 | 60 | 827.334 | 44,6 s |
| Roman, lang | 458 | 458 | 670.006 | 36,5 s |
| Sachbuch | 194 | 194 | 691.163 | 60,6 s |
| Technikbuch | 272 | 272 | 481.720 | 20,1 s |
| Roman, kurz | 100 | 15 | 213.084 | 25,6 s |
| Broschüre | 8 | 8 | 47.360 | 1,7 s |

Sieben von sieben lesbar. Der Scan wäre in 0.5.1 noch abgelehnt worden.

## Automatische Tests

| Lauf | Ergebnis |
| --- | --- |
| JVM-Tests (`ReaderCoreTest`) | 17 von 17 bestanden |
| `PdfImportTest` | 5 von 5, darunter eine Seite, deren Wörter nur als Pixel existieren |
| `OcrSpeedTest` | bestanden |
| `RealBooksTest` | bestanden, 7 von 7 |
| `VoiceListTest` | bestanden, App und Diagnose melden dieselben Stimmen |
| `ReaderUiTest` | 2 von 2 |
| `LibraryUiTest`, `LibraryResumeTest` | bestanden |
| `DiagnosticsTest` | 3 von 3 |
| `MediaButtonTest` | 2 von 2 |
| `PlaybackFocusTest` | 5 von 5 |
| `ProgressivePreparationTest` | 4 von 4 im zweiten Lauf |
| `SpeechAudioTest` | 6 von 6 im zweiten Lauf |
| Lint | ohne Fehler |

Zwei Klassen fielen im ersten Lauf durch und im zweiten nicht: einmal eine Zeitüberschreitung der Sprachausgabe nach 90 Sekunden, einmal eine übersprungene Annahme. Beides betraf die Sprachmaschine des Emulators nach dem Umschalten des Netzes, nicht den geänderten Code. Es steht hier, weil ein verschwiegener Fehlschlag beim nächsten Mal Zeit kostet.

## Rauchtest des Release-Builds

Release-APK installiert, Gerät ohne Netz, App gestartet, Oberfläche ausgelesen. Alle Bedienelemente vorhanden, kein Absturz im Protokoll.

## Was offen bleibt

- **Zweispaltige Seiten mischen ihre Spalten.** Gilt für Textseiten wie für Scans. Der Importer liest nach Position, aber ohne Spaltenerkennung.
- Kopfzeilen, die sich auf jeder Seite wiederholen, werden nicht erkannt.
- Ein 500-Seiten-Scan braucht beim Import rund zehn Minuten. Der Fortschritt wird angesagt, der Import lässt sich abbrechen.
- Wechsel des Abschnitts per Medientaste braucht die App im Hintergrund.
- Die Oberfläche ist deutsch. Mehrsprachigkeit ist der nächste Punkt.
