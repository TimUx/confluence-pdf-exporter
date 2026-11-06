# Installation und Releases

## Voraussetzungen

- Confluence Data Center 10.2.19 auf Java 21 als Ziel.
- System-/App-Verwaltungsrechte zum Installieren.
- Vorheriges Backup und isolierte Testinstanz mit repräsentativen Seiten,
  Berechtigungen und Makros.
- Für Cluster: Session-Affinität am Load Balancer; ausreichend Heap pro Knoten.
- Build: JDK 21, Maven ab 3.9.9, Zugriff auf Maven Central und
  `https://packages.atlassian.com/maven-public/`.

Die genannten Confluence-APIs wurden gegen die veröffentlichten
10.2.19-Artefakte kompiliert. Dies ersetzt **keine Laufzeitkompatibilitätsprüfung**.
Eine erfolgreiche Installation kann hier mangels Testinstanz nicht bestätigt werden.

## Eigenes Plugin-JAR bauen

```sh
mvn -B -ntp -Djava.awt.headless=true clean verify
```

Installationsartefakt:
`confluence-plugin/target/confluence-pdf-exporter-1.0.0-SNAPSHOT.jar`.
Es enthält die komplette PDF-Laufzeit. Atlassian-/Servlet-APIs werden aus dem
Host bezogen. Weder Maven noch ein SDK sind auf dem Produktivserver erforderlich.

## In der Testinstanz

1. App-Verwaltung öffnen und das oben genannte JAR hochladen.
2. Die eigene UPM-Upload-/Signaturpolitik beachten. Das JAR ist nicht als
   Marketplace-geprüft oder signiert ausgewiesen. Nicht pauschal
   Sicherheitseinstellungen deaktivieren.
3. Aktivierung aller Plugin-Module und OSGi-/Spring-Logs prüfen.
4. Administration öffnen; dann Einzelseiten- und Teilbaumexport testen.
5. Die Checkliste in [TESTS.md](TESTS.md) vollständig durchführen,
   einschließlich Rechteentzug zwischen Erzeugung und Download.
6. Erst danach kontrolliert produktiv ausrollen.

Bei `Plugin 'io.github.timux.confluence.pdf' has no container` beim Aufruf
der Servlet-Route zuerst sicherstellen, dass das installierte JAR die
Spring-Scanner-Metadaten enthält und nicht noch ein älterer Plugin-Build aktiv
ist. Der Servlet-Endpunkt benötigt die vom Scanner erzeugte Serviceverdrahtung.

Falls ein Menü nicht sichtbar ist, die Modulaktivierung und Confluence-Menüposition
prüfen. Die direkten relativen Routen sind
`/plugins/servlet/universal-pdf/admin` und
`/plugins/servlet/universal-pdf/export?pageId=<Seiten-ID>`, jeweils unter dem
Confluence-Kontextpfad. Diese Routen umgehen keine Berechtigungsprüfung.

## GitHub Actions

`test.yml` baut und prüft auf Java 21 bei jedem Push/PR und speichert das JAR,
das Paket-Smoke-PDF sowie Surefire-/Failsafe-Berichte als Workflow-Artefakt.

`release.yml` wird nur durch einen semantischen Tag `vX.Y.Z` gestartet:

1. Version in den Maven-POMs setzen, ohne den Branch zurückzuschreiben.
2. Vollständiges `clean verify`.
3. JAR, Projektquellen, OpenHTMLtoPDF-Quell-JARs und SHA-256-Prüfsummen sammeln.
4. GitHub-**Entwurfsrelease** mit den Dateien erstellen.

Der Workflow kontrolliert, dass das JAR vorhanden ist und den
`atlassian-plugin.xml`-Descriptor enthält. Nach dem Release lädt er das
JAR-Asset erneut herunter und vergleicht es bytegenau mit dem gebauten Bundle.
Ein realer Release wurde bisher noch nicht erzeugt; der Workflow-Nachweis
bleibt bis zu einem Versionstag offen.

Das Projektquellarchiv enthält die Original-POMs vom Tag; die Release-Version
wird reproduzierbar im Build gesetzt. Vor Veröffentlichung müssen
Installationsabnahme, Lizenzen und Release Notes geprüft werden. Der Workflow
installiert nichts in Confluence und ändert keinen Standardbranch.

## Upgrade, Deinstallation und Fehlerdiagnose

- Laufende Exporte vor Update/Deaktivierung beenden; PDF-Aufträge gehen verloren.
- Konfiguration vorher als JSON exportieren. Das SAL-Schema bleibt Version 1;
  unbekannte Schemas werden nicht still migriert.
- Bei Renderingfehlern zuerst mit einer kleinen Textseite ohne Bilder testen.
- Fehlende/geschützte Anhänge: Logo-ID und Rechte der ausführenden Benutzer prüfen.
- Unbekannte Aufträge im Cluster: Knotenaffinität und Neustarts prüfen.
- Generische interne Fehler enthalten eine Referenz; im Serverlog nach dieser
  Referenz suchen. Geschützte Dokumentinhalte werden nicht als Fehlermeldung
  protokolliert.
- Deinstallation entfernt keine Originalseiten/Anhänge. Persistierte SAL-Werte
  werden nicht automatisch gelöscht; Aufbewahrungsregeln des Betriebs beachten.
