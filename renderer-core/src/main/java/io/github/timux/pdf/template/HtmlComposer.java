package io.github.timux.pdf.template;

import io.github.timux.pdf.config.PdfSettings;
import io.github.timux.pdf.config.SafeCss;
import io.github.timux.pdf.export.ExportDocument;
import io.github.timux.pdf.export.ExportException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

public final class HtmlComposer {
    public record Prepared(Document html, Map<String, byte[]> assets, List<String> warnings) {}
    private record Entry(String id, int level, String text) {}
    private static final Safelist SAFE = Safelist.none()
            .addTags("p", "div", "span", "h1", "h2", "h3", "h4", "h5", "h6", "br", "hr",
                    "strong", "b", "em", "i", "u", "s", "sub", "sup", "ul", "ol", "li", "dl",
                    "dt", "dd", "blockquote", "pre", "code", "table", "thead", "tbody", "tfoot",
                    "tr", "th", "td", "caption", "a", "img")
            .addAttributes(":all", "id")
            .addAttributes("a", "href").addProtocols("a", "href", "https", "http", "mailto", "#")
            .addAttributes("img", "src", "alt").addProtocols("img", "src", "memory")
            .addAttributes("td", "colspan", "rowspan").addAttributes("th", "colspan", "rowspan")
            .addAttributes("ol", "start");

    public Prepared compose(ExportDocument document, PdfSettings settings, boolean toc, boolean pageNumbers,
                            Map<String, byte[]> assets, String coverLogo, String headerLogo) {
        var warnings = new ArrayList<String>();
        Document output = Document.createShell("");
        output.head().appendElement("meta").attr("charset", "UTF-8");
        output.title(document.title());
        output.head().appendElement("style").text(css(settings, pageNumbers && settings.pageNumbers())
                + SafeCss.validate(settings.customCss()));
        Element body = output.body();
        Element front = settings.cover() ? body.appendElement("div").attr("class", "cover") : body;
        if (settings.header()) {
            Element header = front.appendElement("div").attr("class", "document-header");
            header.appendElement("span").text(document.title());
            addImage(header, headerLogo, assets, "header-logo");
        }
        if (settings.footer()) front.appendElement("div").attr("class", "document-footer").text(settings.footerText());
        if (settings.cover()) {
            addImage(front, coverLogo, assets, "cover-logo");
            front.appendElement("h1").text(document.title());
            front.appendElement("p").text(settings.coverText());
        }
        Element contents = null;
        if (toc && settings.toc()) {
            contents = body.appendElement("div").attr("class", "contents");
            contents.appendElement("h1").text("Inhaltsverzeichnis");
        }
        var entries = new ArrayList<Entry>();
        var numbering = new OutlineNumbering();
        Element main = body.appendElement("div").attr("class", "main");
        int chapter = 0;
        for (ExportDocument.Section section : document.sections()) {
            String prefix = "s" + chapter++ + "-";
            Element part = main.appendElement("div").attr("class", "chapter");
            String chapterId = prefix + "chapter";
            String chapterTitle = numbering.next(section.depth()) + ". " + section.title();
            part.appendElement("h1").attr("id", chapterId).text(chapterTitle);
            entries.add(new Entry(chapterId, Math.min(section.depth(), 5), chapterTitle));
            Document raw = Jsoup.parseBodyFragment(section.html());
            raw.select(".toc-macro,.toc-macro-container,[data-macro-name=toc]").remove();
            if (!raw.select("script,style,link").isEmpty()) warnings.add("Aktive Inhalte und Seitenstile wurden entfernt.");
            raw.select("script,style,link").remove();
            for (Element unsupported : raw.select("iframe,object,embed,video,audio,canvas,svg,form,input,button,textarea,select")) {
                unsupported.replaceWith(new Element("p").text("[Interaktiver Inhalt nicht exportierbar]"));
                warnings.add("Interaktiver Inhalt wurde durch einen Hinweis ersetzt.");
            }
            if (!raw.select("[data-macro-name]").isEmpty())
                warnings.add("Makroausgabe wird statisch exportiert; interaktive Funktionen sind nicht verfügbar.");
            Document clean = new Cleaner(SAFE).clean(raw);
            int anchor = 0;
            var oldIds = new java.util.HashMap<String, String>();
            for (Element element : clean.select("[id]")) {
                String old = element.id();
                String id = prefix + "a" + anchor++;
                oldIds.putIfAbsent(old, id);
                element.attr("id", id);
            }
            int heading = 0;
            int minimumHeading = clean.select("h1,h2,h3,h4,h5,h6").stream()
                    .mapToInt(h -> Integer.parseInt(h.tagName().substring(1))).min().orElse(1);
            for (Element h : clean.select("h1,h2,h3,h4,h5,h6")) {
                if (h.id().isEmpty()) h.attr("id", prefix + "h" + heading++);
                int level = section.depth() + 1 + Integer.parseInt(h.tagName().substring(1)) - minimumHeading;
                String title = numbering.next(level) + " " + h.text();
                h.text(title);
                entries.add(new Entry(h.id(), Math.min(6, level), title));
            }
            for (Element link : clean.select("a[href]")) {
                String href = link.attr("href");
                if (href.startsWith("#")) {
                    String target = oldIds.get(href.substring(1));
                    if (target != null) link.attr("href", "#" + target);
                    else link.removeAttr("href");
                }
            }
            for (Element image : clean.select("img")) {
                if (!assets.containsKey(image.attr("src"))) {
                    image.replaceWith(new Element("span").text("[Bild nicht verfügbar]"));
                    warnings.add("Ein Bild wurde nicht freigegeben oder ist nicht verfügbar.");
                }
            }
            // Colspan/rowspan cannot be allowed to create unbounded layout work.
            for (Element cell : clean.select("[colspan],[rowspan]")) {
                for (String attr : List.of("colspan", "rowspan")) {
                    if (cell.hasAttr(attr) && !cell.attr(attr).matches("[1-9]|[1-4][0-9]|50"))
                        cell.removeAttr(attr);
                }
            }
            part.appendChildren(new ArrayList<>(clean.body().childNodes()));
        }
        if (entries.size() > 5000) throw new ExportException("Zu viele Überschriften.");
        if (contents != null) {
            for (Entry entry : entries)
                contents.appendElement("div").attr("class", "toc-entry level-" + entry.level())
                        .appendElement("a").attr("href", "#" + entry.id()).text(entry.text());
        }
        return new Prepared(output, Map.copyOf(assets), warnings.stream().distinct().toList());
    }

    private static void addImage(Element parent, String uri, Map<String, byte[]> assets, String type) {
        if (uri != null && assets.containsKey(uri))
            parent.appendElement("img").attr("src", uri).attr("class", type).attr("alt", "Logo");
    }

    private static String css(PdfSettings s, boolean numbers) {
        return """
                @page { size: %s %s; margin: %dmm;
                  @top-left { content: element(documentHeader); }
                  @bottom-left { content: element(documentFooter); }
                  @bottom-right { content: %s; font-family: "Export Sans"; font-size: 8pt; }
                }
                %s
                body { margin: 0; font-family: "Export Sans"; font-size: %dpt; line-height: %s; color: %s; }
                h1,h2,h3,h4,h5,h6 { color: %s; page-break-after: avoid; line-height: 1.2; }
                h1 { font-size: 20pt; } h2 { font-size: 16pt; } h3 { font-size: 14pt; }
                h4,h5,h6 { font-size: 11pt; }
                p { margin: 0 0 6pt; } a { color: %s; text-decoration: none; }
                .document-header { position: running(documentHeader); font-size: 8pt;
                  border-bottom: 0.5pt solid %s; padding-bottom: 3pt; }
                .document-footer { position: running(documentFooter); font-size: 8pt;
                  border-top: 0.5pt solid %s; padding-top: 3pt; }
                .header-logo { float: right; max-width: 20mm; max-height: 8mm; }
                .cover { page-break-after: always; padding-top: 1pt; }
                .cover-logo { display: block; margin-left: auto; width: %dmm; max-height: 50mm; }
                .cover h1 { font-size: %dpt; margin-top: 40mm; }
                .contents { page-break-after: always; }
                .toc-entry { margin-bottom: 4pt; }
                .toc-entry a::after { content: leader('.') target-counter(attr(href), page); }
                .level-1 { margin-left: 4mm; } .level-2 { margin-left: 8mm; }
                .level-3 { margin-left: 12mm; } .level-4,.level-5,.level-6 { margin-left: 16mm; }
                .chapter + .chapter { page-break-before: always; }
                img { max-width: 100%%; max-height: 220mm; }
                table { width: 100%%; border-collapse: collapse; table-layout: fixed;
                  -fs-table-paginate: paginate; margin: 6pt 0; }
                th,td { border: 0.5pt solid #b0b0b0; padding: 4pt; word-wrap: break-word; }
                th { background-color: #edf1f4; } tr:nth-child(even) { background-color: #f7f8f9; }
                thead { display: table-header-group; } tr { page-break-inside: avoid; }
                pre { white-space: pre-wrap; word-wrap: break-word; background: #f4f4f4;
                  padding: 6pt; font-family: "Export Sans"; font-size: 9pt; }
                blockquote { border-left: 2pt solid #b0b0b0; margin-left: 8pt; padding-left: 8pt; }
                """.formatted(s.paper(), s.landscape() ? "landscape" : "portrait", s.marginMm(),
                numbers ? "\"Seite \" counter(page) \" / \" counter(pages)" : "none",
                s.cover() ? "@page:first { @top-left { content: none; } @bottom-left { content: none; } @bottom-right { content: none; } }" : "",
                s.fontSizePt(), Double.toString(s.lineHeight()), s.textColor(), s.accentColor(),
                s.accentColor(), s.accentColor(), s.accentColor(), s.logoWidthMm(), s.titleSizePt());
    }
}
