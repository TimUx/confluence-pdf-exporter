package io.github.timux.pdf.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PdfSettingsTest {
    @Test void defaultsRoundTrip() {
        assertEquals(PdfSettings.defaults(), PdfSettings.fromJson(PdfSettings.defaults().toJson()));
    }
    @Test void rejectsUnknownVersionMissingAndUnknownFields() {
        String json = PdfSettings.defaults().toJson();
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson(json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")));
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson("{}"));
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson(json.replace("{", "{\"unexpected\": true,")));
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson("null"));
    }
    @Test void rejectsInjectionAndResourceLimitBypasses() {
        String json = PdfSettings.defaults().toJson();
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson(json.replace("#202124", "red; background:url(file:/etc/passwd)")));
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson(json.replace("\"maxPages\": 100", "\"maxPages\": 201")));
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson(json.replace("\"lineHeight\": 1.4", "\"lineHeight\": 0")));
        assertThrows(IllegalArgumentException.class, () -> PdfSettings.fromJson(" ".repeat(17000)));
    }

    @Test void acceptsOnlyAllowlistedTemplateCss() {
        assertEquals("h1{color:#123456;font-size:22pt;}",
                SafeCss.validate("h1 { color: #123456; font-size: 22pt; }"));
        assertThrows(IllegalArgumentException.class, () -> SafeCss.validate("body{background:url(file:/etc/passwd)}"));
        assertThrows(IllegalArgumentException.class, () -> SafeCss.validate("body{color:red} @import url(https://example.test/x);"));
        assertThrows(IllegalArgumentException.class, () -> SafeCss.validate("script{color:red}"));
    }
}
