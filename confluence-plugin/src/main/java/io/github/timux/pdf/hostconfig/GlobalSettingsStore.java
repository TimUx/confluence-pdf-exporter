package io.github.timux.pdf.hostconfig;

import com.atlassian.beehive.ClusterLockService;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import io.github.timux.pdf.config.PdfSettings;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlobalSettingsStore implements LogoStore {
    private static final String KEY = "io.github.timux.confluence.pdf.settings.v1";
    private static final String LOGO_PREFIX = KEY + ".logo.";
    private static final int MAX_LOGO_BYTES = 512_000;
    private static final Logger LOG = LoggerFactory.getLogger(GlobalSettingsStore.class);
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

    @Override public byte[] logo(String slot) {
        String key = logoKey(slot);
        return transactions.execute(() -> {
            Object stored = factory.createGlobalSettings().get(key);
            if (stored == null) return null;
            if (!(stored instanceof String encoded) || encoded.length() > MAX_LOGO_BYTES * 2)
                throw new IllegalStateException("Gespeichertes Logo ist ungültig.");
            try {
                byte[] bytes = Base64.getDecoder().decode(encoded);
                if (bytes.length > MAX_LOGO_BYTES) throw new IllegalStateException("Gespeichertes Logo ist zu groß.");
                return bytes;
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Gespeichertes Logo ist ungültig.", ex);
            }
        });
    }

    public void saveLogo(String slot, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_LOGO_BYTES)
            throw new IllegalArgumentException("Logo muss zwischen 1 Byte und 500 KiB groß sein.");
        var lock = locks.getLockForName(LOGO_PREFIX + slot);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, TimeUnit.SECONDS);
            if (!acquired) throw new IllegalArgumentException("Logo wird gerade geändert.");
            String encoded = Base64.getEncoder().encodeToString(bytes);
            transactions.execute(() -> {
                factory.createGlobalSettings().put(logoKey(slot), encoded);
                return null;
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Logo-Upload unterbrochen.");
        } finally {
            if (acquired) lock.unlock();
        }
    }

    public void removeLogo(String slot) {
        var lock = locks.getLockForName(LOGO_PREFIX + slot);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, TimeUnit.SECONDS);
            if (!acquired) throw new IllegalArgumentException("Logo wird gerade geändert.");
            transactions.execute(() -> {
                factory.createGlobalSettings().remove(logoKey(slot));
                return null;
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Logo-Änderung unterbrochen.");
        } finally {
            if (acquired) lock.unlock();
        }
    }

    private static String logoKey(String slot) {
        if (!"cover".equals(slot) && !"header".equals(slot))
            throw new IllegalArgumentException("Ungültiger Logo-Bereich.");
        return LOGO_PREFIX + slot;
    }

    static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
