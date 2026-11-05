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
    public static boolean valid(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return false;
        String expected = (String) session.getAttribute(KEY);
        String supplied = request.getParameter("csrf");
        return expected != null && supplied != null && supplied.length() <= 128 &&
                MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
