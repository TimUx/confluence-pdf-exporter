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
- Lokaler Abschlusslauf nach Nacharbeit mit JDK 21 / Maven 3.9.11:
  `mvn -B -ntp -Djava.awt.headless=true clean verify` — **BUILD SUCCESS**,
  **34 Tests**, keine Fehler, Fehlschläge oder übersprungenen Tests:
  20 Kern-Unit-Tests, 5 PDF-Integrationstests, 8 Plugin-Vertragstests und
  1 isolierter Pakettest.
- Erzeugtes Installationsartefakt:
  `confluence-plugin/target/confluence-pdf-exporter-1.0.0-SNAPSHOT.jar`.
- Paket-Smoke-Test rendert mit isolierter Bibliotheksauflösung aus dem
  tatsächlich erzeugten Plugin-JAR.
- Workflow-/Dependabot-YAML wurde lokal syntaktisch geparst.
  Der Quellartefakt-Sammelschritt für die OpenHTMLtoPDF-Releasequellen
  wurde lokal erfolgreich ausgeführt.
- GitHub Actions auf Commit `12b5d99`: Push- und Pull-Request-Verifikationsläufe
  beide **success** (Läufe `42079611247` und `42079630815`).
- GitHub Actions auf Commit `f8e3a07`: Push- und
  Pull-Request-Verifikationsläufe beide **success** (Läufe `42080949939`
  und `42080958897`).
- Nach Aktualisierung auf Node-24-kompatible Actions (`checkout@v5`,
  `setup-java@v5`, `upload-artifact@v7`, `ubuntu-24.04`) sind Push und PR
  auf Commit `c82a1a8` **success** (Läufe `42081544451` und `42081557482`).

## Nacharbeit auf demselben Branch

Nach dem Actions-Lauf wurden implementiert:

- Erweiterte CSS-Eingabe mit Allowlist für Selektoren/Eigenschaften und
  eingeschränkten Farb-/Maßwerten; aktive CSS-Ressourcen/Regeln werden
  zurückgewiesen.
- Upload unabhängiger Deckblatt-/Kopfzeilenlogos als PNG/JPEG bis maximal
  500 KiB und aktuelles Bildlimit. Dekodierung/Re-Encoding entfernt
  Originalmetadaten; die normalisierten Assets werden in globalen
  Plugin-Einstellungen abgelegt.
- Eine Cluster-Sperre serialisiert die PDF-Renderphase auf einen aktiven
  Worker im Cluster. Auftragsstatus und PDF-Ergebnisse bleiben knotengebunden.
- Der lokale Maven-Abschlusslauf nach diesen Änderungen ist oben dokumentiert.

## Nicht belegt / Freigabeblocker

**Keine Confluence-Instanz, kein Cluster und kein UPM-Testsystem wurden bereitgestellt.**
Nicht validiert sind daher Installation/Aktivierung, OSGi-Host-Importauflösung,
Spring-Injection, Web-Menüs, Host-XHTML-Makroverhalten, Persistenz nach echtem
Neustart, effektive Rechte in realen Daten und Clusterpropagierung.
Es wird kein Marketplace-/Data-Center-Kompatibilitätszertifikat behauptet.

Der Release-Workflow wurde noch nicht per Versionstag ausgeführt; ein real
angehängtes installierbares JAR ist daher nicht verifiziert. Das Repository
enthält derzeit keinen Release. Es wurde kein Standardbranch direkt
überschrieben und kein Pull Request automatisch gemergt.

## Noch nicht implementierte Spezifikationsbereiche

- Freier HTML-Editor und frei gestaltbare Template-Platzhalter.
- Vollständiger visueller Layoutdesigner für alle Header-/Footerdetails,
  Hintergrundbilder, Designbalken und eigene Schriftfamilien/Uploads.
- Clusterweite Auftrags-/PDF-Replikation, Wiederaufnahme nach Node-Ausfall,
  globale Benutzer-Deduplizierung und verteilte Queue-Quote. Implementiert ist
  nur die clusterweite Serialisierung der PDF-Renderphase.
- Harte, mitten im Layout garantierte Laufzeit-/Speicherbegrenzung.
- Rein asynchrone Host-Seitenerfassung; sie erfolgt aktuell im Requestkontext.
- Vollständiges revisionssicheres personenbezogenes Audit-Journal.
- Zuverlässige Darstellung beliebiger interaktiver/Drittanbieter-Makros,
  SVG/Browserinhalte und vollständige Browser-CSS-Unterstützung.

Die vorhandene Implementierung ist substanziell ausführbar und getestet,
deckt aber nicht alle ursprünglichen Abnahmekriterien ab.
Vor Produktionsfreigabe mindestens die manuelle Testmatrix in [TESTS.md](TESTS.md)
durchführen und diese Liste nachvollziehbar aktualisieren.
