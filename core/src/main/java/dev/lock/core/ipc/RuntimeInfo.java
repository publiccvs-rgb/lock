package dev.lock.core.ipc;

import dev.lock.core.json.Json;
import dev.lock.core.json.JsonUtil;
import dev.lock.core.util.LockPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Announcement file written by a running Lock runtime so the launcher can find and talk to it. */
public record RuntimeInfo(long pid, int port, String token, String mcVersion, String lockVersion, long started) {

    public static Path dir() { return LockPaths.runtime(); }

    public Path file() { return dir().resolve(pid + ".json"); }

    public void write() throws IOException {
        Files.createDirectories(dir());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pid", pid);
        m.put("port", port);
        m.put("token", token);
        m.put("mcVersion", mcVersion);
        m.put("lockVersion", lockVersion);
        m.put("started", started);
        Path tmp = dir().resolve(pid + ".json.tmp");
        Files.writeString(tmp, Json.pretty(m));
        Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            // the token grants control of the running client: keep it private to the user
            Files.setPosixFilePermissions(file(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    /** All announced runtimes whose process is still alive; stale files are removed. */
    public static List<RuntimeInfo> discover() {
        List<RuntimeInfo> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) return out;
        try (Stream<Path> s = Files.list(dir())) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                try {
                    Map<String, Object> m = Json.parseObject(Files.readString(p));
                    RuntimeInfo r = new RuntimeInfo((long) JsonUtil.num(m, "pid", -1), (int) JsonUtil.num(m, "port", 0),
                            JsonUtil.str(m, "token", ""), JsonUtil.str(m, "mcVersion", "?"), JsonUtil.str(m, "lockVersion", "?"),
                            (long) JsonUtil.num(m, "started", 0));
                    if (ProcessHandle.of(r.pid()).map(ProcessHandle::isAlive).orElse(false)) out.add(r);
                    else Files.deleteIfExists(p);
                } catch (Exception e) {
                    // unreadable announcement: ignore
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }
}
