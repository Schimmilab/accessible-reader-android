# Testprotokoll 0.1.0

Datum: 10. September 2026

## Umgebung

Mac mini M1, Pixel-8a-Emulator mit Android 17 und API 37, ARM64 und 16-KB-Seitengröße. Build mit Java 17, Gradle 8.13 und Android SDK 36.

Der Emulator musste kalt mit `-gpu host` gestartet werden. Mit der zuvor automatisch gewählten Softwaredarstellung reagierten ADB und das Gerät zeitweise sehr langsam. Der Kaltstart hat installierte Pakete und Daten erhalten.

## Erfolgreiche Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Debug-APK bauen | erfolgreich |
| Android Lint | keine Fehler, verbleibende Hinweise auf Bibliotheksversionen, Abhängigkeiten und Stil |
| Kernlogik, fünf JUnit-Tests | bestanden |
| Text-PDF mit zwei Seiten importieren und wieder laden | bestanden |
| PDF ohne Text als noch nicht unterstützten OCR-Fall melden | bestanden |
| Inhaltsverzeichnis öffnen und zweiten Abschnitt auswählen | bestanden |
| Beschriftete Play- und Sprungtasten im Compose-Semantikbaum finden | bestanden |
| Deutsches Offline-Audio erzeugen und unverändert aus dem Cache wiederverwenden | bestanden |
| Reale Wiedergabe starten, pausieren, auf 30 Sekunden und zurück springen | bestanden, beobachtetes Ziel 30.000 ms bei 63.357 ms Kapiteldauer |
| App-Fenster neu erstellen und Player weiter bedienen | bestanden |
| Startoberfläche im Emulator visuell prüfen | bestanden |

Fünf Gerätetests wurden ausgeführt. Der erste Durchgang fand eine zu alte Espresso-Testbibliothek und einen Test, der mehrere asynchrone Playerbefehle ohne Abwarten hintereinander auslöste. Espresso 3.7.0 behebt die Android-17-Inkompatibilität. Der Wiedergabetest wartet jetzt auf die pausierte Startposition, bevor er den nächsten Sprung auslöst. Beide Oberflächentests sind im korrigierten Durchgang bestanden. Die übrigen drei Gerätetests waren bereits bestanden.

Der Emulator bot fünf lokale deutsche Stimmen an. Der Audiotest nutzte `de-DE-language`. Das beweist funktionsfähige lokale Audioerzeugung, nicht eine für die Nutzerin ausreichende Klangqualität.

## Noch offen

- Hörbarkeit, Fokusfolge und Unterbrechungen im praktischen TalkBack-Hörtest.
- Deutsche Spracheingabe über das tatsächliche Mac-Mikrofon. Befehlserkennung aus Text ist getestet; Mikrofon und Erkennungsdienst noch nicht gemeinsam.
- Prozessabbruch und Gerätestart mit Wiederaufnahme, Langzeitbetrieb, echte Kopfhörer und Bluetooth.
- PDF-Lesezeichen mit komplexen Hierarchien und unterschiedliche mehrspaltige Dokumente.
- Große Schrift, Querformat und mehrere echte Android-Geräte.
- Cloud-Stimmen, OCR, automatische Kapitelübergänge, Bibliothek und Lesezeichen.
- Prüfung der App-Lizenz und sämtlicher Drittanbieter-Lizenzen vor Veröffentlichung.

Die Versionsnummer beschreibt einen Entwicklungsprototyp. Eine Freigabe durch die Testnutzerin hat noch nicht stattgefunden.
