package io.github.timux.pdf.template;

import static org.junit.jupiter.api.Assertions.*;
import io.github.timux.pdf.config.PdfSettings;
import io.github.timux.pdf.export.ExportDocument;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HtmlComposerTest {
    private HtmlComposer.Prepared compose(String html) {
        return new HtmlComposer().compose(new ExportDocument("Title <&>", List.of(
                new ExportDocument.Section(1, 0, "Chapter", html))),
                PdfSettings.defaults(), true, true, Map.of(), null, null);
    }
    @Test void stripsActiveContentCssAndUnapprovedResources() {
        var result = compose("""
                <script>alert('x')</script><style>@import 'https://invalid.example/style';</style>
                <p onclick="alert(1)" style="background:url(file:/private)">Text</p>
                <a href="javascript:alert(1)">bad</a><img src="file:/private"><iframe src="http://127.0.0.1"/>
                """);
        assertTrue(result.html().select("script,iframe,[onclick],[style]").isEmpty());
        assertTrue(result.html().select("img").isEmpty());
        assertFalse(result.html().html().contains("javascript:"));
        assertTrue(result.html().text().contains("Text"));
        assertFalse(result.warnings().isEmpty());
    }
    @Test void removesExistingTocAndCreatesScopedAnchors() {
        var result = compose("<div class='toc-macro'>Old TOC</div><h2 id='anchor'>Heading</h2><a href='#anchor'>Jump</a>");
        assertFalse(result.html().text().contains("Old TOC"));
        assertEquals(2, result.html().select(".toc-entry").size());
        assertTrue(result.html().select("a[href='#s0-a0']").size() >= 2);
        assertEquals("Title <&>", result.html().title());
    }
    @Test void xxeIsNotInterpreted() {
        var result = compose("<!DOCTYPE x [<!ENTITY leak SYSTEM 'file:/private'>]><p>&leak;</p>");
        assertFalse(result.html().html().contains("<!ENTITY"));
        assertTrue(result.html().select("p").text().contains("&leak;"));
    }
    @Test void optionsRemoveCoverTocAndCounters() {
        PdfSettings settings = PdfSettings.fromJson(PdfSettings.defaults().toJson()
                .replace("\"cover\": true", "\"cover\": false").replace("\"toc\": true", "\"toc\": false"));
        var result = new HtmlComposer().compose(new ExportDocument("A", List.of(
                new ExportDocument.Section(1, 0, "B", "<h1>C</h1>"))), settings, true, false, Map.of(), null, null);
        assertTrue(result.html().select(".cover,.contents").isEmpty());
        assertFalse(result.html().head().html().contains("\"Seite \""));
    }

    @Test void includesValidatedCustomCssInTheRendererDocument() {
        var settings = PdfSettings.fromJson(PdfSettings.defaults().toJson().replace(
                "\"customCss\": \"\"", "\"customCss\": \"h1 { color: #123456; font-size: 22pt; }\""));
        var result = new HtmlComposer().compose(new ExportDocument("Title", List.of(
                new ExportDocument.Section(1, 0, "Chapter", "<p>Body</p>"))),
                settings, false, false, Map.of(), null, null);
        assertTrue(result.html().head().select("style").html().contains("h1{color:#123456;font-size:22pt;}"));
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson(PdfSettings.defaults().toJson()
                .replace("\"customCss\": \"\"", "\"customCss\": \"h1 { background-color: url(file:/etc/passwd); }\"")));
    }
}
