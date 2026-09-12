# Entscheidung 0004: Texterkennung läuft auf dem Gerät

Datum: 12. September 2026

Status: angenommen

## Ausgangslage

Die Testnutzerin: „OCR wäre schon wichtig, weil die meisten Bücher eigentlich eingescannt sind."

Ein eingescanntes Buch enthält keinen Text, sondern Bilder von Text. Der Reader konnte solche Bücher nur ablehnen. Damit war der größte Teil ihrer Bibliothek für ihn stummes Papier.

Drei Wege standen zur Wahl:

1. **Ein Cloud-Dienst.** Google Vision, Azure, AWS. Beste Erkennung, aber jedes Buch verlässt das Gerät, es kostet pro Seite, und die App bräuchte eine Netzberechtigung. Das widerspricht dem Kern des Projekts.
2. **Texterkennung auf dem Gerät mit ML Kit.** Das Modell liegt im Paket, es läuft ohne Netz.
3. **Gar nichts tun** und beim Öffnen früh sagen, dass es nicht geht.

## Messung statt Meinung

Ein 30-seitiger Scan wurde aus einem echten Buch erzeugt, indem die Seiten zu Bildern gerendert wurden. Gemessen auf dem Emulator, **mit abgeschaltetem WLAN und abgeschaltetem Mobilfunk**:

| Größe | Wert |
| --- | --- |
| pro Seite, rendern und erkennen | rund 1,2 Sekunden |
| 30-seitiger Scan, vollständiger Import | 28 Sekunden |
| hochgerechnet auf 500 Seiten | etwa 10 Minuten, einmalig beim Import |

Der erkannte Text war korrekt, Umlaute eingeschlossen, und die Trennstriche am Zeilenende repariert die vorhandene Textaufbereitung mit.

Die Auflösung wurde ebenfalls gemessen, nicht geschätzt. Von 764 bis 1200 Pixel Seitenbreite lieferte die Erkennung Zeichen für Zeichen denselben Text, während eine Seite von 1,2 auf 1,5 Sekunden stieg. Gerendert wird deshalb mit dem Nötigen: mindestens doppelte Seitenbox, mindestens 900 Pixel breit.

Eine erste Messung hatte 251 Millisekunden pro Seite ergeben und ließ sich nicht wiederholen. Sie war zu optimistisch, und die Zahl, die in dieser Entscheidung steht, ist die mehrfach bestätigte.

## Entscheidung

Weg 2. Eine Seite ohne Textebene wird gerendert und erkannt.

Die Bibliothek bringt `INTERNET` und `ACCESS_NETWORK_STATE` in ihrem eigenen Manifest mit. Beide werden im Manifest der App mit `tools:node="remove"` wieder entfernt. Geprüft wird das am gebauten Paket mit `aapt2 dump permissions`, nicht am Quelltext.

## Preis

- Das Paket wächst von 19 MB auf rund 60 MB. Das Modell und die Bibliotheken für alle Prozessorarchitekturen machen den Unterschied.
- Erkennung macht Fehler. Der Hinweis am Dokument sagt, wie viele Seiten durch die Erkennung gegangen sind.
- Zweispaltige Scans werden ihre Spalten ebenso mischen wie zweispaltige Textseiten.

## Was daraus folgt

Wenn die Paketgröße zum Problem wird, ist der nächste Schritt getrennte Pakete je Prozessorarchitektur. Das betrifft die Auslieferung, nicht den Code.
