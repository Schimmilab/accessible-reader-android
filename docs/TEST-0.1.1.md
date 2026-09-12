# Testprotokoll 0.1.1

Datum: 11. September 2026

## Fehler und Korrektur

Beim Start der Leseprobe unter aktiviertem TalkBack wechselte die fokussierte Taste zwischen „Pause“ und „Vorlesen“. TalkBack kündigte den Zustandswechsel an und unterbrach dadurch wieder die Buchwiedergabe. Vor der Korrektur waren nach etwa 13 Sekunden seit dem Start nur ungefähr 1,3 Sekunden Buchaudio abgespielt. Das Android-Audiofokusprotokoll zeigte wiederholte Anfragen und Freigaben durch TalkBack.

Die App unterschied bisher nicht zwischen dem Wiedergabewunsch und tatsächlich laufendem Audio. Nun steuern `playWhenReady` und der Lade-/Endzustand die Taste und die Umschaltaktion. Eine kurze Unterbrechung durch TalkBack ändert die Tastenbeschriftung nicht. Die automatische Audiofokusverwaltung von Media3 bleibt aktiv. Es gibt keinen Timer, der eine bewusste Pause überschreibt.

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64.
- TalkBack 17.0.0.889642762, Deutsch, mit aktivierter Touch-Erkundung.
- App 0.1.1, Versionscode 2, lokale deutsche Android-Stimme, Leseprobe „Ankommen“ bei einfachem Tempo.
- Java 17, Gradle 8.13, Android SDK 36.

## Ergebnisse

| Prüfung | Ergebnis |
| --- | --- |
| Debug-App und Test-App bauen | bestanden |
| Android Lint | keine Fehler, bestehende Hinweise bleiben |
| Fünf Kernlogiktests | bestanden, keine übersprungen |
| Vier neue Gerätetests für Audiofokus, Pause und Kapitelende | bestanden |
| Fünf bisherige Gerätetests für PDF-Import, Oberfläche, Wiedergabe, Sprünge und Audio-Cache | bestanden |
| Wiedergabe bei eingeschaltetem und gebundenem TalkBack | nach der anfänglichen Ansage stabil; fünf Statusproben über 20 Sekunden durchgehend PLAYING, Position von 12.915 auf 33.927 ms |
| Aktualisierte APK im Emulator installieren | erfolgreich, ohne Löschen der App-Daten |

Die vier neuen Fokusprüfungen benötigen 36,478 Sekunden, die fünf bisherigen Gerätetests 15,480 Sekunden. Die normalen Gerätetests ersetzen keine vollständige TalkBack-Gestenprüfung.

## Noch offen

Der abschließende manuelle Durchgang mit sicher auf der Taste liegendem TalkBack-Fokus, Doppeltipp und erneutem Pausieren konnte nicht vollständig beendet werden, da der Emulator währenddessen geschlossen wurde. Die oben protokollierte stabile Wiedergabe erfolgte mit aktivem TalkBack, die Touch-Eingaben wurden jedoch über ADB eingespeist. Das ist kein vollständiger Nachweis der menschlichen Doppeltipp-Bedienung.

Ein erneuter Hörtest durch den Entwickler und später durch die Testnutzerin bleibt nötig. Die Korrektur ändert weder Stimmenqualität noch Cloud-Anbindung. PDF-OCR, echte Geräte und Mikrofonprüfung bleiben außerhalb dieses Fixes.
