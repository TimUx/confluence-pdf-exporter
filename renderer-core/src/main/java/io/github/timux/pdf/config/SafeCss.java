package io.github.timux.pdf.config;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates the deliberately limited CSS subset supported by the PDF renderer. */
public final class SafeCss {
    private static final Set<String> SELECTORS = Set.of(
            "body", "h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "table", "th", "td",
            "pre", "blockquote", "a", ".cover", ".contents", ".chapter", ".toc-entry",
            ".document-header", ".document-footer");
    private static final Set<String> PROPERTIES = Set.of(
            "color", "background-color", "font-size", "font-weight", "font-style", "text-align",
            "margin-top", "margin-bottom", "margin-left", "padding", "border-color", "border-width",
            "border-style", "line-height", "text-indent");
    private static final Pattern RULE = Pattern.compile("\\s*([^{}]+)\\{([^{}]*)}\\s*");
    private static final Pattern DIMENSION = Pattern.compile("(?:0|[0-9]{1,3}(?:\\.[0-9]{1,2})?(?:pt|px|mm|cm|em|%))");

    private SafeCss() {}

    public static String validate(String input) {
        if (input == null || input.isBlank()) return "";
        if (input.length() > 8_000 || input.contains("/*") || input.contains("*/") ||
                input.indexOf('@') >= 0 || input.indexOf('\\') >= 0)
            throw new IllegalArgumentException("CSS ist zu lang oder enthält nicht unterstützte Syntax.");
        StringBuilder safe = new StringBuilder();
        Matcher matcher = RULE.matcher(input);
        int end = 0;
        while (matcher.find()) {
            if (!input.substring(end, matcher.start()).isBlank())
                throw new IllegalArgumentException("Ungültige CSS-Regel.");
            end = matcher.end();
            String[] selectors = matcher.group(1).trim().split(",", -1);
            for (String selector : selectors) {
                if (!SELECTORS.contains(selector.trim()))
                    throw new IllegalArgumentException("CSS-Selektor nicht freigegeben: " + selector.trim());
            }
            StringBuilder declarations = new StringBuilder();
            for (String declaration : matcher.group(2).split(";", -1)) {
                if (declaration.isBlank()) continue;
                String[] pair = declaration.split(":", 2);
                if (pair.length != 2) throw new IllegalArgumentException("Ungültige CSS-Deklaration.");
                String property = pair[0].trim().toLowerCase();
                String value = pair[1].trim().toLowerCase();
                if (!PROPERTIES.contains(property) || !validValue(property, value))
                    throw new IllegalArgumentException("CSS-Eigenschaft oder -Wert nicht freigegeben: " + property);
                declarations.append(property).append(':').append(value).append(';');
            }
            if (declarations.isEmpty()) throw new IllegalArgumentException("Leere CSS-Regel.");
            safe.append(String.join(",", java.util.Arrays.stream(selectors).map(String::trim).toList()))
                    .append('{').append(declarations).append('}');
        }
        if (!input.substring(end).isBlank()) throw new IllegalArgumentException("Ungültige CSS-Regel.");
        return safe.toString();
    }

    private static boolean validValue(String property, String value) {
        if (value.isEmpty() || value.contains("url") || value.contains("expression") ||
                value.contains("var(") || value.indexOf('<') >= 0 || value.indexOf('>') >= 0)
            return false;
        return switch (property) {
            case "color", "background-color", "border-color" ->
                    value.matches("#[0-9a-f]{6}|black|white|gray|grey|red|green|blue|transparent");
            case "font-size", "margin-top", "margin-bottom", "margin-left", "padding",
                    "border-width", "text-indent" -> DIMENSION.matcher(value).matches();
            case "line-height" -> value.matches("(?:[1-2](?:\\.[0-9]{1,2})?|(?:0\\.[5-9]))");
            case "font-weight" -> value.matches("normal|bold|[1-9]00");
            case "font-style" -> value.matches("normal|italic|oblique");
            case "text-align" -> value.matches("left|right|center|justify");
            case "border-style" -> value.matches("none|solid|dashed|dotted");
            default -> false;
        };
    }
}
