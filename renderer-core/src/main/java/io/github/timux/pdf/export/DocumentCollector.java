package io.github.timux.pdf.export;

import io.github.timux.pdf.config.PdfSettings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Permission checks precede both metadata access and child traversal. */
public final class DocumentCollector {
    public interface Source {
        boolean canRead(long id);
        String title(long id);
        String html(long id);
        List<Long> children(long id);
    }
    private record Pending(long id, int depth) {}

    public ExportDocument collect(Source source, long root, boolean tree, PdfSettings settings) {
        var pending = new ArrayDeque<Pending>();
        var seen = new HashSet<Long>();
        var sections = new ArrayList<ExportDocument.Section>();
        pending.push(new Pending(root, 0));
        long bytes = 0;
        long deadline = System.nanoTime() + settings.timeoutSeconds() * 1_000_000_000L;
        while (!pending.isEmpty()) {
            if (System.nanoTime() > deadline) throw new ExportException("Zeitlimit bei der Seitenerfassung.");
            Pending next = pending.pop();
            if (!seen.add(next.id())) throw new ExportException("Zyklischer Seitenbaum.");
            if (!source.canRead(next.id())) {
                if (next.id() == root) throw new ExportException("Seite nicht verfügbar oder nicht berechtigt.");
                continue;
            }
            if (sections.size() >= settings.maxPages() || next.depth() > 32)
                throw new ExportException("Seiten- oder Hierarchielimit überschritten.");
            String title = source.title(next.id());
            String html = source.html(next.id());
            bytes += html.getBytes(StandardCharsets.UTF_8).length + title.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > settings.maxHtmlBytes()) throw new ExportException("HTML-Limit überschritten.");
            sections.add(new ExportDocument.Section(next.id(), next.depth(), title, html));
            if (tree) {
                List<Long> children = source.children(next.id());
                if (children.size() + pending.size() > 10_000) throw new ExportException("Seitenbaum zu groß.");
                for (int i = children.size() - 1; i >= 0; i--)
                    pending.push(new Pending(children.get(i), next.depth() + 1));
            }
        }
        return new ExportDocument(sections.getFirst().title(), sections);
    }
}
