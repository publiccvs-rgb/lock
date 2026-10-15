package dev.lock.mapping;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Supplies super class and interfaces for classes in the remapper's source namespace. */
public final class ClassHierarchy {
    public record Node(String superName, List<String> interfaces) {}

    private static final Node MISSING = new Node(null, List.of());
    private final Function<String, Node> resolver;
    private final Map<String, Node> cache = new ConcurrentHashMap<>();

    private ClassHierarchy(Function<String, Node> resolver) { this.resolver = resolver; }

    /** Hierarchy read directly from class bytes that are already in the source namespace. */
    public static ClassHierarchy fromBytes(Function<String, byte[]> bytes) {
        return new ClassHierarchy(name -> parse(bytes.apply(name)));
    }

    /** Hierarchy computed by a custom resolver (e.g. reading obfuscated bytes and mapping the names back). */
    public static ClassHierarchy fromResolver(Function<String, Node> resolver) { return new ClassHierarchy(resolver); }

    public static Node parse(byte[] b) {
        if (b == null) return null;
        ClassReader r = new ClassReader(b);
        return new Node(r.getSuperName(), List.of(r.getInterfaces()));
    }

    public Node get(String internalName) {
        Node n = cache.get(internalName);
        if (n != null) return n == MISSING ? null : n;
        n = resolver.apply(internalName);
        cache.put(internalName, n == null ? MISSING : n);
        return n;
    }

    public static byte[] readAll(InputStream in) throws IOException {
        try (in) { return in.readAllBytes(); }
    }
}
