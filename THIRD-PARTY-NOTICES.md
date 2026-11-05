# Drittanbieter-Lizenzen

Die Maven-Abhängigkeiten werden unverändert in separaten JARs eingebettet.
Ihre `META-INF/LICENSE`, `NOTICE` und übrigen Herkunftshinweise bleiben erhalten.
Die konkrete aufgelöste Versionierung ist über `mvn dependency:tree` und den
`Embedded-Artifacts`-Manifestabschnitt des gebauten Plugins nachvollziehbar.

## Enthaltene Laufzeitbibliotheken

- OpenHTMLtoPDF (`io.github.openhtmltopdf`, Core und PDFBox-Adapter),
  Version 1.1.108: **LGPL 2.1 oder später**. Projekt:
  <https://github.com/openhtmltopdf/openhtmltopdf>.
  Unmodifizierte Bibliotheks-JARs bleiben austauschbar auf dem
  `Bundle-ClassPath`; Änderungen an diesen Bibliotheken sind nicht enthalten.
  Der Releaseworkflow legt die zugehörigen Quell-JARs separat bei.
  Quellartefakte sind zusätzlich über Maven Central unter demselben
  Group-/Artifact-/Versionspfad mit `-sources.jar` verfügbar.
  Bei manueller Weitergabe des Plugins diese Quellen und Lizenzinformationen
  ebenfalls bereitstellen. Modifikation/Debugging der LGPL-Komponenten wird
  durch dieses Projekt nicht untersagt.
- Apache PDFBox, PDFBox IO, FontBox und XMPBox 3.0.8:
  **Apache License 2.0**, einschließlich ihrer eingebetteten Drittanbieterhinweise.
  <https://pdfbox.apache.org/>.
- `de.rototor.pdfbox:graphics2d` 3.0.1: **Apache License 2.0**.
  <https://github.com/rototor/pdfbox-graphics2d>.
- Apache Commons Logging 1.4.0: **Apache License 2.0**.
- Jsoup 1.24.1: **MIT License**. <https://jsoup.org/>.
- Gson 2.15.0 und Error Prone Annotations 2.50.0:
  **Apache License 2.0**.
- Die aus PDFBox geladene Liberation-Sans-Schrift:
  **SIL Open Font License 1.1**. Vollständiger Lizenz-/Copyrighttext liegt
  in `pdfbox-3.0.8.jar!/META-INF/LICENSE`.
  Der interne CSS-Alias „Export Sans“ verändert die Schriftdatei nicht.

## Nicht mitgelieferte Host-/Testkomponenten

Confluence, SAL, Beehive, Atlassian User/Core und die Jakarta-Servlet-API
sind `provided`, nicht Teil des Installations-JARs. Die Verwendung von Confluence
unterliegt den Lizenzbedingungen des Betreibers.
JUnit und Mockito sind ausschließlich Testabhängigkeiten und werden nicht
in das Plugin gepackt.

Dies ist eine technische Lizenzinventur, keine Rechtsberatung. Vor externer
Distribution die tatsächlich gebauten JAR-Inhalte und Lizenzpflichten erneut
prüfen, insbesondere bei Abhängigkeitsupdates.
