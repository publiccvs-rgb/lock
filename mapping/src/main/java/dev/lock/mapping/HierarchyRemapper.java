package dev.lock.mapping;

import org.objectweb.asm.commons.Remapper;

import java.util.HashSet;
import java.util.Set;

/**
 * ASM remapper that resolves member names through the class hierarchy, so that a call such as
 * {@code LocalPlayer.getX()} (declared on {@code Entity}) or an override of {@code Screen.render}
 * declared in one of Lock's own classes is mapped correctly.
 */
public final class HierarchyRemapper extends Remapper {
    private final Mappings.Direction dir;
    private final ClassHierarchy hierarchy;

    public HierarchyRemapper(Mappings.Direction dir, ClassHierarchy hierarchy) {
        this.dir = dir;
        this.hierarchy = hierarchy;
    }

    @Override
    public String map(String internalName) {
        String m = dir.mapClass(internalName);
        if (m != null) return m;
        // inner classes of mapped outers that are not listed themselves
        int dollar = internalName.lastIndexOf('$');
        if (dollar > 0) {
            String outer = map(internalName.substring(0, dollar));
            if (outer != null && !outer.equals(internalName.substring(0, dollar))) return outer + internalName.substring(dollar);
        }
        return internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (name.startsWith("<") || owner.startsWith("[")) return name;
        String r = findMethod(owner, name, descriptor, new HashSet<>());
        return r != null ? r : name;
    }

    private String findMethod(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        String r = dir.method(owner, name, desc);
        if (r != null) return r;
        if (owner.startsWith("java/")) return null;
        ClassHierarchy.Node n = hierarchy.get(owner);
        if (n == null) return null;
        r = findMethod(n.superName(), name, desc, seen);
        if (r != null) return r;
        for (String i : n.interfaces()) {
            r = findMethod(i, name, desc, seen);
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        String r = findField(owner, name, descriptor, new HashSet<>());
        return r != null ? r : name;
    }

    private String findField(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        String r = dir.field(owner, name, desc);
        if (r != null) return r;
        if (owner.startsWith("java/")) return null;
        ClassHierarchy.Node n = hierarchy.get(owner);
        if (n == null) return null;
        r = findField(n.superName(), name, desc, seen);
        if (r != null) return r;
        for (String i : n.interfaces()) {
            r = findField(i, name, desc, seen);
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public String mapRecordComponentName(String owner, String name, String descriptor) {
        return mapFieldName(owner, name, descriptor);
    }
}
