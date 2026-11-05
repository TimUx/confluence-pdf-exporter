package io.github.timux.pdf.assets;

import io.github.timux.pdf.export.ExportException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Only re-encoded raster images enter the renderer; no URL fetching is possible here. */
public final class ImageAssets {
    private final Map<String, byte[]> data = new LinkedHashMap<>();
    private final int maxImageBytes;
    private long totalBytes;

    public ImageAssets(int maxImageBytes) { this.maxImageBytes = maxImageBytes; }

    public String add(byte[] input) {
        if (input.length > maxImageBytes) throw new ExportException("Bilddatei zu groß.");
        boolean png = input.length >= 8 && Arrays.equals(Arrays.copyOf(input, 8),
                new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
        boolean jpeg = input.length >= 3 && input[0] == (byte)0xff && input[1] == (byte)0xd8 && input[2] == (byte)0xff;
        if (!png && !jpeg) throw new ExportException("Nur PNG- und JPEG-Bilder sind erlaubt.");
        // Avoid ImageIO's default disk cache, including on shared hosts.
        try (var stream = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new ExportException("Ungültige Bilddatei.");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 12_000_000)
                    throw new ExportException("Bildabmessungen überschreiten das Limit.");
                BufferedImage image = reader.read(0);
                var bytes = new java.io.ByteArrayOutputStream();
                try (var output = new javax.imageio.stream.MemoryCacheImageOutputStream(bytes)) {
                    if (!ImageIO.write(image, "png", output)) throw new ExportException("Bildkonvertierung fehlgeschlagen.");
                }
                byte[] encoded = bytes.toByteArray();
                if (encoded.length > maxImageBytes || totalBytes + encoded.length > 16_000_000)
                    throw new ExportException("Gesamtbildlimit überschritten.");
                totalBytes += encoded.length;
                String uri = "memory:asset-" + data.size();
                data.put(uri, encoded);
                return uri;
            } finally { reader.dispose(); }
        } catch (IOException ex) {
            throw new ExportException("Bilddatei konnte nicht gelesen werden.");
        }
    }

    public byte[] get(String uri) { return data.containsKey(uri) ? data.get(uri).clone() : null; }
    public Map<String, byte[]> snapshot() {
        var copy = new LinkedHashMap<String, byte[]>();
        data.forEach((key, bytes) -> copy.put(key, bytes.clone()));
        return Map.copyOf(copy);
    }
}
