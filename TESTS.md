# Tests und Abnahme

## Automatisierte Prüfung

```sh
mvn -B -ntp -Djava.awt.headless=true clean verify
```

Surefire führt `*Test` aus, Failsafe `*IT`. Alle Tests sind lokal und ohne
Confluence-Server ausführbar. „Integrationstest“ bezeichnet hier die Integration
der PDF-Bibliotheken bzw. des gebauten Pakets, **nicht** eine Serverinstallation.

Abgedeckt:

- Defaults, JSON-Roundtrip, Version/Felder, ungültige Farben und Limits.
- Tiefensuche, Reihenfolge, Tiefe, Einzel-/Teilbaumexport und Rechtefilter vor
  Metadaten-/Kindzugriff; Ressourcenüberschreitung und sichere Dateinamen.
- HTML-Bereinigung, aktive Inhalte, Makro-Verzeichnisentfernung, Anker,
  XXE-artige Eingaben und Verzeichnis-/Deckblattoptionen.
- PNG-/JPEG-Validierung, ablehnende Bildfälle und defensive Kopien.
- Auftragseigentümer, Wartestatus, Fehlerredaktion, aktive Benutzerquote
  und Ablauf fertiger Ergebnisse.
- SAL-Konfiguration mit gemeinsamem Mock-Speicher, Revisionskonflikt und
  Wiederherstellung. **Kein Nachweis echter Datenbank-/Clusterkonsistenz.**
- CSRF-Sitzungsbindung sowie Größen-/Duplikatkontrollen des POST-Parsers.
- Echte PDFs mit PDFBox auswerten: Deckblatt, Text, Titelmetadaten, eingebettete
  Fonts/Bilder, vierseitiges Dokument, echte Verzeichniszielzahlen 3/4,
  interne Linkziele, Seitenzähler, langes mehrseitiges Inhaltsverzeichnis
  und begrenzte Dateiausgabe. Ein lokaler HTTP-Testserver bestätigt null
  Rendererzugriffe auch bei gezielt unsanitierter Testeingabe.
- Hierarchische Nummerierung für kombinierte Seiten-/Überschriftenebenen;
  öffentliche Content-Finder, Host-Exportkonvertierung, Positionserhaltung
  und verweigerte Kinder in Adapter-Vertragstests mit Mocks.
- Gebautes P2-JAR auspacken: Descriptor/Manifest, Bibliotheksvollständigkeit,
  Ausschluss von Host-API-JARs. Renderer in isoliertem ClassLoader nur mit
  den im Plugin enthaltenen Bibliotheken ausführen.

Berichte: `renderer-core/target/{surefire,failsafe}-reports/` und
`confluence-plugin/target/{surefire,failsafe}-reports/`.
Der Pakettest erzeugt `confluence-plugin/target/package-smoke.pdf`.

## Manuelle Testmatrix für Confluence 10.2.19

Alle folgenden Punkte sind mangels Instanz **offen**:

1. **Installation:** JDK 21, JAR-Upload, Aktivierung sämtlicher Module,
   verpflichtende OSGi-Imports und Constructor-Injection ohne Fehler.
2. **UI:** Menü auf Seiten, Administration im Confluence-Layout,
   Kontextpfad ungleich `/`, deutschsprachige Beschriftungen.
3. **Authentifizierung:** anonym abweisen; normale Benutzer dürfen weder
   Konfiguration lesen/ändern noch Vorschauen mit fremden Einstellungen erzeugen.
4. **CSRF:** fehlender/falscher/fremder Token abweisen, GET verändert nichts,
   Formulare hinter realem Reverse Proxy/SSO testen.
5. **Berechtigungen:** Startseite und gemischter Baum mit gesperrten
   Unterseiten, keine Titel-/Metadatenlecks; private Anhänge und Logos.
6. **Widerruf:** Seiten-/Anhangsrecht nach PDF-Erstellung entziehen;
   Status und jeder erneute Download müssen scheitern.
7. **Layout:** Einzel-/Teilbaum, A4/A3, beide Orientierungen,
   lange Titel/Tabellen, Listen, Code und Bildgrößen.
8. **Makros:** Standard-, Hinweis-, Inhaltsverzeichnis- und Drittanbietermakros;
   statische Ausgabe mit Browserdruck vergleichen, Ersatzhinweise kontrollieren.
9. **Administration:** Speichern, Neustartpersistenz, Vorschau ohne Speichern,
   JSON-Roundtrip, ungültige Felder, Defaults und vorherige Konfiguration.
10. **Cluster:** zwei Knoten; Änderungen auf Knoten A auf B sehen,
    gleichzeitige Revisionen, Session-Affinität, verlorene Aufträge bei
    Node-Ausfall und dokumentierten Neustartablauf testen.
11. **Last:** Worker-/Queuegrenzen, parallele Benutzer, Bild-Pixelbomben,
    Zeitüberschreitungen, Heap, Speicherbereinigung und Plugin-Deaktivierung.
12. **Release:** Tagworkflow auf GitHub ausführen, Prüfsummen abgleichen,
    genau dessen JAR installieren. Release bleibt bis zur Abnahme ein Entwurf.

Ein grüner Maven-Build hakt diese Liste ausdrücklich nicht automatisch ab.
