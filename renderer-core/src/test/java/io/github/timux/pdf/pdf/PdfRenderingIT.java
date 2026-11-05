package io.github.timux.pdf.pdf;

import static org.junit.jupiter.api.Assertions.*;
import io.github.timux.pdf.assets.ImageAssets;
import io.github.timux.pdf.config.PdfSettings;
import io.github.timux.pdf.export.ExportDocument;
import io.github.timux.pdf.template.HtmlComposer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfRenderingIT {
    @Test void producesCoverPaginatedTocRealDestinationsMetadataFontsAndLogos() throws Exception {
        var assets = new ImageAssets(100_000);
        var bytes = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(bytes)) {
            ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", stream);
        }
        String logo = assets.add(bytes.toByteArray());
        var document = new ExportDocument("Exportprüfung ä & <Titel>", List.of(
                new ExportDocument.Section(1, 0, "Start", "<h2>Overview</h2><p>First body.</p>"),
                new ExportDocument.Section(2, 1, "Second", "<h2>Details</h2><p>Second body.</p>")));
        var settings = PdfSettings.defaults();
        var prepared = new HtmlComposer().compose(document, settings, true, true, assets.snapshot(), logo, logo);
        byte[] pdfBytes = new PdfEngine().render(prepared, settings);
        assertTrue(pdfBytes.length > 1000);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(4, pdf.getNumberOfPages(), () -> describe(pdf));
            assertEquals(document.title(), pdf.getDocumentInformation().getTitle());
            String cover = text(pdf, 1), toc = text(pdf, 2), first = text(pdf, 3), second = text(pdf, 4);
            assertTrue(cover.contains(document.title()));
            assertFalse(cover.contains("Seite 1"));
            assertTrue(toc.contains("Inhaltsverzeichnis"));
            assertTrue(first.contains("First body."));
            assertTrue(second.contains("Second body."));
            assertTrue(first.contains("Seite 3 / 4"));
            assertTrue(second.contains("Seite 4 / 4"));
            // Actual target-counter values must match the PDF destination pages, not guesses.
            assertTrue(toc.matches("(?s).*1\\. Start\\s*\\.+\\s*3.*"), toc);
            assertTrue(toc.matches("(?s).*1\\.2\\. Second\\s*\\.+\\s*4.*"), toc);
            int links = 0;
            for (var annotation : pdf.getPage(1).getAnnotations()) {
                if (annotation instanceof PDAnnotationLink link) {
                    var destination = link.getDestination();
                    if (destination == null && link.getAction() instanceof PDActionGoTo go) destination = go.getDestination();
                    if (destination instanceof PDPageDestination target) {
                        assertTrue(target.retrievePageNumber() >= 2);
                        links++;
                    }
                }
            }
            assertTrue(links >= 4, "TOC links must be present");
            boolean embedded = false, image = false;
            for (var page : pdf.getPages()) {
                for (var name : page.getResources().getFontNames()) embedded |= page.getResources().getFont(name).isEmbedded();
                for (var name : page.getResources().getXObjectNames()) image |= page.getResources().isImageXObject(name);
            }
            assertTrue(embedded, "Embedded font missing");
            assertTrue(image, "Embedded logo missing");
        }
    }
    @Test void rendersLongTocAcrossPagesAndDoesNotFetchUnapprovedResources() throws Exception {
        var sections = new java.util.ArrayList<ExportDocument.Section>();
        StringBuilder html = new StringBuilder("<p>Static text</p>");
        for (int i = 0; i < 90; i++) html.append("<h2>Heading ").append(i).append("</h2><p>Paragraph.</p>");
        html.append("<img src='http://127.0.0.1:9/no'><img src='file:/private/no'><img src='data:image/png;base64,AAAA'>");
        sections.add(new ExportDocument.Section(1, 0, "Long", html.toString()));
        var prepared = new HtmlComposer().compose(new ExportDocument("Long document", sections),
                PdfSettings.defaults(), true, true, Map.of(), null, null);
        try (var pdf = Loader.loadPDF(new PdfEngine().render(prepared, PdfSettings.defaults()))) {
            assertTrue(pdf.getNumberOfPages() > 5);
            assertTrue(text(pdf, 2).contains("Inhaltsverzeichnis"), () -> describe(pdf));
            assertTrue(text(pdf, 3).contains("Heading"), "TOC must continue onto another page");
            assertTrue(new PDFTextStripper().getText(pdf).contains("Static text"));
        }
    }
    @Test void rejectsPdfOverOutputLimit() {
        var settings = PdfSettings.fromJson(PdfSettings.defaults().toJson().replace("\"maxPdfBytes\": 16000000", "\"maxPdfBytes\": 1024"));
        var prepared = new HtmlComposer().compose(new ExportDocument("Test", List.of(
                new ExportDocument.Section(1, 0, "Test", "<p>Body</p>"))), settings, true, true, Map.of(), null, null);
        assertThrows(java.io.IOException.class, () -> new PdfEngine().render(prepared, settings));
    }
    @Test void rendererItselfCannotFetchHttpEvenIfGivenUnsanitizedHtmlAndAnUntrustedAssetMap() throws Exception {
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/resource";
            var html = org.jsoup.Jsoup.parse("<html><head><title>Security test</title><style>@import url('" + uri
                    + "');</style></head><body><p>Safe text</p><img src='" + uri + "'></body></html>");
            var input = new HtmlComposer.Prepared(html, Map.of(uri, new byte[]{1}), List.of());
            try (var pdf = Loader.loadPDF(new PdfEngine().render(input, PdfSettings.defaults()))) {
                assertTrue(new PDFTextStripper().getText(pdf).contains("Safe text"));
            }
            assertEquals(0, hits.get(), "No HTTP request may leave the renderer");
        } finally { server.stop(0); }
    }
    @Test void rendersSingleA3LandscapePageWithoutOptionalFrontMatterOrCounters() throws Exception {
        String json = PdfSettings.defaults().toJson().replace("\"paper\": \"A4\"", "\"paper\": \"A3\"")
                .replace("\"landscape\": false", "\"landscape\": true");
        for (String flag : List.of("cover", "toc", "header", "footer", "pageNumbers"))
            json = json.replace("\"" + flag + "\": true", "\"" + flag + "\": false");
        var settings = PdfSettings.fromJson(json);
        var prepared = new HtmlComposer().compose(new ExportDocument("Title", List.of(
                new ExportDocument.Section(1, 0, "Single", "<p>Only content.</p>"))),
                settings, false, false, Map.of(), null, null);
        try (var pdf = Loader.loadPDF(new PdfEngine().render(prepared, settings))) {
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(pdf.getPage(0).getMediaBox().getWidth() > 1100);
            assertTrue(pdf.getPage(0).getMediaBox().getHeight() > 800);
            String text = text(pdf, 1);
            assertTrue(text.contains("Only content."));
            assertFalse(text.contains("Inhaltsverzeichnis"));
            assertFalse(text.contains("Seite "));
        }
    }
    private static String text(PDDocument pdf, int page) throws Exception {
        var stripper = new PDFTextStripper(); stripper.setStartPage(page); stripper.setEndPage(page);
        return stripper.getText(pdf);
    }
    private static String describe(PDDocument pdf) {
        try {
            StringBuilder description = new StringBuilder();
            for (int i = 1; i <= pdf.getNumberOfPages(); i++)
                description.append("\nPAGE ").append(i).append(": ").append(text(pdf, i));
            return description.toString();
        } catch (Exception ex) { return ex.toString(); }
    }
}
