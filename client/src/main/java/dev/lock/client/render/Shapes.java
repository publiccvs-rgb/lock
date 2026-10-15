package dev.lock.client.render;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Anti-aliased primitives: circles (used for rounded corners and knobs) and soft shadows. Circle
 * textures exist at several resolutions so corners stay smooth at any GUI scale.
 */
public final class Shapes {
    private static final int[] CIRCLE_SIZES = {16, 32, 64, 128};
    private static Identifier[] circles;
    private static Identifier shadow;
    static final int SHADOW_TEX = 64, SHADOW_EDGE = 24;

    private Shapes() {}

    static void init() {
        if (circles != null) return;
        circles = new Identifier[CIRCLE_SIZES.length];
        for (int i = 0; i < CIRCLE_SIZES.length; i++) {
            int s = CIRCLE_SIZES[i];
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(0, 0, s, s));
            g.dispose();
            circles[i] = Tex.upload("shape/circle" + s, img, true);
        }
        // soft shadow: blurred rounded square
        BufferedImage img = new BufferedImage(SHADOW_TEX, SHADOW_TEX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(SHADOW_EDGE / 2f, SHADOW_EDGE / 2f, SHADOW_TEX - SHADOW_EDGE, SHADOW_TEX - SHADOW_EDGE, 8, 8));
        g.dispose();
        shadow = Tex.upload("shape/shadow", blur(img, 6, 3), true);
    }

    private static BufferedImage blur(BufferedImage src, int radius, int passes) {
        int w = src.getWidth(), h = src.getHeight();
        float[] a = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) a[y * w + x] = (src.getRGB(x, y) >>> 24) / 255f;
        float[] tmp = new float[w * h];
        for (int p = 0; p < passes; p++) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                float s = 0; int n = 0;
                for (int k = -radius; k <= radius; k++) { int xx = x + k; if (xx >= 0 && xx < w) { s += a[y * w + xx]; n++; } }
                tmp[y * w + x] = s / n;
            }
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                float s = 0; int n = 0;
                for (int k = -radius; k <= radius; k++) { int yy = y + k; if (yy >= 0 && yy < h) { s += tmp[yy * w + x]; n++; } }
                a[y * w + x] = s / n;
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < a.length; i++) out.setRGB(i % w, i / w, (Math.min(255, Math.round(a[i] * 255)) << 24) | 0xFFFFFF);
        return out;
    }

    private static int circleIndex(float realDiameter) {
        for (int i = 0; i < CIRCLE_SIZES.length; i++) if (CIRCLE_SIZES[i] >= realDiameter * 1.5f) return i;
        return CIRCLE_SIZES.length - 1;
    }

    /** Filled rounded rectangle; r is clamped to half the smaller side. Coordinates in current GUI units. */
    static void rrect(Gfx g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.g.fill(x, y, x + w, y + h, color); return; }
        init();
        int idx = circleIndex(r * 2 * g.realScale());
        Identifier tex = circles[idx];
        int s = CIRCLE_SIZES[idx], half = s / 2;
        // corners: quarter circles scaled to r x r
        g.g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, r, r, half, half, s, s, color);
        g.g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, half, 0, r, r, half, half, s, s, color);
        g.g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + h - r, 0, half, r, r, half, half, s, s, color);
        g.g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + h - r, half, half, r, r, half, half, s, s, color);
        // body
        g.g.fill(x + r, y, x + w - r, y + h, color);
        g.g.fill(x, y + r, x + r, y + h - r, color);
        g.g.fill(x + w - r, y + r, x + w, y + h - r, color);
    }

    static void circle(Gfx g, float cx, float cy, float radius, int color) {
        init();
        int idx = circleIndex(radius * 2 * g.realScale());
        int s = CIRCLE_SIZES[idx];
        g.push();
        g.translate(cx - radius, cy - radius);
        float k = radius * 2 / s;
        g.scale(k);
        g.g.blit(RenderPipelines.GUI_TEXTURED, circles[idx], 0, 0, 0, 0, s, s, s, s, s, s, color);
        g.pop();
    }

    /** Soft drop shadow around a box. */
    static void shadow(Gfx g, int x, int y, int w, int h, int spread, int color) {
        init();
        int e = SHADOW_EDGE, t = SHADOW_TEX, m = t - 2 * e;
        int x0 = x - spread, y0 = y - spread, x1 = x + w + spread, y1 = y + h + spread;
        int cw = spread * 2, ch = spread * 2;
        if (x1 - x0 < cw * 2 || y1 - y0 < ch * 2) return;
        Identifier id = shadow;
        var gg = g.g;
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x0, y0, 0, 0, cw, ch, e, e, t, t, color);
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x1 - cw, y0, t - e, 0, cw, ch, e, e, t, t, color);
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x0, y1 - ch, 0, t - e, cw, ch, e, e, t, t, color);
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x1 - cw, y1 - ch, t - e, t - e, cw, ch, e, e, t, t, color);
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x0 + cw, y0, e, 0, x1 - x0 - 2 * cw, ch, m, e, t, t, color);
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x0 + cw, y1 - ch, e, t - e, x1 - x0 - 2 * cw, ch, m, e, t, t, color);
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x0, y0 + ch, 0, e, cw, y1 - y0 - 2 * ch, e, m, t, t, color);
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x1 - cw, y0 + ch, t - e, e, cw, y1 - y0 - 2 * ch, e, m, t, t, color);
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x0 + cw, y0 + ch, e, e, x1 - x0 - 2 * cw, y1 - y0 - 2 * ch, m, m, t, t, color);
    }
}
