package dev.lock.client.core;

import dev.lock.client.LockClient;
import dev.lock.client.module.ClientModule;
import dev.lock.core.bridge.AgentContext;
import dev.lock.core.json.Json;
import dev.lock.core.util.Log;
import dev.lock.core.util.LockPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime status, compatibility checks and error reporting. */
public final class Diagnostics {
    private final LockClient lock;
    public final List<String> compatibility = new ArrayList<>();

    public Diagnostics(LockClient lock) { this.lock = lock; }

    public int hooksApplied() { int n = 0; for (AgentContext.HookStatus h : lock.agent.hooks()) if (h.applied()) n++; return n; }

    public List<AgentContext.HookStatus> failedHooks() {
        List<AgentContext.HookStatus> out = new ArrayList<>();
        for (AgentContext.HookStatus h : lock.agent.hooks()) if (!h.applied()) out.add(h);
        return out;
    }

    /** Checks run once the game is up; results are shown in the Diagnostics tab and sent to the launcher. */
    public void runCompatibilityChecks() {
        compatibility.clear();
        compatibility.add("Minecraft " + lock.agent.minecraftVersion() + (lock.agent.minecraftVersion().equals("1.21.11") ? " (supported)" : " (UNSUPPORTED)"));
        compatibility.add("Java " + System.getProperty("java.version") + ", " + System.getProperty("os.arch"));
        compatibility.add("Hooks applied: " + hooksApplied() + "/" + lock.agent.hooks().size());
        for (AgentContext.HookStatus h : failedHooks()) compatibility.add("Hook not active: " + h.name() + " - " + h.detail());
        List<String> unbound = lock.modules.unbound();
        compatibility.add(unbound.isEmpty() ? "All catalog modules have implementations" : "Modules without implementation: " + unbound);
        for (String p : lock.config.loadProblems()) compatibility.add("Config: " + p);
        for (String c : compatibility) Log.info("compat: " + c);
    }

    public Map<String, Object> moduleStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ClientModule mod : lock.modules.all()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("enabled", mod.enabled());
            e.put("active", mod.active());
            e.put("faulted", lock.modules.faulted(mod.def.id));
            e.put("errors", mod.errorCount());
            e.put("support", mod.def.support.name());
            m.put(mod.def.id, e);
        }
        return m;
    }

    public Path writeReport() throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", LocalDateTime.now().toString());
        m.put("lock", lock.agent.lockVersion());
        m.put("minecraft", lock.agent.minecraftVersion());
        m.put("startup", lock.agent.startupInfo());
        List<Object> hooks = new ArrayList<>();
        for (AgentContext.HookStatus h : lock.agent.hooks()) hooks.add(h.name() + ": " + (h.applied() ? "ok" : "NOT APPLIED") + " (" + h.detail() + ")");
        m.put("hooks", hooks);
        m.put("compatibility", compatibility);
        m.put("modules", moduleStatus());
        m.put("hudRenderMicros", lock.hud.averageCostMicros());
        List<Object> log = new ArrayList<>();
        for (Log.Entry e : Log.recent()) log.add(e.level() + " " + e.message());
        m.put("log", log);
        Path p = LockPaths.logs().resolve("diagnostics-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        Files.createDirectories(p.getParent());
        Files.writeString(p, Json.pretty(m));
        return p;
    }
}
