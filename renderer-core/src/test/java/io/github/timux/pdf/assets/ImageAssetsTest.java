package io.github.timux.pdf.assets;

import static org.junit.jupiter.api.Assertions.*;
import io.github.timux.pdf.export.ExportException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Test;

class ImageAssetsTest {
    public static byte[] png(int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", stream);
        }
        return output.toByteArray();
    }
    @Test void storesRasterInMemoryWithDefensiveCopies() throws Exception {
        var assets = new ImageAssets(100_000);
        String uri = assets.add(png(12, 12));
        assertTrue(uri.startsWith("memory:"));
        byte[] first = assets.get(uri); first[0] = 0;
        assertNotEquals(0, assets.get(uri)[0]);
        assertNull(assets.get("https://invalid.example/image.png"));
    }
    @Test void rejectsInvalidOversizedAndNonRasterData() {
        var assets = new ImageAssets(1024);
        assertThrows(ExportException.class, () -> assets.add("<svg/>".getBytes()));
        assertThrows(ExportException.class, () -> assets.add(new byte[1025]));
        assertThrows(ExportException.class, () -> assets.add(new byte[]{(byte)0xff, (byte)0xd8, (byte)0xff}));
    }
}
