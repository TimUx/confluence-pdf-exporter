# Architektur

## Module und Verantwortlichkeiten

`renderer-core` hat keine Atlassian-Abhängigkeiten:

- `config.PdfSettings`: unveränderliches Konfigurationsschema 1, Defaults,
  JSON-Serialisierung und feldweise Wertevalidierung; `SafeCss` validiert
  optionale Layoutregeln gegen eine enge Eigenschafts-/Selektor-Allowlist.
- `export.DocumentCollector`: iterative Tiefensuche, Berechtigungsfilter,
  Quellseiten-/Byte-/Hierarchielimits; verweigerte Knoten werden nicht traversiert.
- `assets.ImageAssets`: ausschließlich PNG/JPEG, Dimensionsprüfung vor dem
  Dekodieren, Re-Encoding, RAM-Speicher und anonyme `memory:`-Adressen.
- `template.HtmlComposer`: HTML-Allowlist, Überschriftenanker, Verzeichnis,
  sichere Templates und generiertes CSS.
- `pdf.PdfEngine`: OpenHTMLtoPDF mit PDFBox 3, eingebettete Schrift,
  geschlossene Ressourcenauflösung, begrenzter Ausgabestream,
  PDFBox-Prüfung des erzeugten Dokuments.
- `export.ExportJobs`: feste Worker-/Queuegrenzen, Eigentümerprüfung,
  Statusübergänge und automatische RAM-Bereinigung.

`confluence-plugin` ist die P2-/Jakarta-Anbindung:

- `HostContent`: öffentliche ContentService-/AttachmentService-Finder statt
  veralteter Manager-Lademethoden; Host-Downloadpfade nur zur internen
  AttachmentManager-Binärdatenauflösung verwenden, nicht per HTTP.
- `ConfluenceDocumentSource`: effektive Benutzerberechtigungen,
  paginierte ChildContentService-Abfragen, Sortierung nach Position/Titel,
  öffentliche ContentBodyConversionService-Konvertierung nach `EXPORT_VIEW`,
  berechtigte AttachmentManager-Zugriffe und Überführung in den neutralen Kern.
- `GlobalSettingsStore`: SAL PluginSettingsFactory, SAL TransactionTemplate,
  ClusterLockService, SHA-256-Revision, vorherige Konfiguration und begrenzte
  Base64-kodierte PNG/JPEG-Logo-Assets in globalen Plugin-Einstellungen.
- `PdfServlet`: Formulare, Administration, Vorschau, Aufträge und Download.
- `Csrf`/`BoundedForm`: sitzungsgebundener Token und begrenztes POST-Parsing.

## Ablauf

1. Authentifizierung und ggf. Administrationsrecht prüfen.
2. POST-Body begrenzt lesen, Token prüfen; globale Konfiguration laden.
3. Maximal zwei gleichzeitige Snapshots je Knoten aufnehmen. Während einer
   SAL-Transaktion werden Seitendaten, Reihenfolge und Anhänge mit dem
   effektiven Benutzer gelesen. Kein Administrator-Exemption-Kontext.
4. Statische Host-Makroausgabe sanitieren; nicht freigegebene Ressourcen
   entfernen. Keine ORM-Objekte oder Benutzer-ThreadLocals an Worker übergeben.
5. Unveränderliche Exportdaten in eine begrenzte knotengebundene Queue geben.
6. PDF im Worker rendern. Eine Cluster-Sperre serialisiert die PDF-Renderer
   über Knoten hinweg. CSS `target-counter()` verwendet tatsächliche
   Layoutziele; keine geschätzten Seitenzahlen und keine zusätzliche
   PDFBox-Paginierungsrunde. Die internen Linkziele werden mit PDFBox getestet.
7. Status/Download nur für den Auftraggeber; aktuelle Seiten- und
   Anhangsberechtigungen auch bei wiederholtem Download erneut prüfen.

## Speicherung und Cluster

Die Konfiguration liegt in `PluginSettingsFactory.createGlobalSettings()`,
nicht in einer Datei oder statischen Map. Schreibzugriffe sind transaktional,
unter einer benannten Cluster-Sperre und mit erwarteter Revision abgesichert.
Es gibt keinen eigenen Konfigurationscache. Die tatsächliche SAL-Clusterpropagierung
ist auf einer Mehrknoteninstanz noch zu prüfen.

PDFs bleiben bewusst im RAM des Ursprungsknotens. Sie werden weder in
öffentliche Anhänge noch auf einen lokalen/shared Dateipfad geschrieben.
Die Cluster-Sperre begrenzt gleichzeitige Layout-/PDF-Erzeugung auf einen
aktiven Worker im Cluster, repliziert aber weder Queue noch Ergebnisdateien.
Nach zehn Minuten ab Auftragserstellung entfernt ein Minutentakt die
abgeschlossenen/fehlgeschlagenen Aufträge. Laufende Aufträge werden nicht
während des Renderns entfernt. Ein Auftrag wird nur einmal an den lokalen
Executor übergeben, aber es gibt keine clusterweite Deduplizierung separater
Benutzeranfragen.

## Bibliotheks-/OSGi-Grenze

Laufzeitbibliotheken sind unverändert als verschachtelte JARs auf dem
`Bundle-ClassPath` enthalten. Kein Export dieser Pakete und kein Import von
Host-PDFBox-/Gson-/Jsoup-Versionen. Host-APIs sind `provided`. Die Imports
für die verwendeten Confluence-, SAL-, Beehive- und Jakarta-Pakete sind
verpflichtend; optionale Bibliotheksfunktionen wie PDF-Signierung und zusätzliche
Logging-Bridges bleiben optionale Imports. Keine PDF-Signierung implementiert.
Der lokale Packaging-Test bestätigt Bibliotheksvollständigkeit, **nicht** die
OSGi-Auflösung in einer laufenden Confluence-Instanz.
