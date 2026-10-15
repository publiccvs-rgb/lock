package lock.render;

import lock.LockClient;
import lock.module.Esp;
import lock.module.Health;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tiny pill-shaped health bar anchored above each target's head.
 * 30x4 px at full size, shrinks with distance, no frame clutter: dark pill, 1px inset fill,
 * a pale trail that drains after damage, and a soft same-colour halo.
 */
public final class HealthHud {
    private static final class State {
        float shown = -1f, trail = 1f;
        long seen;
    }

    private static final Map<Integer, State> states = new HashMap<>();
    private static long lastFrame = System.nanoTime();

    private HealthHud() {}

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        Health cfg = LockClient.modules.health;
        if (!cfg.enabled() || mc.level == null || mc.player == null || mc.options.hideGui) return;

        long now = System.nanoTime();
        float delta = Math.min((now - lastFrame) / 1e9f, 0.1f);
        lastFrame = now;
        float pt = dt.getGameTimeDeltaPartialTick(true);
        float range = (float) (double) LockClient.modules.general.range.get();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !Esp.targets(e)) continue;

            Vec3 head = new Vec3(
                Mth.lerp(pt, e.xOld, e.getX()),
                Mth.lerp(pt, e.yOld, e.getY()) + e.getBbHeight() + 0.32,
                Mth.lerp(pt, e.zOld, e.getZ()));
            float[] s = Projection.toScreen(head);
            if (s == null) continue;

            float dist = mc.player.distanceTo(e);
            float k = Mth.clamp(1.1f - dist * 0.014f, 0.55f, 1f) * cfg.scale.f();
            float alpha = Mth.clamp((range - dist) / (range * 0.25f), 0.2f, 1f);

            float maxHp = Math.max(le.getMaxHealth(), 1f);
            float frac = Mth.clamp(le.getHealth() / maxHp, 0f, 1f);
            float abs = Mth.clamp(le.getAbsorptionAmount() / maxHp, 0f, 1f);

            State st = states.computeIfAbsent(e.getId(), i -> new State());
            st.seen = now;
            if (st.shown < 0) { st.shown = frac; st.trail = frac; }
            st.shown += (frac - st.shown) * Math.min(1f, delta * 14f);
            if (st.trail < st.shown) st.trail = st.shown;
            else st.trail = Math.max(st.shown, st.trail - delta * 0.35f);

            int c = switch (cfg.color.get()) {
                case "Accent" -> LockClient.modules.general.accent.get();
                case "Team" -> Esp.color(e, LockClient.modules.boxes.color);
                default -> Colors.health(frac);
            };

            draw(g, cfg, le, s[0], s[1], k, alpha, st, c, abs);
        }

        for (Iterator<State> it = states.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seen > 2_000_000_000L) it.remove();
        }
    }

    private static void draw(GuiGraphics g, Health cfg, LivingEntity le, float sx, float sy, float k, float alpha,
                             State st, int rgb, float abs) {
        final int W = 30, H = 4;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(sx, sy, 0);
        pose.scale(k, k, 1f);
        int x0 = -W / 2, y0 = -H;

        // halo
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, Colors.argb(rgb, 0.10f * alpha));
        // pill background with notched corners
        int bg = Colors.argb(0x0B0B10, 0.62f * alpha);
        g.fill(x0 + 1, y0, x0 + W - 1, y0 + H, bg);
        g.fill(x0, y0 + 1, x0 + W, y0 + H - 1, bg);

        int innerW = W - 2;
        int fill = Math.round(innerW * st.shown);
        int trail = Math.round(innerW * st.trail);
        if (cfg.trail.get() && trail > fill) {
            g.fill(x0 + 1 + fill, y0 + 1, x0 + 1 + trail, y0 + H - 1, Colors.argb(0xFFFFFF, 0.35f * alpha));
        }
        if (fill > 0) g.fill(x0 + 1, y0 + 1, x0 + 1 + fill, y0 + H - 1, Colors.argb(rgb, 0.95f * alpha));
        if (abs > 0f) {
            int a = Math.round(innerW * abs);
            g.fill(x0 + 1, y0 + H - 2, x0 + 1 + a, y0 + H - 1, Colors.argb(0xFFE08A, 0.95f * alpha));
        }

        var font = Minecraft.getInstance().font;
        if (cfg.name.get()) {
            String n = le.getName().getString();
            pose.pushPose();
            pose.translate(0, y0 - 6, 0);
            pose.scale(0.55f, 0.55f, 1f);
            g.drawString(font, n, -font.width(n) / 2, 0, Colors.argb(0xFFFFFF, 0.8f * alpha), false);
            pose.popPose();
        }
        if (cfg.number.get()) {
            String n = String.valueOf(Math.round(le.getHealth() + le.getAbsorptionAmount()));
            pose.pushPose();
            pose.translate(0, y0 + H + 2, 0);
            pose.scale(0.5f, 0.5f, 1f);
            g.drawString(font, n, -font.width(n) / 2, 0, Colors.argb(rgb, 0.85f * alpha), false);
            pose.popPose();
        }
        pose.popPose();
    }
}
