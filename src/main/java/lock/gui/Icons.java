package lock.gui;

/**
 * Flat glyph icons built only from {@link Gfx#round} / {@link Gfx#rect} (the same primitive that already renders
 * perfectly smooth for toggles, dots and panel corners elsewhere in the menu) plus a handful of short thick
 * strokes for the few genuinely diagonal marks (chevrons, the tracer line). Earlier icons approximated circles
 * with many short triangle segments, which facets badly at 7-9px; every circular shape here is instead a solid
 * disc, optionally with a background-coloured disc or band cut into it to form a ring/hole - no polygons.
 */
final class Icons {
    private Icons() {}

    private static void stroke(Gfx g, float x1, float y1, float x2, float y2, float w, int c) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) { g.round(x1 - w / 2, y1 - w / 2, w, w, w / 2, c); return; }
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        g.tri(x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, c);
        g.tri(x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, c);
        g.round(x1 - w / 2, y1 - w / 2, w, w, w / 2, c);
        g.round(x2 - w / 2, y2 - w / 2, w, w, w / 2, c);
    }

    private static void dot(Gfx g, float cx, float cy, float r, int c) { g.round(cx - r, cy - r, r * 2, r * 2, r, c); }

    /** Filled disc with a smaller disc of {@code bg} cut into its centre, i.e. a clean ring - no polygon joints. */
    private static void ring(Gfx g, float cx, float cy, float r, float thickness, int c, int bg) {
        dot(g, cx, cy, r, c);
        dot(g, cx, cy, r - thickness, bg);
    }

    // ---- sidebar / section icons, drawn in a 9x9 box at (x, y) ----

    /** Eye: a lens (stadium) with a hole cut for the iris, and a solid pupil dot on top. */
    static void eye(Gfx g, float x, float y, int c, int bg) {
        float cx = x + 4.5f, cy = y + 4.6f;
        g.round(x + 0.2f, y + 2.6f, 8.6f, 4f, 2f, c);
        dot(g, cx, cy, 1.5f, bg);
        dot(g, cx, cy, 0.8f, c);
    }

    /** Gear: a disc with four square teeth and a hole cut for the centre. */
    static void gear(Gfx g, float x, float y, int c, int bg) {
        float cx = x + 4.5f, cy = y + 4.5f, r = 2.9f;
        g.round(cx - 1, y, 2, 1.8f, 0.5f, c);
        g.round(cx - 1, y + 9 - 1.8f, 2, 1.8f, 0.5f, c);
        g.round(x, cy - 1, 1.8f, 2, 0.5f, c);
        g.round(x + 9 - 1.8f, cy - 1, 1.8f, 2, 0.5f, c);
        dot(g, cx, cy, r, c);
        dot(g, cx, cy, r - 1.5f, bg);
        dot(g, cx, cy, 0.9f, c);
    }

    /** Magnifier: a ring with a short handle made of three tapering dots (smoother than a straight diagonal). */
    static void search(Gfx g, float x, float y, int c, int bg) {
        float cx = x + 3.5f, cy = y + 3.5f, r = 2.6f;
        ring(g, cx, cy, r, 1.1f, c, bg);
        dot(g, x + 5.6f, y + 5.6f, 1.0f, c);
        dot(g, x + 6.5f, y + 6.5f, 0.9f, c);
        dot(g, x + 7.4f, y + 7.4f, 0.8f, c);
    }

    static void grid(Gfx g, float x, float y, int c) {
        float s = 3f, gap = 1.2f;
        for (int row = 0; row < 2; row++)
            for (int col = 0; col < 2; col++)
                g.round(x + col * (s + gap), y + row * (s + gap), s, s, 1f, c);
    }

    static void list(Gfx g, float x, float y, int c) {
        for (int i = 0; i < 3; i++) g.round(x, y + i * 3.2f, 9, 1.3f, 0.65f, c);
    }

    static void user(Gfx g, float x, float y, int c) {
        dot(g, x + 4.5f, y + 2.6f, 2f, c);
        g.roundEx(x + 1, y + 5.1f, 7, 4.2f, 3.5f, 3.5f, 1, 1, c);
    }

    static void person(Gfx g, float x, float y, int c) { user(g, x, y, c); }

    static void star(Gfx g, float x, float y, int c) {
        float cx = x + 4.5f, cy = y + 4.5f;
        for (int i = 0; i < 5; i++) {
            double a0 = -Math.PI / 2 + i * (Math.PI * 2 / 5), a1 = a0 + Math.PI * 2 / 5;
            float x0 = (float) (cx + Math.cos(a0) * 4.2), y0 = (float) (cy + Math.sin(a0) * 4.2);
            float x1 = (float) (cx + Math.cos(a1) * 4.2), y1 = (float) (cy + Math.sin(a1) * 4.2);
            double am = (a0 + a1) / 2;
            float xm = (float) (cx + Math.cos(am) * 1.6), ym = (float) (cy + Math.sin(am) * 1.6);
            g.tri(cx, cy, x0, y0, xm, ym, c);
            g.tri(cx, cy, xm, ym, x1, y1, c);
        }
    }

    static void bars(Gfx g, float x, float y, int c) {
        g.round(x + 1.5f, y + 1, 2.2f, 6.4f, 1.1f, c);
        g.round(x + 5.3f, y + 1, 2.2f, 6.4f, 1.1f, c);
    }

    /** Globe: a disc with a thin equator band and meridian stripe cut in, no polygon rings. */
    static void globe(Gfx g, float x, float y, int c, int bg) {
        float cx = x + 4.5f, cy = y + 4.5f, r = 3.6f;
        dot(g, cx, cy, r, c);
        g.round(x, cy - 0.55f, 9, 1.1f, 0.5f, bg);
        g.round(cx - 0.9f, y, 1.8f, 9, 0.9f, bg);
    }

    static void keybind(Gfx g, float x, float y, int c) {
        g.roundEx(x, y, 9, 7, 2, 2, 2, 2, c);
    }

    static void plus(Gfx g, float x, float y, float size, int c) {
        float cx = x + size / 2, cy = y + size / 2, t = Math.max(1.1f, size * 0.22f);
        g.round(cx - t / 2, y, t, size, t / 2, c);
        g.round(x, cy - t / 2, size, t, t / 2, c);
    }

    static void plusCircle(Gfx g, float x, float y, int acc, int ink) {
        dot(g, x + 4.5f, y + 4.5f, 4.5f, acc);
        plus(g, x + 2, y + 2, 5, ink);
    }

    static void sun(Gfx g, float x, float y, int c) {
        float cx = x + 4.5f, cy = y + 4.5f;
        dot(g, cx, cy, 2.1f, c);
        g.round(cx - 0.6f, y, 1.2f, 1.6f, 0.6f, c);
        g.round(cx - 0.6f, y + 7.4f, 1.2f, 1.6f, 0.6f, c);
        g.round(x, cy - 0.6f, 1.6f, 1.2f, 0.6f, c);
        g.round(x + 7.4f, cy - 0.6f, 1.6f, 1.2f, 0.6f, c);
    }

    static void box(Gfx g, float x, float y, int c) {
        g.roundEx(x + 0.5f, y + 1.5f, 8, 3, 1, 1, 0, 0, c);
        g.roundEx(x + 0.5f, y + 4.5f, 8, 3.2f, 0, 0, 1, 1, c);
        g.rect(x + 0.5f, y + 4.3f, 8, 0.6f, 0xFF000000 | ((c >> 16 & 255) / 2 << 16 | (c >> 8 & 255) / 2 << 8 | (c & 255) / 2));
    }

    static void tracerLine(Gfx g, float x, float y, int c) {
        dot(g, x + 1.2f, y + 7.4f, 1.1f, c);
        dot(g, x + 7.6f, y + 1.2f, 1.1f, c);
        dot(g, x + 3.3f, y + 5.3f, 0.95f, c);
        dot(g, x + 5.5f, y + 3.1f, 0.95f, c);
    }

    static void nametag(Gfx g, float x, float y, int c) {
        dot(g, x + 4.5f, y + 2.3f, 1.7f, c);
        g.roundEx(x + 1.3f, y + 5.2f, 6.4f, 3.4f, 1.6f, 1.6f, 1.6f, 1.6f, c);
    }

    /** dir: -1 up, 1 down, 2 right. */
    static void chevron(Gfx g, float x, float y, int dir, int c) {
        if (dir == 2) {
            stroke(g, x, y - 2.2f, x + 2.4f, y, 1.3f, c);
            stroke(g, x + 2.4f, y, x, y + 2.2f, 1.3f, c);
            return;
        }
        float f = dir < 0 ? 1 : -1;
        stroke(g, x - 2.4f, y + f * 1.1f, x, y - f * 1.1f, 1.3f, c);
        stroke(g, x, y - f * 1.1f, x + 2.4f, y + f * 1.1f, 1.3f, c);
    }

    /** Vertical kebab: three small dots. */
    static void dots(Gfx g, float x, float y, int c) {
        for (int i = 0; i < 3; i++) dot(g, x, y + i * 3.4f, 0.9f, c);
    }
}
