package io.github.timux.pdf.confluence;

import com.atlassian.beehive.ClusterLockService;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.api.service.content.AttachmentService;
import com.atlassian.confluence.api.service.content.ChildContentService;
import com.atlassian.confluence.api.service.content.ContentBodyConversionService;
import com.atlassian.confluence.api.service.content.ContentService;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.timux.pdf.hostconfig.GlobalSettingsStore;
import io.github.timux.pdf.config.PdfSettings;
import io.github.timux.pdf.export.ExportException;
import io.github.timux.pdf.export.ExportJobs;
import io.github.timux.pdf.pdf.PdfEngine;
import io.github.timux.pdf.security.Csrf;
import io.github.timux.pdf.security.BoundedForm;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.Part;

@MultipartConfig(maxFileSize = 512_000, maxRequestSize = 540_672, fileSizeThreshold = 0)
public final class PdfServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(PdfServlet.class);
    private static final String[] FLAGS = {"enabled", "cover", "toc", "header", "footer", "pageNumbers", "landscape"};
    private final ContentService contents;
    private final ChildContentService children;
    private final AttachmentService attachmentService;
    private final ContentBodyConversionService conversion;
    private final PermissionManager permissions;
    private final AttachmentManager attachments;
    private final TransactionTemplate transactions;
    private final GlobalSettingsStore settings;
    private final ClusterLockService clusterLocks;
    private final ExportJobs jobs = new ExportJobs();
    private final Semaphore snapshots = new Semaphore(2);

    public PdfServlet(@ComponentImport ContentService contentService,
                      @ComponentImport ChildContentService childContentService,
                      @ComponentImport AttachmentService attachmentService,
                      @ComponentImport ContentBodyConversionService contentBodyConversionService,
                      @ComponentImport PermissionManager permissionManager,
                      @ComponentImport AttachmentManager attachmentManager,
                      @ComponentImport PluginSettingsFactory pluginSettingsFactory,
                      @ComponentImport TransactionTemplate transactionTemplate,
                      @ComponentImport ClusterLockService clusterLockService) {
        this.contents = contentService; this.children = childContentService; this.attachmentService = attachmentService;
        this.conversion = contentBodyConversionService; this.permissions = permissionManager; this.attachments = attachmentManager;
        this.transactions = transactionTemplate;
        this.clusterLocks = clusterLockService;
        this.settings = new GlobalSettingsStore(pluginSettingsFactory, transactionTemplate, clusterLockService);
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, false);
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, true);
    }

    private void handle(HttpServletRequest req, HttpServletResponse res, boolean post) throws IOException {
        req.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store, private");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "same-origin");
        res.setHeader("Content-Security-Policy", "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'");
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) { page(res, 401, "Anmeldung erforderlich", "<p>Bitte bei Confluence anmelden.</p>", false); return; }
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            if (post) {
                boolean upload = "/logo".equals(path) && req.getContentType() != null
                        && req.getContentType().toLowerCase().startsWith("multipart/form-data");
                if (!upload) req = BoundedForm.read(req);
                String csrf = Csrf.problem(req);
                if (csrf != null) {
                    LOG.warn("PDF request rejected: path={} reason=csrf ({})", path, csrf);
                    page(res, 403, "Formular abgelaufen",
                            "<p>Das Formular ist abgelaufen oder ungültig. Bitte die vorherige Seite neu laden und erneut absenden.</p>", false);
                    return;
                }
                switch (path) {
                    case "/admin" -> saveSettings(req, res, user);
                    case "/export", "/preview" -> start(req, res, user, path.equals("/preview"));
                    case "/logo" -> uploadLogo(req, res, user);
                    default -> res.sendError(404);
                }
            } else {
                switch (path) {
                    case "/admin" -> { admin(user); administration(req, res); }
                    case "/configuration" -> {
                        admin(user);
                        res.setContentType("application/json;charset=UTF-8");
                        res.setHeader("Content-Disposition", "attachment; filename=\"pdf-settings.json\"");
                        res.getWriter().write(settings.read().settings().toJson());
                    }
                    case "/export" -> exportForm(req, res, user);
                    case "/status" -> status(req, res, user);
                    case "/download" -> download(req, res, user);
                    default -> res.sendError(404);
                }
            }
        } catch (SecurityException ex) {
            LOG.warn("PDF request rejected: path={} reason=permission", path);
            page(res, 403, "Keine Berechtigung", "<p>Für diese Aktion oder Seite fehlt die Berechtigung.</p>", false);
        } catch (IllegalArgumentException | ExportException ex) {
            page(res, 400, "Export nicht möglich", "<p>" + escape(ex.getMessage()) + "</p><p>Bitte zur vorherigen Seite zurückkehren.</p>", false);
        } catch (Exception ex) {
            String reference = UUID.randomUUID().toString();
            LOG.error("PDF request failed: reference={} path={}", reference, path, ex);
            page(res, 500, "Interner Exportfehler", "<p>Referenz: " + reference + "</p>", false);
        }
    }

    private void admin(ConfluenceUser user) {
        if (!permissions.isConfluenceAdministrator(user)) throw new SecurityException();
    }
    private String owner(ConfluenceUser user) { return user.getKey().getStringValue(); }
    private String base(HttpServletRequest req) { return req.getContextPath() + "/plugins/servlet/universal-pdf"; }
    private long pageId(HttpServletRequest req) {
        String text = req.getParameter("pageId");
        if (text == null || !text.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException("Ungültige Seiten-ID.");
        return Long.parseLong(text);
    }
    private void checkPage(long id, ConfluenceUser user) {
        if (HostContent.page(contents, id) == null) throw new SecurityException();
    }

    private void exportForm(HttpServletRequest req, HttpServletResponse res, ConfluenceUser user) throws IOException {
        long id = pageId(req);
        transactions.execute(() -> { checkPage(id, user); return null; });
        String form = "<form method=\"post\" action=\"" + escape(base(req)) + "/export\">" + hidden(req)
                + "<input type=\"hidden\" name=\"pageId\" value=\"" + id + "\">"
                + "<p>Der Titel und das globale Design werden automatisch übernommen.</p>"
                + checkbox("tree", "Unterseiten einschließen", false)
                + checkbox("toc", "Inhaltsverzeichnis (falls global aktiviert)", true)
                + checkbox("numbers", "Seitenzahlen (falls global aktiviert)", true)
                + "<button class=\"aui-button aui-button-primary\">PDF erzeugen</button></form>";
        page(res, 200, "Als PDF exportieren", form, false);
    }

    private void start(HttpServletRequest req, HttpServletResponse res, ConfluenceUser user, boolean preview) throws IOException {
        PdfSettings config;
        if (preview) { admin(user); config = postedSettings(req); }
        else config = settings.read().settings();
        if (!config.enabled() && !preview) throw new ExportException("PDF-Export ist administrativ deaktiviert.");
        if (!snapshots.tryAcquire()) throw new ExportException("Seitenerfassung ausgelastet. Bitte später erneut versuchen.");
        try {
            long root = pageId(req);
            boolean tree = "on".equals(req.getParameter("tree")) && !preview;
            boolean toc = preview || "on".equals(req.getParameter("toc"));
            boolean numbers = preview || "on".equals(req.getParameter("numbers"));
            var source = new ConfluenceDocumentSource(contents, children, attachmentService,
                    conversion, permissions, attachments, user, config, req.getContextPath(), settings);
            var snapshot = transactions.execute(() -> source.snapshot(root, tree, toc, numbers));
            UUID id = jobs.submit(owner(user), snapshot.document().sections().stream().map(s -> s.id()).toList(),
                    snapshot.attachmentIds(), snapshot.document().fileName(),
                    () -> {
                        var lock = clusterLocks.getLockForName("io.github.timux.confluence.pdf.renderer");
                        boolean acquired = false;
                        try {
                            acquired = lock.tryLock(config.timeoutSeconds(), TimeUnit.SECONDS);
                            if (!acquired) throw new ExportException("Clusterweite PDF-Warteschlange ausgelastet.");
                            return new ExportJobs.Result(new PdfEngine().render(snapshot.prepared(), config),
                                    snapshot.prepared().warnings());
                        } finally {
                            if (acquired) lock.unlock();
                        }
                    });
            res.setStatus(303);
            res.setHeader("Location", base(req) + "/status?id=" + id);
        } finally { snapshots.release(); }
    }

    private void uploadLogo(HttpServletRequest req, HttpServletResponse res, ConfluenceUser user)
            throws IOException, ServletException {
        admin(user);
        String slot = req.getParameter("slot");
        if (!"cover".equals(slot) && !"header".equals(slot))
            throw new IllegalArgumentException("Ungültiger Logo-Bereich.");
        Part part = req.getPart("logo");
        if (part == null || part.getSize() < 1 || part.getSize() > 512_000)
            throw new IllegalArgumentException("Logo muss zwischen 1 Byte und 500 KiB groß sein.");
        byte[] uploaded;
        try (var stream = part.getInputStream()) {
            uploaded = stream.readNBytes(512_001);
        }
        if (uploaded.length > 512_000) throw new IllegalArgumentException("Logo überschreitet das Uploadlimit.");
        var imageAssets = new io.github.timux.pdf.assets.ImageAssets(
                Math.min(512_000, settings.read().settings().maxImageBytes()));
        String imageUri = imageAssets.add(uploaded);
        byte[] normalized = imageAssets.get(imageUri);
        settings.saveLogo(slot, normalized);
        res.setStatus(303);
        res.setHeader("Location", base(req) + "/admin");
    }

    private UUID jobId(HttpServletRequest req) {
        try { return UUID.fromString(req.getParameter("id")); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Ungültiger Auftrag."); }
    }
    private void recheck(UUID id, ConfluenceUser user, String contextPath) {
        String owner = owner(user);
        transactions.execute(() -> {
            for (long page : jobs.pageIds(id, owner)) checkPage(page, user);
            for (long attachment : jobs.attachmentIds(id, owner)) {
                var a = HostContent.attachment(attachmentService, attachments, attachment, contextPath);
                if (a == null || !a.isCurrent() ||
                        !permissions.hasPermissionNoExemptions(user, Permission.VIEW, a.getContainer()) ||
                        !permissions.hasPermissionNoExemptions(user, Permission.VIEW, a)) throw new SecurityException();
            }
            return null;
        });
    }
    private void status(HttpServletRequest req, HttpServletResponse res, ConfluenceUser user) throws IOException {
        UUID id = jobId(req);
        recheck(id, user, req.getContextPath());
        var view = jobs.view(id, owner(user));
        String text = switch (view.state()) {
            case QUEUED -> "In Warteschlange";
            case RUNNING -> "PDF wird erzeugt";
            case COMPLETE -> "PDF fertig";
            case FAILED -> view.error();
        };
        StringBuilder body = new StringBuilder("<p>").append(escape(text)).append("</p>");
        if (view.state() == ExportJobs.State.COMPLETE) {
            body.append("<a class=\"aui-button aui-button-primary\" href=\"").append(escape(base(req)))
                    .append("/download?id=").append(id).append("\">PDF herunterladen</a>");
            if (!view.warnings().isEmpty()) {
                body.append("<h2>Hinweise</h2><ul>");
                view.warnings().forEach(w -> body.append("<li>").append(escape(w)).append("</li>"));
                body.append("</ul>");
            }
        } else if (view.state() != ExportJobs.State.FAILED) {
            body.append("<a class=\"aui-button\" href=\"").append(escape(base(req))).append("/status?id=")
                    .append(id).append("\">Status aktualisieren</a>");
        }
        body.append("<p>Aufträge sind knotengebunden und werden nach zehn Minuten entfernt. ")
                .append("Bei einem Knotenwechsel bitte auf dem ursprünglichen Knoten bleiben oder neu exportieren.</p>");
        page(res, 200, "PDF-Exportstatus", body.toString(), false);
    }
    private void download(HttpServletRequest req, HttpServletResponse res, ConfluenceUser user) throws IOException {
        UUID id = jobId(req);
        recheck(id, user, req.getContextPath());
        var view = jobs.view(id, owner(user));
        byte[] bytes = jobs.download(id, owner(user));
        String encoded = URLEncoder.encode(view.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        res.setContentType("application/pdf");
        res.setHeader("Content-Disposition", "attachment; filename=\"export.pdf\"; filename*=UTF-8''" + encoded);
        res.setContentLength(bytes.length);
        res.getOutputStream().write(bytes);
    }

    private void saveSettings(HttpServletRequest req, HttpServletResponse res, ConfluenceUser user) throws IOException {
        admin(user);
        String action = req.getParameter("action");
        PdfSettings next = switch (action == null ? "" : action) {
            case "reset" -> PdfSettings.defaults();
            case "restore" -> settings.previous();
            case "import" -> PdfSettings.fromJson(req.getParameter("json"));
            case "save" -> postedSettings(req);
            default -> throw new IllegalArgumentException("Unbekannte Aktion.");
        };
        settings.save(next, req.getParameter("revision"));
        res.setStatus(303); res.setHeader("Location", base(req) + "/admin");
    }
    private PdfSettings postedSettings(HttpServletRequest req) {
        JsonObject json = JsonParser.parseString(settings.read().settings().toJson()).getAsJsonObject();
        for (String flag : FLAGS) json.addProperty(flag, "on".equals(req.getParameter(flag)));
        for (String field : new String[]{"paper", "textColor", "accentColor", "footerText", "coverText", "customCss"}) {
            String value = req.getParameter(field);
            int limit = "customCss".equals(field) ? 8_000 : 2_000;
            if (value == null || value.length() > limit) throw new IllegalArgumentException("Fehlendes oder zu langes Feld: " + field);
            json.addProperty(field, value);
        }
        for (String field : new String[]{"marginMm", "fontSizePt", "titleSizePt", "logoWidthMm", "maxPages",
                "maxHtmlBytes", "maxImageBytes", "maxPdfBytes", "timeoutSeconds", "coverLogoId", "headerLogoId"}) {
            String value = req.getParameter(field);
            if (value == null || !value.matches("[0-9]{1,18}")) throw new IllegalArgumentException("Ungültiger Zahlenwert: " + field);
            json.addProperty(field, Long.parseLong(value));
        }
        String lineHeight = req.getParameter("lineHeight");
        if (lineHeight == null || !lineHeight.matches("[12](\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Ungültiger Zeilenabstand.");
        json.addProperty("lineHeight", Double.parseDouble(lineHeight));
        return PdfSettings.fromJson(json.toString());
    }

    private void administration(HttpServletRequest req, HttpServletResponse res) throws IOException {
        var snapshot = settings.read();
        PdfSettings s = snapshot.settings();
        JsonObject json = JsonParser.parseString(s.toJson()).getAsJsonObject();
        String action = escape(base(req));
        StringBuilder form = new StringBuilder("<p>Globale Vorlage · Schema 1 · Änderungen gelten für neue Exporte.</p>")
                .append("<form method=\"post\" action=\"").append(action).append("/admin\">")
                .append(hidden(req)).append("<input type=\"hidden\" name=\"revision\" value=\"").append(snapshot.revision()).append("\">");
        String[] labels = {"Export aktivieren", "Deckblatt", "Inhaltsverzeichnis", "Kopfzeile", "Fußzeile", "Seitenzahlen", "Querformat"};
        for (int i = 0; i < FLAGS.length; i++) form.append(checkbox(FLAGS[i], labels[i], json.get(FLAGS[i]).getAsBoolean()));
        form.append("<h2>Gestaltung</h2><p>Die eingebettete Schrift „Export Sans“ wird für alle Dokumente verwendet.</p>");
        field(form, "paper", "Papierformat (A4 / A3)", s.paper());
        field(form, "marginMm", "Seitenrand (mm, 12–45)", s.marginMm());
        field(form, "fontSizePt", "Schriftgröße (pt, 8–20)", s.fontSizePt());
        field(form, "lineHeight", "Zeilenabstand (1–2)", s.lineHeight());
        field(form, "titleSizePt", "Deckblatttitel (pt, 18–54)", s.titleSizePt());
        field(form, "textColor", "Textfarbe (#RRGGBB)", s.textColor());
        field(form, "accentColor", "Überschriften- und Linienfarbe (#RRGGBB)", s.accentColor());
        field(form, "coverText", "Deckblatttext", s.coverText());
        field(form, "footerText", "Fußzeilentext", s.footerText());
        form.append("<p><label for=\"customCss\">Erweitertes CSS (freigegebene Elemente/Eigenschaften, max. 8 KiB)</label><br>")
                .append("<textarea id=\"customCss\" name=\"customCss\" rows=\"10\" maxlength=\"8000\">")
                .append(escape(s.customCss())).append("</textarea></p>");
        form.append("<h2>Logos</h2><p>Ein Logo kann als PNG/JPEG hochgeladen oder als Confluence-Anhang verwendet werden. ")
                .append("Für Anhänge die ID eintragen (0 = hochgeladenes Logo). Jeder Export prüft die Anhangsberechtigung.</p>");
        field(form, "coverLogoId", "Deckblattlogo: Anhangs-ID", s.coverLogoId());
        field(form, "headerLogoId", "Kopfzeilenlogo: Anhangs-ID", s.headerLogoId());
        field(form, "logoWidthMm", "Deckblattlogo-Breite (mm)", s.logoWidthMm());
        form.append("<p>Ein hochgeladenes Logo wird verwendet, wenn für den jeweiligen Platz keine Anhangs-ID gesetzt ist.</p>");
        form.append("<h2>Ressourcenlimits</h2><p>Renderer: eine clusterweite Sperre; pro Node zwei Renderer-Slots und zwei Warteschlangenplätze; ein aktiver Auftrag pro Benutzer und Node.</p>");
        field(form, "maxPages", "Maximale Quellseiten (1–200)", s.maxPages());
        field(form, "maxHtmlBytes", "Maximale HTML-Bytes", s.maxHtmlBytes());
        field(form, "maxImageBytes", "Maximale Bytes je Bild", s.maxImageBytes());
        field(form, "maxPdfBytes", "Maximale PDF-Bytes", s.maxPdfBytes());
        field(form, "timeoutSeconds", "Kooperatives Zeitlimit (Sekunden)", s.timeoutSeconds());
        form.append("<button class=\"aui-button aui-button-primary\" name=\"action\" value=\"save\">Speichern</button> ")
                .append("<button class=\"aui-button\" name=\"action\" value=\"reset\">Standardwerte wiederherstellen</button> ")
                .append("<button class=\"aui-button\" name=\"action\" value=\"restore\">Vorherige Konfiguration</button>")
                .append("<h2>PDF-Vorschau ohne Speichern</h2>");
        field(form, "pageId", "Testseiten-ID", "");
        form.append("<button class=\"aui-button\" formaction=\"").append(action)
                .append("/preview\">Vorschau erzeugen</button></form>")
                .append("<h2>Logos hochladen</h2>");
        for (String slot : new String[]{"cover", "header"}) {
            String label = "cover".equals(slot) ? "Deckblattlogo" : "Kopfzeilenlogo";
            form.append("<form method=\"post\" enctype=\"multipart/form-data\" action=\"")
                    .append(action).append("/logo\">").append(hidden(req))
                    .append("<input type=\"hidden\" name=\"slot\" value=\"").append(slot).append("\">")
                    .append("<label>").append(label).append(" hochladen (PNG/JPEG, max. 500 KiB und Bildlimit) ")
                    .append("<input type=\"file\" name=\"logo\" accept=\"image/png,image/jpeg\" required></label> ")
                    .append("<button class=\"aui-button\">Logo speichern</button></form>");
        }
        form.append("<h2>Konfiguration übertragen</h2><p><a href=\"").append(action)
                .append("/configuration\">JSON exportieren</a></p><form method=\"post\" action=\"")
                .append(action).append("/admin\">").append(hidden(req))
                .append("<input type=\"hidden\" name=\"revision\" value=\"").append(snapshot.revision()).append("\">")
                .append("<label for=\"json\">Vollständige Konfiguration (maximal 16 KiB)</label>")
                .append("<textarea id=\"json\" name=\"json\" rows=\"18\" maxlength=\"16384\">")
                .append(escape(s.toJson())).append("</textarea><button class=\"aui-button\" name=\"action\" value=\"import\">JSON importieren</button></form>")
                .append("<p>CSS ist auf freigegebene Selektoren und Eigenschaften beschränkt; freies HTML und externe Ressourcen sind nicht freigeschaltet. ")
                .append("Installation und Clusterbetrieb müssen vor Produktivnutzung in einer Testinstanz abgenommen werden.</p>");
        page(res, 200, "Universal PDF Exporter", form.toString(), true);
    }

    private String hidden(HttpServletRequest req) {
        return "<input type=\"hidden\" name=\"csrf\" value=\"" + escape(Csrf.token(req)) + "\">";
    }
    private static String checkbox(String name, String label, boolean checked) {
        return "<p><label><input type=\"checkbox\" name=\"" + name + "\"" + (checked ? " checked" : "") + "> "
                + escape(label) + "</label></p>";
    }
    private static void field(StringBuilder form, String name, String label, Object value) {
        form.append("<p><label for=\"").append(name).append("\">").append(escape(label)).append("</label><br>")
                .append("<input class=\"text\" id=\"").append(name).append("\" name=\"").append(name)
                .append("\" value=\"").append(escape(String.valueOf(value))).append("\" maxlength=\"2000\"></p>");
    }
    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static void page(HttpServletResponse res, int code, String title, String body, boolean administration) throws IOException {
        res.setStatus(code); res.setContentType("text/html;charset=UTF-8");
        res.getWriter().write("<!doctype html><html lang=\"de\"><head><meta charset=\"UTF-8\"><meta name=\"decorator\" content=\""
                + (administration ? "atl.admin" : "atl.general") + "\"><title>" + escape(title)
                + "</title><style>main{max-width:980px;margin:2em auto;padding:1em}textarea{width:100%}"
                + "input.text{width:36em;max-width:100%}button{margin:0.5em 0}</style></head>"
                + "<body><main class=\"aui-page-panel\">"
                // atl.general already shows the <title> as page heading.
                + (administration ? "<h1>" + escape(title) + "</h1>" : "") + body + "</main></body></html>");
    }
    @Override public void destroy() { jobs.close(); super.destroy(); }
}
