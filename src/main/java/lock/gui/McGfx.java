package lock.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;

/**
 * Gfx backed by the game's GuiGraphics. Corner insets are precomputed per radius and string widths cached,
 * so a full frame is a few hundred cheap fills with no allocation.
 */
public final class McGfx implements Gfx {
    /** Exact (sub-pixel) left inset of a circular corner's boundary at each row, indexed [radius][row]. */
    private static final float[][] EXACT = new float[9][];
    private static final HashMap<String, Float> WIDTHS = new HashMap<>();

    private final GuiGraphics g;
    private final Font font = UiFont.get(), bold = UiFont.bold();
    private float alpha = 1f;

    public McGfx(GuiGraphics g) { this.g = g; }

    private static float[] exact(int r) {
        float[] t = EXACT[r];
        if (t == null) {
            t = new float[r];
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                t[i] = r - (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            }
            EXACT[r] = t;
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

    /**
     * Fills one screen row between exact (sub-pixel) x bounds with true edge-coverage anti-aliasing: the
     * interior is one solid fill, and each boundary gets a single extra column blended at partial alpha
     * equal to how much of that pixel the shape actually covers. This works because every fill in this
     * renderer is already alpha-composited onto whatever was drawn before - no shader needed, just real
     * fractional coverage instead of a hard round() step.
     */
    private void fillAA(float xLo, float xHi, int y, int argb) {
        if (xHi <= xLo || (argb >>> 24) == 0) return;
        int loFloor = (int) Math.floor(xLo), hiFloor = (int) Math.floor(xHi);
        int solidL = (int) Math.ceil(xLo), solidR = hiFloor;
        if (solidL < solidR) g.fill(solidL, y, solidR, y + 1, argb);
        float leftCov = solidL - xLo;           // fraction of the boundary pixel actually covered
        if (leftCov > 0.004f && loFloor < solidL) g.fill(loFloor, y, loFloor + 1, y + 1, withCoverage(argb, leftCov));
        float rightCov = xHi - hiFloor;
        if (rightCov > 0.004f && hiFloor >= solidR) g.fill(hiFloor, y, hiFloor + 1, y + 1, withCoverage(argb, rightCov));
    }

    private static int withCoverage(int argb, float cov) {
        int a = Math.round(((argb >>> 24) & 255) * Math.min(1f, Math.max(0f, cov)));
        return (a << 24) | (argb & 0xFFFFFF);
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
            float l = i < rtl ? exact(rtl)[i] : 0, r = i < rtr ? exact(rtr)[i] : 0;
            fillAA(x0 + l, x0 + W - r, y0 + i, argb);
        }
        for (int i = 0; i < bot; i++) {
            float l = i < rbl ? exact(rbl)[i] : 0, r = i < rbr ? exact(rbr)[i] : 0;
            fillAA(x0 + l, x0 + W - r, y0 + H - 1 - i, argb);
        }
    }

    @Override public void gradient(float x, float y, float w, float h, float tl, float tr, float br, float bl, int top, int bottom) {
        int x0 = Math.round(x), y0 = Math.round(y);
        int W = Math.round(x + w) - x0, H = Math.round(y + h) - y0;
        if (W <= 0 || H <= 0) return;
        int rtl = radius(tl, W, H), rtr = radius(tr, W, H), rbr = radius(br, W, H), rbl = radius(bl, W, H);
        int tp = Math.max(rtl, rtr), bt = Math.max(rbl, rbr);
        if (tp + bt > H) { tp = Math.min(tp, H / 2); bt = Math.min(bt, H - tp); }
        int t = a(top), b = a(bottom);
        if (H - tp - bt > 0) {
            int ct = lerp(t, b, tp / (float) H), cb = lerp(t, b, (H - bt) / (float) H);
            g.fillGradient(x0, y0 + tp, x0 + W, y0 + H - bt, ct, cb);
        }
        for (int i = 0; i < tp; i++) {
            float l = i < rtl ? exact(rtl)[i] : 0, r = i < rtr ? exact(rtr)[i] : 0;
            fillAA(x0 + l, x0 + W - r, y0 + i, lerp(t, b, (i + 0.5f) / H));
        }
        for (int i = 0; i < bt; i++) {
            float l = i < rbl ? exact(rbl)[i] : 0, r = i < rbr ? exact(rbr)[i] : 0;
            fillAA(x0 + l, x0 + W - r, y0 + H - 1 - i, lerp(t, b, (H - i - 0.5f) / H));
        }
    }

    private static int lerp(int a, int b, float t) {
        if (a == b) return a;
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    @Override public void text(String s, float x, float y, int argb, float scale, boolean isBold) {
        if ((argb >>> 24) == 0) return;
        Font f = isBold ? bold : font;
        if (scale == 1f) {
            g.drawString(f, s, Math.round(x), Math.round(y), a(argb), false);
            return;
        }
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.drawString(f, s, 0, 0, a(argb), false);
        pose.popMatrix();
    }

    @Override public float width(String s, float scale, boolean isBold) {
        String key = isBold ? "\u0001" + s : s;
        Float w = WIDTHS.get(key);
        if (w == null) {
            if (WIDTHS.size() > 1024) WIDTHS.clear();
            w = (isBold ? bold : font).getSplitter().stringWidth(s);
            WIDTHS.put(key, w);
        }
        return w * scale;
    }

    @Override public void push(float x, float y, float scale) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
    }

    @Override public void pop() { g.pose().popMatrix(); }

    /** Scanline triangle with edge-coverage AA on the left/right boundary of every row (see {@link #fillAA}). */
    @Override public void tri(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        argb = a(argb);
        float minY = Math.min(y1, Math.min(y2, y3)), maxY = Math.max(y1, Math.max(y2, y3));
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            float yc = y + 0.5f, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            float[][] e = {{x1, y1, x2, y2}, {x2, y2, x3, y3}, {x3, y3, x1, y1}};
            for (float[] k : e) {
                if ((yc < Math.min(k[1], k[3])) || (yc > Math.max(k[1], k[3])) || k[1] == k[3]) continue;
                float xx = k[0] + (yc - k[1]) * (k[2] - k[0]) / (k[3] - k[1]);
                lo = Math.min(lo, xx); hi = Math.max(hi, xx);
            }
            if (hi > lo) fillAA(lo, hi, y, argb);
        }
    }

    @Override public void clip(float x, float y, float w, float h) {
        g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
    }

    @Override public void unclip() { g.disableScissor(); }
}
