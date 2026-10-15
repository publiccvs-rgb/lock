package lock.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;

/**
 * Gfx backed by the game's GuiGraphics. Corner insets are precomputed per radius and string widths cached,
 * so a full frame is a few hundred cheap fills with no allocation.
 */
public final class McGfx implements Gfx {
    private static final int[][] INSETS = new int[9][];
    private static final HashMap<String, Float> WIDTHS = new HashMap<>();

    private final GuiGraphics g;
    private final Font font = UiFont.get();
    private float alpha = 1f;

    public McGfx(GuiGraphics g) { this.g = g; }

    private static int[] insets(int r) {
        int[] t = INSETS[r];
        if (t == null) {
            t = new int[r];
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                t[i] = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            }
            INSETS[r] = t;
        }
        return t;
    }

    private int a(int argb) {
        if (alpha >= 0.999f) return argb;
        return (((int) ((argb >>> 24) * alpha)) << 24) | (argb & 0xFFFFFF);
    }

    @Override public void alpha(float v) { alpha = v; }

    @Override public void rect(float x, float y, float w, float h, int argb) {
        int x0 = Math.round(x), y0 = Math.round(y);
        int x1 = Math.round(x + w), y1 = Math.round(y + h);
        if (x1 > x0 && y1 > y0 && (argb >>> 24) != 0) g.fill(x0, y0, x1, y1, a(argb));
    }

    @Override public void round(float x, float y, float w, float h, float r, int argb) {
        roundEx(x, y, w, h, r, r, r, r, argb);
    }

    private static int radius(float r, int W, int H) {
        return Math.max(0, Math.min(Math.min((int) Math.floor(r + 0.35f), 8), Math.min(W, H) / 2));
    }

    @Override public void roundEx(float x, float y, float w, float h, float tl, float tr, float br, float bl, int argb) {
        if ((argb >>> 24) == 0) return;
        argb = a(argb);
        int x0 = Math.round(x), y0 = Math.round(y);
        int W = Math.round(x + w) - x0, H = Math.round(y + h) - y0;
        if (W <= 0 || H <= 0) return;
        int rtl = radius(tl, W, H), rtr = radius(tr, W, H), rbr = radius(br, W, H), rbl = radius(bl, W, H);
        int top = Math.max(rtl, rtr), bot = Math.max(rbl, rbr);
        if (top + bot > H) { top = Math.min(top, H / 2); bot = Math.min(bot, H - top); }
        if (H - top - bot > 0) g.fill(x0, y0 + top, x0 + W, y0 + H - bot, argb);
        for (int i = 0; i < top; i++) {
            int l = i < rtl ? insets(rtl)[i] : 0, r = i < rtr ? insets(rtr)[i] : 0;
            g.fill(x0 + l, y0 + i, x0 + W - r, y0 + i + 1, argb);
        }
        for (int i = 0; i < bot; i++) {
            int l = i < rbl ? insets(rbl)[i] : 0, r = i < rbr ? insets(rbr)[i] : 0;
            g.fill(x0 + l, y0 + H - 1 - i, x0 + W - r, y0 + H - i, argb);
        }
    }

    @Override public void text(String s, float x, float y, int argb, float scale) {
        if ((argb >>> 24) == 0) return;
        if (scale == 1f) {
            g.drawString(font, s, Math.round(x), Math.round(y), a(argb), false);
            return;
        }
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.drawString(font, s, 0, 0, a(argb), false);
        pose.popMatrix();
    }

    @Override public float width(String s, float scale) {
        Float w = WIDTHS.get(s);
        if (w == null) {
            if (WIDTHS.size() > 1024) WIDTHS.clear();
            w = font.getSplitter().stringWidth(s);
            WIDTHS.put(s, w);
        }
        return w * scale;
    }

    @Override public void clip(float x, float y, float w, float h) {
        g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
    }

    @Override public void unclip() { g.disableScissor(); }
}
