package io.github.timux.pdf.pdf;

import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Keeps PDFBox's font choice, but does not report the 14 standard PDF fonts as a "fallback".
 *
 * <p>OpenHTMLtoPDF creates all standard fonts (Courier, Helvetica, Times, Symbol, ...) for every
 * document. They are never embedded: the PDF only names them and viewers supply them. Without these
 * fonts installed on the server, PDFBox logs one warning per font and document, which would flood the
 * Confluence log. Fonts outside the standard set still report fallbacks, so real problems stay visible.
 */
final class Standard14FontMapper implements FontMapper {
    private final FontMapper delegate;

    private Standard14FontMapper(FontMapper delegate) { this.delegate = delegate; }

    /** Idempotent; PDFBox is embedded in the plugin, so this only affects this plugin's copy. */
    static synchronized void install() {
        FontMapper current = FontMappers.instance();
        if (!(current instanceof Standard14FontMapper)) FontMappers.set(new Standard14FontMapper(current));
    }

    @Override public FontMapping<TrueTypeFont> getTrueTypeFont(String baseFont, PDFontDescriptor descriptor) {
        return quiet(baseFont, delegate.getTrueTypeFont(baseFont, descriptor));
    }

    @Override public FontMapping<FontBoxFont> getFontBoxFont(String baseFont, PDFontDescriptor descriptor) {
        return quiet(baseFont, delegate.getFontBoxFont(baseFont, descriptor));
    }

    @Override public CIDFontMapping getCIDFont(String baseFont, PDFontDescriptor descriptor, PDCIDSystemInfo systemInfo) {
        return delegate.getCIDFont(baseFont, descriptor, systemInfo);
    }

    private static <T extends FontBoxFont> FontMapping<T> quiet(String baseFont, FontMapping<T> mapping) {
        if (mapping == null || !mapping.isFallback() || baseFont == null || !Standard14Fonts.containsName(baseFont)) {
            return mapping;
        }
        return new FontMapping<>(mapping.getFont(), false);
    }
}
