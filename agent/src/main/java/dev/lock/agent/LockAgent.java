package dev.lock.agent;

import dev.lock.core.bridge.AgentContext;
import dev.lock.core.json.Json;
import dev.lock.core.json.JsonUtil;
import dev.lock.core.util.Log;
import dev.lock.core.util.LockPaths;
import dev.lock.mapping.Mappings;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point of the Lock runtime. The JVM calls {@link #premain} before Minecraft's main method
 * when the game is started with {@code -javaagent:lock-agent.jar} (the Lock launcher adds this to a
 * profile in the official Minecraft Launcher). Lock deliberately does not support attaching to an
 * already running game.
 */
public final class LockAgent {
    public static final String SUPPORTED_VERSION = "1.21.11";

    private LockAgent() {}

    public static void premain(String args, Instrumentation inst) {
        long t0 = System.nanoTime();
        Log.init(LockPaths.logs().resolve("runtime.log"), "lock");
        String lockVersion = LockAgent.class.getPackage().getImplementationVersion();
        if (lockVersion == null) lockVersion = "dev";
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("lockVersion", lockVersion);
        info.put("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        info.put("arch", System.getProperty("os.arch"));
        info.put("os", System.getProperty("os.name"));
        Log.info("Lock " + lockVersion + " starting (Java " + info.get("java") + ", " + info.get("os") + " " + info.get("arch") + ")");

        AgentStatus status = AgentStatus.INSTANCE;
        status.startupInfo.putAll(info);
        status.lockVersion = lockVersion;
        String mc = detectMinecraftVersion();
        status.minecraftVersion = mc;
        status.startupInfo.put("minecraft", mc);
        status.startIpc();
        try {
            if (args != null && args.contains("disable")) {
                status.fail("DISABLED", "Lock was disabled by the agent argument.");
                return;
            }
            if (!SUPPORTED_VERSION.equals(mc)) {
                status.fail("UNSUPPORTED_VERSION", "Minecraft " + mc + " detected; this Lock build supports " + SUPPORTED_VERSION + " only. The game will run without Lock.");
                return;
            }
            Mappings mappings = MappingProvider.load(mc, status.startupInfo);
            ClientClassLoader loader = new ClientClassLoader(LockAgent.class.getClassLoader(), mappings);
            HookTransformer transformer = new HookTransformer(mappings, HookSpecs.all(mappings), status);
            inst.addTransformer(transformer, false);
            Hooks.install(loader, status);
            status.state = "WAITING_FOR_GAME";
            status.startupInfo.put("agentInitMs", (System.nanoTime() - t0) / 1_000_000);
            Log.info("Lock agent ready in " + (System.nanoTime() - t0) / 1_000_000 + " ms; " + transformer.pendingCount() + " hook targets registered");
        } catch (Throwable t) {
            Log.error("Lock failed to initialise; the game will run without Lock", t);
            status.fail("FAILED", "Initialisation failed: " + t);
        }
    }

    /** Reads the version id from the version.json bundled inside the running client jar. */
    static String detectMinecraftVersion() {
        try (InputStream in = ClassLoader.getSystemResourceAsStream("version.json")) {
            if (in != null) {
                Map<String, Object> m = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                String id = JsonUtil.str(m, "id", null);
                if (id != null) return id;
            }
        } catch (Exception e) {
            Log.warn("could not read version.json: " + e);
        }
        String cmd = System.getProperty("sun.java.command", "");
        String[] parts = cmd.split(" ");
        for (int i = 0; i < parts.length - 1; i++) if (parts[i].equals("--version")) return parts[i + 1];
        return "unknown";
    }

    /** Context handed to the client once it is created. */
    static AgentContext context(AgentStatus status) {
        return new AgentContext() {
            @Override public String lockVersion() { return status.lockVersion; }
            @Override public String minecraftVersion() { return status.minecraftVersion; }
            @Override public List<HookStatus> hooks() { return new ArrayList<>(status.hooks.values()); }
            @Override public Map<String, Object> startupInfo() { return status.startupInfo; }
        };
    }
}
