package dev.lock.launcher;

import dev.lock.core.json.Json;
import dev.lock.core.json.JsonUtil;
import dev.lock.core.util.LockPaths;
import dev.lock.core.util.OfficialMappings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Installs Lock into the official Minecraft Launcher by adding a "Lock" profile whose JVM arguments
 * load the Lock agent at game start. Authentication, downloads and launching stay with Mojang's
 * launcher. The previous launcher_profiles.json is backed up before every change.
 */
public final class Installer {
    public static final String PROFILE_ID = "lock";
    public static final String DEFAULT_JVM = "-Xmx4G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M";

    public record Check(String label, boolean ok, String detail) {}

    private final Path mcDir;

    public Installer(Path mcDir) { this.mcDir = mcDir; }

    public Path mcDir() { return mcDir; }

    public Path agentTarget() { return mcDir.resolve("lock").resolve("lock-agent.jar"); }

    public List<Path> profileFiles() {
        List<Path> out = new ArrayList<>();
        for (String n : new String[]{"launcher_profiles.json", "launcher_profiles_microsoft_store.json"}) {
            Path p = mcDir.resolve(n);
            if (Files.exists(p)) out.add(p);
        }
        return out;
    }

    public boolean versionInstalled() {
        return Files.exists(mcDir.resolve("versions").resolve(OfficialMappings.VERSION).resolve(OfficialMappings.VERSION + ".json"));
    }

    /** Java runtimes bundled by the official launcher. */
    public List<String> bundledRuntimes() {
        List<String> out = new ArrayList<>();
        Path rt = mcDir.resolve("runtime");
        if (!Files.isDirectory(rt)) return out;
        try (Stream<Path> s = Files.list(rt)) {
            s.filter(Files::isDirectory).forEach(p -> out.add(p.getFileName().toString()));
        } catch (IOException ignored) {
        }
        return out;
    }

    public boolean profileInstalled() {
        for (Path p : profileFiles()) {
            try {
                Map<String, Object> profiles = JsonUtil.obj(Json.parseObject(Files.readString(p)).get("profiles"));
                if (profiles != null && profiles.containsKey(PROFILE_ID)) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public boolean agentCurrent() {
        try (InputStream bundled = bundledAgent()) {
            if (bundled == null || !Files.exists(agentTarget())) return false;
            return java.util.Arrays.equals(bundled.readAllBytes(), Files.readAllBytes(agentTarget()));
        } catch (IOException e) {
            return false;
        }
    }

    static InputStream bundledAgent() { return Installer.class.getClassLoader().getResourceAsStream("lock-agent.jar"); }

    public List<Check> checks() {
        List<Check> c = new ArrayList<>();
        c.add(new Check("Minecraft folder", Files.isDirectory(mcDir), mcDir.toString()));
        c.add(new Check("Official launcher profiles", !profileFiles().isEmpty(), profileFiles().isEmpty() ? "launcher_profiles.json not found — run the Minecraft Launcher once" : profileFiles().get(0).getFileName().toString()));
        c.add(new Check("Minecraft " + OfficialMappings.VERSION, versionInstalled(), versionInstalled() ? "installed" : "not downloaded yet — the Minecraft Launcher downloads it on first launch of the Lock profile"));
        List<String> rts = bundledRuntimes();
        c.add(new Check("Java runtime", !rts.isEmpty() || Runtime.version().feature() >= 21, rts.isEmpty() ? "launcher runtime not found; Java " + Runtime.version().feature() + " on this system" : "launcher runtimes: " + String.join(", ", rts)));
        c.add(new Check("Official mappings", OfficialMappings.present(), OfficialMappings.present() ? "downloaded and verified" : "downloaded from Mojang during install"));
        c.add(new Check("Lock runtime", agentCurrent(), agentCurrent() ? agentTarget().toString() : Files.exists(agentTarget()) ? "outdated — reinstall to update" : "not installed"));
        c.add(new Check("Lock profile", profileInstalled(), profileInstalled() ? "\"Lock " + OfficialMappings.VERSION + "\" in the Minecraft Launcher" : "not installed"));
        return c;
    }

    /** The -javaagent argument. The official launcher runs the game with the game directory as working directory, so a relative path avoids quoting problems with spaces. */
    String agentArgument() {
        String abs = agentTarget().toAbsolutePath().toString();
        if (!abs.contains(" ")) return "-javaagent:" + abs;
        return "-javaagent:lock/lock-agent.jar";
    }

    public String install(String jvmArgs) throws IOException {
        if (profileFiles().isEmpty()) throw new IOException("launcher_profiles.json not found in " + mcDir + ". Start the official Minecraft Launcher once, then try again.");
        Files.createDirectories(agentTarget().getParent());
        try (InputStream in = bundledAgent()) {
            if (in == null) throw new IOException("this launcher build does not contain lock-agent.jar");
            Files.copy(in, agentTarget(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (!OfficialMappings.present()) OfficialMappings.download();
        String args = (jvmArgs == null || jvmArgs.isBlank() ? DEFAULT_JVM : jvmArgs.trim()) + " " + agentArgument();
        for (Path p : profileFiles()) {
            backup(p);
            Map<String, Object> root = Json.parseObject(Files.readString(p, StandardCharsets.UTF_8));
            Map<String, Object> profiles = JsonUtil.obj(root.get("profiles"));
            if (profiles == null) {
                profiles = new LinkedHashMap<>();
                root.put("profiles", profiles);
            }
            Map<String, Object> prof = JsonUtil.obj(profiles.get(PROFILE_ID));
            if (prof == null) prof = new LinkedHashMap<>();
            String now = Instant.now().toString();
            prof.putIfAbsent("created", now);
            prof.put("lastUsed", now);
            prof.put("icon", "data:image/png;base64," + ProfileIcon.base64());
            prof.put("lastVersionId", OfficialMappings.VERSION);
            prof.put("name", "Lock " + OfficialMappings.VERSION);
            prof.put("type", "custom");
            prof.put("javaArgs", args);
            profiles.put(PROFILE_ID, prof);
            write(p, Json.pretty(root));
        }
        return "Installed. Open the Minecraft Launcher and play the \"Lock " + OfficialMappings.VERSION + "\" profile.";
    }

    public String uninstall() throws IOException {
        for (Path p : profileFiles()) {
            Map<String, Object> root = Json.parseObject(Files.readString(p, StandardCharsets.UTF_8));
            Map<String, Object> profiles = JsonUtil.obj(root.get("profiles"));
            if (profiles != null && profiles.remove(PROFILE_ID) != null) {
                backup(p);
                write(p, Json.pretty(root));
            }
        }
        Files.deleteIfExists(agentTarget());
        return "Lock profile removed. Your Lock settings were kept in " + LockPaths.home();
    }

    private static void backup(Path p) throws IOException {
        Path dir = LockPaths.backups();
        Files.createDirectories(dir);
        Files.copy(p, dir.resolve(p.getFileName() + "." + System.currentTimeMillis() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void write(Path p, String text) throws IOException {
        Path tmp = p.resolveSibling(p.getFileName() + ".lock-tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
    }
}
