package lock.gui;

import lock.Core;
import lock.module.Arrows;
import lock.module.Esp;
import lock.module.NameTags;
import lock.render.Colors;
import lock.render.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** NameTags (name, slim health bar, armor pips, held item) and offscreen Arrows. */
final class OverlayHud {
    private OverlayHud() {}

    private static final class TagState { float shown = -1, trail = 1; long seen; }
    private static final Map<Integer, TagState> STATES = new HashMap<>();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    static void render(Gfx g, GuiGraphics raw, Minecraft mc, int sw, int sh, int accent, float dt) {
        NameTags nt = Core.modules.nameTags;
        Arrows ar = Core.modules.arrows;
        if (!nt.enabled() && !ar.enabled()) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float range = (float) (double) Core.modules.general.range.get();
        long now = System.nanoTime();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !Esp.targets(e)) continue;
            Vec3 head = new Vec3(Mth.lerp(pt, e.xOld, e.getX()), Mth.lerp(pt, e.yOld, e.getY()) + e.getBbHeight() + 0.3, Mth.lerp(pt, e.zOld, e.getZ()));
            float dist = mc.player.distanceTo(e);
            float alpha = Mth.clamp((range - dist) / (range * 0.25f), 0.2f, 1f);
            float[] s = Projection.toScreen(head);
            if (s != null && nt.enabled()) {
                TagState st = STATES.computeIfAbsent(e.getId(), i -> new TagState());
                st.seen = now;
                tag(g, le, s[0], s[1], dist, alpha, st, nt, accent, dt);
            } else if (s == null && ar.enabled() && e instanceof Player) {
                arrow(g, e, head, dist, alpha, ar, sw, sh, accent);
            }
        }
        for (Iterator<TagState> it = STATES.values().iterator(); it.hasNext(); ) if (now - it.next().seen > 2_000_000_000L) it.remove();
    }

    private static void tag(Gfx g, LivingEntity e, float sx, float sy, float dist, float alpha, TagState st, NameTags cfg, int accent, float dt) {
        float k = Mth.clamp(1.12f - dist * 0.012f, 0.55f, 1f) * cfg.scale.f();
        float maxHp = Math.max(e.getMaxHealth(), 1f);
        float frac = Mth.clamp(e.getHealth() / maxHp, 0f, 1f);
        float abs = Mth.clamp(e.getAbsorptionAmount() / maxHp, 0f, 1f);
        if (st.shown < 0) { st.shown = frac; st.trail = frac; }
        st.shown += (frac - st.shown) * Math.min(1f, dt * 14f);
        st.trail = st.trail < st.shown ? st.shown : Math.max(st.shown, st.trail - dt * 0.35f);

        int bar = switch (cfg.color.get()) {
            case "Accent" -> accent;
            case "Team" -> 0xFF000000 | Esp.color(e, Core.modules.boxes.color);
            default -> 0xFF000000 | Colors.health(frac);
        };

        String name = e.getName().getString();
        String hp = cfg.number.get() ? Integer.toString(Math.round(e.getHealth() + e.getAbsorptionAmount())) : null;
        float nameW = g.width(name, 0.8f, true);
        float w = Math.max(nameW + (hp != null ? g.width(hp, 0.7f) + 6 : 0) + 14, 44);
        boolean showBar = cfg.health.get();
        ItemStack held = e.getMainHandItem();
        boolean showHeld = cfg.held.get() && !held.isEmpty();
        boolean showArmor = cfg.armor.get();
        float h = 13 + (showBar ? 6 : 0) + (showArmor ? 4 : 0) + (showHeld ? 8 : 0);

        g.alpha(alpha);
        g.push(sx, sy, k);
        float x = -w / 2f, y = -h;
        g.round(x - 1, y - 0.5f, w + 2, h + 2, 4.5f, 0x28000000);
        g.round(x, y, w, h, 4, Theme.alpha(Theme.PANEL, 0.92f));

        g.text(name, x + 7, y + 4, Theme.TITLE, 0.8f, true);
        if (hp != null) g.text(hp, x + w - 7 - g.width(hp, 0.7f), y + 4.6f, bar, 0.7f, true);
        float cy = y + 13;
        if (showBar) {
            float bw = w - 14;
            g.round(x + 7, cy, bw, 2.4f, 1.2f, Theme.TRACK);
            if (cfg.trail.get() && st.trail > st.shown) g.round(x + 7, cy, Math.max(1.5f, bw * st.trail), 2.4f, 1.2f, 0x55FFFFFF);
            if (st.shown > 0.005f) g.round(x + 7, cy, Math.max(1.5f, bw * st.shown), 2.4f, 1.2f, bar);
            if (abs > 0) g.round(x + 7, cy + 2.7f, Math.max(1.5f, bw * abs), 0.9f, 0.45f, 0xFFFFD479);
            cy += 6;
        }
        if (showArmor) {
            float pw = 5.5f, gap = 1.8f, px0 = -(4 * pw + 3 * gap) / 2f;
            for (int i = 0; i < 4; i++) {
                ItemStack st2 = e.getItemBySlot(ARMOR[i]);
                int c;
                if (st2.isEmpty()) c = Theme.alpha(Theme.TRACK, 1f);
                else if (st2.isDamageableItem()) c = 0xFF000000 | Colors.health(1f - st2.getDamageValue() / (float) Math.max(1, st2.getMaxDamage()));
                else c = 0xFFB9C6D6;
                g.round(px0 + i * (pw + gap), cy - 0.2f, pw, 1.6f, 0.8f, c);
            }
            cy += 4;
        }
        if (showHeld) {
            String hn = held.getHoverName().getString();
            if (hn.length() > 18) hn = hn.substring(0, 17) + "...";
            g.text(hn, -g.width(hn, 0.58f) / 2f, cy + 0.4f, Theme.DESC, 0.58f);
        }
        g.pop();
        g.alpha(1f);
    }

    private static void arrow(Gfx g, Entity e, Vec3 pos, float dist, float alpha, Arrows cfg, int sw, int sh, int accent) {
        float[] d = Projection.edgeDir(pos);
        if (d == null) return;
        float len = (float) Math.hypot(d[0], d[1]);
        if (len < 1e-4f) { d[0] = 0; d[1] = -1; len = 1; }
        float dx = d[0] / len, dy = -d[1] / len;                  // screen y points down
        float rad = cfg.radius.f() * Math.min(sw, sh) / 2f;
        float cx = sw / 2f + dx * rad * (sw / (float) Math.min(sw, sh)) * 0.6f, cy = sh / 2f + dy * rad;
        float s = cfg.size.f() * 1.0f;
        float tipx = cx + dx * 8 * s, tipy = cy + dy * 8 * s;
        float bx = cx - dx * 3 * s, by = cy - dy * 3 * s;
        float nx = -dy, ny = dx;
        int col = 0xFF000000 | Esp.color(e, cfg.color);
        g.alpha(alpha);
        g.tri(tipx + 0.8f * dx, tipy + 0.8f * dy, bx + nx * 6.2f * s, by + ny * 6.2f * s, bx - nx * 6.2f * s, by - ny * 6.2f * s, Theme.alpha(0xFF000000, 0.45f));
        g.tri(tipx, tipy, bx + nx * 5 * s, by + ny * 5 * s, bx - nx * 5 * s, by - ny * 5 * s, col);
        if (cfg.distance.get()) {
            String t = Math.round(dist) + "m";
            g.text(t, cx - g.width(t, 0.62f) / 2f - dx * 1f, cy + 8 * s + (dy > 0 ? -22 : 0), Theme.TEXT, 0.62f, true);
        }
        g.alpha(1f);
    }
}
