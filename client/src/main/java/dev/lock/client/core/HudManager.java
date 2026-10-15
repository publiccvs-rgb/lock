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
    private double avgFrameNanos;

    public HudManager(LockClient lock) { this.lock = lock; }

    public float globalScale() { return (float) lock.config.state("hud_settings").num("scale"); }

    public double averageCostMicros() { return avgFrameNanos / 1000.0; }

    /** Draws every visible HUD element. In editor mode all enabled elements are drawn regardless of visibility rules. */
    public void render(Gfx gfx, boolean editor) {
        long t0 = System.nanoTime();
        int sw = gfx.g.guiWidth(), sh = gfx.g.guiHeight();
        for (HudModule m : lock.modules.huds()) {
            if (!m.active() || lock.modules.faulted(m.def.id)) continue;
            if (!editor) {
                if (!lock.visibility.visible(m.def.visibilityGroup) || !m.state().bool(ModuleDef.H_LOCAL)) continue;
                if (m.empty()) continue;
            }
            long e0 = System.nanoTime();
            m.editing = editor;
            lock.modules.guard(m, "render", () -> drawElement(gfx, m, sw, sh));
            m.editing = false;
            m.lastRenderNanos = System.nanoTime() - e0;
        }
        lastFrameNanos = System.nanoTime() - t0;
        avgFrameNanos = avgFrameNanos == 0 ? lastFrameNanos : avgFrameNanos * 0.95 + lastFrameNanos * 0.05;
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

    private void drawElement(Gfx gfx, HudModule m, int sw, int sh) {
        int[] box = layout(gfx, m, sw, sh);
        ModuleState st = m.state();
        float s = scale(m);
        gfx.push();
        gfx.translate(box[0], box[1]);
        gfx.scale(s);
        gfx.alpha = (float) st.num(ModuleDef.H_OPACITY);
        if (st.bool(ModuleDef.H_BG)) gfx.round(0, 0, box[4], box[5], st.color(ModuleDef.H_BG_COLOR));
        if (st.bool(ModuleDef.H_BORDER)) gfx.outline(0, 0, box[4], box[5], st.color(ModuleDef.H_BORDER_COLOR));
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
