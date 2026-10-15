package dev.lock.launcher;

import dev.lock.core.ipc.RuntimeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds running Minecraft Java processes. Games started with Lock announce themselves (with their
 * IPC port) and are always found; other Minecraft processes are recognised from their command line
 * where the operating system exposes it (Linux, macOS; Windows usually does not).
 */
public final class ProcessScanner {
    public record Game(long pid, String version, String java, boolean lock, RuntimeInfo runtime, String command) {}

    private ProcessScanner() {}

    public static List<Game> scan() {
        Map<Long, RuntimeInfo> lockGames = new HashMap<>();
        for (RuntimeInfo r : RuntimeInfo.discover()) lockGames.put(r.pid(), r);
        List<Game> out = new ArrayList<>();
        for (RuntimeInfo r : lockGames.values()) {
            String cmd = ProcessHandle.of(r.pid()).flatMap(h -> h.info().command()).orElse("java");
            out.add(new Game(r.pid(), r.mcVersion(), cmd, true, r, ""));
        }
        ProcessHandle.allProcesses().forEach(h -> {
            if (lockGames.containsKey(h.pid())) return;
            Optional<String> line = h.info().commandLine();
            if (line.isEmpty()) return;
            String l = line.get();
            if (!l.contains("net.minecraft.client.main.Main")) return;
            out.add(new Game(h.pid(), arg(l, "--version"), h.info().command().orElse("java"), false, null, l));
        });
        return out;
    }

    static String arg(String cmd, String name) {
        String[] p = cmd.split("\\s+");
        for (int i = 0; i < p.length - 1; i++) if (p[i].equals(name)) return p[i + 1];
        return "unknown";
    }
}
