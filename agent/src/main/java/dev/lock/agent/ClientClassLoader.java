package dev.lock.agent;

import dev.lock.core.util.Log;
import dev.lock.mapping.ClassHierarchy;
import dev.lock.mapping.HierarchyRemapper;
import dev.lock.mapping.Mappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads Lock's in-game client classes. They are compiled against Mojang-named Minecraft and stored
 * in the agent jar under {@code lock-client/}; this loader remaps each one to the obfuscated names of
 * the running game before defining it. Minecraft classes resolve through the parent (system) loader.
 */
public final class ClientClassLoader extends ClassLoader {
    static final String PREFIX = "lock-client/";
    private final ClassLoader parent;
    private final Mappings mappings;
    private final HierarchyRemapper remapper;

    static { registerAsParallelCapable(); }

    public ClientClassLoader(ClassLoader parent, Mappings mappings) {
        super("lock-client", parent);
        this.parent = parent;
        this.mappings = mappings;
        ClassHierarchy hierarchy = ClassHierarchy.fromResolver(this::resolveNamed);
        this.remapper = new HierarchyRemapper(mappings.toObf, hierarchy);
    }

    /** Super class/interfaces of a class given by its Mojang (named) internal name. */
    private ClassHierarchy.Node resolveNamed(String named) {
        String obf = mappings.toObf.mapClass(named);
        if (obf != null) {
            ClassHierarchy.Node n = ClassHierarchy.parse(read(parent, obf + ".class"));
            if (n == null) return null;
            List<String> ifs = new ArrayList<>();
            for (String i : n.interfaces()) ifs.add(toNamed(i));
            return new ClassHierarchy.Node(n.superName() == null ? null : toNamed(n.superName()), ifs);
        }
        byte[] own = read(parent, PREFIX + named + ".class");
        if (own != null) return ClassHierarchy.parse(own);
        return ClassHierarchy.parse(read(parent, named + ".class"));
    }

    private String toNamed(String obf) {
        String n = mappings.toNamed.mapClass(obf);
        return n != null ? n : obf;
    }

    private static byte[] read(ClassLoader cl, String resource) {
        try (InputStream in = cl.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String internal = name.replace('.', '/');
        byte[] b = read(parent, PREFIX + internal + ".class");
        if (b == null) throw new ClassNotFoundException(name);
        try {
            ClassReader cr = new ClassReader(b);
            ClassWriter cw = new ClassWriter(0);
            cr.accept(new ClassRemapper(cw, remapper), 0);
            byte[] out = cw.toByteArray();
            return defineClass(name, out, 0, out.length);
        } catch (RuntimeException | LinkageError e) {
            Log.error("failed to load Lock class " + name, e);
            throw new ClassNotFoundException(name, e);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream own = parent.getResourceAsStream(PREFIX + name);
        return own != null ? own : super.getResourceAsStream(name);
    }

    public Mappings mappings() { return mappings; }
}
