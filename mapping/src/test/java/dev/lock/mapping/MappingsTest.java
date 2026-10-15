package dev.lock.mapping;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class MappingsTest {
    static final String SAMPLE = """
            # comment
            net.minecraft.Base -> a:
                int count -> a
                12:14:void tick() -> b
                java.lang.String name(net.minecraft.Base,int[]) -> c
            net.minecraft.Child -> b:
                1:1:void own() -> d
            """;

    @Test
    void parsesBothDirections() throws Exception {
        Mappings m = Mappings.parse(new StringReader(SAMPLE));
        assertEquals("a", m.toObf.mapClass("net/minecraft/Base"));
        assertEquals("net/minecraft/Child", m.toNamed.mapClass("b"));
        assertEquals("b", m.toObf.method("net/minecraft/Base", "tick", "()V"));
        assertEquals("c", m.toObf.method("net/minecraft/Base", "name", "(Lnet/minecraft/Base;[I)Ljava/lang/String;"));
        assertEquals("name", m.toNamed.method("a", "c", "(La;[I)Ljava/lang/String;"));
        assertEquals("a", m.toObf.field("net/minecraft/Base", "count", "I"));
    }

    @Test
    void remapperResolvesInheritedMembers() throws Exception {
        Mappings m = Mappings.parse(new StringReader(SAMPLE));
        // named hierarchy: Child extends Base
        ClassHierarchy h = ClassHierarchy.fromResolver(n -> n.equals("net/minecraft/Child")
                ? new ClassHierarchy.Node("net/minecraft/Base", java.util.List.of()) : n.equals("net/minecraft/Base")
                ? new ClassHierarchy.Node("java/lang/Object", java.util.List.of()) : null);
        HierarchyRemapper r = new HierarchyRemapper(m.toObf, h);
        assertEquals("b", r.mapMethodName("net/minecraft/Child", "tick", "()V"), "inherited method maps through the superclass");
        assertEquals("a", r.mapFieldName("net/minecraft/Child", "count", "I"));
        assertEquals("toString", r.mapMethodName("net/minecraft/Child", "toString", "()Ljava/lang/String;"));
        assertEquals("b$Inner", r.map("net/minecraft/Child$Inner"), "unlisted inner classes follow their outer class");
    }

    @Test
    void remapsGeneratedClassBytes() throws Exception {
        Mappings m = Mappings.parse(new StringReader(SAMPLE));
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "dev/lock/X", null, "java/lang/Object", null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(Lnet/minecraft/Child;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/Child", "tick", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        ClassHierarchy h = ClassHierarchy.fromResolver(n -> n.equals("net/minecraft/Child") ? new ClassHierarchy.Node("net/minecraft/Base", java.util.List.of()) : null);
        ClassWriter out = new ClassWriter(0);
        new ClassReader(cw.toByteArray()).accept(new ClassRemapper(out, new HierarchyRemapper(m.toObf, h)), 0);
        ClassNode cn = new ClassNode();
        new ClassReader(out.toByteArray()).accept(cn, 0);
        assertEquals("(Lb;)V", cn.methods.get(0).desc);
        MethodInsnNode call = (MethodInsnNode) cn.methods.get(0).instructions.get(1);
        assertEquals("b", call.owner);
        assertEquals("b", call.name);
    }
}
