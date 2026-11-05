package io.github.timux.pdf.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parse only the small POST form body, never unbounded container parameter aggregation. */
public final class BoundedForm {
    private BoundedForm() {}

    public static HttpServletRequest read(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded"))
            throw new IllegalArgumentException("Nur URL-kodierte Formulare sind erlaubt.");
        if (request.getContentLengthLong() > 65_536) throw new IllegalArgumentException("Formular zu groß.");
        byte[] bytes = request.getInputStream().readNBytes(65_537);
        if (bytes.length > 65_536) throw new IllegalArgumentException("Formular zu groß.");
        Map<String, String[]> fields = new LinkedHashMap<>();
        for (String pair : new String(bytes, StandardCharsets.UTF_8).split("&", -1)) {
            if (pair.isEmpty()) continue;
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
            if (key.length() > 64 || fields.size() >= 40 || fields.putIfAbsent(key, new String[]{value}) != null)
                throw new IllegalArgumentException("Ungültige oder doppelte Formularfelder.");
        }
        return new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                String[] value = fields.get(name); return value == null ? null : value[0];
            }
            @Override public String[] getParameterValues(String name) {
                String[] value = fields.get(name); return value == null ? null : value.clone();
            }
            @Override public Map<String, String[]> getParameterMap() {
                Map<String, String[]> copy = new LinkedHashMap<>();
                fields.forEach((key, value) -> copy.put(key, value.clone()));
                return java.util.Collections.unmodifiableMap(copy);
            }
            @Override public java.util.Enumeration<String> getParameterNames() {
                return java.util.Collections.enumeration(fields.keySet());
            }
        };
    }
}
