# Changelog

## Unveröffentlicht

- Behoben: Admin- und Exportseite schlugen mit
  `Plugin 'io.github.timux.confluence.pdf' has no container` fehl. Das JAR
  enthält jetzt einen Spring-Kontext (`META-INF/spring/plugin-context.xml`,
  `Spring-Context: *`), damit Confluence einen Plugin-Container anlegt.
- Spring-Scanner-Runtime wird nicht mehr eingebettet (stellt Confluence bereit).
- `PdfServlet` ist kein zusätzlicher Spring-Bean mehr; das Servlet-Modul
  erzeugt die einzige Instanz (keine doppelten Export-Worker-Threads).
- Keine PDFBox-Warnungen `Using fallback font … for base font …` mehr pro
  Export. Die 14 nicht eingebetteten PDF-Standardschriften gelten nicht mehr
  als Fallback; die Schriftauswahl selbst ist unverändert.

## 1.0.0-SNAPSHOT — 2026-11-05

- Java-21-Maven-Reaktor aus leerem README-Repository erstellt.
- Testbarer OpenHTMLtoPDF-/PDFBox-3-Kern mit neutraler Universalvorlage.
- Confluence-10.2.19-P2-Adapter, Jakarta-Servlet, globale SAL-Konfiguration,
  Seiten-/Teilbaumexport und berechtigtes Attachment-Rendering.
- Deutsches Admin-/Exportformular, JSON-Verwaltung, Logo-Anhangsauswahl,
  ungespeicherte Vorschau und begrenzte asynchrone PDF-Aufträge.
- Sichere, begrenzte CSS-Regeln, direkte PNG-/JPEG-Logo-Uploads mit
  globaler PluginSettings-Speicherung und clusterweite Renderer-Sperre.
- Releaseworkflow prüft das erzeugte P2-JAR vor Upload und vergleicht nach
  dem Draft-Release das heruntergeladene Attachment bytegenau mit dem Build.
- Sicherheitskontrollen, PDF-/Pakettests, GitHub Actions und Dependabot.
- Build-/Installationsanleitung und expliziter Status aller nicht real
  validierten bzw. noch nicht implementierten Funktionen.

Keine Behauptung einer erfolgreichen Installation auf einer echten
Confluence-Instanz.
