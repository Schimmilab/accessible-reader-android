# Bedienung

Diese Anleitung ist zum Anhören gedacht. Kurze Sätze, klare Überschriften, keine Bilder, die etwas erklären, was im Text fehlt.

Die App heißt Accessible Reader. Sie liest PDF-Dateien vor. Sie lässt sich vollständig ohne Sicht bedienen.

## Was die App tut

Sie nimmt eine PDF-Datei, holt den Text heraus und lässt die Sprachausgabe deines Telefons daraus Audio erzeugen. Dieses Audio wird gespeichert. Dadurch kannst du pausieren, genau dreißig Sekunden springen und später an derselben Stelle weiterhören.

Sie spricht nicht live mit. Beim ersten Start eines Abschnitts dauert es deshalb einen Moment, bis Ton kommt. Danach läuft es durch.

## Der erste Start

Beim ersten Öffnen ist eine Leseprobe geladen. Sie hat drei Abschnitte und dient dazu, die Bedienung auszuprobieren, ohne eine eigene Datei zu brauchen.

Die Taste heißt „Leseprobe". Sie ist oben, rechts neben „PDF öffnen".

## Ein eigenes PDF öffnen

Es gibt zwei Wege.

Der erste: die Taste „PDF öffnen" oben auf dem Bildschirm. Es erscheint die Dateiauswahl von Android.

Der zweite, und meist der bequemere: Tippe die PDF-Datei in einer anderen App an, zum Beispiel in einem Messenger oder im Dateimanager, und wähle Accessible Reader zum Öffnen oder Teilen. Das Dokument landet dann direkt in der Bibliothek.

Während des Einlesens sagt die App die Seitenzahl mit. Bei einem dicken Buch dauert das einige Sekunden.

## Die Bibliothek

Die Taste „Bibliothek" steht unter „PDF öffnen".

Dort stehen alle Dokumente, die du einmal geöffnet hast, das neueste oben. Jede Zeile nennt den Titel, die Anzahl der Abschnitte und wo du aufgehört hast, zum Beispiel: „Der Garten, zwölf Abschnitte, zuletzt bei Abschnitt vier von zwölf."

Ein Dokument auswählen öffnet es. Die Taste „Entfernen" neben einer Zeile löscht es, aber erst nach einer Rückfrage. Die bestätigende Taste heißt „Ja, entfernen", damit sie sich von der Taste in der Liste unterscheidet.

Entfernen löscht den herausgeholten Text und die Hörposition. Deine PDF-Datei auf dem Telefon bleibt unangetastet.

## Vorlesen und Pause

Die große Taste in der Mitte heißt „Vorlesen". Läuft die Wiedergabe, heißt sie „Pause".

Diese Taste ändert ihre Beschriftung nicht, wenn TalkBack kurz dazwischenredet. Das ist Absicht. Sonst würde TalkBack den neuen Zustand vorlesen und das Buch wieder unterbrechen.

## Dreißig Sekunden springen

Zwei Tasten unter „Vorlesen": „30 Sekunden zurück" und „30 Sekunden vor".

Gemeint sind echte dreißig Sekunden Hörzeit, unabhängig von Textlänge und Sprechtempo. Der Sprung geht auch über die Grenzen der einzelnen Audiostücke hinweg.

Am Anfang eines Abschnitts hält der Rücksprung am Anfang. Solange noch Teile erzeugt werden, hält der Vorwärtssprung beim bereits vorbereiteten Audio.

## Abschnitte wechseln

Im Bereich „Im Dokument" stehen „Vorheriger Abschnitt" und „Nächster Abschnitt".

Was ein Abschnitt ist, hängt vom PDF ab. Hat es Kapitelmarken, werden daraus Abschnitte. Hat es keine, wird jede Seite ein Abschnitt.

Jeder Abschnitt beginnt mit einer gesprochenen Ansage in der Buchstimme, zum Beispiel „Abschnitt zwei von drei: Unterwegs." Bei Seitenabschnitten nur „Seite sieben."

Ist ein Abschnitt zu Ende, liest die App ohne weitere Eingabe im nächsten weiter. Beim letzten Abschnitt bleibt sie stehen und bietet einen Neustart an.

## Inhaltsverzeichnis

Die Taste „Inhaltsverzeichnis" öffnet die Liste aller Abschnitte. Dort kannst du einen auswählen.

Im Dialog gibt es zusätzlich „Übersicht vorlesen". Das spricht die ersten zwanzig Einträge am Stück in der Buchstimme. Die vollständige Liste bleibt darunter mit TalkBack bedienbar.

## Wo bin ich

Die Taste „Wo bin ich? Position vorlesen" nennt den Abschnitt, seine Nummer und die Hörzeit in Minuten und Sekunden.

Läuft TalkBack, liest TalkBack die Angabe vor. Läuft es nicht, spricht die App selbst. Es wird also nie doppelt gesprochen.

## Tempo

Im Bereich „Stimme & Tempo" stehen „Langsamer" und „Schneller", dazwischen der aktuelle Wert. Der Bereich geht von 0,5 bis 2,0.

Das Tempo wird gespeichert und gilt auch beim nächsten Start.

## Stimme und Sprachausgabe wählen

Die Taste „Stimme und Einstellungen" öffnet den Dialog dafür.

Ganz oben steht „Sprachausgabe prüfen". Das prüft jede installierte Sprachausgabe einzeln und schreibt einen Bericht. Das dauert bis zu zwei Minuten, und die App bleibt dabei bedienbar. Der Bericht lässt sich über „Bericht teilen" verschicken.

Darunter steht der Abschnitt „Sprachausgabe" mit allen auf dem Telefon installierten. Deine Auswahl hier gilt nur für diese App. TalkBack behält seine eigene Einstellung.

Darunter der Abschnitt „Stimmen". Jede Stimme hat eine Taste „Probe", die bei allen Stimmen denselben Satz spricht, mit Zahlen, einem Datum und Abkürzungen. So lassen sich Stimmen vergleichen.

Manche Sprachausgaben geben ihre Stimmen nicht einzeln preis. Dann steht dort nur ein Eintrag namens „Standardstimme dieser Sprachausgabe". Das ist kein Fehler.

Ausführlicher, auch zum Nachrüsten besserer Stimmen: [VOICES.md](VOICES.md).

## Sprachbefehle

Die Taste „Sprachbefehl geben" im Bereich „Mit deiner Stimme" beginnt das Zuhören. Die Wiedergabe pausiert dabei. Die App hört nur nach diesem Tastendruck zu, nie dauerhaft.

Erkannt werden:

- Vorlesen, auch: weiter, fortsetzen, start, starten, abspielen
- Pause, auch: stopp, anhalten
- nächstes Kapitel, nächste Seite
- vorheriges Kapitel, letztes Kapitel, vorherige Seite
- Inhaltsverzeichnis, Kapitel anzeigen
- Bibliothek, meine Bücher, meine Dokumente
- wo bin ich, Position, aktuelle Position
- dreißig Sekunden zurück, ebenso zehn und sechzig, jeweils auch vor
- Kapitel zwei, oder gehe zu Kapitel zwei

Die Erkennung läuft auf dem Gerät und braucht Android 12 oder neuer sowie deutsche Offline-Sprachdaten. Fehlen die, bleibt die Spracheingabe aus und alle Tasten funktionieren weiterhin. Es wird nie heimlich auf eine Erkennung im Internet ausgewichen.

Unter „Alle Befehle und Texteingabe" lassen sich dieselben Befehle auch eintippen. Das ist zum Ausprobieren gedacht.

## Kopfhörer, Bluetooth und Benachrichtigung

Die Wiedergabe läuft als Medienwiedergabe. Sie geht weiter, wenn der Bildschirm aus ist oder du die App verlässt.

An Kopfhörern, einer Bluetooth-Box und in der Medienbenachrichtigung gilt:

- Wiedergabe und Pause wie gewohnt
- Weiter wechselt zum nächsten Abschnitt
- Zurück springt an den Anfang des Abschnitts. Drückst du innerhalb von drei Sekunden erneut, geht es in den vorherigen Abschnitt
- In der Benachrichtigung gibt es zusätzlich dreißig Sekunden vor und zurück

## Stehenbleiben und weiterhören

Die Hörposition wird etwa jede Sekunde gespeichert, zusammen mit Dokument, Abschnitt und Stimme.

Du kannst pausieren, die App schließen und später weiterhören. Beim nächsten Antippen von „Vorlesen" setzt sie an derselben Stelle fort.

Wenn du die Stimme wechselst, geht die Sekunde verloren, bei der du warst, denn in einer anderen Stimme liegt an derselben Sekunde ein anderes Wort. Der Reader beginnt deshalb das gerade laufende Stück noch einmal von vorn. Du hörst höchstens etwa eine Minute zweimal, nicht den ganzen Abschnitt.

## Im Buch suchen

Unter dem Inhaltsverzeichnis steht die Taste **Im Buch suchen**.

Du gibst ein Wort ein und drückst „Suchen". Der Reader sagt dir sofort, wie viele Fundstellen es gibt, zum Beispiel „3 Fundstellen für Brücke." In der Liste nennt jeder Eintrag zuerst den Abschnitt und dann die Wörter rundherum, damit du hörst, ob es die richtige Stelle ist. Tippst du sie an, wird ab dort vorgelesen.

Umlaute kannst du schreiben, wie du magst: „Brücke" und „Bruecke" finden dasselbe.

Per Sprache: „Suche nach Brücke".

Gefunden werden höchstens zwanzig Stellen, höchstens drei je Abschnitt — sonst wäre die Liste nicht mehr zu überblicken, wenn man sie vorgelesen bekommt.

## Einschlafen

Unter den Lesezeichen steht die Taste **Einschlaftimer**. Sie sagt dir immer, was gerade eingestellt ist: „Einschlaftimer" heißt aus, sonst zum Beispiel „Einschlaftimer, noch 24 Minuten". Du musst also nichts öffnen, um zu hören, wie lange noch gelesen wird.

Zur Auswahl stehen: aus, nach 15, 30, 45 oder 60 Minuten, oder am Ende des laufenden Abschnitts.

Zwanzig Sekunden vor Schluss wird der Reader leiser und hört dann auf — er bricht nicht mitten im Satz ab, das würde eher aufwecken als einschlafen lassen. Die Stelle bleibt gespeichert, du hörst am nächsten Tag genau dort weiter.

Der Timer läuft auch dann noch, wenn du die App weggewischt hast. Er gehört nämlich zur Wiedergabe, nicht zum Bildschirm.

Per Sprache: „Einschlaftimer".

## Eine Stelle merken

Unter „Wo bin ich?" stehen zwei Tasten nebeneinander.

- **Stelle merken** merkt sich, wo du gerade bist. Der Reader sagt dir sofort, was er sich gemerkt hat, zum Beispiel „Lesezeichen gesetzt: Kapitel 3, Minute 12."
- **Lesezeichen** öffnet die Liste. Jeder Eintrag sagt Abschnitt und Minute; tippst du ihn an, geht es dort weiter. Daneben steht für jeden Eintrag eine Taste „Entfernen".

Beides geht auch per Sprache: „Stelle merken" und „Lesezeichen".

Zwanzig Lesezeichen je Buch werden behalten, das älteste fällt danach heraus. Drückst du zweimal an derselben Stelle, bleibt es ein Lesezeichen — der Reader weiß, dass du dir nur nicht sicher warst, ob es geklappt hat.

## Zwei Stimmen für einen Roman

Im Dialog „Stimme und Einstellungen" steht unter der Stimmenliste der Punkt „Zweite Stimme für Gespräche".

Wählst du dort eine zweite Stimme, spricht sie alles, was in Anführungszeichen steht, also das, was die Figuren sagen. Alles andere liest die Stimme, die du oben gewählt hast.

Drei Dinge dazu, damit nichts überrascht:

- Der Reader unterscheidet **nicht**, wer gerade spricht. Alle Figuren bekommen dieselbe zweite Stimme. Bei einem echten Roman steht bei vier von fünf Sätzen nirgends, wer sie sagt — geraten würde also meistens falsch, und eine falsche Stimme ist schlimmer als eine einzige.
- Beide Stimmen sprechen im selben Tempo, nämlich dem, das du eingestellt hast.
- Das Vorbereiten dauert etwas länger, weil der Text an jedem Anführungszeichen geteilt wird. Gemessen wurden 14 Prozent mehr.

Beide Stimmen müssen aus derselben Sprachausgabe kommen. Wechselst du die Sprachausgabe, ist die zweite Stimme wieder abgewählt.

## Erzeugtes Audio

Das erzeugte Audio liegt auf dem Telefon, damit Sprünge und Fortsetzen funktionieren. Der Dialog „Stimme und Einstellungen" zeigt unten, wie viel es ist.

Ab fünfhundert Megabyte räumt die App selbst auf und wirft das am längsten nicht Gehörte weg. Brauchst du es wieder, erzeugt sie es neu. Die Taste „Erzeugtes Audio löschen" räumt alles auf einmal weg. Deine Dokumente und Hörpositionen bleiben dabei erhalten.

## Wenn etwas nicht geht

**„Diese Sprachausgabe meldet keine deutsche Stimme."** Öffne „Stimme und Einstellungen" und wähle unter „Sprachausgabe" eine andere. Hilft das nicht, tippe oben auf „Sprachausgabe prüfen" und schicke den Bericht weiter.

**Es kommt kein Ton, obwohl „Pause" dasteht.** Warte einen Moment. Beim ersten Start eines Abschnitts wird Audio erzeugt. Der Zeittext unter dem Fortschritt sagt, wie viel schon bereit ist.

**„Dieser Abschnitt enthält keinen lesbaren Text."** Die Seite ist eingescannt. Texterkennung fehlt der App noch. Ein anderer Abschnitt kann funktionieren.

**„Kein lesbarer Text gefunden."** Das ganze PDF ist eingescannt. Damit kann die App noch nichts anfangen.

**„Dieses PDF ist passwortgeschützt."** Es braucht eine entsperrte Kopie.

**Das PDF ist zu groß.** Die Grenzen liegen bei dreitausend Seiten, sechs Millionen Zeichen und hundertzwanzig Megabyte.

## Was noch fehlt

- Texterkennung für eingescannte Seiten
- Eigene Lesezeichen und eine Suche
- Die gesprochene Abschnittsansage lässt sich nicht abschalten
- Wird die App aus der Übersicht der letzten Apps entfernt, spielt sie nur den laufenden Abschnitt zu Ende. Pause und die dreißig Sekunden gehen weiter, der Abschnittswechsel nicht
- Eine Hörposition je Dokument
