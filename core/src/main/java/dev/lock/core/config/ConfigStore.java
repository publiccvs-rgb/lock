package dev.lock.core.config;

import dev.lock.core.json.Json;
import dev.lock.core.json.JsonUtil;
import dev.lock.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads and saves {@link LockConfig}. Saving is atomic (write to temp file, then move) and keeps
 * rotating backups. A corrupt file is preserved next to the original and defaults are used, so a
 * bad edit can never brick the client or silently destroy the user's data.
 */
public final class ConfigStore {
    private final Path file;
    private final Path backupDir;
    private final List<String> problems = new ArrayList<>();
    private String recoveredFrom;

    public ConfigStore(Path file, Path backupDir) {
        this.file = file;
        this.backupDir = backupDir;
    }

    public Path file() { return file; }

    /** Validation problems found during the last load. */
    public List<String> problems() { return problems; }

    /** If the last load had to recover from a corrupt file, the path where the corrupt copy was kept. */
    public String recoveredFrom() { return recoveredFrom; }

    public LockConfig load() {
        problems.clear();
        recoveredFrom = null;
        if (!Files.exists(file)) return LockConfig.defaults();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> m = Json.parseObject(text);
            int schema = (int) JsonUtil.num(m, "schema", LockConfig.SCHEMA);
            if (schema > LockConfig.SCHEMA) problems.add("config written by a newer Lock (schema " + schema + "); unknown fields are preserved");
            LockConfig c = LockConfig.fromJson(m, problems);
            for (String p : problems) Log.warn("config: " + p);
            return c;
        } catch (Exception e) {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt-" + stamp);
            try {
                Files.copy(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            recoveredFrom = corrupt.toString();
            problems.add("config could not be read (" + e.getMessage() + "); kept a copy at " + corrupt + " and loaded defaults");
            Log.error("config unreadable, recovered with defaults", e);
            LockConfig fromBackup = loadNewestBackup();
            if (fromBackup != null) {
                problems.add("restored settings from the newest backup");
                return fromBackup;
            }
            return LockConfig.defaults();
        }
    }

    private LockConfig loadNewestBackup() {
        if (backupDir == null || !Files.isDirectory(backupDir)) return null;
        try (Stream<Path> s = Files.list(backupDir)) {
            List<Path> list = s.filter(p -> p.getFileName().toString().startsWith("config-")).sorted(Comparator.reverseOrder()).toList();
            for (Path p : list) {
                try {
                    return LockConfig.fromJson(Json.parseObject(Files.readString(p)), new ArrayList<>());
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public synchronized void save(LockConfig c, int backupsKept) throws IOException {
        Files.createDirectories(file.getParent());
        String text = Json.pretty(c.toJson());
        if (Files.exists(file) && backupDir != null && backupsKept > 0) backup(backupsKept);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void backup(int keep) {
        try {
            Files.createDirectories(backupDir);
            // At most one backup per minute so autosave does not churn the disk.
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
            Path target = backupDir.resolve("config-" + stamp + ".json");
            if (Files.exists(target)) return;
            Files.copy(file, target);
            try (Stream<Path> s = Files.list(backupDir)) {
                List<Path> list = s.filter(p -> p.getFileName().toString().startsWith("config-")).sorted(Comparator.reverseOrder()).toList();
                for (int i = keep; i < list.size(); i++) Files.deleteIfExists(list.get(i));
            }
        } catch (IOException e) {
            Log.warn("config backup failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ export / import

    public static void exportProfile(Profile p, Path target) throws IOException {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("lockProfileExport", 1);
        m.put("profile", p.toJson());
        Files.writeString(target, Json.pretty(m), StandardCharsets.UTF_8);
    }

    public static void exportAll(LockConfig c, Path target) throws IOException {
        Files.writeString(target, Json.pretty(c.toJson()), StandardCharsets.UTF_8);
    }

    /**
     * Imports a profile export or a full config file. Profiles are added (renamed on conflict),
     * waypoints are merged by id. Returns a human-readable summary.
     */
    public static String importFile(LockConfig into, Path source) throws IOException {
        Map<String, Object> m = Json.parseObject(Files.readString(source, StandardCharsets.UTF_8));
        List<String> problems = new ArrayList<>();
        List<String> added = new ArrayList<>();
        if (m.containsKey("lockProfileExport")) {
            Map<String, Object> pj = JsonUtil.obj(m.get("profile"));
            String name = JsonUtil.str(pj, "name", "Imported");
            if (LockConfig.validateName(name) != null) name = "Imported";
            added.add(into.addImported(Profile.fromJson(name, pj, problems)));
        } else if (m.containsKey("profiles")) {
            LockConfig other = LockConfig.fromJson(m, problems);
            for (Profile p : other.profiles().values()) {
                if (p.builtIn()) continue;
                added.add(into.addImported(p));
            }
            int wp = 0;
            for (Waypoint w : other.waypoints) if (into.waypoint(w.id) == null) { into.waypoints.add(w); wp++; }
            if (wp > 0) added.add(wp + " waypoint(s)");
        } else {
            throw new IOException("not a Lock profile or configuration file");
        }
        String summary = added.isEmpty() ? "Nothing new to import." : "Imported: " + String.join(", ", added);
        if (!problems.isEmpty()) summary += " (" + problems.size() + " value(s) corrected)";
        return summary;
    }
}
