package dev.lock.client.core;

import dev.lock.client.LockClient;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Gfx;
import dev.lock.core.config.ModuleState;
import dev.lock.core.module.Anchor;
import dev.lock.core.module.ModuleDef;

/**
 * Lays out and draws HUD elements from their settings. No position is hard-coded: every element is
 * placed from its anchor + offset, scaled by its own and the global scale, and clamped on screen.
 */
public final class HudManager {
    private final LockClient lock;
    public long lastFrameNanos;
    /** Last 20 frames, for a rolling average that settles quickly even at low frame rates. */
    private final long[] recent = new long[20];
    private long recentIdx;
    private double avgFrameNanos;

    public HudManager(LockClient lock) { this.lock = lock; }

    public float globalScale() {
        var st = lock.config.state("hud_settings");
        float s = (float) st.num("scale");
        if (!st.bool("followGuiScale")) s *= 2f / Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        return s;
    }

    /** Elements enabled since the last frame that should be moved to a free spot once their size is known. */
    private final java.util.Set<String> pendingPlacement = new java.util.HashSet<>();

    public void requestPlacement(HudModule m) {
        if (lock.config.state("hud_settings").bool("autoPlace")) pendingPlacement.add(m.def.id);
    }

    private boolean overlaps(HudModule m, int x, int y, int w, int h) {
        for (HudModule o : lock.modules.huds()) {
            if (o == m || !o.active() || o.lastW == 0) continue;
            if (x < o.lastX + o.lastW + 2 && x + w + 2 > o.lastX && y < o.lastY + o.lastH + 2 && y + h + 2 > o.lastY) return true;
        }
        return false;
    }

    /** Finds the free position nearest to the element's current one, scanning down/up its column then across. */
    private void place(HudModule m, int sw, int sh) {
        if (!overlaps(m, m.lastX, m.lastY, m.lastW, m.lastH)) return;
        int bestX = -1, bestY = -1;
        double best = Double.MAX_VALUE;
        for (int x = 0; x + m.lastW <= sw; x += 4) {
            for (int y = 0; y + m.lastH <= sh; y += 2) {
                double d = Math.hypot(x - m.lastX, (y - m.lastY) * 0.7);
                if (d >= best) continue;
                if (!overlaps(m, x, y, m.lastW, m.lastH)) { best = d; bestX = x; bestY = y; }
            }
        }
        if (bestX >= 0) {
            moveTo(m, bestX, bestY, sw, sh);
            lock.config.changed();
        }
    }

    public double averageCostMicros() { return avgFrameNanos / 1000.0; }

    /** Draws every visible HUD element. In editor mode all enabled elements are drawn regardless of visibility rules. */
    private static long now() { return System.nanoTime(); }

    public void render(Gfx gfx, boolean editor) {
        long t0 = now();
        frame++;
        int sw = gfx.g.guiWidth(), sh = gfx.g.guiHeight();
        for (HudModule m : lock.modules.huds()) {
            if (!m.active() || lock.modules.faulted(m.def.id)) continue;
            if (!editor) {
                if (!lock.visibility.visible(m.def.visibilityGroup) || !m.state().bool(ModuleDef.H_LOCAL)) continue;
                if (m.empty()) continue;
            }
            boolean sample = (frame & 31) == 0; // per-element timing is sampled so instrumentation stays negligible
            long e0 = sample ? now() : 0;
            m.editing = editor;
            if (pendingPlacement.remove(m.def.id)) lock.modules.guard(m, "place", () -> { layout(gfx, m, sw, sh); place(m, sw, sh); });
            lock.modules.guard(m, "render", () -> drawElement(gfx, m, sw, sh));
            m.editing = false;
            if (sample) m.lastRenderNanos = now() - e0;
        }
        lastFrameNanos = now() - t0;
        recent[(int) (recentIdx++ % recent.length)] = lastFrameNanos;
        long sum = 0;
        int n = (int) Math.min(recentIdx, recent.length);
        for (int i = 0; i < n; i++) sum += recent[i];
        avgFrameNanos = sum / (double) n;
    }

    /** Computes the element's on-screen box {x, y, w, h} and caches it on the module. */
    public int[] layout(Gfx gfx, HudModule m, int sw, int sh) {
        ModuleState st = m.state();
        int[] size = m.size(gfx);
        int pad = m.padding();
        int w = size[0] + pad * 2, h = size[1] + pad * 2;
        float s = scale(m);
        int pw = Math.round(w * s), ph = Math.round(h * s);
        Anchor a = m.anchor();
        int x = a.resolveX(sw, pw, st.integer(ModuleDef.H_X));
        int y = a.resolveY(sh, ph, st.integer(ModuleDef.H_Y));
        x = Math.max(0, Math.min(sw - pw, x));
        y = Math.max(0, Math.min(sh - ph, y));
        m.lastX = x; m.lastY = y; m.lastW = pw; m.lastH = ph;
        return new int[]{x, y, pw, ph, w, h, size[0], size[1]};
    }

    public float scale(HudModule m) { return globalScale() * (float) m.state().num(ModuleDef.H_SCALE); }

    /** Frame counter and per-element "first drawn" times for fade-in when an element appears. */
    private long frame;
    private final java.util.Map<String, long[]> seen = new java.util.HashMap<>();

    private float fade(HudModule m) {
        long now = System.currentTimeMillis();
        long[] s = seen.computeIfAbsent(m.def.id, k -> new long[]{-2, now});
        if (s[0] < frame - 1) s[1] = now; // not drawn last frame: restart fade
        s[0] = frame;
        float t = Math.min(1f, (now - s[1]) / 180f);
        return 1f - (1f - t) * (1f - t);
    }

    private void drawElement(Gfx gfx, HudModule m, int sw, int sh) {
        int[] box = layout(gfx, m, sw, sh);
        ModuleState st = m.state();
        float s = scale(m);
        gfx.push();
        gfx.translate(box[0], box[1]);
        gfx.scale(s);
        gfx.alpha = (float) st.num(ModuleDef.H_OPACITY) * fade(m);
        int r = st.integer(ModuleDef.H_RADIUS);
        boolean bg = !m.ownBackground();
        if (bg && st.bool(ModuleDef.H_BORDER)) gfx.rrect(-1, -1, box[4] + 2, box[5] + 2, r + 1, st.color(ModuleDef.H_BORDER_COLOR));
        if (bg && st.bool(ModuleDef.H_BG)) gfx.rrect(0, 0, box[4], box[5], r, st.color(ModuleDef.H_BG_COLOR));
        int pad = m.padding();
        gfx.translate(pad, pad);
        m.draw(gfx, box[6], box[7]);
        gfx.alpha = 1f;
        gfx.pop();
    }

    /** Moves an element so its top-left lands at (x, y); re-anchors to the nearest screen region. */
    public void moveTo(HudModule m, int x, int y, int sw, int sh) {
        Anchor a = Anchor.nearest(sw, sh, x + m.lastW / 2, y + m.lastH / 2);
        ModuleState st = m.state();
        st.set(ModuleDef.H_ANCHOR, a.name());
        st.set(ModuleDef.H_X, a.offsetX(sw, m.lastW, x));
        st.set(ModuleDef.H_Y, a.offsetY(sh, m.lastH, y));
        m.lastX = x;
        m.lastY = y;
    }

    public void resetPosition(HudModule m) {
        ModuleState st = m.state();
        for (String k : new String[]{ModuleDef.H_ANCHOR, ModuleDef.H_X, ModuleDef.H_Y, ModuleDef.H_SCALE})
            st.set(k, m.def.setting(k).defaultValue);
    }
}
