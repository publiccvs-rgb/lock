package dev.lock.mapping;

import dev.lock.core.json.Json;
import dev.lock.core.json.JsonUtil;
import dev.lock.core.util.LockPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Development setup: downloads the official Minecraft client, its mappings and libraries for one
 * version into a local cache and produces a Mojang-named compile-only jar. Used by the build and
 * by the integration test harness; nothing it downloads is packaged into Lock.
 */
public final class DevSetup {
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public static void main(String[] args) throws Exception {
        String version = args.length > 0 ? args[0] : "1.21.11";
        Path dir = Paths.get(args.length > 1 ? args[1] : ".mc-dev").resolve(version);
        Files.createDirectories(dir);
        Path versionJson = dir.resolve(version + ".json");
        if (!Files.exists(versionJson)) {
            Map<String, Object> manifest = Json.parseObject(fetchString(MANIFEST));
            String url = null, sha = null;
            for (Object o : JsonUtil.arr(manifest.get("versions"))) {
                Map<String, Object> v = JsonUtil.obj(o);
                if (version.equals(JsonUtil.str(v, "id", null))) {
                    url = JsonUtil.str(v, "url", null);
                    sha = JsonUtil.str(v, "sha1", null);
                }
            }
            if (url == null) throw new IOException("version " + version + " not in manifest");
            download(url, sha, versionJson);
        }
        Map<String, Object> v = Json.parseObject(Files.readString(versionJson));
        Map<String, Object> downloads = JsonUtil.obj(v.get("downloads"));
        Path client = dir.resolve("client.jar");
        Path mappings = dir.resolve("client.txt");
        download(JsonUtil.str(JsonUtil.obj(downloads.get("client")), "url", null), JsonUtil.str(JsonUtil.obj(downloads.get("client")), "sha1", null), client);
        Map<String, Object> cm = JsonUtil.obj(downloads.get("client_mappings"));
        download(JsonUtil.str(cm, "url", null), JsonUtil.str(cm, "sha1", null), mappings);

        List<String> cp = new ArrayList<>();
        Path libs = dir.resolve("libraries");
        for (Object o : JsonUtil.arr(v.get("libraries"))) {
            Map<String, Object> lib = JsonUtil.obj(o);
            if (!rulesAllow(JsonUtil.arr(lib.get("rules")))) continue;
            Map<String, Object> art = JsonUtil.obj(JsonUtil.obj(lib.get("downloads")) == null ? null : JsonUtil.obj(lib.get("downloads")).get("artifact"));
            if (art == null) continue;
            Path target = libs.resolve(JsonUtil.str(art, "path", "x.jar"));
            download(JsonUtil.str(art, "url", null), JsonUtil.str(art, "sha1", null), target);
            cp.add(target.toAbsolutePath().toString());
        }
        Files.write(dir.resolve("libraries.txt"), cp);

        Path named = dir.resolve("client-named.jar");
        if (!Files.exists(named) || Files.getLastModifiedTime(named).compareTo(Files.getLastModifiedTime(mappings)) < 0) {
            RemapJar.remap(client, mappings, named);
        }

        // asset index (sounds/languages are optional for the test harness, the index is not)
        Map<String, Object> ai = JsonUtil.obj(v.get("assetIndex"));
        Path index = dir.resolve("assets/indexes/" + JsonUtil.str(ai, "id", "x") + ".json");
        download(JsonUtil.str(ai, "url", null), JsonUtil.str(ai, "sha1", null), index);
        // Asset objects are only needed to actually run the game (tools/dev-run.sh). Sounds are
        // skipped unless -Dlock.devSounds=true; the game runs without them.
        if (args.length > 2 && args[2].equals("--assets")) downloadAssets(index, dir.resolve("assets/objects"));
        System.out.println("Minecraft " + version + " dev environment ready in " + dir.toAbsolutePath());
    }

    static void downloadAssets(Path index, Path objects) throws Exception {
        boolean sounds = Boolean.getBoolean("lock.devSounds");
        Map<String, Object> objs = JsonUtil.obj(Json.parseObject(Files.readString(index)).get("objects"));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
        for (Map.Entry<String, Object> e : objs.entrySet()) {
            if (!sounds && e.getKey().endsWith(".ogg")) continue;
            String hash = JsonUtil.str(JsonUtil.obj(e.getValue()), "hash", null);
            Path target = objects.resolve(hash.substring(0, 2)).resolve(hash);
            if (Files.exists(target)) continue;
            fs.add(pool.submit(() -> {
                download("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash, hash, target);
                return null;
            }));
        }
        for (var f : fs) f.get();
        pool.shutdown();
        System.out.println("Assets ready (" + fs.size() + " downloaded)");
    }

    static boolean rulesAllow(List<Object> rules) {
        if (rules == null) return true;
        boolean allow = false;
        for (Object o : rules) {
            Map<String, Object> r = JsonUtil.obj(o);
            Map<String, Object> os = JsonUtil.obj(r.get("os"));
            boolean matches = os == null || LockPaths.os().equals(JsonUtil.str(os, "name", ""));
            if (os != null && os.containsKey("arch")) matches &= System.getProperty("os.arch").contains(JsonUtil.str(os, "arch", ""));
            if (matches) allow = "allow".equals(JsonUtil.str(r, "action", "allow"));
        }
        return allow;
    }

    static String fetchString(String url) throws IOException, InterruptedException {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " for " + url);
        return r.body();
    }

    public static void download(String url, String sha1, Path target) throws IOException, InterruptedException {
        if (url == null) throw new IOException("missing url for " + target);
        if (Files.exists(target) && (sha1 == null || sha1.equalsIgnoreCase(sha1(target)))) return;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " for " + url);
                try (InputStream in = r.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                if (sha1 != null && !sha1.equalsIgnoreCase(sha1(tmp))) throw new IOException("SHA-1 mismatch for " + url);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(1000L << attempt);
            }
        }
        throw last;
    }

    public static String sha1(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
