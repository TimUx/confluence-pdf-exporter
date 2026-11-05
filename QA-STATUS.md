# Qualitäts- und Validierungsstatus

Stand: 2026-11-05.

## Belegt

- Repository enthielt nur eine kurze README, kein Maven-Starterprojekt.
- JDK 21 und Maven 3.9.11 wurden für die lokale Prüfung bereitgestellt.
- Confluence-Artefakt 10.2.19 und die verwendeten SAL-/Beehive-/Renderer-APIs
  konnten aus dem öffentlichen Atlassian-Repository bezogen werden.
- Der Quellcode wird gegen diese echten API-Artefakte kompiliert, nicht gegen
  selbst geschriebene Confluence-API-Stubs.
- Maven erzeugt ein P2-/OSGi-Plugin-JAR mit eingebetteten PDF-Abhängigkeiten.
- Lokaler Abschlusslauf mit JDK 21 / Maven 3.9.11:
  `mvn -B -ntp -Djava.awt.headless=true clean verify` — **BUILD SUCCESS**,
  **31 Tests**, keine Fehler, Fehlschläge oder übersprungenen Tests:
  18 Kern-Unit-Tests, 5 PDF-Integrationstests, 7 Plugin-Vertragstests und
  1 isolierter Pakettest.
- Erzeugtes Installationsartefakt:
  `confluence-plugin/target/confluence-pdf-exporter-1.0.0-SNAPSHOT.jar`.
- Paket-Smoke-Test rendert mit isolierter Bibliotheksauflösung aus dem
  tatsächlich erzeugten Plugin-JAR.
- Workflow-/Dependabot-YAML wurde lokal syntaktisch geparst.
  Der Quellartefakt-Sammelschritt für die OpenHTMLtoPDF-Releasequellen
  wurde lokal erfolgreich ausgeführt.

## Nicht belegt / Freigabeblocker

**Keine Confluence-Instanz, kein Cluster und kein UPM-Testsystem wurden bereitgestellt.**
Nicht validiert sind daher Installation/Aktivierung, OSGi-Host-Importauflösung,
Spring-Injection, Web-Menüs, Host-XHTML-Makroverhalten, Persistenz nach echtem
Neustart, effektive Rechte in realen Daten und Clusterpropagierung.
Es wird kein Marketplace-/Data-Center-Kompatibilitätszertifikat behauptet.

Die Workflows wurden implementiert; ein GitHub-gehosteter Lauf oder Release
wurde aus dieser Umgebung nicht ausgelöst. Es wurde kein Standardbranch
direkt überschrieben und kein Pull Request automatisch gemergt.

## Noch nicht implementierte Spezifikationsbereiche

- Freier HTML-/CSS-Editor und frei gestaltbare Template-Platzhalter.
- Vollständiger Layoutdesigner für alle Überschriften-/Tabellen-/Logo-/Footerdetails,
  Hintergrundbilder, Designbalken, eigene Schriftfamilien/Uploads.
- Eigener Datei-Upload im Plugin (statt regulärer Confluence-Anhänge).
- Clusterweite Auftrags-/PDF-Replikation, Wiederaufnahme nach Node-Ausfall,
  verteilte Parallelitätsquoten und clusterweite Deduplizierung.
- Harte, mitten im Layout garantierte Laufzeit-/Speicherbegrenzung.
- Rein asynchrone Host-Seitenerfassung; sie erfolgt aktuell im Requestkontext.
- Vollständiges revisionssicheres personenbezogenes Audit-Journal.
- Zuverlässige Darstellung beliebiger interaktiver/Drittanbieter-Makros,
  SVG/Browserinhalte und vollständige Browser-CSS-Unterstützung.

Die vorhandene Implementierung ist substanziell ausführbar und getestet,
deckt aber nicht alle ursprünglichen Abnahmekriterien ab.
Vor Produktionsfreigabe mindestens die manuelle Testmatrix in [TESTS.md](TESTS.md)
durchführen und diese Liste nachvollziehbar aktualisieren.
