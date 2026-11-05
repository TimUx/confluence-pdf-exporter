package io.github.timux.pdf.export;

import java.util.List;

public record ExportDocument(String title, List<Section> sections) {
    public ExportDocument {
        if (title == null || title.isBlank() || sections == null || sections.isEmpty())
            throw new IllegalArgumentException("Leeres Dokument.");
        sections = List.copyOf(sections);
    }
    public record Section(long id, int depth, String title, String html) {
        public Section {
            if (id <= 0 || depth < 0 || depth > 32 || title == null || html == null)
                throw new IllegalArgumentException("Ungültiger Dokumentabschnitt.");
        }
    }

    public String fileName() {
        String base = title.replaceAll("[^\\p{L}\\p{N}._ -]", "_").replaceAll("^[. ]+|[. ]+$", "");
        if (base.isBlank()) base = "Export";
        return base.substring(0, Math.min(100, base.length())) + ".pdf";
    }
}
