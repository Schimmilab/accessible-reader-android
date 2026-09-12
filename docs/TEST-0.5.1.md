# Testprotokoll 0.5.1

Datum: 12. September 2026

## Was neu ist

Drei Befunde aus einer Runde Hörrückmeldung.

- **Wiederaufnahme aus der Bibliothek wird jetzt gesagt.** Das Fortsetzen hat schon vorher funktioniert, aber auf dem Bildschirm stand nur „noch nicht vorbereitet“ und die Sprungtasten waren aus. Wer nicht sieht, schließt daraus, die App habe die Stelle vergessen. Die Öffnen-Meldung nennt jetzt den Abschnitt, bei dem es weitergeht.
- **Ein Scan wird nach einer Stichprobe abgelehnt, nicht am Ende.** Vorher las die App ein 500-Seiten-Scan bis zur letzten Seite, bevor sie zugab, dass kein Text darin steht. Jetzt reichen 25 Seiten ohne ein einziges Zeichen, und die Meldung benennt die fehlende Texterkennung.
- **Drucksatz wird vorher aufgeräumt.** Ein 524-Seiten-Buch las seine Seitenzahlen mitten im Satz mit und zerbrach jedes getrennte Wort. Ursache waren weiche Trennzeichen und, in einem anderen Buch, 302-mal das alte Zeichen `¬` als Trennstrich.

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64.
- App 0.5.1, Versionscode 12, Release-Build.
- TalkBack für die automatischen Läufe abgeschaltet.

## Prüfung des Pakets

| Prüfung | Ergebnis |
| --- | --- |
| Signatur verifiziert | bestanden, Schema v2 und v3 |
| Zertifikat stimmt mit dem Keystore überein | bestanden, SHA-256 `8ec34b04…32c800d8` |
| `application-debuggable` | nicht vorhanden, wie es sein muss |
| Version im Paket | 0.5.1, Code 12 |
| Größe | 19,1 MB |

## Echte Bücher

Sechs Bücher wurden mit `RealBooksTest` durch den Importer geschickt. Der Lauf liegt im Log unter der Marke `ReaderBooks`.

| Buch | Seiten im PDF | Abschnitte | Zeichen | Importdauer |
| --- | --- | --- | --- | --- |
| Roman, kurz | 100 | 15 | 213.026 | 7,2 s |
| Sachbuch, groß | 524 | 60 | 827.204 | 13,7 s |
| Roman, lang | 458 | 458 | 669.970 | 17,7 s |
| Technikbuch | 272 | 272 | 481.720 | 4,6 s |
| Sachbuch | 194 | 194 | 690.714 | 13,4 s |
| Broschüre | 8 | 8 | 47.360 | 0,5 s |

Alle sechs lesbar, in keinem blieb ein Trennzeichen übrig. Zwei der Bücher hätten die alte Grenze von 300 Seiten nicht passiert.

## Automatische Tests

| Lauf | Ergebnis |
| --- | --- |
| JVM-Tests (`ReaderCoreTest`) | 16 von 16 bestanden |
| `PdfImportTest` | 4 von 4 bestanden |
| `LibraryResumeTest` | bestanden, beweist die Wiederaufnahme |
| `ReaderUiTest` | 2 von 2 bestanden |
| `RealBooksTest` | bestanden, 6 von 6 Büchern lesbar |
| Lint | ohne Fehler |

## Rauchtest des Release-Builds

Release-APK installiert, App gestartet, Oberfläche ausgelesen. Alle Bedienelemente vorhanden, kein Absturz im Protokoll.

## Was offen bleibt

- **Texterkennung für Scans fehlt.** Die meisten Bücher der Testnutzerin sind eingescannt. Der Weg ohne Cloud ist ML Kit auf dem Gerät, siehe unten.
- **Zweispaltige Seiten mischen ihre Spalten.** In der Broschüre steht mitten im Satz der Text der Nachbarspalte. Der Importer liest nach Position, aber ohne Spaltenerkennung.
- Kopfzeilen, die sich auf jeder Seite wiederholen, werden noch nicht erkannt. In den geprüften Büchern gab es keine, in Fachbüchern sind sie üblich.
- Wechsel des Abschnitts per Medientaste braucht die App im Hintergrund.
