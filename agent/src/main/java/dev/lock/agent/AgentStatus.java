package dev.lock.agent;

import dev.lock.core.bridge.AgentContext.HookStatus;
import dev.lock.core.ipc.RuntimeServer;
import dev.lock.core.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration state as seen by the agent. Served over IPC from the moment the JVM starts, so the
 * launcher can report the real state (waiting for the game, ready, failed, ...) rather than a guess.
 */
public final class AgentStatus {
    public static final AgentStatus INSTANCE = new AgentStatus();

    volatile String state = "STARTING";
    volatile String message = "";
    volatile String lockVersion = "dev";
    volatile String minecraftVersion = "unknown";
    final Map<String, Object> startupInfo = new ConcurrentHashMap<>();
    final Map<String, HookStatus> hooks = java.util.Collections.synchronizedMap(new LinkedHashMap<>());
    private RuntimeServer server;

    void fail(String state, String message) {
        this.state = state;
        this.message = message;
        Log.error(message);
    }

    void hook(String name, boolean ok, String detail) {
        hooks.put(name, new HookStatus(name, ok, detail));
        if (!ok && !detail.startsWith("pending")) Log.warn("hook " + name + " not applied: " + detail);
    }

    void startIpc() {
        try {
            server = new RuntimeServer(this::handle);
            server.start(minecraftVersion, lockVersion);
        } catch (Exception e) {
            Log.warn("IPC unavailable; the launcher will not see live status: " + e);
        }
    }

    private Map<String, Object> handle(Map<String, Object> req) {
        Object cmd = req.get("cmd");
        if ("status".equals(cmd) || Hooks.bridge() == null) {
            Map<String, Object> m = agentStatus();
            if (Hooks.bridge() != null) {
                Map<String, Object> client = Hooks.bridge().handleCommand(req);
                m.putAll(client);
            } else if (!"status".equals(cmd)) {
                m.put("ok", false);
                m.put("error", "Lock is not running in game yet (" + state + ")");
            }
            return m;
        }
        return Hooks.bridge().handleCommand(req);
    }

    Map<String, Object> agentStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("state", state);
        m.put("message", message);
        m.put("lockVersion", lockVersion);
        m.put("minecraftVersion", minecraftVersion);
        m.put("pid", ProcessHandle.current().pid());
        m.put("startup", new LinkedHashMap<>(startupInfo));
        List<Object> hs = new ArrayList<>();
        synchronized (hooks) {
            for (HookStatus h : hooks.values()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("name", h.name());
                e.put("applied", h.applied());
                e.put("detail", h.detail());
                hs.add(e);
            }
        }
        m.put("hooks", hs);
        List<Object> errs = new ArrayList<>();
        for (Log.Entry e : Log.recent()) {
            if (e.level() == Log.Level.INFO) continue;
            Map<String, Object> en = new LinkedHashMap<>();
            en.put("time", e.time());
            en.put("level", e.level().name());
            en.put("message", e.message());
            errs.add(en);
        }
        m.put("log", errs);
        return m;
    }
}
