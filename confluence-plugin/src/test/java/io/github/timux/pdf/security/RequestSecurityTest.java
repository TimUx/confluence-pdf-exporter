package io.github.timux.pdf.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class RequestSecurityTest {
    @Test void csrfRequiresTheSessionBoundToken() {
        var req = mock(HttpServletRequest.class);
        var session = mock(HttpSession.class);
        var attributes = new HashMap<String, Object>();
        when(req.getSession(true)).thenReturn(session); when(req.getSession(false)).thenReturn(session);
        when(session.getAttribute(anyString())).thenAnswer(i -> attributes.get(i.getArgument(0)));
        doAnswer(i -> { attributes.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(session).setAttribute(anyString(), any());
        String token = Csrf.token(req);
        assertEquals(token, Csrf.token(req));
        assertFalse(Csrf.valid(req));
        when(req.getParameter("csrf")).thenReturn("incorrect"); assertFalse(Csrf.valid(req));
        when(req.getParameter("csrf")).thenReturn(token); assertTrue(Csrf.valid(req));
        when(req.getSession(false)).thenReturn(null); assertFalse(Csrf.valid(req));
    }
    @Test void boundedBodyRejectsDuplicatesOversizedAndWrongContentType() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> BoundedForm.read(request("csrf=one&csrf=two")));
        assertThrows(IllegalArgumentException.class, () -> BoundedForm.read(request("x=" + "a".repeat(65_537))));
        var req = request("{}"); when(req.getContentType()).thenReturn("application/json");
        assertThrows(IllegalArgumentException.class, () -> BoundedForm.read(req));
    }
    @Test void postParametersComeOnlyFromBoundedBodyAndDecodeUtf8() throws Exception {
        var original = request("field=Pr%C3%BCfung+%26+Text&csrf=body");
        when(original.getParameter("csrf")).thenReturn("query-token");
        var parsed = BoundedForm.read(original);
        assertEquals("Prüfung & Text", parsed.getParameter("field"));
        assertEquals("body", parsed.getParameter("csrf"));
        parsed.getParameterValues("field")[0] = "changed";
        assertEquals("Prüfung & Text", parsed.getParameter("field"));
    }
    @Test void bodyAlreadyConsumedByHostFilterFallsBackToContainerParameters() throws Exception {
        var req = request("");
        var params = new java.util.LinkedHashMap<String, String[]>();
        params.put("csrf", new String[]{"token"}); params.put("pageId", new String[]{"42"});
        when(req.getParameterMap()).thenReturn(params);
        var parsed = BoundedForm.read(req);
        assertEquals("token", parsed.getParameter("csrf"));
        assertEquals("42", parsed.getParameter("pageId"));
        params.put("csrf", new String[]{"one", "two"});
        assertThrows(IllegalArgumentException.class, () -> BoundedForm.read(req));
    }
    private static HttpServletRequest request(String body) throws Exception {
        var request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded;charset=UTF-8");
        when(request.getContentLengthLong()).thenReturn(-1L);
        var data = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            public boolean isFinished() { return data.available() == 0; }
            public boolean isReady() { return true; }
            public void setReadListener(ReadListener listener) {}
            public int read() { return data.read(); }
        });
        return request;
    }
}
