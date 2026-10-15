package dev.lock.agent;

import dev.lock.core.util.Log;
import dev.lock.mapping.Mappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Applies {@link HookSpecs} to Minecraft classes as the JVM loads them. */
final class HookTransformer implements ClassFileTransformer {
    private record Target(HookSpecs.Spec spec, String obfName, String obfDesc) {}

    private final Mappings mappings;
    private final AgentStatus status;
    private final Map<String, List<Target>> byClass = new HashMap<>();

    HookTransformer(Mappings m, List<HookSpecs.Spec> specs, AgentStatus status) {
        this.mappings = m;
        this.status = status;
        for (HookSpecs.Spec s : specs) {
            String owner = m.toObf.mapClass(s.owner());
            String name = owner == null ? null : m.toObf.method(s.owner(), s.method(), s.desc());
            if (owner == null || name == null) {
                status.hook(s.name(), false, "not found in mappings: " + s.owner() + "." + s.method() + s.desc());
                continue;
            }
            status.hook(s.name(), false, "pending: class not loaded yet");
            byClass.computeIfAbsent(owner, k -> new ArrayList<>()).add(new Target(s, name, m.toObf.mapDesc(s.desc())));
        }
    }

    int pendingCount() { return byClass.values().stream().mapToInt(List::size).sum(); }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain pd, byte[] bytes) {
        if (className == null) return null;
        List<Target> targets = byClass.get(className);
        if (targets == null) return null;
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            int applied = 0;
            for (Target t : targets) {
                MethodNode mn = null;
                for (MethodNode m : new ArrayList<>(cn.methods)) if (m.name.equals(t.obfName) && m.desc.equals(t.obfDesc)) mn = m;
                if (mn == null) {
                    status.hook(t.spec().name(), false, "method missing in class " + className);
                    continue;
                }
                try {
                    t.spec().injector().inject(cn, mn, mappings);
                    status.hook(t.spec().name(), true, t.spec().owner().substring(t.spec().owner().lastIndexOf('/') + 1) + "." + t.spec().method());
                    applied++;
                } catch (RuntimeException e) {
                    status.hook(t.spec().name(), false, "injection failed: " + e.getMessage());
                }
            }
            if (applied == 0) return null;
            // COMPUTE_MAXS only: injected code adds explicit frames, and COMPUTE_FRAMES would need
            // to load game classes from inside the transformer.
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            Log.info("hooked " + mappings.toNamed.mapClass(className) + " (" + applied + " injection(s))");
            return cw.toByteArray();
        } catch (Throwable t) {
            Log.error("failed to transform " + className + "; leaving it unmodified", t);
            for (Target target : targets) status.hook(target.spec().name(), false, "transform failed: " + t);
            return null;
        }
    }
}
