package io.github.timux.pdf.confluence;

import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.content.ContentStatus;
import com.atlassian.confluence.api.model.content.ContentType;
import com.atlassian.confluence.api.model.content.id.ContentId;
import com.atlassian.confluence.api.model.link.LinkType;
import com.atlassian.confluence.api.service.content.AttachmentService;
import com.atlassian.confluence.api.service.content.ContentService;

final class HostContent {
    private HostContent() {}
    static Content page(ContentService contents, long id) {
        // Public finders enforce visibility for the current authenticated request user.
        return contents.find().withId(ContentId.of(id)).fetch()
                .filter(content -> content.getType().equals(ContentType.PAGE) && content.getStatus().equals(ContentStatus.CURRENT))
                .orElse(null);
    }
    static Attachment attachment(AttachmentService service, AttachmentManager attachments, long id, String contextPath) {
        var content = service.find().withId(ContentId.of(id)).fetch().orElse(null);
        if (content == null || !content.getStatus().equals(ContentStatus.CURRENT)) return null;
        var link = content.getLinks().get(LinkType.DOWNLOAD);
        if (link == null) return null;
        String path = attachmentPath(link.getPath(), contextPath);
        if (path == null) return null;
        return attachments.findAttachmentForDownloadPath(path).filter(a -> a.getId() == id).orElse(null);
    }
    static String attachmentPath(String path, String contextPath) {
        if (!contextPath.isEmpty() && path.startsWith(contextPath + "/")) path = path.substring(contextPath.length());
        if (!path.matches("/download/attachments/[0-9]+/[^?#]+([?].*)?")) return null;
        return path.split("\\?", 2)[0];
    }
}
