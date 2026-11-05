package io.github.timux.pdf.confluence;

import com.atlassian.confluence.api.model.Depth;
import com.atlassian.confluence.api.model.Expansion;
import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.content.ContentRepresentation;
import com.atlassian.confluence.api.model.content.ContentType;
import com.atlassian.confluence.api.model.content.id.ContentId;
import com.atlassian.confluence.api.model.pagination.SimplePageRequest;
import com.atlassian.confluence.api.service.content.AttachmentService;
import com.atlassian.confluence.api.service.content.ChildContentService;
import com.atlassian.confluence.api.service.content.ContentBodyConversionService;
import com.atlassian.confluence.api.service.content.ContentService;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.ConfluenceUser;
import io.github.timux.pdf.assets.ImageAssets;
import io.github.timux.pdf.config.PdfSettings;
import io.github.timux.pdf.export.DocumentCollector;
import io.github.timux.pdf.export.ExportDocument;
import io.github.timux.pdf.export.ExportException;
import io.github.timux.pdf.hostconfig.LogoStore;
import io.github.timux.pdf.template.HtmlComposer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Request-thread adapter: host rendering, ORM access and effective-user checks stay together. */
public final class ConfluenceDocumentSource implements DocumentCollector.Source {
    public record Snapshot(ExportDocument document, HtmlComposer.Prepared prepared, List<Long> attachmentIds) {}
    private final ContentService contents;
    private final ChildContentService children;
    private final AttachmentService attachmentService;
    private final ContentBodyConversionService conversion;
    private final PermissionManager permissions;
    private final AttachmentManager attachments;
    private final ConfluenceUser user;
    private final PdfSettings settings;
    private final String contextPath;
    private final byte[] uploadedCoverLogo;
    private final byte[] uploadedHeaderLogo;
    private final Map<Long, Content> approved = new HashMap<>();
    private final Map<Long, String> imageUris = new HashMap<>();
    private final ImageAssets assets;
    private final List<String> warnings = new ArrayList<>();

    public ConfluenceDocumentSource(ContentService contents, ChildContentService children, AttachmentService attachmentService,
                                    ContentBodyConversionService conversion, PermissionManager permissions,
                                    AttachmentManager attachments, ConfluenceUser user, PdfSettings settings, String contextPath,
                                    LogoStore settingsStore) {
        this.contents = contents; this.children = children; this.attachmentService = attachmentService;
        this.conversion = conversion; this.permissions = permissions; this.attachments = attachments;
        this.user = user; this.settings = settings; this.contextPath = contextPath;
        this.uploadedCoverLogo = settingsStore.logo("cover");
        this.uploadedHeaderLogo = settingsStore.logo("header");
        assets = new ImageAssets(settings.maxImageBytes());
    }

    @Override public boolean canRead(long id) {
        Content page = HostContent.page(contents, id);
        if (page == null) return false;
        approved.put(id, page);
        return true;
    }
    private Content page(long id) {
        Content page = approved.get(id);
        if (page == null) throw new ExportException("Seite nicht verfügbar.");
        return page;
    }
    @Override public String title(long id) { return page(id).getTitle(); }
    @Override public List<Long> children(long id) {
        page(id);
        List<Content> visible = new ArrayList<>();
        int offset = 0;
        while (true) {
            var response = children.findContent(ContentId.of(id)).withDepth(Depth.ROOT)
                    .fetchMany(ContentType.PAGE, new SimplePageRequest(offset, 100));
            for (Content child : response.getResults()) {
                long childId = child.getId().asLong();
                if (canRead(childId)) visible.add(page(childId));
            }
            if (visible.size() > settings.maxPages()) throw new ExportException("Seitenlimit überschritten.");
            offset += response.size();
            if (!response.hasMore()) break;
            if (response.size() == 0 || offset > 10_000) throw new ExportException("Seitenbaum zu groß.");
        }
        visible.sort(java.util.Comparator.comparingInt(Content::getPosition)
                .thenComparing(Content::getTitle, String.CASE_INSENSITIVE_ORDER));
        return visible.stream().map(content -> content.getId().asLong()).toList();
    }
    @Override public String html(long id) {
        page(id);
        try {
            Content page = contents.find(new Expansion("body.storage")).withId(ContentId.of(id)).fetch()
                    .orElseThrow(() -> new ExportException("Seite nicht verfügbar."));
            var storage = page.getBody().get(ContentRepresentation.STORAGE);
            if (storage == null || storage.getValue().length() > settings.maxHtmlBytes())
                throw new ExportException("Quellseite überschreitet das Größenlimit.");
            String rendered = conversion.convertBody(page, ContentRepresentation.EXPORT_VIEW).getValue();
            if (rendered.length() > settings.maxHtmlBytes()) throw new ExportException("HTML-Limit überschritten.");
            var fragment = Jsoup.parseBodyFragment(rendered);
            for (Element image : fragment.select("img")) {
                try {
                    Attachment attachment = null;
                    String linked = image.attr("data-linked-resource-id");
                    if (linked.matches("[1-9][0-9]{0,17}"))
                        attachment = HostContent.attachment(attachmentService, attachments, Long.parseLong(linked), contextPath);
                    if (attachment == null) {
                        String path = HostContent.attachmentPath(image.attr("src"), contextPath);
                        if (path != null) attachment = attachments.findAttachmentForDownloadPath(path).orElse(null);
                    }
                    image.attr("src", load(attachment));
                } catch (ExportException | IOException ex) {
                    image.replaceWith(new Element("span").text("[Bild nicht verfügbar]"));
                    warnings.add("Mindestens ein Bild konnte nicht berechtigt als PNG/JPEG geladen werden.");
                }
            }
            return fragment.body().html();
        } catch (ExportException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExportException("Confluence-Inhalt konnte nicht gerendert werden.");
        }
    }

    private String load(Attachment attachment) throws IOException {
        if (attachment == null || !attachment.isCurrent() ||
                !permissions.hasPermissionNoExemptions(user, Permission.VIEW, attachment.getContainer()) ||
                !permissions.hasPermissionNoExemptions(user, Permission.VIEW, attachment))
            throw new ExportException("Anhang nicht verfügbar.");
        if (imageUris.containsKey(attachment.getId())) return imageUris.get(attachment.getId());
        if (imageUris.size() >= 64 || attachment.getFileSize() > settings.maxImageBytes())
            throw new ExportException("Anhangslimit überschritten.");
        try (var input = attachments.getAttachmentData(attachment)) {
            byte[] data = input.readNBytes(settings.maxImageBytes() + 1);
            String uri = assets.add(data);
            imageUris.put(attachment.getId(), uri);
            return uri;
        }
    }

    public Snapshot snapshot(long root, boolean tree, boolean toc, boolean numbers) {
        ExportDocument document = new DocumentCollector().collect(this, root, tree, settings);
        try {
            String coverLogo = settings.coverLogoId() == 0 || !settings.cover() ? null :
                    load(HostContent.attachment(attachmentService, attachments, settings.coverLogoId(), contextPath));
            String headerLogo = settings.headerLogoId() == 0 || !settings.header() ? null :
                    load(HostContent.attachment(attachmentService, attachments, settings.headerLogoId(), contextPath));
            if (settings.coverLogoId() == 0 && settings.cover()) coverLogo = uploadedLogo("cover");
            if (settings.headerLogoId() == 0 && settings.header()) headerLogo = uploadedLogo("header");
            var prepared = new HtmlComposer().compose(document, settings, toc, numbers, assets.snapshot(), coverLogo, headerLogo);
            var combined = new ArrayList<>(prepared.warnings());
            combined.addAll(warnings);
            prepared = new HtmlComposer.Prepared(prepared.html(), prepared.assets(), combined.stream().distinct().toList());
            return new Snapshot(document, prepared, List.copyOf(imageUris.keySet()));
        } catch (IOException ex) {
            throw new ExportException("Konfiguriertes Logo konnte nicht geladen werden.");
        }
    }

    private String uploadedLogo(String slot) throws IOException {
        byte[] image = "cover".equals(slot) ? uploadedCoverLogo : uploadedHeaderLogo;
        return image == null ? null : assets.add(image);
    }
}
