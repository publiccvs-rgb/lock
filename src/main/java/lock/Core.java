package lock;

import lock.gui.LockScreen;
import lock.module.Esp;
import lock.module.Module;
import lock.module.ModuleManager;
import lock.render.CharmSystem;
import lock.render.WorldEsp;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** Entry points the platform layer (mixins) calls into. Everything here is typed with game classes. */
public final class Core {
    public static ModuleManager modules;
    private static boolean menuKeyDown;
    private static final boolean[] bindDown = new boolean[512];

    private Core() {}

    public static void init() {
        modules = new ModuleManager();
        modules.load();
    }

    // ---- frame / tick ----

    public static void tick() {
        if (modules == null) return;
        Minecraft mc = Minecraft.getInstance();
        modules.tick();
        long win = mc.getWindow().handle();
        boolean down = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (down && !menuKeyDown && mc.screen == null && mc.player != null) mc.setScreen(new LockScreen());
        menuKeyDown = down;
        if (mc.screen == null && mc.player != null) pollBinds(win);
    }

    private static void pollBinds(long win) {
        int i = 0;
        for (Module m : modules.all) {
            int key = m.bind.get();
            if (key < 0) continue;
            boolean down = GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
            boolean was = bindDown[key & 511];
            if (m.bind.hold) { if (down != m.enabled()) m.setEnabled(down); }
            else if (down && !was) m.toggle();
            bindDown[key & 511] = down;
        }
    }

    public static void world(Camera camera, DeltaTracker delta, Matrix4f view, Matrix4f proj) {
        if (modules == null) return;
        WorldEsp.render(camera, delta.getGameTimeDeltaPartialTick(true), view, proj);
        if (modules.ambience.enabled()) lock.render.Sky.render(camera, delta.getGameTimeDeltaPartialTick(true), view);
    }

    public static void hud(GuiGraphics g, DeltaTracker delta) {
        if (modules == null) return;
        lock.gui.Hud.render(g, delta);
    }

    // ---- entity outline / team colour ----

    public static boolean glow(boolean orig, Entity e) {
        if (modules == null) return orig;
        var o = modules.outlines;
        if (orig || !o.enabled() || !Esp.targets(e)) return orig;
        return !o.playersOnly.get() || e instanceof Player;
    }

    public static int teamColor(int orig, Entity e) {
        if (modules == null) return orig;
        var o = modules.outlines;
        if (!o.enabled() || !Esp.targets(e)) return orig;
        if (o.playersOnly.get() && !(e instanceof Player)) return orig;
        return Esp.color(e, o.color);
    }

    public static boolean nameTag(boolean orig, Entity e) {
        if (modules == null) return orig;
        var n = modules.nameTags;
        if (orig && n.enabled() && n.hideVanilla.get() && e instanceof Player && Esp.targets(e)) return false;
        return orig;
    }

    // ---- chams ----

    public static void extract(LivingEntity e, LivingEntityRenderState state) { CharmSystem.extract(e, state); }
    public static int tint(int orig, LivingEntityRenderState state) { return CharmSystem.tint(orig, state); }
    public static RenderType layer(RenderType orig, LivingEntityRenderer<?, ?, ?> r, LivingEntityRenderState state) { return CharmSystem.layer(orig, r, state); }
}
