# Entscheidung 0002: Referenzgerät ist ein Samsung Galaxy S25

Datum: 12. September 2026

Status: angenommen

## Ausgangslage

Bisher wurde ausschließlich auf einem Pixel-8a-Emulator mit Google-Sprachausgabe und Google TalkBack entwickelt. Das tatsächliche Gerät der Testnutzerin wurde über ihren sehenden Mann erfragt, der das Telefon eingerichtet hat:

- Samsung Galaxy S25, Android 16, One UI 8.5, Kernel 6.6.98, neun Monate alt
- TalkBack 16.2.00.12, also Samsungs eigener TalkBack-Zweig, nicht Googles 17er-Reihe
- Sprachausgabe über die Vocalizer-Engine
- Hört überwiegend über eine Bluetooth-Box, selten über Kabelkopfhörer

## Entscheidung

Das Samsung Galaxy S25 ist das Referenzgerät. Der Pixel-Emulator bleibt die schnelle Entwicklungsumgebung, entscheidet aber keine Frage mehr allein.

Daraus folgt:

- Keine Annahme über die Sprachausgabe gilt als geprüft, solange sie nur auf Google TTS geprüft wurde. Insbesondere ist offen, ob Vocalizer `synthesizeToFile` unterstützt. Die gesamte Audioerzeugung hängt daran.
- Die App muss ihre eigene Umgebung berichten können, weil hier kein Samsung-Gerät verfügbar ist. Dafür gibt es die Selbstdiagnose in den Einstellungen.
- Bluetooth ist der Normalfall, nicht der Sonderfall. Übergänge zwischen Audioteilen, Verzögerung beim Pausieren und die Tasten der Box werden daran gemessen.
- Samsungs Energieverwaltung beendet Hintergrunddienste aggressiv. Die bekannte Schwäche, dass Kapitelwechsel und automatisches Weiterlesen ein lebendes ViewModel brauchen, wiegt auf diesem Gerät schwerer als auf einem Pixel.
- Ein zweites Testgerät zum Selbertesten sollte ebenfalls ein Samsung sein, kein Pixel.

## Folgen

Der Emulator kann weiterhin Logik, Barrierefreiheits-Semantik und Regressionen prüfen. Er kann nicht mehr belegen, dass eine Funktion auf dem Zielgerät funktioniert.

⭐ Nachtrag vom 12. September 2026: Der Diagnosebericht liegt vor. Alle drei auf dem Zielgerät installierten Sprachausgaben, Acapela, Google und Vocalizer, können Audio in Dateien schreiben. Die dateibasierte Bauweise ist damit bestätigt und ein zweiter Wiedergabeweg wird nicht gebraucht. Der ursprüngliche Vorbehalt lautete:

Offen bleibt, ob Vocalizer Audio in Dateien schreiben darf. Fällt die Antwort negativ aus, braucht die App entweder eine Auswahl der Sprachmaschine, damit eine andere Engine genutzt werden kann, oder einen zweiten Wiedergabeweg ohne Zwischendateien. Beides wird erst entschieden, wenn der Diagnosebericht vom Zielgerät vorliegt.
