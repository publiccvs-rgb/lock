package dev.lock.agent;

import dev.lock.core.bridge.VanillaElement;
import dev.lock.mapping.Mappings;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Declares every injection point, in Mojang names. {@link HookTransformer} resolves them against the
 * mappings and reports each one individually, so a single moved method degrades one feature instead
 * of the whole client.
 */
final class HookSpecs {
    static final String HOOKS = "dev/lock/agent/Hooks";

    interface Injector {
        void inject(ClassNode owner, MethodNode method, Mappings m);
    }

    /** Code appended after the original method returns; locals hold the original arguments. */
    interface After {
        void emit(InsnList il, MethodNode wrapper, Mappings m);
    }

    /** owner/name/desc are Mojang names. */
    record Spec(String name, String owner, String method, String desc, Injector injector) {}

    private HookSpecs() {}

    static List<Spec> all(Mappings m) {
        List<Spec> l = new ArrayList<>();
        String gg = "Lnet/minecraft/client/gui/GuiGraphics;", dt = "Lnet/minecraft/client/DeltaTracker;";
        String gui = "net/minecraft/client/gui/Gui";

        l.add(new Spec("tick", "net/minecraft/client/Minecraft", "tick", "()V",
                (cn, mn, mm) -> mn.instructions.insert(call("tick", "()V"))));

        l.add(new Spec("hud", gui, "render", "(" + gg + dt + ")V", wrap((il, w, mm) -> {
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new VarInsnNode(Opcodes.ALOAD, 2));
            il.add(call("renderHud", "(Ljava/lang/Object;Ljava/lang/Object;)V"));
        })));

        cancel(l, VanillaElement.HOTBAR, gui, "renderItemHotbar", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.HEALTH, gui, "renderHearts", "(" + gg + "Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V");
        cancel(l, VanillaElement.ARMOR, gui, "renderArmor", "(" + gg + "Lnet/minecraft/world/entity/player/Player;IIII)V");
        cancel(l, VanillaElement.FOOD, gui, "renderFood", "(" + gg + "Lnet/minecraft/world/entity/player/Player;II)V");
        cancel(l, VanillaElement.AIR, gui, "renderAirBubbles", "(" + gg + "Lnet/minecraft/world/entity/player/Player;III)V");
        cancel(l, VanillaElement.XP, "net/minecraft/client/gui/contextualbar/ExperienceBarRenderer", "renderBackground", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.XP, "net/minecraft/client/gui/contextualbar/ExperienceBarRenderer", "render", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.XP, "net/minecraft/client/gui/contextualbar/ContextualBarRenderer", "renderExperienceLevel", "(" + gg + "Lnet/minecraft/client/gui/Font;I)V");
        cancel(l, VanillaElement.CROSSHAIR, gui, "renderCrosshair", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.EFFECTS, gui, "renderEffects", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.SCOREBOARD, gui, "renderScoreboardSidebar", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.BOSSBAR, gui, "renderBossOverlay", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.ITEM_NAME, gui, "renderSelectedItemName", "(" + gg + ")V");
        cancel(l, VanillaElement.ACTIONBAR, gui, "renderOverlayMessage", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.TITLES, gui, "renderTitle", "(" + gg + dt + ")V");
        cancel(l, VanillaElement.VIGNETTE, gui, "renderVignette", "(" + gg + "Lnet/minecraft/world/entity/Entity;)V");

        l.add(new Spec("fov", "net/minecraft/client/renderer/GameRenderer", "getFov", "(Lnet/minecraft/client/Camera;FZ)F", wrap((il, w, mm) -> {
            // stack: [fov]; local 3 is the useFovSetting flag (false for the held-item pass)
            il.add(new VarInsnNode(Opcodes.ILOAD, 3));
            il.add(call("fov", "(FZ)F"));
        })));

        String mh = "net/minecraft/client/MouseHandler";
        l.add(new Spec("turn", mh, "turnPlayer", "(D)V", (cn, mn, mm) -> {
            String owner = mm.toObf.mapClass(mh);
            InsnList il = new InsnList();
            for (String f : new String[]{"accumulatedDX", "accumulatedDY"}) {
                String obf = mm.toObf.field(mh, f, "D");
                if (obf == null) throw new IllegalStateException("field " + f + " not in mappings");
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, owner, obf, "D"));
                il.add(call("turn", "(D)D"));
                il.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, obf, "D"));
            }
            mn.instructions.insert(il);
        }));
        l.add(new Spec("mouse", mh, "onButton", "(JLnet/minecraft/client/input/MouseButtonInfo;I)V", (cn, mn, mm) -> {
            InsnList il = new InsnList();
            il.add(new VarInsnNode(Opcodes.ALOAD, 3));
            il.add(new VarInsnNode(Opcodes.ILOAD, 4));
            il.add(call("mouseButton", "(Ljava/lang/Object;I)V"));
            mn.instructions.insert(il);
        }));
        l.add(new Spec("key", "net/minecraft/client/KeyboardHandler", "keyPress", "(JILnet/minecraft/client/input/KeyEvent;)V", (cn, mn, mm) -> {
            InsnList il = new InsnList();
            il.add(new VarInsnNode(Opcodes.ALOAD, 4));
            il.add(new VarInsnNode(Opcodes.ILOAD, 3));
            il.add(call("key", "(Ljava/lang/Object;I)V"));
            mn.instructions.insert(il);
        }));
        l.add(new Spec("scroll", mh, "onScroll", "(JDD)V", (cn, mn, mm) -> {
            InsnList il = new InsnList();
            il.add(new VarInsnNode(Opcodes.DLOAD, 5));
            il.add(call("scroll", "(D)Z"));
            il.add(earlyReturn());
            mn.instructions.insert(il);
        }));

        l.add(new Spec("tooltip", "net/minecraft/world/item/ItemStack", "getTooltipLines",
                "(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
                wrap((il, w, mm) -> {
                    // stack: [list]
                    il.add(new InsnNode(Opcodes.DUP));
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(call("tooltip", "(Ljava/lang/Object;Ljava/lang/Object;)V"));
                })));
        return l;
    }

    private static void cancel(List<Spec> l, VanillaElement e, String owner, String method, String desc) {
        l.add(new Spec("vanilla:" + e.setting + ":" + method, owner, method, desc, (cn, mn, mm) -> {
            InsnList il = new InsnList();
            il.add(new IntInsnNode(Opcodes.BIPUSH, e.ordinal()));
            il.add(call("cancel", "(I)Z"));
            il.add(earlyReturn());
            mn.instructions.insert(il);
        }));
    }

    /**
     * Renames the original method to a private {@code lock$<name>} and replaces it with a straight-line
     * wrapper that calls the original and then runs {@code after} with the return value on the stack.
     * Unlike inserting code before return instructions, this never has to agree with the original
     * method's (optimised) stack map frames.
     */
    static Injector wrap(After after) {
        return (cn, mn, mm) -> {
            boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;
            String origName = "lock$" + mn.name;
            MethodNode w = new MethodNode(mn.access, mn.name, mn.desc, mn.signature,
                    mn.exceptions == null ? null : mn.exceptions.toArray(new String[0]));
            mn.name = origName;
            mn.access = (mn.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
            InsnList il = w.instructions;
            int slot = 0;
            if (!isStatic) il.add(new VarInsnNode(Opcodes.ALOAD, slot++));
            for (Type a : Type.getArgumentTypes(mn.desc)) {
                il.add(new VarInsnNode(a.getOpcode(Opcodes.ILOAD), slot));
                slot += a.getSize();
            }
            il.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, cn.name, origName, mn.desc, false));
            after.emit(il, w, mm);
            il.add(new InsnNode(Type.getReturnType(mn.desc).getOpcode(Opcodes.IRETURN)));
            cn.methods.add(w);
        };
    }

    /** {@code ifeq L; return; L: (frame same)} — valid at method entry where locals equal the initial frame. */
    private static InsnList earlyReturn() {
        InsnList il = new InsnList();
        LabelNode cont = new LabelNode();
        il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
        il.add(new InsnNode(Opcodes.RETURN));
        il.add(cont);
        il.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        return il;
    }

    private static InsnList call(String name, String desc) {
        InsnList il = new InsnList();
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false));
        return il;
    }

    private static List<AbstractInsnNode> returns(MethodNode mn, int opcode) {
        List<AbstractInsnNode> out = new ArrayList<>();
        for (AbstractInsnNode n : mn.instructions) if (n.getOpcode() == opcode) out.add(n);
        if (out.isEmpty()) throw new IllegalStateException("no " + Type.getReturnType(mn.desc) + " return found");
        return out;
    }
}
