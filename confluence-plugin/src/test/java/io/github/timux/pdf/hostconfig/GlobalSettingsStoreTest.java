package io.github.timux.pdf.hostconfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.atlassian.beehive.ClusterLockService;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import io.github.timux.pdf.config.PdfSettings;
import java.util.HashMap;
import com.atlassian.beehive.ClusterLock;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GlobalSettingsStoreTest {
    @Test void sharesPersistenceAcrossStoreInstancesAndRejectsStaleWrites() throws Exception {
        var values = new HashMap<String, Object>();
        PluginSettings pluginSettings = mock(PluginSettings.class);
        when(pluginSettings.get(anyString())).thenAnswer(i -> values.get(i.getArgument(0)));
        when(pluginSettings.put(anyString(), any())).thenAnswer(i -> values.put(i.getArgument(0), i.getArgument(1)));
        PluginSettingsFactory factory = mock(PluginSettingsFactory.class);
        when(factory.createGlobalSettings()).thenReturn(pluginSettings);
        ClusterLockService locks = mock(ClusterLockService.class);
        ClusterLock lock = mock(ClusterLock.class);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(locks.getLockForName(anyString())).thenReturn(lock);
        TransactionTemplate transactions = new TransactionTemplate() {
            @Override public <T> T execute(TransactionCallback<T> callback) { return callback.doInTransaction(); }
        };
        var first = new GlobalSettingsStore(factory, transactions, locks);
        var second = new GlobalSettingsStore(factory, transactions, locks);
        var original = first.read();
        assertEquals(PdfSettings.defaults(), original.settings());
        PdfSettings next = PdfSettings.fromJson(original.settings().toJson().replace("\"cover\": true", "\"cover\": false"));
        first.save(next, original.revision());
        assertEquals(next, second.read().settings());
        assertEquals(PdfSettings.defaults(), second.previous());
        assertThrows(IllegalArgumentException.class, () -> second.save(PdfSettings.defaults(), original.revision()));
        second.save(second.previous(), second.read().revision());
        assertEquals(PdfSettings.defaults(), first.read().settings());
    }

    @Test void logoAssetsShareGlobalPersistenceAndCanBeRemoved() throws Exception {
        var values = new HashMap<String, Object>();
        PluginSettings pluginSettings = mock(PluginSettings.class);
        when(pluginSettings.get(anyString())).thenAnswer(i -> values.get(i.getArgument(0)));
        when(pluginSettings.put(anyString(), any())).thenAnswer(i -> values.put(i.getArgument(0), i.getArgument(1)));
        when(pluginSettings.remove(anyString())).thenAnswer(i -> values.remove(i.getArgument(0)));
        PluginSettingsFactory factory = mock(PluginSettingsFactory.class);
        when(factory.createGlobalSettings()).thenReturn(pluginSettings);
        ClusterLock lock = mock(ClusterLock.class);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        ClusterLockService locks = mock(ClusterLockService.class);
        when(locks.getLockForName(anyString())).thenReturn(lock);
        TransactionTemplate transactions = new TransactionTemplate() {
            @Override public <T> T execute(TransactionCallback<T> callback) { return callback.doInTransaction(); }
        };
        var first = new GlobalSettingsStore(factory, transactions, locks);
        var second = new GlobalSettingsStore(factory, transactions, locks);
        byte[] image = {1, 2, 3};
        first.saveLogo("cover", image);
        assertArrayEquals(image, second.logo("cover"));
        second.removeLogo("cover");
        assertNull(first.logo("cover"));
        assertThrows(IllegalArgumentException.class, () -> first.saveLogo("unknown", image));
        assertThrows(IllegalArgumentException.class, () -> first.saveLogo("header", new byte[512_001]));
    }
}
