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
        if (bytes.length == 0) {
            // A host filter (e.g. one reading "pageId") may already have parsed and consumed the body.
            // Tomcat bounds that parsing itself (maxPostSize / maxParameterCount); apply our limits on top.
            int total = 0;
            for (var entry : request.getParameterMap().entrySet()) {
                String[] values = entry.getValue();
                if (entry.getKey().length() > 64 || fields.size() >= 40 || values == null || values.length != 1)
                    throw new IllegalArgumentException("Ungültige oder doppelte Formularfelder.");
                total += entry.getKey().length() + values[0].length();
                if (total > 65_536) throw new IllegalArgumentException("Formular zu groß.");
                fields.put(entry.getKey(), new String[]{values[0]});
            }
            return wrap(request, fields);
        }
        for (String pair : new String(bytes, StandardCharsets.UTF_8).split("&", -1)) {
            if (pair.isEmpty()) continue;
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
            if (key.length() > 64 || fields.size() >= 40 || fields.putIfAbsent(key, new String[]{value}) != null)
                throw new IllegalArgumentException("Ungültige oder doppelte Formularfelder.");
        }
        return wrap(request, fields);
    }

    private static HttpServletRequest wrap(HttpServletRequest request, Map<String, String[]> fields) {
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
