# Testprotokoll 0.5.0

Datum: 12. September 2026

## Was neu ist

Nur die Auslieferung, kein Verhalten. Erstmals ein mit eigenem Schlüssel signierter Release-Build statt eines Debug-Builds.

- Signaturschlüssel angelegt, RSA 4096, gültig bis Januar 2054. Er liegt außerhalb des Repositories, das Verfahren steht in [RELEASING.md](RELEASING.md).
- Signaturschemata v2 und v3. v3 hält den späteren Schlüsseltausch offen.
- Minifizierung bleibt bewusst aus. Media3 und PDFBox arbeiten mit Reflexion, und ohne geprüfte Keep-Regeln würde ein Schrumpfen die Wiedergabe in einem Build brechen, den Nutzer nicht debuggen können.

## Umgebung

- Mac mini M1, Pixel-8a-Emulator, Android 17, API 37, ARM64.
- App 0.5.0, Versionscode 11, Release-Build.
- TalkBack für die automatischen Läufe abgeschaltet.

## Prüfung des Pakets

| Prüfung | Ergebnis |
| --- | --- |
| Signatur verifiziert | bestanden, Schema v2 und v3 |
| Zertifikat stimmt mit dem Keystore überein | bestanden, SHA-256 `8ec34b04…32c800d8` |
| `application-debuggable` | nicht vorhanden, wie es sein muss |
| Version im Paket | 0.5.0, Code 11 |
| Größe | 19,1 MB gegenüber 24,5 MB beim Debug-Build |

## Rauchtest des Release-Builds auf dem Gerät

Ein Release-Build ist ein anderer Build-Typ und war nie auf einem Gerät. Deshalb von Hand geprüft, nicht nur gebaut:

| Schritt | Ergebnis |
| --- | --- |
| Installieren und starten | bestanden, keine Abstürze im Protokoll |
| Berechtigung für Benachrichtigungen beim ersten Antippen von „Vorlesen" | erscheint wie erwartet, danach startet die Wiedergabe |
| Leseprobe öffnen | bestanden, drei Abschnitte |
| Audio erzeugen und abspielen | bestanden, sechs Synthesen, Position lief durchgehend hoch |
| Automatisches Weiterlesen über alle drei Abschnitte | bestanden, endete auf „Abschnitt 3 von 3" |
| Letztes Kapitel bleibt stehen und bietet Neustart | bestanden, Taste zeigt „Vorlesen" |

⚠️ Beim Beobachten führte die Statuszeile zunächst in die Irre. Sie sagte weiterhin „Ankommen. Wiedergabe läuft.", während der Reader längst im dritten Abschnitt war. Das ist Absicht: Beim automatischen Weiterlesen wird der Status nicht verändert, weil jede Änderung der Live-Region TalkBack dazu bringt, das Buch zu unterbrechen. Maßgeblich ist die Zeile „Abschnitt x von y", und die war korrekt.

Für die Fehlersuche heißt das: Der Statustext ist kein verlässlicher Anzeiger des aktuellen Kapitels.

## Noch offen

- ⛔ Der Signaturschlüssel existiert nur auf einem Rechner. Ohne Sicherung an einem zweiten Ort ist bei Verlust kein Update mehr über eine bestehende Installation möglich, und jeder Nutzer verliert beim Neuinstallieren seine Bibliothek.
- 0.5.0 lässt sich nicht über 0.4.2 installieren, weil sich die Signatur unterscheidet. Wer 0.4.2 hat, muss deinstallieren.
- Minifizierung, siehe oben. Braucht einen eigenen Gerätedurchlauf.
- Mehrsprachigkeit der Oberfläche.
