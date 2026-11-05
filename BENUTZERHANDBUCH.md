# Benutzerhandbuch

## PDF erstellen

1. Auf der gewünschten Confluence-Seite anmelden.
2. Im Seitenmenü **Als PDF exportieren (Universal)** wählen.
3. Optional **Unterseiten einschließen** aktivieren.
4. Verzeichnis und Seitenzahlen nach Bedarf abschalten.
5. **PDF erzeugen** wählen und den Status über **Status aktualisieren** abfragen.
6. Bei **PDF fertig** den Download starten.

Titel, Deckblatt, Farben, Logos und Dateiname stammen aus der Startseite bzw.
der globalen Vorlage. Es muss kein Dokumenttitel eingegeben werden.
Die Seiten werden nicht verändert. Nicht lesbare Unterseiten einschließlich
ihrer Nachkommen werden ausgelassen, ohne deren Titel im PDF aufzunehmen.

## Inhalte und Hinweise

Normale Texte, Überschriften, Listen, Tabellen, Zitate und Code werden statisch
ausgegeben. Das PDF-Verzeichnis ersetzt die erkannte Ausgabe eines bestehenden
Inhaltsverzeichnis-Makros in der Exportkopie. Verzeichniseinträge sind anklickbar.
Die angezeigten Seitenzahlen zählen das Deckblatt mit; auf dem Deckblatt selbst
wird keine Seitenzahl angezeigt.

Anhänge müssen als PNG oder JPEG vorliegen und lesbar sein. Externe Bilder
werden nicht aus dem Internet nachgeladen. Interaktive Inhalte erhalten
Ersatzhinweise; zusätzliche Warnungen stehen im Auftragsstatus. Komplexe
Makros können von der Browseransicht abweichen. PDF-Inhalt vor Weitergabe prüfen.

## Häufige Meldungen

- **Seite/Anhang nicht verfügbar:** Zugriffsrechte, Löschung und ggf.
  administrativ ausgewählte Logo-Anhänge prüfen lassen.
- **Formular abgelaufen:** Formular neu öffnen; kein Token aus anderen
  Sitzungen oder Tabs übernehmen.
- **Exportwarteschlange/Auftragsspeicher voll:** Später erneut versuchen;
  fertige Ergebnisse werden nach zehn Minuten automatisch entfernt.
- **Ein Export läuft bereits:** Vorhandenen Auftrag abschließen lassen.
- **Ressourcenlimit/Renderingfehler:** Kleineren Teilbaum oder einzelne Seite
  versuchen; Administration mit dem Fehlerzeitpunkt informieren.
- **Auftrag nicht verfügbar:** Sitzung/Benutzer, Knotenwechsel, zehnminütige
  Aufbewahrung oder Serverneustart prüfen; ggf. Export neu starten.

Nur der Auftraggeber kann Status und Datei abrufen. Auch ein zweiter Download
prüft die aktuellen Rechte erneut. Nach einem Rechteentzug kann die Datei
nicht weiter vom Plugin abgerufen werden; bereits heruntergeladene Kopien
kann das Plugin selbstverständlich nicht zurückrufen.
