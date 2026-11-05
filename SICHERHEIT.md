# Sicherheit und Betriebsgrenzen

## Implementierte Kontrollen

- Authentifizierter Confluence-Benutzer für alle Plugin-Routen.
- Administrationsrecht auf allen Konfigurations-/Vorschauzugriffen.
- Vor Metadaten-/Inhaltszugriff effektive Seitenberechtigung; keine
  Administrator-Exemptions. Verweigerte Teilbäume werden abgeschnitten.
- Anhang und Container werden beim Laden und erneut beim Download geprüft.
- Knotengebundene, zufällige Auftrags-IDs mit zusätzlicher stabiler
  Benutzer-Key-Prüfung; die ID allein reicht nicht zum Zugriff.
- Status/Download prüfen die aktuellen Berechtigungen aller im Snapshot
  enthaltenen Seiten und Anhänge erneut.
- Konfigurationsänderungen/Exportstart nur per POST mit kryptografisch
  zufälligem sitzungsgebundenem Token und konstantzeitlichem Vergleich.
- URL-kodierte POST-Daten maximal 64 KiB, begrenzte Feldanzahl/-namen,
  keine doppelten Felder und keine Übernahme von Query-Parametern in POST.
- Ausgaben werden HTML-escaped. Inhalt wird durch eine enge Jsoup-Allowlist
  geführt; keine Skripte, Inline-Styles, Eventhandler oder aktiven Einbettungen.
- Konfiguration erlaubt nur validierte CSS-Einzelwerte, kein freies CSS/HTML.
- Renderer erhält einen W3C-DOM aus Jsoup, keine extern gelesene XML-Datei.
  Keine XML-DTD-/Entity-Auflösung über Benutzereingaben.
- Ressourcenauflösung ausschließlich für vorab berechtigte `memory:`-Bilder.
  Beide OpenHTMLtoPDF-Zugriffskontrollen und der URI-Resolver verweigern
  HTTP(S), `file:`, `data:` und unbekannte Ressourcen.
- PNG-/JPEG-Signaturen und Pixelgrenzen vor Dekodierung; Re-Encoding
  entfernt Originalmetadaten. ImageIO verwendet explizite RAM-Streams.
- Bounded Queue, feste Workerzahl, Byte-/Seiten-/Bildgrenzen, keine
  PDF-Veröffentlichung als öffentlicher Anhang oder lokale Datei.
- `Cache-Control: no-store, private` für Antworten; keine geschützten Inhalte
  oder Original-Rendererfehlermeldungen in der Plugin-Fehlerausgabe.

## Nicht zugesicherte Eigenschaften

Eine PDF-Inhalts-/HTML-Sanitierung ist **kein Beweis vollständiger
Ressourcenisolierung der gesamten Confluence-Instanz**: Confluence-Makros
werden zuvor durch den Host im effektiven Benutzerkontext ausgeführt. Solche
Makros können entsprechend ihrer eigenen Implementierung Dienste aufrufen.
Die Netzwerksperre hier bezieht sich auf den nachgelagerten PDF-Renderer.

Zeitlimits werden zwischen Erfassungsschritten sowie nach dem Rendern geprüft.
Ein einzelner blockierender Host-Makroaufruf oder Layoutschritt kann nicht sicher
mitten im JVM-Thread abgebrochen werden. Ein hartes Zeit-/Heapbudget pro Auftrag
ist nicht implementiert. Die Output-/PDF-Seitengrenze greift erst nach bzw.
während der PDF-Ausgabe, nicht vor sämtlicher Layoutallokation.

Quellseiten werden während einer Transaktion erfasst, aber es gibt keinen
clusterweiten unveränderlichen Dokument-Snapshot. Rechteänderungen werden
bei Abruf erneut geprüft; eine atomare Sperre gegen gleichzeitig erfolgenden
Rechteentzug während der letzten Downloadbytes ist nicht möglich.

Externe HTTP(S)- und Mail-**Links** dürfen als PDF-Verweise erhalten bleiben;
der Renderer lädt ihre Ziele nicht. Klickt ein Empfänger darauf, entscheidet
dessen PDF-Viewer über den Zugriff.

Einige Renderer-/PDFBox-Fallbackfonts können auf Systemschriften des Hosts
zugreifen. Dies ist keine Freigabe von `file:`-URLs aus Seiteninhalten.
Die Standardschrift für Dokumenttext wird aus der PDFBox-Bibliothek eingebettet.

## Vor Produktivfreigabe

Die Tests sind lokale Funktions-/Negativtests, kein unabhängiges Security-Audit.
Anmeldung, Superuser-/Anhangsrechte, UPM-/OSGi-Integration, CSRF unter dem
realen Proxy, Makros, große Dokumente und Cluster-Szenarien müssen zusätzlich
in einer echten Testinstanz geprüft werden. Bibliotheksupdates regelmäßig
übernehmen und dabei PDF- sowie Pakettests erneut ausführen.
