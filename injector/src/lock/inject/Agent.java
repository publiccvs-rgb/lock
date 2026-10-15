package lock.inject;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

/**
 * javaagent entry point. Loaded dynamically into an already-running JVM via the Attach API (see {@link Attacher}).
 * Args, semicolon separated: "targetInternalClass;method;descriptor" - the coordinates {@link Mapping} resolved
 * on the attaching side. Kept intentionally narrow for now: one hook, proven working, rather than a wide surface
 * that has never actually run against a live process.
 */
public final class Agent {
    public static void agentmain(String args, Instrumentation inst) { start(args, inst); }
    public static void premain(String args, Instrumentation inst) { start(args, inst); }

    private static void start(String args, Instrumentation inst) {
        try {
            String[] p = args.split(";");
            String internalClass = p[0], method = p[1], descriptor = p[2];
            System.out.println("[lock.inject] agent attached, patching " + internalClass + "." + method + descriptor);
            Transformer t = new Transformer(internalClass, method, descriptor, "lock/inject/Hooks", "onTick");
            inst.addTransformer(t, true);

            Class<?> target = findLoaded(inst, internalClass);
            if (target != null) {
                inst.retransformClasses(target);
                System.out.println("[lock.inject] retransformed already-loaded class " + target.getName());
            } else {
                System.out.println("[lock.inject] class not loaded yet, will patch on first load: " + internalClass);
            }
            System.out.println("[lock.inject] agent ready");
        } catch (UnmodifiableClassException | RuntimeException e) {
            System.err.println("[lock.inject] agent failed to start: " + e);
        }
    }

    private static Class<?> findLoaded(Instrumentation inst, String internalName) {
        String dotted = internalName.replace('/', '.');
        for (Class<?> c : inst.getAllLoadedClasses()) if (c.getName().equals(dotted)) return c;
        return null;
    }
}
