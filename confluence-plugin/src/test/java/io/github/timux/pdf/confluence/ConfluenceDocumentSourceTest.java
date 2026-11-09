package io.github.timux.pdf.confluence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.atlassian.confluence.api.model.Depth;
import com.atlassian.confluence.api.model.Expansion;
import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.content.ContentBody;
import com.atlassian.confluence.api.model.content.ContentRepresentation;
import com.atlassian.confluence.api.model.content.ContentStatus;
import com.atlassian.confluence.api.model.content.ContentType;
import com.atlassian.confluence.api.model.content.id.ContentId;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.service.content.AttachmentService;
import com.atlassian.confluence.api.service.content.ChildContentService;
import com.atlassian.confluence.api.service.content.ContentBodyConversionService;
import com.atlassian.confluence.api.service.content.ContentService;
import io.github.timux.pdf.config.PdfSettings;
import io.github.timux.pdf.export.ExportException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfluenceDocumentSourceTest {
    @Test void publicFinderDenialPreventsMetadataAccess() {
        ContentService service = mock(ContentService.class);
        var finder = mock(ContentService.ContentFinder.class);
        var single = mock(ContentService.SingleContentFetcher.class);
        when(service.find()).thenReturn(finder);
        when(finder.withId(ContentId.of(1))).thenReturn(single);
        when(single.fetch()).thenReturn(Optional.empty());
        var source = new ConfluenceDocumentSource(service, null, null, null, null, null,
                null, PdfSettings.defaults(), "", mock(io.github.timux.pdf.hostconfig.LogoStore.class));
        assertFalse(source.canRead(1));
        assertThrows(ExportException.class, () -> source.title(1));
        assertThrows(ExportException.class, () -> source.children(1));
    }

    @Test void publicConversionAndChildPaginationPreservePositionOrderAndPruneDeniedChild() {
        ContentService service = mock(ContentService.class);
        var finder = mock(ContentService.ContentFinder.class);
        when(service.find(any(Expansion[].class))).thenReturn(finder);
        Content parent = content(1, "Root", 0), second = content(2, "Second", 20), first = content(3, "First", 10);
        for (Content content : List.of(parent, second, first)) {
            var single = mock(ContentService.SingleContentFetcher.class);
            when(finder.withId(content.getId())).thenReturn(single);
            when(single.fetch()).thenReturn(Optional.of(content));
        }
        var denied = mock(ContentService.SingleContentFetcher.class);
        when(finder.withId(ContentId.of(4))).thenReturn(denied);
        when(denied.fetch()).thenReturn(Optional.empty());
        Content unreadable = mock(Content.class);
        when(unreadable.getId()).thenReturn(ContentId.of(4));
        ChildContentService children = mock(ChildContentService.class);
        var childFinder = mock(ChildContentService.ChildContentFinder.class);
        when(children.findContent(ContentId.of(1))).thenReturn(childFinder);
        when(childFinder.withDepth(Depth.ROOT)).thenReturn(childFinder);
        @SuppressWarnings("unchecked") PageResponse<Content> response = mock(PageResponse.class);
        when(response.getResults()).thenReturn(List.of(second, unreadable, first));
        when(response.size()).thenReturn(3);
        when(childFinder.fetchMany(eq(ContentType.PAGE), any())).thenReturn(response);
        var storage = mock(ContentBody.class);
        when(storage.getValue()).thenReturn("<p>Stored</p>");
        when(parent.getBody()).thenReturn(Map.of(ContentRepresentation.STORAGE, storage));
        var converted = mock(ContentBody.class);
        when(converted.getValue()).thenReturn("<p>Export view</p>");
        var conversion = mock(ContentBodyConversionService.class);
        when(conversion.convertBody(parent, ContentRepresentation.EXPORT_VIEW)).thenReturn(converted);
        var source = new ConfluenceDocumentSource(service, children, mock(AttachmentService.class), conversion,
                null, null, null, PdfSettings.defaults(), "", mock(io.github.timux.pdf.hostconfig.LogoStore.class));
        assertTrue(source.canRead(1));
        assertEquals(List.of(3L, 2L), source.children(1));
        assertTrue(source.html(1).contains("Export view"));
        verify(unreadable, never()).getTitle();
        verify(conversion).convertBody(parent, ContentRepresentation.EXPORT_VIEW);
    }

    @Test void exportViewFailureFallsBackToNormalView() {
        ContentService service = mock(ContentService.class);
        var finder = mock(ContentService.ContentFinder.class);
        when(service.find(any(Expansion[].class))).thenReturn(finder);
        Content page = content(1, "Root", 0);
        var single = mock(ContentService.SingleContentFetcher.class);
        when(finder.withId(page.getId())).thenReturn(single);
        when(single.fetch()).thenReturn(Optional.of(page));
        var storage = mock(ContentBody.class);
        when(storage.getValue()).thenReturn("<p>Stored</p>");
        when(page.getBody()).thenReturn(Map.of(ContentRepresentation.STORAGE, storage));
        var view = mock(ContentBody.class);
        when(view.getValue()).thenReturn("<p>Normal view</p>");
        var conversion = mock(ContentBodyConversionService.class);
        when(conversion.convertBody(page, ContentRepresentation.EXPORT_VIEW)).thenThrow(new IllegalStateException("macro"));
        when(conversion.convertBody(page, ContentRepresentation.VIEW)).thenReturn(view);
        var source = new ConfluenceDocumentSource(service, null, mock(AttachmentService.class), conversion,
                null, null, null, PdfSettings.defaults(), "", mock(io.github.timux.pdf.hostconfig.LogoStore.class));
        assertTrue(source.canRead(1));
        assertTrue(source.html(1).contains("Normal view"));
        when(conversion.convertBody(page, ContentRepresentation.VIEW)).thenThrow(new IllegalStateException("broken"));
        var failure = assertThrows(ExportException.class, () -> source.html(1));
        assertTrue(failure.getMessage().contains("Referenz"));
    }

    @Test void attachmentPathsRejectUrlsAndRespectContextPath() {
        assertEquals("/download/attachments/42/logo.png",
                HostContent.attachmentPath("/wiki/download/attachments/42/logo.png?version=1", "/wiki"));
        assertNull(HostContent.attachmentPath("https://invalid.example/download/attachments/42/logo.png", ""));
        assertNull(HostContent.attachmentPath("file:/private/file.png", ""));
        assertNull(HostContent.attachmentPath("//invalid.example/image.png", ""));
    }

    private static Content content(long id, String title, int position) {
        var content = mock(Content.class);
        when(content.getId()).thenReturn(ContentId.of(id));
        when(content.getTitle()).thenReturn(title);
        when(content.getPosition()).thenReturn(position);
        when(content.getType()).thenReturn(ContentType.PAGE);
        when(content.getStatus()).thenReturn(ContentStatus.CURRENT);
        return content;
    }
}
