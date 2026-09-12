# Entscheidung 0003: Die Sprachmaschine ist wählbar, und Stimmen sind optional

Datum: 12. September 2026

Status: angenommen

## Ausgangslage

Der Diagnosebericht vom Galaxy S25 der Testnutzerin (0.2.3, 12. September 2026):

```
Voreingestellte Sprachmaschine: es.codefactory.vocalizertts
Installierte Sprachmaschinen: Google (com.google.android.tts),
  Acapela TTS (com.acapelagroup.android.tts), Vocalizer TTS (es.codefactory.vocalizertts)
Deutsche Stimmen: 0, davon offline nutzbar: 0
```

Null Stimmen, obwohl die Nutzerin Vocalizer täglich auf Deutsch mit TalkBack hört. Die App war auf ihrem Gerät damit unbenutzbar: Ohne Stimme verweigert das Vorlesen den Dienst.

Die Ursache lag in zwei Annahmen, die nur auf dem Entwicklungsemulator mit Google TTS zutrafen:

1. Die App nahm immer die vom System voreingestellte Sprachmaschine.
2. Die App fragte Stimmen ausschließlich über `TextToSpeech.getVoices()` ab, die seit API 21 existiert.

Lizenzierte Sprachausgaben wie Vocalizer von Code Factory und Acapela bedienen häufig nur die ältere Schnittstelle über `isLanguageAvailable` und `setLanguage`. Sie melden keine einzelnen Stimmen, können die Sprache aber sehr wohl.

## Entscheidung

- Die Sprachmaschine wird in der App ausgewählt, nicht vom System übernommen. Alle installierten Maschinen stehen zur Wahl, die Auswahl wird gespeichert.
- Meldet eine Maschine keine einzelnen deutschen Stimmen, prüft die App die ältere Sprachabfrage. Ist Deutsch verfügbar, bietet sie einen Eintrag „Standardstimme dieser Sprachausgabe“ an und setzt beim Erzeugen die Sprache statt einer Stimme.
- Der Cache-Schlüssel enthält die tatsächlich benutzte Maschine, damit Audio zweier Maschinen nicht verwechselt wird.
- Die Selbstdiagnose prüft jede installierte Maschine einzeln: Start, Verfügbarkeit von Deutsch, gemeldete Stimmen und ob Audio in eine Datei geschrieben werden kann.

## Folgen

Die App ist nicht mehr davon abhängig, dass ausgerechnet die voreingestellte Maschine die moderne Schnittstelle bedient. Sie funktioniert mit einer Maschine, die nur eine einzige namenlose deutsche Stimme anbietet.

Ein erwünschter Nebeneffekt: Kauft sich die Nutzerin später eine bessere Stimme als eigene Sprachausgabe-App, erscheint diese automatisch in der Auswahl. Sie hat genau das als Wunsch geäußert, weil Acapela nach längerem Zuhören anstrengend wird.

Offen bleibt, welche der drei Maschinen auf ihrem Gerät Audio in eine Datei schreiben darf. Das beantwortet erst der nächste Bericht. Kann keine davon das, braucht der Reader einen zweiten Wiedergabeweg, der direkt spricht statt Dateien zu erzeugen; dann entfallen allerdings genaue Sprünge und das Fortsetzen an der Hörposition in ihrer heutigen Form.

## Was daraus zu lernen war

Der Emulator hatte genau eine Sprachmaschine installiert, und zwar die, die alles unterstützt. Jede Annahme über Sprachausgabe, die nur dort geprüft wurde, ist unbelegt. Das gilt sinngemäß für TalkBack-Version, Bluetooth und Energieverwaltung, siehe [0002](0002-zielgeraet-samsung-s25.md).
