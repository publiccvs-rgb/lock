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
import java.util.HashMap;
import java.util.Map;

/**
 * Smooth TrueType text (Poppins, OFL) rendered from glyph atlases. An atlas is rasterised per
 * weight at the real on-screen pixel size (bucketed), so text stays crisp at every GUI scale.
 * Layout uses one reference set of advances so measured and drawn widths always agree.
 */
public final class LockFont {
    public enum Weight { REGULAR("Regular"), MEDIUM("Medium"), SEMIBOLD("SemiBold");
        final String file;
        Weight(String f) { file = f; }
    }

    private static final int[] BUCKETS = {8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40, 48, 56, 64, 80};
    private static final int REF = 32, PAD = 2;
    private static final String CHARS;

    static {
        StringBuilder sb = new StringBuilder();
        for (char c = 32; c < 127; c++) sb.append(c);
        sb.append("°µ·…←↑→↓↖↗↘↙★☆✓✕•×±❤∞éèàäöüßñç–—");
        CHARS = sb.toString();
    }

    private record Glyph(int x, int y, int w, int h) {}

    private static final class Atlas {
        Identifier tex;
        int texW, texH, px;
        final Map<Character, Glyph> glyphs = new HashMap<>();
    }

    private static final Font[] BASE = new Font[Weight.values().length];
    private static final Map<Integer, Atlas> ATLASES = new HashMap<>();
    /** Advances at REF px, per weight. */
    private static final Map<Weight, Map<Character, Float>> ADV = new HashMap<>();
    private static float ascentRatio = 1.05f, capRatio = 0.7f;
    private static boolean failed;

    private LockFont() {}

    private static Font base(Weight w) {
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

    private static Map<Character, Float> advances(Weight w) {
        return ADV.computeIfAbsent(w, k -> {
            Map<Character, Float> m = new HashMap<>();
            Font f = base(k).deriveFont((float) REF);
            FontRenderContext frc = frc();
            for (char c : CHARS.toCharArray()) if (f.canDisplay(c)) m.put(c, (float) f.getStringBounds(String.valueOf(c), frc).getWidth());
            var lm = f.getLineMetrics("H", frc);
            ascentRatio = lm.getAscent() / REF;
            capRatio = (float) f.createGlyphVector(frc, "H").getVisualBounds().getHeight() / REF;
            return m;
        });
    }

    private static Atlas atlas(Weight w, int px) {
        int key = w.ordinal() * 1000 + px;
        Atlas a = ATLASES.get(key);
        if (a != null) return a;
        a = new Atlas();
        a.px = px;
        Font f = base(w).deriveFont((float) px);
        FontRenderContext frc = frc();
        var lm = f.getLineMetrics("Hg", frc);
        int lineH = (int) Math.ceil(lm.getAscent() + lm.getDescent()) + PAD * 2;
        int texW = 1024, x = 0, y = 0;
        Map<Character, int[]> cells = new HashMap<>();
        for (char c : CHARS.toCharArray()) {
            if (!f.canDisplay(c)) continue;
            int cw = (int) Math.ceil(f.getStringBounds(String.valueOf(c), frc).getWidth()) + PAD * 2 + 2;
            if (x + cw > texW) { x = 0; y += lineH; }
            cells.put(c, new int[]{x, y, cw});
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
        for (Map.Entry<Character, int[]> e : cells.entrySet()) {
            int[] c = e.getValue();
            g.drawString(String.valueOf(e.getKey()), c[0] + PAD, c[1] + PAD + lm.getAscent());
            a.glyphs.put(e.getKey(), new Glyph(c[0], c[1], c[2], lineH));
        }
        g.dispose();
        a.texW = texW;
        a.texH = texH;
        a.tex = Tex.upload("font/" + w.name().toLowerCase() + "_" + px, img, true);
        ATLASES.put(key, a);
        return a;
    }

    private static int bucket(float realPx) {
        for (int b : BUCKETS) if (b >= realPx) return b;
        return BUCKETS[BUCKETS.length - 1];
    }

    /** Width in GUI units of {@code s} at {@code size} (em size in GUI units). */
    public static float width(String s, float size, Weight w) {
        if (s == null || s.isEmpty()) return 0;
        Map<Character, Float> adv = advances(w);
        float total = 0;
        for (int i = 0; i < s.length(); i++) {
            Float a = adv.get(s.charAt(i));
            total += a != null ? a : adv.getOrDefault('?', REF * 0.5f);
        }
        return total * size / REF;
    }

    /** Distance from the top of the line box to the top of capital letters. */
    public static float capTop(float size) { advances(Weight.REGULAR); return (ascentRatio - capRatio) * size + PAD * size / REF; }

    public static float capHeight(float size) { advances(Weight.REGULAR); return capRatio * size; }

    /** Draws text with its line box top at (x, y). Returns the x after the text. */
    public static float draw(Gfx gfx, String s, float x, float y, float size, int color, Weight w) {
        if (s == null || s.isEmpty() || ((color >>> 24) == 0)) return x;
        if (failed) {
            gfx.g.drawString(Minecraft.getInstance().font, s, Math.round(x), Math.round(y), color, false);
            return x + Minecraft.getInstance().font.width(s);
        }
        try {
            Map<Character, Float> adv = advances(w);
            int px = bucket(size * gfx.realScale());
            Atlas a = atlas(w, px);
            float k = size / px; // GUI units per atlas pixel
            float advScale = (float) px / REF;
            gfx.push();
            gfx.translate(x, y);
            gfx.scale(k);
            float pen = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                Glyph gl = a.glyphs.get(c);
                Float ad = adv.get(c);
                if (gl == null) { gl = a.glyphs.get('?'); ad = adv.get('?'); }
                if (c != ' ' && gl != null)
                    gfx.g.blit(RenderPipelines.GUI_TEXTURED, a.tex, Math.round(pen) - PAD, 0, gl.x(), gl.y(), gl.w(), gl.h(), gl.w(), gl.h(), a.texW, a.texH, color);
                pen += (ad == null ? REF * 0.5f : ad) * advScale;
            }
            gfx.pop();
            return x + width(s, size, w);
        } catch (Throwable t) {
            failed = true;
            Log.error("Lock font rendering failed; falling back to the Minecraft font", t);
            return x;
        }
    }
}
