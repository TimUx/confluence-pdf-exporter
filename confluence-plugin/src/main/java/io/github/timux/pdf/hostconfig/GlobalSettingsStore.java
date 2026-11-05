package io.github.timux.pdf.hostconfig;

import com.atlassian.beehive.ClusterLockService;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import io.github.timux.pdf.config.PdfSettings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class GlobalSettingsStore {
    private static final String KEY = "io.github.timux.confluence.pdf.settings.v1";
    private static final Logger LOG = Logger.getLogger(GlobalSettingsStore.class.getName());
    private final PluginSettingsFactory factory;
    private final TransactionTemplate transactions;
    private final ClusterLockService locks;
    public record Snapshot(PdfSettings settings, String revision) {}

    public GlobalSettingsStore(PluginSettingsFactory factory, TransactionTemplate transactions, ClusterLockService locks) {
        this.factory = factory; this.transactions = transactions; this.locks = locks;
    }

    public Snapshot read() {
        return transactions.execute(() -> {
            Object stored = factory.createGlobalSettings().get(KEY);
            String json = stored == null ? PdfSettings.defaults().toJson() : (String) stored;
            return new Snapshot(PdfSettings.fromJson(json), digest(json));
        });
    }

    public void save(PdfSettings settings, String expectedRevision) {
        var lock = locks.getLockForName(KEY);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, TimeUnit.SECONDS);
            if (!acquired) throw new IllegalArgumentException("Konfiguration wird gerade geändert.");
            transactions.execute(() -> {
                var store = factory.createGlobalSettings();
                Object value = store.get(KEY);
                String previous = value == null ? PdfSettings.defaults().toJson() : (String) value;
                if (!digest(previous).equals(expectedRevision))
                    throw new IllegalArgumentException("Konfiguration wurde geändert. Bitte neu laden.");
                // Keep exactly one known-valid previous revision for recovery.
                PdfSettings.fromJson(previous);
                store.put(KEY + ".previous", previous);
                store.put(KEY, settings.toJson());
                return null;
            });
            LOG.info("Universal PDF configuration updated by authenticated administrator.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Konfigurationsänderung unterbrochen.");
        } finally {
            if (acquired) lock.unlock();
        }
    }

    public PdfSettings previous() {
        return transactions.execute(() -> {
            Object value = factory.createGlobalSettings().get(KEY + ".previous");
            if (!(value instanceof String text)) throw new IllegalArgumentException("Keine vorherige Konfiguration.");
            return PdfSettings.fromJson(text);
        });
    }

    static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
