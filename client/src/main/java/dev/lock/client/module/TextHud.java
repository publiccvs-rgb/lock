package dev.lock.client.module;

import dev.lock.client.LockClient;
import dev.lock.client.render.Gfx;

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

    @Override
    public int[] size(Gfx gfx) {
        List<Line> lines = content();
        float ts = textScale();
        int w = 0;
        for (Line l : lines) w = Math.max(w, gfx.width(text(l)));
        int lineH = Math.round(9 * ts);
        int gap = Math.max(1, padding() / 2);
        int h = lines.isEmpty() ? lineH : lines.size() * lineH + (lines.size() - 1) * gap;
        return new int[]{Math.max(1, Math.round(w * ts)), h};
    }

    @Override
    public void draw(Gfx gfx, int w, int h) {
        List<Line> lines = content();
        float ts = textScale();
        int lineH = Math.round(9 * ts);
        int gap = Math.max(1, padding() / 2);
        String align = align();
        boolean shadow = shadow();
        int y = 0;
        for (Line l : lines) {
            String full = text(l);
            int lw = Math.round(gfx.width(full) * ts);
            int x = switch (align) {
                case "Center" -> (w - lw) / 2;
                case "Right" -> w - lw;
                default -> 0;
            };
            gfx.push();
            gfx.translate(x, y);
            if (ts != 1f) gfx.scale(ts);
            int cx = 0;
            if (l.label() != null && !l.label().isEmpty()) cx = gfx.text(l.label() + " ", 0, 0, labelColor(), shadow);
            gfx.text(l.value(), cx, 0, l.color() != 0 ? l.color() : textColor(), shadow);
            gfx.pop();
            y += lineH + gap;
        }
    }
}
