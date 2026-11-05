package io.github.timux.pdf.pdf;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.github.timux.pdf.config.PdfSettings;
import io.github.timux.pdf.export.ExportException;
import io.github.timux.pdf.template.HtmlComposer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.helper.W3CDom;

public final class PdfEngine {
    public byte[] render(HtmlComposer.Prepared input, PdfSettings settings) throws IOException {
        long start = System.nanoTime();
        var output = new BoundedOutput(settings.maxPdfBytes());
        var builder = new PdfRendererBuilder();
        builder.withProducer("Universal PDF Exporter");
        builder.withW3cDocument(new W3CDom().fromJsoup(input.html()), null);
        builder.useUriResolver((base, uri) -> allowed(input, uri) ? uri : null);
        builder.useProtocolsStreamImplementation(uri -> new com.openhtmltopdf.extend.FSStream() {
            @Override public InputStream getStream() {
                byte[] data = input.assets().get(uri);
                return data == null ? null : new ByteArrayInputStream(data);
            }
            @Override public java.io.Reader getReader() {
                InputStream stream = getStream();
                return stream == null ? null : new java.io.InputStreamReader(stream, StandardCharsets.UTF_8);
            }
        }, "memory");
        builder.useExternalResourceAccessControl((uri, type) -> allowed(input, uri),
                ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
        builder.useExternalResourceAccessControl((uri, type) -> allowed(input, uri),
                ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        builder.useFont(PdfEngine::font, "Export Sans");
        builder.toStream(output);
        builder.run();
        if (Thread.currentThread().isInterrupted() ||
                System.nanoTime() - start > settings.timeoutSeconds() * 1_000_000_000L)
            throw new ExportException("Zeitlimit bei der PDF-Erzeugung.");
        byte[] bytes = output.bytes();
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() > 1000) throw new ExportException("PDF-Seitenlimit überschritten.");
            if (pdf.getNumberOfPages() == 0) throw new ExportException("Leeres PDF.");
        }

        return bytes;
    }

    private static boolean allowed(HtmlComposer.Prepared input, String uri) {
        return uri != null && uri.startsWith("memory:asset-") && input.assets().containsKey(uri);
    }

    private static InputStream font() {
        InputStream input = PDDocument.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
        if (input == null) throw new IllegalStateException("Eingebettete Schrift fehlt.");
        return input;
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private final int limit;
        BoundedOutput(int limit) { this.limit = limit; }
        @Override public void write(int value) throws IOException {
            check(1); data.write(value);
        }
        @Override public void write(byte[] bytes, int off, int len) throws IOException {
            check(len); data.write(bytes, off, len);
        }
        private void check(int extra) throws IOException {
            if ((long) data.size() + extra > limit) throw new IOException("PDF-Dateilimit überschritten.");
        }
        byte[] bytes() { return data.toByteArray(); }
    }
}
