package io.github.timux.pdf.confluence;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class BundlePackagingIT {
    @Test void bundleContainsDescriptorAndRunsRendererWithOnlyItsNestedRuntimeLibraries() throws Exception {
        Path target = Path.of("target");
        Path bundle;
        try (var files = Files.list(target)) {
            bundle = files.filter(p -> p.getFileName().toString().startsWith("confluence-pdf-exporter-")
                    && p.toString().endsWith(".jar")).findFirst().orElseThrow();
        }
        Path unpacked = Files.createDirectories(target.resolve("bundle-test-libs"));
        var urls = new ArrayList<URL>();
        try (JarFile jar = new JarFile(bundle.toFile())) {
            var manifest = jar.getManifest().getMainAttributes();
            assertEquals("io.github.timux.confluence.pdf", manifest.getValue("Atlassian-Plugin-Key"));
            assertNotNull(manifest.getValue("Bundle-ClassPath"));
            assertNull(manifest.getValue("Export-Package"), "Internal libraries must not be exported");
            String imports = manifest.getValue("Import-Package");
            assertTrue(imports.contains("jakarta.servlet.http"));
            assertFalse(imports.contains("com.openhtmltopdf"));
            assertFalse(imports.contains("org.apache.pdfbox"));
            String descriptor = new String(jar.getInputStream(jar.getJarEntry("atlassian-plugin.xml")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(descriptor.contains("${"));
            assertTrue(descriptor.contains("plugins-version=\"2\""));
            assertNotNull(jar.getJarEntry("META-INF/THIRD-PARTY-NOTICES.md"));
            for (String item : manifest.getValue("Bundle-ClassPath").split(",")) {
                if (item.equals(".")) continue;
                assertFalse(item.startsWith("confluence-") || item.startsWith("sal-api-") || item.startsWith("jakarta.servlet-"),
                        "Host APIs must not be bundled: " + item);
                var entry = jar.getJarEntry(item);
                assertNotNull(entry, "Missing nested dependency " + item);
                Path path = unpacked.resolve(item);
                Files.write(path, jar.getInputStream(entry).readAllBytes());
                urls.add(path.toUri().toURL());
                try (JarFile library = new JarFile(path.toFile())) {
                    if (item.startsWith("pdfbox-3")) assertNotNull(library.getJarEntry("META-INF/LICENSE"));
                }
            }
        }
        // Platform-only parent prevents the test runner from silently supplying missing libraries.
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Class<?> settingsClass = loader.loadClass("io.github.timux.pdf.config.PdfSettings");
            Object settings = settingsClass.getMethod("defaults").invoke(null);
            Class<?> sectionClass = loader.loadClass("io.github.timux.pdf.export.ExportDocument$Section");
            Object section = sectionClass.getConstructor(long.class, int.class, String.class, String.class)
                    .newInstance(1L, 0, "Smoke test", "<p>Bundled renderer works.</p>");
            Class<?> documentClass = loader.loadClass("io.github.timux.pdf.export.ExportDocument");
            Object document = documentClass.getConstructor(String.class, List.class).newInstance("Package test", List.of(section));
            Class<?> composerClass = loader.loadClass("io.github.timux.pdf.template.HtmlComposer");
            Object prepared = composerClass.getMethod("compose", documentClass, settingsClass, boolean.class,
                            boolean.class, Map.class, String.class, String.class)
                    .invoke(composerClass.getConstructor().newInstance(), document, settings, true, true, Map.of(), null, null);
            Class<?> engine = loader.loadClass("io.github.timux.pdf.pdf.PdfEngine");
            byte[] pdf = (byte[]) engine.getMethod("render", prepared.getClass(), settingsClass)
                    .invoke(engine.getConstructor().newInstance(), prepared, settings);
            assertTrue(pdf.length > 1000);
            Files.write(target.resolve("package-smoke.pdf"), pdf);
        }
    }
}
