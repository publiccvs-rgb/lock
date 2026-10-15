package lock.gui;

/**
 * Thin line-style glyphs (stroke width ~1.1px) drawn as small triangle strokes and rounded dots, matching the
 * flat minimal icon style of the reference (search grid, person tag, diagonal tracer line, sun, storage box).
 * Everything is built from {@link #stroke} and {@link #dot} so it stays crisp at any GUI scale.
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
        // round caps so joined strokes read as one continuous line
        g.round(x1 - w / 2, y1 - w / 2, w, w, w / 2, c);
        g.round(x2 - w / 2, y2 - w / 2, w, w, w / 2, c);
    }

    private static void dot(Gfx g, float cx, float cy, float r, int c) { g.round(cx - r, cy - r, r * 2, r * 2, r, c); }

    private static void ring(Gfx g, float cx, float cy, float r, float w, int c) {
        int n = 14;
        float prevX = cx + r, prevY = cy;
        for (int i = 1; i <= n; i++) {
            double a = i / (double) n * Math.PI * 2;
            float x = (float) (cx + Math.cos(a) * r), y = (float) (cy + Math.sin(a) * r);
            stroke(g, prevX, prevY, x, y, w, c);
            prevX = x; prevY = y;
        }
    }

    // ---- sidebar / section icons, drawn in a 9x9 box at (x, y) ----

    static void eye(Gfx g, float x, float y, int c) {
        float cx = x + 4.5f, cy = y + 4.5f;
        // lens outline as two arcs approximated by strokes between sampled points
        float[][] top = {{x, cy}, {x + 1.6f, y + 1.8f}, {x + 4.5f, y + 1.1f}, {x + 7.4f, y + 1.8f}, {x + 9, cy}};
        float[][] bot = {{x, cy}, {x + 1.6f, y + 7.2f}, {x + 4.5f, y + 7.9f}, {x + 7.4f, y + 7.2f}, {x + 9, cy}};
        for (int i = 0; i < top.length - 1; i++) stroke(g, top[i][0], top[i][1], top[i + 1][0], top[i + 1][1], 1.1f, c);
        for (int i = 0; i < bot.length - 1; i++) stroke(g, bot[i][0], bot[i][1], bot[i + 1][0], bot[i + 1][1], 1.1f, c);
        dot(g, cx, cy, 1.5f, c);
    }

    static void gear(Gfx g, float x, float y, int c) {
        float cx = x + 4.5f, cy = y + 4.5f, r = 3.4f;
        ring(g, cx, cy, r, 1.1f, c);
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2;
            float x1 = (float) (cx + Math.cos(a) * (r + 0.4)), y1 = (float) (cy + Math.sin(a) * (r + 0.4));
            float x2 = (float) (cx + Math.cos(a) * (r + 1.6)), y2 = (float) (cy + Math.sin(a) * (r + 1.6));
            stroke(g, x1, y1, x2, y2, 1.2f, c);
        }
        dot(g, cx, cy, 1.1f, c);
    }

    static void search(Gfx g, float x, float y, int c) {
        float cx = x + 3.6f, cy = y + 3.6f, r = 2.8f;
        ring(g, cx, cy, r, 1.1f, c);
        stroke(g, cx + r * 0.72f, cy + r * 0.72f, x + 8.6f, y + 8.6f, 1.3f, c);
    }

    static void grid(Gfx g, float x, float y, int c) {
        float s = 3f, gap = 1.2f;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                g.round(x + col * (s + gap), y + row * (s + gap), s, s, 0.8f, c);
            }
        }
    }

    static void list(Gfx g, float x, float y, int c) {
        for (int i = 0; i < 3; i++) stroke(g, x, y + 1 + i * 3.2f, x + 9, y + 1 + i * 3.2f, 1.1f, c);
    }

    static void user(Gfx g, float x, float y, int c) {
        dot(g, x + 4.5f, y + 2.6f, 2f, c);
        stroke(g, x + 1, y + 8.3f, x + 1.8f, y + 6.5f, 1.3f, c);
        stroke(g, x + 8, y + 8.3f, x + 7.2f, y + 6.5f, 1.3f, c);
        stroke(g, x + 1.8f, y + 6.5f, x + 7.2f, y + 6.5f, 1.3f, c);
    }

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
        stroke(g, x + 2.2f, y + 7, x + 2.2f, y + 1.5f, 1.4f, c);
        stroke(g, x + 6.8f, y + 7, x + 6.8f, y + 1.5f, 1.4f, c);
    }

    static void globe(Gfx g, float x, float y, int c) {
        float cx = x + 4.5f, cy = y + 4.5f, r = 3.6f;
        ring(g, cx, cy, r, 1.05f, c);
        stroke(g, cx, cy - r, cx, cy + r, 1.0f, c);
        stroke(g, cx - r, cy, cx + r, cy, 1.0f, c);
        float rw = r * 0.5f;
        for (int s = -1; s <= 1; s += 2) {
            float[][] pts = new float[9][2];
            for (int i = 0; i <= 8; i++) {
                double a = -Math.PI / 2 + i / 8.0 * Math.PI;
                pts[i][0] = cx + s * (float) (Math.sin(a) * rw);
                pts[i][1] = cy - (float) (Math.cos(a) * r);
            }
            for (int i = 0; i < 8; i++) stroke(g, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], 0.9f, c);
        }
    }

    static void keybind(Gfx g, float x, float y, int c) {
        g.roundEx(x, y, 9, 7, 1.6f, 1.6f, 1.6f, 1.6f, 0);
        stroke(g, x + 1, y, x + 8, y, 1.1f, c);
        stroke(g, x + 1, y + 7, x + 8, y + 7, 1.1f, c);
        stroke(g, x, y + 1, x, y + 6, 1.1f, c);
        stroke(g, x + 9, y + 1, x + 9, y + 6, 1.1f, c);
    }

    static void plusCircle(Gfx g, float x, float y, int acc, int ink) {
        g.round(x, y, 9, 9, 4.5f, acc);
        plus(g, x + 2, y + 2, 5, ink);
    }

    static void plus(Gfx g, float x, float y, float size, int c) {
        float cx = x + size / 2, cy = y + size / 2;
        stroke(g, cx, y, cx, y + size, 1.3f, c);
        stroke(g, x, cy, x + size, cy, 1.3f, c);
    }

    /** dir: -1 up, 1 down, 2 right. */
    static void chevron(Gfx g, float x, float y, int dir, int c) {
        if (dir == 2) {
            stroke(g, x, y - 2.2f, x + 2.4f, y, 1.15f, c);
            stroke(g, x + 2.4f, y, x, y + 2.2f, 1.15f, c);
            return;
        }
        float f = dir < 0 ? 1 : -1;
        stroke(g, x - 2.4f, y + f * 1.1f, x, y - f * 1.1f, 1.15f, c);
        stroke(g, x, y - f * 1.1f, x + 2.4f, y + f * 1.1f, 1.15f, c);
    }

    static void sun(Gfx g, float x, float y, int c) {
        float cx = x + 4.5f, cy = y + 4.5f;
        dot(g, cx, cy, 2.1f, c);
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2;
            float x1 = (float) (cx + Math.cos(a) * 2.9), y1 = (float) (cy + Math.sin(a) * 2.9);
            float x2 = (float) (cx + Math.cos(a) * 4.3), y2 = (float) (cy + Math.sin(a) * 4.3);
            stroke(g, x1, y1, x2, y2, 1.0f, c);
        }
    }

    static void box(Gfx g, float x, float y, int c) {
        float cx = x + 4.5f, cy = y + 3.1f, r = 3.6f;
        float[][] top = {{cx, cy - r}, {cx + r, cy - r * 0.5f}, {cx, cy}, {cx - r, cy - r * 0.5f}, {cx, cy - r}};
        for (int i = 0; i < 4; i++) stroke(g, top[i][0], top[i][1], top[i + 1][0], top[i + 1][1], 1.0f, c);
        stroke(g, cx, cy, cx, cy + r * 1.1f, 1.0f, c);
        stroke(g, cx + r, cy - r * 0.5f, cx + r, cy + r * 0.6f, 1.0f, c);
        stroke(g, cx - r, cy - r * 0.5f, cx - r, cy + r * 0.6f, 1.0f, c);
        stroke(g, cx, cy + r * 1.1f, cx + r, cy + r * 0.6f, 1.0f, c);
        stroke(g, cx, cy + r * 1.1f, cx - r, cy + r * 0.6f, 1.0f, c);
    }

    static void tracerLine(Gfx g, float x, float y, int c) {
        dot(g, x + 1.2f, y + 7.4f, 1.0f, c);
        dot(g, x + 7.6f, y + 1.2f, 1.0f, c);
        stroke(g, x + 2.1f, y + 6.6f, x + 6.7f, y + 2.1f, 1.0f, c);
    }

    static void nametag(Gfx g, float x, float y, int c) {
        dot(g, x + 4.5f, y + 2.3f, 1.7f, c);
        g.roundEx(x + 1.3f, y + 5.2f, 6.4f, 3.4f, 1.6f, 1.6f, 1.6f, 1.6f, 0);
        stroke(g, x + 1.3f, y + 5.2f, x + 7.7f, y + 5.2f, 1.0f, c);
        stroke(g, x + 1.3f, y + 8.6f, x + 7.7f, y + 8.6f, 1.0f, c);
        stroke(g, x + 1.3f, y + 5.2f, x + 1.3f, y + 8.6f, 1.0f, c);
        stroke(g, x + 7.7f, y + 5.2f, x + 7.7f, y + 8.6f, 1.0f, c);
    }

    static void person(Gfx g, float x, float y, int c) { user(g, x, y, c); }

    /** Vertical kebab: three small dots. */
    static void dots(Gfx g, float x, float y, int c) {
        for (int i = 0; i < 3; i++) dot(g, x, y + i * 3.4f, 0.85f, c);
    }
}
