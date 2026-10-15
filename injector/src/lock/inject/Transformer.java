package lock.inject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Patches exactly one method: emits a static call as the very first instruction (a "HEAD" injection, the same
 * semantics as a Fabric/Mixin @Inject(at = "HEAD")). The target is named by its real internal (slash-separated,
 * obfuscated) class name and the method's obfuscated name + JVM descriptor, both resolved ahead of time by
 * {@link Mapping} - this transformer itself has no knowledge of mappings, only raw bytecode coordinates.
 */
public final class Transformer implements ClassFileTransformer {
    private final String targetInternalName;      // e.g. "gfj" for Minecraft
    private final String targetMethod;             // e.g. "x" for tick()
    private final String targetDescriptor;         // e.g. "()V"
    private final String hookOwner;                // e.g. "lock/inject/Hooks"
    private final String hookMethod;                // e.g. "onTick"

    public Transformer(String targetInternalName, String targetMethod, String targetDescriptor, String hookOwner, String hookMethod) {
        this.targetInternalName = targetInternalName;
        this.targetMethod = targetMethod;
        this.targetDescriptor = targetDescriptor;
        this.hookOwner = hookOwner;
        this.hookMethod = hookMethod;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                             ProtectionDomain domain, byte[] classfileBuffer) {
        if (className == null || !className.equals(targetInternalName)) return null;
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (!name.equals(targetMethod) || !descriptor.equals(targetDescriptor)) return mv;
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            visitMethodInsn(Opcodes.INVOKESTATIC, hookOwner, hookMethod, "()V", false);
                        }
                    };
                }
            };
            reader.accept(cv, 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            System.err.println("[lock.inject] transform failed for " + className + ": " + t);
            return null;
        }
    }
}
