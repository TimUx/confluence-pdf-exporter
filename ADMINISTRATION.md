# Administration

Zugriff: **Confluence Administration → Universal PDF Exporter**.
Jeder administrierende GET-/POST-Endpunkt prüft das Confluence-Administrationsrecht.
Änderungen benötigen zusätzlich einen sitzungsgebundenen CSRF-Token.

## Einfache Vorlage

Formularoptionen steuern:

- Exportfunktion, Deckblatt, Verzeichnis, Kopf-/Fußzeilen und Seitenzahlen.
- A4/A3 und Hoch-/Querformat.
- Rand 12–45 mm, Schrift 8–20 pt, Zeilenabstand 1–2.
- Deckblatttitel 18–54 pt, Text- und Akzentfarbe in `#RRGGBB`.
- Frei eingegebener Deckblatt-/Fußtext; Ausgabe ausschließlich als Text,
  niemals als aktives HTML. Copyright-/Vertraulichkeitstext bei Bedarf hier eintragen.
- Deckblattlogo-Breite 10–70 mm; das Kopfzeilenlogo wird auf 20 × 8 mm begrenzt.
- Quellseiten-, HTML-, Bild-, PDF- und kooperative Zeitgrenzen.

Änderungen gelten ab dem nächsten Export. Ein laufender Auftrag behält seinen
Konfigurationssnapshot. Anwender können keine Gestaltung überschreiben;
Verzeichnis/Seitenzahlen lassen sich nur abschalten, nicht gegen globale
Deaktivierung einschalten.

## Logos ohne Neubuild ersetzen

1. In der Administration PNG/JPEG im Formular „Logos hochladen“ direkt für
   Deckblatt oder Kopfzeile hochladen (maximal 500 KiB). Das Bild wird
   dekodiert, auf Rasterdaten begrenzt und ohne Originalmetadaten neu kodiert.
2. Alternativ ein PNG/JPEG regulär auf einer Confluence-Seite als Anhang
   hochladen und die numerische **Anhangs-ID** eintragen.
3. Für das verwendete Upload-Logo muss die jeweilige Anhangs-ID auf `0` stehen;
   anschließend speichern und Vorschau erzeugen.
4. Bei regulären Confluence-Anhängen mit einem normalen Benutzer testen, der
   die Logo-Anhangsseite lesen darf.

Die IDs `0` aktivieren das hochgeladene Logo, sofern vorhanden; ohne Upload
bleibt der Platz leer. Beide Logos sind unabhängig austauschbar. Uploads werden
in den globalen Confluence-Plugin-Einstellungen gespeichert. Bei regulären
Confluence-Anhängen prüft jeder Export Anhang und Container; die administrative
Auswahl gewährt anderen Benutzern **keine** zusätzlichen Leserechte.
Keine externen Logo-URLs, SVG-Dateien oder lokalen Dateipfade.

## Vorschau und Konfigurationstransfer

Im selben Formular eine lesbare Testseiten-ID eintragen und **Vorschau erzeugen**
wählen. Die ungespeicherten Werte werden nur für diesen administrativen
Einzelseitenauftrag verwendet; globale Einstellungen bleiben unverändert.

**JSON exportieren** liefert die vollständige Konfiguration (Schema 1).
Zum Import die vollständige JSON-Konfiguration ins Formular übernehmen.
Unbekannte/fehlende Felder und ungültige Werte werden abgewiesen. Das Formular
nennt die zulässigen Werte; die Java-Validierung ist verbindlich.

**Standardwerte wiederherstellen** speichert neutrale Defaults.
**Vorherige Konfiguration** stellt genau den letzten gültigen Stand wieder her.
Revision und Cluster-Schreibsperre verhindern unbemerktes Überschreiben
gleichzeitiger Änderungen. Bei Konflikten neu laden und Änderungen erneut anwenden.
Erfolgreiche Schreibzugriffe erzeugen einen generischen Audit-Logeintrag; dies ist
kein vollständiges revisionssicheres Administrationsjournal.

## Template- und Erweiterungsgrenzen

Der Dokumenttitel wird intern über DOM-Textknoten gesetzt. Es gibt keine frei
interpretierte Platzhaltersprache, insbesondere keine vom Benutzer auszuwertenden
HTML-/Velocity-Ausdrücke. Deckblatt, Kopfzeile, Metadaten und Dateiname werden
automatisch mit dem Startseitentitel befüllt.

Die Schrift wird eingebettet und intern als „Export Sans“ registriert.
Erweitertes CSS ist auf eine dokumentierte Selektor-/Eigenschafts-Allowlist
und begrenzte Farb-/Maßwerte beschränkt; `url()`, `@`-Regeln, unbekannte
Selektoren und freie HTML-Ausgabe werden zurückgewiesen. Der CSS-Editor kann
Überschriften, Tabellen, Text, Listen, Codeblöcke, Deckblatt, Kopf-/Fußzeile
und Inhaltsverzeichnis gestalten. Alternative Schriftfamilien,
Hintergrundbilder und ein freier visueller Designer bleiben offen.

## Ressourcenplanung

Clusterweit darf eine PDF-Renderaufgabe gleichzeitig laufen; pro Knoten bestehen
zwei Renderer-Worker-Slots und zwei Warteschlangenplätze, höchstens acht
gespeicherte Aufträge und ein aktiver Auftrag je Benutzer. Default: 100 Quellseiten, 4 MB HTML,
2 MB je Bild, 16 MB PDF, 60 Sekunden kooperatives Zeitlimit. Zusätzlich:
64 verschiedene Bilder, insgesamt 16 MB re-encodierte Bilder, 12 Millionen
Pixel je Bild, Tiefe 32, 5000 Verzeichniseinträge und 1000 erzeugte PDF-Seiten.
Maximal konfigurierbar: 200 Quellseiten, 8 MB HTML, 4 MB je Bild, 32 MB PDF,
120 Sekunden. Ein MB bedeutet hier 1.000.000 Bytes.

Acht fertige PDFs können allein bis zu 256 MB RAM belegen; Layout,
Schrift-/Bilddekodierung und aktive Snapshots benötigen zusätzlichen Heap.
Grenzen vor breiter Freigabe mit realen Dokumenten prüfen. Keine replizierten
Auftragsdaten, Failover-Wiederaufnahme oder harte Timeout-Garantie: siehe
[SICHERHEIT.md](SICHERHEIT.md).
