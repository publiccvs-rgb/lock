package dev.lock.agent;

import dev.lock.core.bridge.ClientBridge;
import dev.lock.core.util.Log;

import java.lang.reflect.Constructor;

/**
 * Static entry points called from bytecode injected into Minecraft. Every call is guarded: an
 * exception inside Lock is logged and, if a hook keeps failing, that hook is switched off rather than
 * crashing the game.
 */
public final class Hooks {
    private static volatile ClientBridge bridge;
    private static ClientClassLoader loader;
    private static AgentStatus status;
    private static volatile boolean initFailed;
    private static final int MAX_FAILURES = 50;
    private static final int[] failures = new int[16];
    private static final boolean[] disabled = new boolean[16];
    private static final String[] NAMES = {"tick", "hud", "cancel", "fov", "turn", "mouse", "scroll", "tooltip", "key"};

    private Hooks() {}

    static void install(ClientClassLoader l, AgentStatus s) {
        loader = l;
        status = s;
    }

    public static ClientBridge bridge() { return bridge; }

    private static boolean ready(int id) {
        if (bridge != null) return !disabled[id];
        if (initFailed || loader == null) return false;
        return false;
    }

    private static void fail(int id, Throwable t) {
        if (++failures[id] == 1) Log.error("Lock hook '" + NAMES[id] + "' threw", t);
        if (failures[id] >= MAX_FAILURES && !disabled[id]) {
            disabled[id] = true;
            status.hook("runtime:" + NAMES[id], false, "disabled after " + MAX_FAILURES + " errors: " + t);
        }
    }

    private static synchronized void init() {
        if (bridge != null || initFailed || loader == null) return;
        long t0 = System.nanoTime();
        try {
            status.state = "INITIALIZING";
            Class<?> c = Class.forName("dev.lock.client.LockClient", true, loader);
            Constructor<?> ctor = c.getConstructor(dev.lock.core.bridge.AgentContext.class);
            bridge = (ClientBridge) ctor.newInstance(LockAgent.context(status));
            status.state = "READY";
            status.startupInfo.put("clientInitMs", (System.nanoTime() - t0) / 1_000_000);
            Log.info("Lock client initialised in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        } catch (Throwable t) {
            initFailed = true;
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
            Log.error("Lock client failed to initialise; continuing without Lock", cause);
            status.fail("FAILED", "Client initialisation failed: " + cause);
        }
    }

    // ----------------------------------------------------------------- entry points (public: called from game classes)

    public static void tick() {
        if (bridge == null) init();
        if (!ready(0)) return;
        try { bridge.tick(); } catch (Throwable t) { fail(0, t); }
    }

    public static void renderHud(Object graphics, Object delta) {
        if (!ready(1)) return;
        try { bridge.renderHud(graphics, delta); } catch (Throwable t) { fail(1, t); }
    }

    public static boolean cancel(int element) {
        if (!ready(2)) return false;
        try { return bridge.cancelVanilla(element); } catch (Throwable t) { fail(2, t); return false; }
    }

    public static float fov(float fov, boolean world) {
        if (!ready(3)) return fov;
        try {
            float f = bridge.modifyFov(fov, world);
            return Float.isFinite(f) && f > 0 ? f : fov;
        } catch (Throwable t) { fail(3, t); return fov; }
    }

    public static double turn(double delta) {
        if (!ready(4)) return delta;
        try {
            double d = bridge.modifyTurn(delta);
            return Double.isFinite(d) ? d : delta;
        } catch (Throwable t) { fail(4, t); return delta; }
    }

    public static void mouseButton(Object info, int action) {
        if (!ready(5)) return;
        try { bridge.mouseButton(info, action); } catch (Throwable t) { fail(5, t); }
    }

    public static boolean scroll(double amount) {
        if (!ready(6)) return false;
        try { return bridge.mouseScroll(amount); } catch (Throwable t) { fail(6, t); return false; }
    }

    public static void tooltip(Object lines, Object stack) {
        if (!ready(7)) return;
        try { bridge.tooltip(stack, lines); } catch (Throwable t) { fail(7, t); }
    }

    public static void key(Object event, int action) {
        if (!ready(8)) return;
        try { bridge.key(event, action); } catch (Throwable t) { fail(8, t); }
    }
}
