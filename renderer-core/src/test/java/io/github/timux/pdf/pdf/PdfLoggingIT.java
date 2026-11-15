package io.github.timux.pdf.pdf;

import static org.junit.jupiter.api.Assertions.*;
import io.github.timux.pdf.config.PdfSettings;
import io.github.timux.pdf.export.ExportDocument;
import io.github.timux.pdf.template.HtmlComposer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class PdfLoggingIT {
    @Test void renderingDoesNotLogStandardFontFallbackWarnings() throws Exception {
        var warnings = new CopyOnWriteArrayList<String>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() < Level.WARNING.intValue()) return;
                String message = record.getMessage();
                // PDFBox scans the host's installed fonts once and reports files it cannot parse
                // (e.g. the bitmap-only NotoColorEmoji.ttf on current CI runners). That depends on the
                // machine, not on the export, and is not a font fallback.
                if (message != null && message.startsWith("Could not load font file")) return;
                warnings.add(message);
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        Logger pdfbox = Logger.getLogger("org.apache.pdfbox");
        pdfbox.addHandler(handler);
        try {
            var document = new ExportDocument("Log test", List.of(new ExportDocument.Section(1, 0, "Start", "<p>Text</p>")));
            var settings = PdfSettings.defaults();
            var prepared = new HtmlComposer().compose(document, settings, true, true, Map.of(), null, null);
            assertTrue(new PdfEngine().render(prepared, settings).length > 1000);
        } finally {
            pdfbox.removeHandler(handler);
        }
        assertEquals(List.of(), warnings);
    }
}
