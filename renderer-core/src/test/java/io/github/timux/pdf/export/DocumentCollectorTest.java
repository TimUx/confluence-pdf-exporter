package io.github.timux.pdf.export;

import static org.junit.jupiter.api.Assertions.*;
import io.github.timux.pdf.config.PdfSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentCollectorTest {
    private static final DocumentCollector.Source SOURCE = new DocumentCollector.Source() {
        @Override public boolean canRead(long id) { return id != 3; }
        @Override public String title(long id) {
            if (id == 3) fail("Restricted metadata accessed");
            return "Title " + id;
        }
        @Override public String html(long id) { return "<p>Body " + id + "</p>"; }
        @Override public List<Long> children(long id) {
            if (id == 3) fail("Restricted children accessed");
            return id == 1 ? List.of(2L, 3L, 4L) : id == 2 ? List.of(5L) : List.of();
        }
    };
    @Test void preservesDepthFirstOrderAndPrunesRestrictedSubtrees() {
        ExportDocument doc = new DocumentCollector().collect(SOURCE, 1, true, PdfSettings.defaults());
        assertEquals("Title 1", doc.title());
        assertEquals(List.of(1L, 2L, 5L, 4L), doc.sections().stream().map(ExportDocument.Section::id).toList());
        assertEquals(List.of(0, 1, 2, 1), doc.sections().stream().map(ExportDocument.Section::depth).toList());
    }
    @Test void supportsSinglePageAndDeniesRestrictedRoot() {
        assertEquals(1, new DocumentCollector().collect(SOURCE, 1, false, PdfSettings.defaults()).sections().size());
        assertThrows(ExportException.class, () -> new DocumentCollector().collect(SOURCE, 3, true, PdfSettings.defaults()));
    }
    @Test void enforcesPageAndHtmlLimits() {
        PdfSettings limited = PdfSettings.fromJson(PdfSettings.defaults().toJson().replace("\"maxPages\": 100", "\"maxPages\": 1"));
        assertThrows(ExportException.class, () -> new DocumentCollector().collect(SOURCE, 1, true, limited));
        var giant = new DocumentCollector.Source() {
            public boolean canRead(long id) { return true; }
            public String title(long id) { return "Test"; }
            public String html(long id) { return "x".repeat(4_000_001); }
            public List<Long> children(long id) { return List.of(); }
        };
        assertThrows(ExportException.class, () -> new DocumentCollector().collect(giant, 1, false, PdfSettings.defaults()));
    }
    @Test void filenameNeverContainsPathOrControlCharacters() {
        String file = new ExportDocument("../Report/\r\n\" ä", List.of(new ExportDocument.Section(1, 0, "A", ""))).fileName();
        assertFalse(file.contains("/")); assertFalse(file.contains("\r")); assertFalse(file.contains("\""));
        assertFalse(file.startsWith(".")); assertTrue(file.endsWith(".pdf"));
    }
    @Test void cyclesFailInsteadOfRecursingForever() {
        var source = new DocumentCollector.Source() {
            public boolean canRead(long id) { return true; }
            public String title(long id) { return "Test"; }
            public String html(long id) { return "<p>Body</p>"; }
            public List<Long> children(long id) { return List.of(1L); }
        };
        assertThrows(ExportException.class, () -> new DocumentCollector().collect(source, 1, true, PdfSettings.defaults()));
    }
}
