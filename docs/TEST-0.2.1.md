# Testprotokoll 0.2.1

Datum: 11. September 2026

## Fehler und Korrektur

Beim ersten manuellen Durchgang mit TalkBack auf dem Emulator entstand nach der gesprochenen Kapitelansage eine hörbare Pause, bevor der eigentliche Text begann. Das Logcat des Durchgangs belegt es für die Leseprobe „Ankommen": Wiedergabe ab 21:50:46, Ansage zu Ende bei Position 3048 ms und Zustand STOPPED um 21:50:49.6, danach 2,2 Sekunden Stille, Textteil ab 21:50:52.

Ursache war der Startmechanismus aus 0.2.0: Die Wiedergabe begann, sobald Teil 0 vorlag. Teil 0 ist aber die nur rund drei Sekunden lange Kapitelansage. Der Player spielte sie ab, bevor der erste Textteil synthetisiert war, lief leer und musste warten.

Korrektur: Die Wiedergabe startet erst, wenn ab der Startposition mindestens `MIN_LEAD_MS` (12 Sekunden) Audio bereitliegt oder alle Teile fertig sind. Damit wird vor dem Start immer mindestens der erste Textteil mitvorbereitet. Die restlichen Teile werden weiterhin während des Hörens angehängt.

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64, Google-Play-Abbild.
- TalkBack 17.0.0.889642762, lokale deutsche Android-Stimme.
- App 0.2.1, Versionscode 4, Leseprobe mit drei Abschnitten.
- Java 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android SDK 36.

## Ergebnisse der automatischen Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Debug-App und Test-App bauen, Android Lint | bestanden, Lint ohne Fehler |
| Sechs Kernlogiktests (JVM) | bestanden |
| ProgressivePreparationTest, drei Prüfungen, neu: Start nicht auf der Ansage allein (Dauer beim Start liegt über dem Mindestvorlauf) | bestanden |
| PlaybackFocusTest, ReaderUiTest, MediaButtonTest | siehe unten, im Regressionslauf |

Der neue Test prüft direkt die Korrektur: Beim Übergang in den Zustand „spielt" muss bereits mehr als der Mindestvorlauf vorbereitet sein, also mehr als die drei Sekunden der Ansage. Auf dem Emulator dauert die Synthese eines Teils rund 30 Sekunden, deshalb ist der Start dort weiterhin langsam. Das ist Emulator-Verhalten, kein App-Fehler; auf echter Hardware ist die Synthese schneller als die Wiedergabe.

## Grenzen des Emulators

Der erste Durchgang zeigte außerdem einen langsamen Kaltstart und ruckelnde Bedienung. Das Logcat weist das dem ARM-übersetzten Emulator zu: „Skipped frames", „too much work on main thread" und ein App-Start von 16 Sekunden. Diese Punkte lassen sich nur auf einem echten Android-Gerät bewerten.

## Noch offen

- Der manuelle TalkBack-Durchgang aus [TEST-0.2.md](TEST-0.2.md) auf einem echten Android-Gerät. Erst dort sind Startgeschwindigkeit, Flüssigkeit von Pause und Fortsetzen und das Verhalten der Kopfhörer-Tasten fair zu beurteilen.
- Prüfen, ob nach der Korrektur die Trägheit beim Pausieren und Fortsetzen verschwindet.
- Der Hörtest der Testnutzerin.
