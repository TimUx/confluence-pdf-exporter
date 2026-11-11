package io.github.timux.pdf.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class Csrf {
    private static final String KEY = Csrf.class.getName();
    private Csrf() {}
    public static String token(HttpServletRequest request) {
        var session = request.getSession(true);
        synchronized (session) {
            String token = (String) session.getAttribute(KEY);
            if (token == null) {
                byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                session.setAttribute(KEY, token);
            }
            return token;
        }
    }
    public static boolean valid(HttpServletRequest request) { return problem(request) == null; }

    /** Why the token check failed (never includes token values), or null when valid. */
    public static String problem(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return "no session";
        String expected = (String) session.getAttribute(KEY);
        if (expected == null) return "no token in session";
        String supplied = request.getParameter("csrf");
        if (supplied == null) return "no token in form";
        if (supplied.length() > 128 ||
                !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            return "token mismatch";
        return null;
    }
}
