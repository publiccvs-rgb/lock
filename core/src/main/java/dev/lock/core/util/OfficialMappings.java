package dev.lock.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Mojang's official client mappings for the supported version. Mojang's licence does not allow
 * redistributing them, so Lock downloads them from Mojang (verified by SHA-1) into its cache.
 */
public final class OfficialMappings {
    public static final String VERSION = "1.21.11";
    public static final String URL = "https://piston-data.mojang.com/v1/objects/031a68bebf55d824f66d6573d8c752f0e1bf232a/client.txt";
    public static final String SHA1 = "031a68bebf55d824f66d6573d8c752f0e1bf232a";

    private OfficialMappings() {}

    public static Path cached() { return LockPaths.cache().resolve("mappings").resolve(VERSION + "-client.txt"); }

    public static boolean present() {
        try {
            return Files.exists(cached()) && SHA1.equalsIgnoreCase(sha1(cached()));
        } catch (IOException e) {
            return false;
        }
    }

    public static void download() throws IOException {
        Path file = cached();
        Files.createDirectories(file.getParent());
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        Path tmp = file.resolveSibling(file.getFileName() + ".part");
        try {
            HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(90)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " from Mojang");
            try (InputStream in = r.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (!SHA1.equalsIgnoreCase(sha1(tmp))) {
            Files.deleteIfExists(tmp);
            throw new IOException("downloaded mappings failed SHA-1 verification");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static String sha1(Path p) throws IOException {
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
