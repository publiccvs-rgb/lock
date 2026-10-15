package dev.lock.client.module;

import dev.lock.client.LockClient;
import dev.lock.client.render.Gfx;
import dev.lock.client.render.LockFont;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD element made of "label value" lines. Content is rebuilt at most every {@link #refreshMs}
 * milliseconds instead of every frame.
 */
public abstract class TextHud extends HudModule {
    public record Line(String label, String value, int color) {}

    private List<Line> cached = new ArrayList<>();
    private long lastBuild;
    protected long refreshMs = 50;

    protected TextHud(LockClient lock, String id) { super(lock, id); }

    /** Adds this element's lines. {@code compact} requests shorter labels. */
    protected abstract void lines(List<Line> out, boolean compact);

    protected final Line line(String label, String value) { return new Line(label, value, 0); }

    protected final Line line(String label, String value, int color) { return new Line(label, value, color); }

    private List<Line> content() {
        long now = System.currentTimeMillis();
        if (now - lastBuild >= refreshMs || cached.isEmpty() || editing) {
            List<Line> l = new ArrayList<>();
            lines(l, compact());
            if (l.isEmpty() && editing) l.add(new Line("", def.name, 0));
            cached = l;
            lastBuild = now;
        }
        return cached;
    }

    /** Forces a rebuild on the next frame (after settings change). */
    public void invalidate() { lastBuild = 0; }

    @Override public void onSettingChanged(String key) { invalidate(); }

    @Override public boolean empty() { return content().isEmpty(); }

    private String text(Line l) {
        if (l.label() == null || l.label().isEmpty()) return l.value();
        return l.label() + " " + l.value();
    }

    private static final float BASE = 8f;

    private float lineW(Gfx gfx, Line l, float size) {
        float w = 0;
        if (l.label() != null && !l.label().isEmpty()) w += gfx.width(l.label() + " ", size, LockFont.Weight.REGULAR);
        return w + gfx.width(l.value(), size, LockFont.Weight.MEDIUM);
    }

    private int lineH() { return Math.round(10 * textScale()); }

    private int iconSpace() { return icon() != null ? Math.round(12 * textScale()) : 0; }

    @Override
    public int[] size(Gfx gfx) {
        List<Line> lines = content();
        float size = BASE * textScale();
        float w = 0;
        for (Line l : lines) w = Math.max(w, lineW(gfx, l, size));
        int gap = Math.max(0, padding() / 2 - 1);
        int n = Math.max(1, lines.size());
        return new int[]{(int) Math.ceil(w) + iconSpace(), n * lineH() + (n - 1) * gap};
    }

    @Override
    public void draw(Gfx gfx, int w, int h) {
        List<Line> lines = content();
        float size = BASE * textScale();
        int lh = lineH(), gap = Math.max(0, padding() / 2 - 1);
        String icon = icon();
        int is = iconSpace();
        if (icon != null) {
            float isz = 8 * textScale();
            float iy = lines.size() > 1 ? (lh - isz) / 2f : (h - isz) / 2f;
            gfx.icon(icon, 0, iy, isz, lock.theme.accent());
        }
        String align = align();
        float y = 0;
        for (Line l : lines) {
            float lw = lineW(gfx, l, size);
            float avail = w - is;
            float x = is + switch (align) {
                case "Center" -> (avail - lw) / 2f;
                case "Right" -> avail - lw;
                default -> 0f;
            };
            float cy = y + lh / 2f;
            if (l.label() != null && !l.label().isEmpty())
                x = gfx.textMid(l.label() + " ", x, cy, size, labelColor(), LockFont.Weight.REGULAR);
            gfx.textMid(l.value(), x, cy, size, l.color() != 0 ? l.color() : textColor(), LockFont.Weight.MEDIUM);
            y += lh + gap;
        }
    }
}
