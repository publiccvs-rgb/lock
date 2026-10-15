package dev.lock.agent;

import dev.lock.core.util.Log;
import dev.lock.core.util.LockPaths;
import dev.lock.mapping.Mappings;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Supplies Mojang's official client mappings for the running version. Mojang's licence does not
 * allow redistributing them, so Lock downloads them from Mojang on first use (the launcher does
 * this during installation) and verifies the SHA-1 published in the version manifest.
 */
public final class MappingProvider {
    public static final String URL_1_21_11 = "https://piston-data.mojang.com/v1/objects/031a68bebf55d824f66d6573d8c752f0e1bf232a/client.txt";
    public static final String SHA1_1_21_11 = "031a68bebf55d824f66d6573d8c752f0e1bf232a";

    private MappingProvider() {}

    public static Path cached(String version) { return LockPaths.cache().resolve("mappings").resolve(version + "-client.txt"); }

    public static Mappings load(String version, Map<String, Object> info) throws IOException {
        Path file = cached(version);
        long t0 = System.nanoTime();
        if (!Files.exists(file) || !SHA1_1_21_11.equalsIgnoreCase(sha1(file))) {
            Log.info("downloading official mappings for " + version);
            download(file);
            info.put("mappingsSource", "downloaded");
        } else info.put("mappingsSource", "cache");
        Mappings m;
        try (var r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            m = Mappings.parse(r);
        }
        info.put("mappingsClasses", m.toObf.classes.size());
        info.put("mappingsLoadMs", (System.nanoTime() - t0) / 1_000_000);
        return m;
    }

    public static void download(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        Path tmp = file.resolveSibling(file.getFileName() + ".part");
        try {
            HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(URL_1_21_11)).timeout(Duration.ofSeconds(60)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
            try (InputStream in = r.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (!SHA1_1_21_11.equalsIgnoreCase(sha1(tmp))) {
            Files.deleteIfExists(tmp);
            throw new IOException("downloaded mappings failed SHA-1 verification");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    static String sha1(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
