package io.github.timux.pdf.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;

/** Immutable, versioned configuration. Only constrained CSS values reach the renderer. */
public record PdfSettings(
        int schemaVersion, boolean enabled, boolean cover, boolean toc, boolean header,
        boolean footer, boolean pageNumbers, String paper, boolean landscape,
        int marginMm, int fontSizePt, double lineHeight, String textColor, String accentColor,
        int titleSizePt, String footerText, String coverText, long coverLogoId, long headerLogoId,
        int logoWidthMm, int maxPages, int maxHtmlBytes, int maxImageBytes, int maxPdfBytes,
        int timeoutSeconds) {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "enabled", "cover", "toc", "header", "footer", "pageNumbers",
            "paper", "landscape", "marginMm", "fontSizePt", "lineHeight", "textColor", "accentColor",
            "titleSizePt", "footerText", "coverText", "coverLogoId", "headerLogoId", "logoWidthMm",
            "maxPages", "maxHtmlBytes", "maxImageBytes", "maxPdfBytes", "timeoutSeconds");

    public PdfSettings {
        require(schemaVersion == 1, "Unbekannte Konfigurationsversion.");
        require(Set.of("A4", "A3").contains(paper == null ? "" : paper), "Papierformat: A4 oder A3.");
        range(marginMm, 12, 45, "Rand");
        range(fontSizePt, 8, 20, "Schriftgröße");
        require(Double.isFinite(lineHeight) && lineHeight >= 1 && lineHeight <= 2, "Zeilenabstand: 1–2.");
        require(isColor(textColor) && isColor(accentColor), "Farben müssen #RRGGBB entsprechen.");
        range(titleSizePt, 18, 54, "Titelgröße");
        require(footerText != null && footerText.length() <= 300, "Fußtext: maximal 300 Zeichen.");
        require(coverText != null && coverText.length() <= 2000, "Deckblatttext: maximal 2000 Zeichen.");
        require(coverLogoId >= 0 && headerLogoId >= 0, "Ungültige Anhangs-ID.");
        range(logoWidthMm, 10, 70, "Logobreite");
        range(maxPages, 1, 200, "Quellseitenlimit");
        range(maxHtmlBytes, 1024, 8_000_000, "HTML-Limit");
        range(maxImageBytes, 1024, 4_000_000, "Bildlimit");
        range(maxPdfBytes, 1024, 32_000_000, "PDF-Limit");
        range(timeoutSeconds, 5, 120, "Zeitlimit");
    }

    public static PdfSettings defaults() {
        return new PdfSettings(1, true, true, true, true, true, true, "A4", false,
                20, 10, 1.4, "#202124", "#365870", 30, "", "", 0, 0,
                35, 100, 4_000_000, 2_000_000, 16_000_000, 60);
    }

    public String toJson() { return JSON.toJson(this); }

    public static PdfSettings fromJson(String value) {
        require(value != null && value.length() <= 16_384, "Konfiguration zu groß.");
        try {
            JsonObject object = JsonParser.parseString(value).getAsJsonObject();
            require(object.keySet().equals(FIELDS), "Konfiguration muss genau die bekannten Felder enthalten.");
            return JSON.fromJson(object, PdfSettings.class);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Ungültige JSON-Konfiguration.", ex);
        }
    }

    private static boolean isColor(String color) { return color != null && color.matches("#[0-9a-fA-F]{6}"); }
    private static void range(int n, int min, int max, String field) {
        require(n >= min && n <= max, field + ": " + min + "–" + max + ".");
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
