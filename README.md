# Universal PDF Exporter

Ein eigenständiges P2-Plugin für **Confluence Data Center 10.2.19 / Java 21**.
Die PDF-Erstellung läuft in der Confluence-JVM, ohne externen Dienst,
Browserprozess oder zusätzliche Laufzeitinstallation. Das Design ist neutral;
es gibt keine mitgelieferten Kundenlogos, kundenspezifischen Inhalte oder
vorkonfigurierten Anhänge.

> **Status:** Aus einem Repository mit ausschließlich README neu implementiert.
> Der aktuelle lokale Abschlusslauf `mvn clean verify` auf Java 21 ist
> erfolgreich: 34 Tests ohne Fehler.
> **Keine echte Confluence-Instanz stand zur Verfügung.** Installation,
> Spring-/OSGi-Serviceverdrahtung, Menüs und Clusterbetrieb sind deshalb noch
> nicht abgenommen. Dies ist ein testbarer erster Funktionsstand, keine
> Zusage vollständiger Produktionsreife. Details: [QA-STATUS.md](QA-STATUS.md).

## Funktionen

- Einzelseiten- und hierarchischer Teilbaumexport; Quellreihenfolge bleibt
  erhalten. Nicht lesbare Teilbäume werden vor Titel-/Inhaltszugriff übersprungen.
- Automatischer Startseitentitel auf Deckblatt, Kopfzeile, in PDF-Metadaten
  und Download-Dateiname.
- Globale, versionierte Vorlage: Deckblatt, Kopf-/Fußzeile, Seitenzahlen,
  A4/A3, Orientierung, Ränder, Schriftgröße, Zeilenabstand und Farben.
- PDF-Inhaltsverzeichnis mit Überschriften H1–H6, echten CSS-Zielseitenzählern,
  Punktlinien und PDF-internen Links; mehrseitiges Verzeichnis getestet.
- Text, Listen, Tabellen, Code, Zitate und statische Confluence-Makroausgabe;
  vorhandene Inhaltsverzeichnis-Makroausgabe wird aus der Exportkopie entfernt.
- PNG-/JPEG-Anhänge sowie zwei unabhängig hochladbare, global gespeicherte Logos.
  Keine HTTP-/Datei-URL-Auflösung durch den PDF-Renderer.
- Deutsche Administrations-/Exportformulare, JSON-Import/-Export, Standardwerte,
  Wiederherstellung der vorherigen Konfiguration und ungespeicherte PDF-Vorschau.
- Datenbankgestützte SAL-Konfiguration ohne eigenen Knotencache,
  Cluster-Schreibsperre und optimistische Revisionserkennung. Logo-Uploads
  verwenden ebenfalls die globale PluginSettings-Speicherung.
- Asynchrone PDF-Renderer mit begrenzter Warteschlange, benutzergebundenem
  Auftragsstatus und erneutem Berechtigungscheck bei jedem Download.
- Ein einzelnes Plugin-JAR mit eingebetteten, nicht exportierten Laufzeitbibliotheken.

## Bauen und testen

Voraussetzungen: vollständiges **JDK 21**, **Maven ≥ 3.9.9** und Zugriff auf
Maven Central sowie das öffentliche Atlassian-Artefaktrepository.

```sh
mvn --batch-mode --no-transfer-progress -Djava.awt.headless=true clean verify
```

Das Ergebnis ist:

```text
confluence-plugin/target/confluence-pdf-exporter-1.0.0-SNAPSHOT.jar
```

Nur dieses JAR installieren, **nicht** `renderer-core` und keine
Abhängigkeiten einzeln. Der Build benötigt kein Atlassian SDK und startet keine
Confluence-Instanz. Die `bundle`-Paketierung erzeugt P2-Descriptor,
OSGi-Manifest und `Bundle-ClassPath`. Der isolierte Pakettest rendert ein PDF
aus den tatsächlich im JAR enthaltenen Bibliotheken.

## Installation und Benutzung

1. In einer gesicherten Testinstanz über die App-Verwaltung das Plugin-JAR
   hochladen; ggf. UPM-Signatur-/Uploadrichtlinien der Instanz beachten.
2. Unter **Administration → Universal PDF Exporter** die globale Vorlage prüfen.
3. Auf einer lesbaren Seite **Als PDF exportieren (Universal)** wählen.
4. Unterseiten, Inhaltsverzeichnis und Seitenzahlen auswählen, Auftrag starten,
   Status aktualisieren und PDF herunterladen.

Für einen Cluster ist **Session-Affinität zum ursprünglichen Knoten erforderlich**.
Aufträge/PDFs bleiben im Knotenspeicher und werden nicht repliziert. Sie gehen
bei Neustart oder Knotenausfall verloren; ein Export muss dann neu gestartet
werden. Ein clusterweiter Lock lässt jeweils nur einen PDF-Renderer gleichzeitig
laufen. Ein Failover-/Wiederaufnahmeverfahren und ein global replizierter
Auftragsstatus sind nicht implementiert.

## Bewusste Grenzen

Die CSS-Eingabe erlaubt nur explizit freigegebene Selektoren/Eigenschaften;
freies HTML, URL-Werte, externe Ressourcen und CSS-Regeln mit aktiven
Funktionen werden abgewiesen. Logos können als PNG/JPEG bis 500 KiB im
Confluence-Plugin-Speicher hochgeladen oder aus berechtigten Anhängen geladen
werden. Schriftarten-Uploads und eine vollständige visuelle Layoutdesigner-
Oberfläche bleiben offen. Interaktive Makros, SVG, externe Bilder und eigene
Seitenskripte werden nicht ausgeführt. Fehlende Inhalte erhalten Ersatzhinweise.

Seitenerfassung und Confluence-Makroausgabe laufen begrenzt im Request/Transaktionskontext;
nur die nachfolgende PDF-Erzeugung ist asynchron. Zeitlimits sind **kooperativ**,
kein harter JVM-Threadabbruch. Außer dem clusterweit serialisierten Renderer
gibt es keine clusterweite Exportquote oder garantierte Failover-Verfügbarkeit.

## Dokumentation

- [Installation und Release](INSTALLATION.md)
- [Administration und Logo-Austausch](ADMINISTRATION.md)
- [Benutzerhandbuch](BENUTZERHANDBUCH.md)
- [Architektur](ARCHITEKTUR.md)
- [Sicherheitsgrenzen](SICHERHEIT.md)
- [Tests und Abnahmeschritte](TESTS.md)
- [Validierungsstatus](QA-STATUS.md)
- [Änderungen](CHANGELOG.md)
- [Drittanbieter-Lizenzen](THIRD-PARTY-NOTICES.md)

GitHub Actions führt `clean verify` bei Push und Pull Request aus.
Dependabot prüft Maven- und Actions-Abhängigkeiten wöchentlich. Ein Tag
`vX.Y.Z` erzeugt nach erfolgreichen Tests einen **Entwurfsrelease** mit
Plugin-JAR, Quellarchiven und SHA-256-Prüfsummen; keine automatische
Produktivfreigabe. Der Release-Workflow wurde noch nicht mit einem Versionstag
ausgeführt; es gibt daher noch keinen verifizierten Release-Anhang.
