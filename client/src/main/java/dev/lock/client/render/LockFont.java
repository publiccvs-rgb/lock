package dev.lock.client.render;

import dev.lock.core.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Smooth TrueType text (Poppins, OFL) from glyph atlases rasterised at the real on-screen pixel size
 * (bucketed), so text is crisp at every scale. Atlases are pre-rasterised off-thread by
 * {@link #warmUp} and uploaded on the render thread; lookups use flat arrays to keep per-frame cost low.
 * Layout uses one reference set of advances so measured and drawn widths always agree.
 */
public final class LockFont {
    public enum Weight { REGULAR("Regular"), MEDIUM("Medium"), SEMIBOLD("SemiBold");
        final String file;
        Weight(String f) { file = f; }
    }

    private static final int[] BUCKETS = {8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40, 48, 56, 64, 80};
    private static final int REF = 32, PAD = 2, MAX_CHAR = 0x2800;
    private static final String CHARS;

    static {
        StringBuilder sb = new StringBuilder();
        for (char c = 32; c < 127; c++) sb.append(c);
        sb.append("°µ·…←↑→↓↖↗↘↙★☆✓✕•×±❤∞éèàäöüßñç–—");
        CHARS = sb.toString();
    }

    /** One rasterised atlas. Glyph data is in flat arrays indexed by char for allocation-free lookup. */
    private static final class Atlas {
        volatile Identifier tex;
        BufferedImage image;
        int texW, texH, lineH;
        final short[] gx = new short[MAX_CHAR], gy = new short[MAX_CHAR], gw = new short[MAX_CHAR];
    }

    private static final Font[] BASE = new Font[Weight.values().length];
    private static final Map<Integer, Atlas> ATLASES = new ConcurrentHashMap<>();
    /** Advances at REF px, per weight, indexed by char (NaN = not displayable). */
    private static final float[][] ADV = new float[Weight.values().length][];
    private static float ascentRatio = 1.05f, capRatio = 0.7f;
    private static volatile boolean failed;
    /** Use Minecraft's own (batched) font instead of glyph-by-glyph TTF rendering. */
    public static volatile boolean fast;
    /** Minecraft font units per em: its caps are 7 units tall, Poppins caps are ~0.7 em. */
    private static final float MC_UNITS = 10f;

    private LockFont() {}

    private static synchronized Font base(Weight w) {
        Font f = BASE[w.ordinal()];
        if (f != null) return f;
        try (InputStream in = LockFont.class.getClassLoader().getResourceAsStream("assets/lock/fonts/Poppins-" + w.file + ".ttf")) {
            f = in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            Log.warn("Lock font unavailable, using system sans-serif: " + e);
        }
        if (f == null) f = new Font(Font.SANS_SERIF, w == Weight.REGULAR ? Font.PLAIN : Font.BOLD, 12);
        BASE[w.ordinal()] = f;
        return f;
    }

    private static FontRenderContext frc() { return new FontRenderContext(null, true, true); }

    private static float[] advances(Weight w) {
        float[] a = ADV[w.ordinal()];
        if (a != null) return a;
        synchronized (ADV) {
            if (ADV[w.ordinal()] != null) return ADV[w.ordinal()];
            a = new float[MAX_CHAR];
            java.util.Arrays.fill(a, Float.NaN);
            Font f = base(w).deriveFont((float) REF);
            FontRenderContext frc = frc();
            for (char c : CHARS.toCharArray()) if (f.canDisplay(c)) a[c] = (float) f.getStringBounds(String.valueOf(c), frc).getWidth();
            if (w == Weight.REGULAR) {
                var lm = f.getLineMetrics("H", frc);
                ascentRatio = lm.getAscent() / REF;
                capRatio = (float) f.createGlyphVector(frc, "H").getVisualBounds().getHeight() / REF;
            }
            ADV[w.ordinal()] = a;
            return a;
        }
    }

    /** Rasterises an atlas (any thread). */
    private static Atlas rasterise(Weight w, int px) {
        Atlas a = new Atlas();
        Font f = base(w).deriveFont((float) px);
        FontRenderContext frc = frc();
        var lm = f.getLineMetrics("Hg", frc);
        int lineH = (int) Math.ceil(lm.getAscent() + lm.getDescent()) + PAD * 2;
        int texW = 1024, x = 0, y = 0;
        for (char c : CHARS.toCharArray()) {
            if (!f.canDisplay(c)) continue;
            int cw = (int) Math.ceil(f.getStringBounds(String.valueOf(c), frc).getWidth()) + PAD * 2 + 2;
            if (x + cw > texW) { x = 0; y += lineH; }
            a.gx[c] = (short) x;
            a.gy[c] = (short) y;
            a.gw[c] = (short) cw;
            x += cw;
        }
        int texH = Integer.highestOneBit(Math.max(1, y + lineH - 1)) << 1;
        BufferedImage img = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(f);
        g.setColor(Color.WHITE);
        for (char c : CHARS.toCharArray())
            if (a.gw[c] > 0) g.drawString(String.valueOf(c), a.gx[c] + PAD, a.gy[c] + PAD + lm.getAscent());
        g.dispose();
        a.texW = texW;
        a.texH = texH;
        a.lineH = lineH;
        a.image = img;
        return a;
    }

    private static int key(Weight w, int px) { return w.ordinal() * 1000 + px; }

    /** Render thread: returns an uploaded atlas, rasterising synchronously only if warm-up has not produced it. */
    private static Atlas atlas(Weight w, int px) {
        Atlas a = ATLASES.computeIfAbsent(key(w, px), k -> rasterise(w, px));
        if (a.tex == null) {
            a.tex = Tex.upload("font/" + w.name().toLowerCase() + "_" + px, a.image, true);
            a.image = null;
        }
        return a;
    }

    /** Pre-rasterises atlases for the given pixel sizes on the calling (background) thread and queues their upload. */
    public static void warmUp(float... realPx) {
        for (Weight w : Weight.values()) advances(w);
        for (float r : realPx) {
            int px = bucket(r);
            for (Weight w : Weight.values()) {
                Atlas a = ATLASES.computeIfAbsent(key(w, px), k -> rasterise(w, px));
                Tex.onRenderThread(() -> { if (a.tex == null && a.image != null) { a.tex = Tex.upload("font/" + w.name().toLowerCase() + "_" + px, a.image, true); a.image = null; } });
            }
        }
    }

    private static int bucket(float realPx) {
        for (int b : BUCKETS) if (b >= realPx) return b;
        return BUCKETS[BUCKETS.length - 1];
    }

    /** Width in GUI units of {@code s} at {@code size} (em size in GUI units). */
    public static float width(String s, float size, Weight w) {
        if (s == null || s.isEmpty()) return 0;
        if (fast || failed) return Minecraft.getInstance().font.width(s) * size / MC_UNITS;
        float[] adv = advances(w);
        float q = adv['?'], total = 0;
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            float a = c < MAX_CHAR ? adv[c] : Float.NaN;
            total += a == a ? a : q; // NaN check without boxing
        }
        return total * size / REF;
    }

    /** Distance from the top of the line box to the top of capital letters. */
    public static float capTop(float size) {
        if (fast || failed) return 0;
        advances(Weight.REGULAR);
        return (ascentRatio - capRatio) * size + PAD * size / REF;
    }

    public static float capHeight(float size) {
        if (fast || failed) return 7 * size / MC_UNITS;
        advances(Weight.REGULAR);
        return capRatio * size;
    }

    /** Draws text with its line box top at (x, y). Returns the x after the text. */
    public static float draw(Gfx gfx, String s, float x, float y, float size, int color, Weight w) {
        if (s == null || s.isEmpty() || ((color >>> 24) == 0)) return x;
        if (fast || failed) {
            float k = size / MC_UNITS;
            gfx.push();
            gfx.translate(x, y);
            gfx.scale(k);
            gfx.g.drawString(Minecraft.getInstance().font, s, 0, 0, color, false);
            gfx.pop();
            return x + Minecraft.getInstance().font.width(s) * k;
        }
        try {
            float[] adv = advances(w);
            int px = bucket(size * gfx.realScale());
            Atlas a = atlas(w, px);
            float k = size / px; // GUI units per atlas pixel
            float advScale = (float) px / REF, q = adv['?'];
            gfx.push();
            gfx.translate(x, y);
            gfx.scale(k);
            float pen = 0;
            for (int i = 0, n = s.length(); i < n; i++) {
                char c = s.charAt(i);
                if (c >= MAX_CHAR || a.gw[c] == 0) c = '?';
                float ad = adv[c];
                if (c != ' ')
                    gfx.g.blit(RenderPipelines.GUI_TEXTURED, a.tex, Math.round(pen) - PAD, 0, a.gx[c], a.gy[c], a.gw[c], a.lineH, a.gw[c], a.lineH, a.texW, a.texH, color);
                pen += (ad == ad ? ad : q) * advScale;
            }
            gfx.pop();
            return x + pen * k; // same advances as width(), without a second pass
        } catch (Throwable t) {
            failed = true;
            Log.error("Lock font rendering failed; falling back to the Minecraft font", t);
            return x;
        }
    }
}
