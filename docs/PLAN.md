# Projektplan

Stand: 9. September 2026

## Umsetzungsstand vom 10. September 2026

Das native Grundgerüst 0.1.0 ist umgesetzt und als Debug-APK auf dem Pixel-8a-Emulator installiert. Es verbindet Text-PDF-Import, PDF-Lesezeichen oder Seitennavigation, lokale Android-TTS-Audioerzeugung mit Cache, Media3-Wiedergabe, 30-Sekunden-Sprünge, Inhaltsverzeichnis, Geschwindigkeit und gespeicherte Hörpositionen. Die App hat eine TalkBack-Semantik und eine eigene Schnittstelle für lokale deutsche Sprachbefehle.

Fünf Logiktests und fünf Gerätetests prüfen die Kernfunktionen. Der Gerätetest hat Audio mit einer deutschen Offline-Stimme erzeugt, den Cache wiederverwendet, die Kapitelwahl und die 30-Sekunden-Sprünge ausgeführt. Der aktuelle Umfang und die Grenzen stehen in der README. Die nächsten Schritte sind ein praktischer Mikrofon- und TalkBack-Hörtest, der Vergleich natürlicherer Stimmen, fortlaufendes Abspielen mehrerer Kapitel und OCR.

Der nachfolgende Produktplan beschreibt weiterhin das vollständige Ziel. Er ist keine Liste bereits fertiggestellter Funktionen.

## 1. Ziel

Die App ist ein Audio-Reader fuer blinde Menschen. Sie oeffnet PDF-Dokumente, erkennt deren Struktur und liest sie mit einer moeglichst natuerlichen Stimme vor. Die gesamte Bedienung funktioniert mit TalkBack und zusaetzlich ueber Sprachbefehle.

Die App soll als Open-Source-Projekt auf GitHub erscheinen. Eine kostenlose lokale Stimme bildet die Grundversorgung. Hochwertige Cloud-Stimmen koennen Nutzerinnen und Nutzer optional einschalten.

Android hat Vorrang. Der vollstaendige erste Reader wird fuer Android entwickelt, getestet und mit der blinden Testnutzerin verbessert. Eine iOS-Version ist eine spaetere eigene Ausbaustufe. Gemeinsam genutzt werden nur Teile, bei denen das keinen Kompromiss bei TalkBack, VoiceOver oder Mediensteuerung erzwingt.

## 2. Produktgrundsaetze

### Audio zuerst

Die sichtbare Oberflaeche ist nicht der Ausgangspunkt. Jede Funktion wird zuerst als Ablauf mit TalkBack, Spracheingabe und Kopfhoerer-Tasten beschrieben. Erst danach entsteht die visuelle Darstellung.

### Mit blinden Menschen testen

Die Testnutzerin soll den Prototyp bereits waehrend der Entwicklung testen. Automatische Barrierefreiheitstests reichen nicht aus. Besonders wichtig sind die Zahl der Wischbewegungen, die Reihenfolge der Elemente, verstaendliche Rueckmeldungen und das Verhalten bei gleichzeitig laufendem Buchaudio.

### Hoerqualitaet ueber lange Zeit

Eine Stimme wird nicht nach einer fuenf Sekunden langen Probe ausgewaehlt. Der Hoertest dauert mindestens zwei bis fuenf Minuten und enthaelt:

- erzaehlende Passagen und Dialog,
- kurze und lange Saetze,
- Zahlen, Uhrzeiten, Datumsangaben und Abkuerzungen,
- fremdsprachige Namen,
- verschiedene Geschwindigkeiten von 1,0 bis mindestens 1,5.

Spaeter folgt ein Dauertest von mindestens 30 Minuten. Bewertet werden Betonung, Pausen, Aussprachefehler, Gleichfoermigkeit und Hoermuedigkeit.

### Kosten bleiben vorhersehbar

Die App erzeugt Cloud-Audio abschnittsweise und speichert es lokal. Bereits erzeugte Abschnitte werden nicht erneut berechnet. Ein einstellbares Monatslimit stoppt weitere Cloud-Anfragen und aktiviert die lokale Stimme.

### Kein Zwangskonto fuer die Grundfunktion

PDF-Import, Navigation, lokale Stimme und gespeicherte Positionen funktionieren ohne Registrierung. Eine Cloud-Anmeldung wird nur verlangt, wenn eine Cloud-Stimme benutzt wird.

## 3. Bedienkonzept

### TalkBack

TalkBack liest Menues, Schaltflaechen, Zustaende und Fehlermeldungen. Die App stellt dafuer saubere Rollen, Beschriftungen, Ueberschriften, Zustaende und eine feste Fokusreihenfolge bereit. Komplexe Gesten erhalten gleichwertige benannte Aktionen.

Es gibt keine rein bildlichen Schaltflaechen und keine Funktion, die nur durch Ziehen oder eine versteckte Geste erreichbar ist.

### Spracheingabe

Die Spracheingabe startet ueber eine grosse Schaltflaeche. Eine spaetere Version kann Kopfhoerer-Tasten oder einen optionalen Aktivierungssatz unterstuetzen. Dauerhaftes Mithoeren ist fuer die erste Version nicht vorgesehen.

Beim Start eines Befehls pausiert das Buch. Die App bestaetigt die erkannte Aktion kurz und setzt die Wiedergabe anschliessend fort.

Vorgesehene Befehle:

- Vorlesen, Pause und Fortsetzen
- 30 Sekunden zurueck oder vor
- eine frei gesprochene Zeit zurueck oder vor
- naechstes oder vorheriges Kapitel
- Inhaltsverzeichnis oeffnen oder vorlesen
- zu einem Kapitel oder einer Seite wechseln
- aktuelle Position ansagen
- Lesegeschwindigkeit aendern
- Lesezeichen setzen und aufrufen
- Aussprache korrigieren
- Einschlaftimer setzen

Die erste Version erkennt fest definierte Absichten lokal. Ein grosses Sprachmodell ist dafuer nicht erforderlich.

### Mediensteuerung

Android Media3 und MediaSession steuern Wiedergabe, Pause, Spruenge und Kapitelwechsel. Dadurch funktionieren auch Sperrbildschirm, Benachrichtigungsbereich, Bluetooth-Kopfhoerer und physische Medientasten.

## 4. Dokumentverarbeitung

Die App erzeugt aus jedem PDF ein internes Dokumentmodell:

- Titel und Autor, soweit vorhanden
- Kapitel und Unterkapitel
- Absaetze
- Quellseite und Textposition
- Lesefortschritt und Lesezeichen
- Verknuepfung zwischen Textabschnitt und erzeugtem Audio

Die Verarbeitung erfolgt in dieser Reihenfolge:

1. Vorhandene PDF-Lesezeichen und Strukturinformationen uebernehmen.
2. Eingebetteten Text in sinnvoller Lesereihenfolge extrahieren.
3. Kopfzeilen, Fusszeilen, wiederholte Seitennummern und Trennstriche bereinigen.
4. Fehlende Ueberschriften anhand von Schriftgroesse, Position und Textmerkmalen erkennen.
5. Bei Bild-PDFs die Seiten lokal per OCR verarbeiten.
6. Unsichere Ergebnisse klar kennzeichnen, statt ein erfundenes Inhaltsverzeichnis vorzugeben.

Mehrspaltige Seiten, Tabellen, Fussnoten, Formeln und gescannte Dokumente sind eigene Testfaelle. Die App muss solche Inhalte ansagen oder ueberspringbar machen.

## 5. Stimmen

Die App bekommt eine austauschbare Anbieterschnittstelle. Der Player und die Dokumentverarbeitung duerfen nicht direkt von Google oder einem bestimmten lokalen Modell abhaengen.

### Lokale Kandidaten

#### Kikiri German

Kikiri German basiert auf der Kokoro-Architektur. Aktuell gibt es die deutsche Maennerstimme Martin und die Frauenstimme Victoria. Die Modelle stehen unter Apache 2.0. Eine ONNX-Fassung von Martin ist ungefaehr 327 MB gross.

Vor einer Entscheidung werden Startzeit, Speicherverbrauch, Akkubelastung, Geschwindigkeit und Klang auf einem Mittelklasse-Androidgeraet gemessen.

- Projekt: https://github.com/semidark/kikiri-tts
- ONNX-Modell: https://huggingface.co/Godelaune/Kokoro-82M-ONNX-German-Martin
- Victoria: https://huggingface.co/kikiri-tts/kikiri-german-victoria

#### Piper

Piper ist kleiner und schneller. Die deutsche Thorsten-Stimme benoetigt je nach Qualitaetsstufe ungefaehr 60 bis 114 MB. Piper eignet sich als Offline-Reserve, wenn Kikiri auf einem Geraet zu langsam ist.

- Engine: https://github.com/OHF-Voice/piper1-gpl
- Thorsten: https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/high

Die GPL-Lizenz der aktuellen Piper-Engine wird vor einer direkten Einbindung geprueft. Stimmenmodelle und Engine haben getrennte Lizenzen.

### Cloud-Kandidaten

#### Google Chirp 3 HD

Chirp 3 HD ist der bevorzugte Qualitaetsmodus. Google bietet zahlreiche deutsche Stimmen. Nach dem derzeitigen Preismodell sind bis zu eine Million Zeichen pro Monat kostenlos, danach kostet die Nutzung 30 US-Dollar je Million Zeichen. Preise und Freikontingente koennen sich aendern.

#### Google Neural2

Neural2 ist der Cloud-Sparmodus. Nach dem derzeitigen Preismodell sind bis zu eine Million Zeichen pro Monat kostenlos, danach kostet die Nutzung 16 US-Dollar je Million Zeichen.

- Preise: https://cloud.google.com/text-to-speech/pricing?hl=de
- Stimmen: https://docs.cloud.google.com/text-to-speech/docs/list-voices-and-types?hl=de

### Anbieterzugang

Ein gemeinsamer geheimer API-Schluessel darf niemals in der Android-App liegen. Fuer die erste Open-Source-Version werden diese Wege untersucht:

- eigener Zugang des Nutzers,
- frei konfigurierbarer kompatibler Server,
- spaeter ein gemeinschaftlich finanzierter Dienst mit Tages- und Monatsgrenzen.

Ein oeffentlicher Gemeinschaftsdienst benoetigt Anmeldung, Missbrauchsschutz, nachvollziehbare Quoten und einen klaren Datenschutztext. Er ist nicht Teil des ersten Prototyps.

## 6. Audioerzeugung und Cache

Die App teilt Text an Absatz- und Satzgrenzen in kurze Abschnitte. Sie erzeugt den aktuellen Abschnitt und laedt einige folgende Abschnitte voraus.

Der Cache-Schluessel beruecksichtigt:

- bereinigten Text,
- Stimmenanbieter und Stimmenkennung,
- Geschwindigkeit und weitere Sprachparameter,
- Modellversion.

Der Cache kann pro Dokument oder vollstaendig geloescht werden. Die App zeigt Speicherverbrauch und geschaetzte Cloud-Kosten barrierefrei an.

Zeitliche Spruenge beziehen sich auf die Audioposition. Kapitelwechsel beziehen sich auf das interne Dokumentmodell. Beim Wechsel der Stimme bleibt die Textposition erhalten.

## 7. Vorgeschlagene Android-Technik

- Kotlin
- Jetpack Compose mit vollstaendiger Semantik fuer TalkBack
- Android Media3 und MediaSession
- Room fuer Bibliothek, Fortschritt, Kapitel und Lesezeichen
- WorkManager fuer laengere OCR- und Aufbereitungsarbeiten
- Android SpeechRecognizer fuer Sprachbefehle, bevorzugt lokal ab Android 12
- ML Kit Text Recognition fuer lokale OCR
- ONNX Runtime fuer geeignete lokale Stimmenmodelle

Die konkrete PDF-Bibliothek wird nach Tests mit strukturierten, mehrspaltigen und gescannten PDFs ausgewaehlt.

## 8. MVP

Der erste benutzbare Prototyp umfasst:

- Installation als private APK
- PDF ueber Dateiauswahl und Android-Teilen-Menue oeffnen
- eine vollstaendig mit TalkBack bedienbare Bibliothek
- Play, Pause und Fortsetzen
- 30 Sekunden zurueck und vor
- naechstes und vorheriges Kapitel
- Inhaltsverzeichnis anzeigen und vorlesen
- aktuelle Position ansagen
- letzte Position automatisch speichern
- Geschwindigkeit einstellen
- einfache Spracheingabe fuer alle genannten Funktionen
- eine lokale Teststimme
- eine optionale Google-Cloud-Stimme
- lokalen Audiocache und Kostenlimit

## 9. Abnahmekriterien fuer den ersten Prototyp

Die Testnutzerin kann ohne sehende Hilfe:

1. eine PDF-Datei aus einer anderen App oeffnen,
2. das Vorlesen beginnen und pausieren,
3. 30 Sekunden springen,
4. Kapitel wechseln,
5. das Inhaltsverzeichnis aufrufen,
6. ihre aktuelle Position erfahren,
7. einen Sprachbefehl geben,
8. die App schliessen und spaeter an derselben Stelle fortsetzen,
9. erkennen, ob gerade die lokale oder eine kostenpflichtige Stimme aktiv ist,
10. das Cloud-Kostenlimit verstehen und aendern.

Kein Bedienweg darf in einer Sackgasse fuer TalkBack enden.

## 10. Entwicklungsphasen

### Phase 0: Hoertest und Bedieninterview

- identische laengere Proben aller Stimmen erstellen
- Die Testnutzerin bewertet Stimmen bei mehreren Geschwindigkeiten
- ihre bisherigen Reader und bevorzugten Gesten aufnehmen
- Muss-Funktionen und stoerende Verhaltensweisen dokumentieren

### Phase 1: Barrierefreier Audioplayer

- statisches Testbuch verwenden
- TalkBack-Navigation und MediaSession umsetzen
- Spruenge, Kapitelwechsel, Position und Spracheingabe testen

### Phase 2: PDF-Verarbeitung

- Text-PDFs importieren
- Dokumentstruktur erzeugen
- OCR fuer Bild-PDFs hinzufuegen
- schwierige Layouts sammeln und als Regressionstests sichern

### Phase 3: Stimmenanbieter

- lokalen Modellkandidaten auf Android messen
- Google Chirp 3 HD und Neural2 anbinden
- Cache, Kostenanzeige und harte Limits umsetzen

### Phase 4: Oeffentliche Testversion

- Lizenz festlegen
- Datenschutz- und Sicherheitspruefung
- reproduzierbare Builds und automatisierte Tests
- GitHub-Dokumentation und barrierefreie Fehlerberichte
- APK-Testverteilung, danach Entscheidung ueber den Play Store

## 11. Qualitaetssicherung

- manuelle Tests mit TalkBack auf mindestens einem Google- und einem Samsung-Geraet
- Tests mit abgeschaltetem Bildschirm
- Tests mit Bluetooth-Kopfhoerern und Kabel-Headset
- Accessibility Scanner und automatisierte Compose-Semantiktests
- Hoertests bei mehreren Geschwindigkeiten
- Flugmodus, schlechte Verbindung und abgelaufenes Cloud-Limit
- grosse PDFs und knapper Geraetespeicher
- Wiederaufnahme nach App-Abbruch und Neustart

## 12. Offene Entscheidungen

- endgueltiger Projektname
- Zielversionen von Android und Referenzgeraete
- bevorzugte Stimmen nach ihrem Hoertest
- Lizenz fuer App und einen moeglichen Gemeinschaftsserver
- erste PDF-Bibliothek
- eigener Cloud-Zugang oder spaeterer Gemeinschaftsdienst
- APK-Verteilung oder Google Play Store
- Umfang von EPUB, Word-Dokumenten und Webseiten nach dem PDF-MVP
- Zeitpunkt und Umfang einer spaeteren nativen iOS-Version

## 13. Naechste konkrete Schritte

1. Zwei- bis fuenfminuetige Hoerproben mit identischem Text erzeugen.
2. Die Testnutzerin laesst Stimmen, Geschwindigkeit und Aussprache bewerten.
3. Ihr Smartphone-Modell und die Android-Version erfassen.
4. Einen kleinen TalkBack-faehigen Audioplayer mit Sprachbefehlen bauen.
5. Den ersten Test gemeinsam ohne Sicht durchgehen.
