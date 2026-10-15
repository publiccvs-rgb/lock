package dev.lock.client.render;

import dev.lock.core.ui.IconSet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** In-game icons: {@link IconSet} rasterised into an atlas at the on-screen pixel size and tinted when drawn. */
public final class Icons {
    private static final int[] BUCKETS = {12, 16, 20, 24, 32, 40, 48, 64};
    private static final Map<Integer, Identifier> ATLAS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Integer> INDEX = new HashMap<>();
    private static final int COLS = 16;

    static {
        int i = 0;
        for (String k : IconSet.all().keySet()) INDEX.put(k, i++);
    }

    private Icons() {}

    public static boolean has(String name) { return INDEX.containsKey(name); }

    private static final Map<Integer, BufferedImage> RASTERISED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Off-thread: rasterise the icon atlas for a pixel size and queue its upload. */
    public static void warmUp(float realPx) {
        int px = bucket(realPx);
        if (ATLAS.containsKey(px) || RASTERISED.containsKey(px)) return;
        RASTERISED.put(px, rasterise(px));
        Tex.onRenderThread(() -> atlas(px));
    }

    private static int bucket(float real) {
        for (int b : BUCKETS) if (b >= real) return b;
        return BUCKETS[BUCKETS.length - 1];
    }

    private static Identifier atlas(int px) {
        Identifier id = ATLAS.get(px);
        if (id != null) return id;
        BufferedImage pre = RASTERISED.remove(px);
        id = Tex.upload("icons/" + px, pre != null ? pre : rasterise(px), true);
        ATLAS.put(px, id);
        return id;
    }

    private static BufferedImage rasterise(int px) {
        int n = INDEX.size(), cell = px + 2, rows = (n + COLS - 1) / COLS;
        BufferedImage img = new BufferedImage(COLS * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        int i = 0;
        for (String name : IconSet.all().keySet()) {
            IconSet.paint(g, name, (i % COLS) * cell + 1, (i / COLS) * cell + 1, px);
            i++;
        }
        g.dispose();
        return img;
    }

    /** Draws icon {@code name} in a size x size box at (x, y). */
    public static void draw(Gfx gfx, String name, float x, float y, float size, int color) {
        Integer idx = INDEX.get(name);
        if (idx == null || (color >>> 24) == 0) return;
        float real = size * gfx.realScale();
        int px = bucket(real);
        Identifier tex = atlas(px);
        int cell = px + 2, rows = (INDEX.size() + COLS - 1) / COLS;
        gfx.push();
        gfx.translate(x, y);
        gfx.scale(size / px);
        gfx.g.blit(RenderPipelines.GUI_TEXTURED, tex, -1, -1, (idx % COLS) * cell, (idx / COLS) * cell, cell, cell, cell, cell, COLS * cell, rows * cell, color);
        gfx.pop();
    }
}
