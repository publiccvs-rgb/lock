package dev.lock.client.gui;

import dev.lock.client.LockClient;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.render.Gfx;
import dev.lock.client.render.LockFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Immediate-mode UI toolkit: widgets are drawn and registered as hit regions every frame; input
 * routes to the topmost region of the previous frame. Visual language: dark flat surfaces, small
 * radii, pill switches with round knobs, accent fills, smooth TTF text and line icons.
 */
public final class Ui {
    public interface Click { void click(int mx, int my, int button); }
    public interface Drag { void drag(int mx, int my); }

    public static final float FS = 7.5f, FS_SMALL = 6.5f;
    public static final LockFont.Weight R = LockFont.Weight.REGULAR, M = LockFont.Weight.MEDIUM, SB = LockFont.Weight.SEMIBOLD;

    record Region(int x, int y, int w, int h, Click click, DoubleConsumer scroll) {
        boolean contains(int px, int py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }

    /** Single-line text input. */
    public static final class Field {
        public String text;
        public final int max;
        public final boolean numeric;
        public Consumer<String> onCommit;
        public Consumer<String> onChange;

        public Field(String text, int max, boolean numeric) {
            this.text = text;
            this.max = max;
            this.numeric = numeric;
        }

        void type(String s) {
            for (char c : s.toCharArray()) {
                if (text.length() >= max) break;
                if (c < 0x20 || c == '§') continue;
                if (numeric && !(Character.isDigit(c) || c == '-')) continue;
                text += c;
            }
            if (onChange != null) onChange.accept(text);
        }

        void backspace() {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            if (onChange != null) onChange.accept(text);
        }
    }

    public final LockClient lock;
    public final ThemeManager theme;
    public Gfx gfx;
    public int mx, my;
    private final List<Region> regions = new ArrayList<>();
    private final List<Region> pending = new ArrayList<>();
    private int[] clip;
    public Drag drag;
    public Field focused;
    private String tooltip;
    private int tipX, tipY;
    private final Map<Object, Float> anim = new HashMap<>();
    private long lastFrame = System.nanoTime();
    private float dt;

    public Ui(LockClient lock) {
        this.lock = lock;
        this.theme = lock.theme;
    }

    public void begin(Gfx gfx, int mx, int my) {
        this.gfx = gfx;
        this.mx = mx;
        this.my = my;
        pending.clear();
        tooltip = null;
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
    }

    public void end() {
        regions.clear();
        regions.addAll(pending);
        if (tooltip != null && lock.config.state("click_gui").bool("tooltips")) drawTooltip();
    }

    /** Eases a value toward {@code target}; used for restrained hover/toggle transitions. */
    public float ease(Object key, float target) {
        if (!lock.config.state("click_gui").bool("animations")) { anim.put(key, target); return target; }
        float v = anim.getOrDefault(key, target);
        v += (target - v) * Math.min(1f, dt * 14f);
        if (Math.abs(v - target) < 0.002f) v = target;
        anim.put(key, v);
        return v;
    }

    private final java.util.ArrayDeque<int[]> clips = new java.util.ArrayDeque<>();

    /** Pushes a clip rectangle (intersected with the current one). */
    public void clip(int x1, int y1, int x2, int y2) {
        if (clip != null) {
            x1 = Math.max(x1, clip[0]); y1 = Math.max(y1, clip[1]);
            x2 = Math.min(x2, clip[2]); y2 = Math.min(y2, clip[3]);
            clips.push(clip);
        }
        clip = new int[]{x1, y1, Math.max(x1, x2), Math.max(y1, y2)};
        gfx.scissor(x1, y1, Math.max(x1, x2), Math.max(y1, y2));
    }

    public void unclip() {
        clip = clips.isEmpty() ? null : clips.pop();
        gfx.unscissor();
    }

    public boolean hover(int x, int y, int w, int h) {
        if (clip != null && (mx < clip[0] || my < clip[1] || mx >= clip[2] || my >= clip[3])) return false;
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public void region(int x, int y, int w, int h, Click c) { region(x, y, w, h, c, null, null); }

    public void region(int x, int y, int w, int h, Click c, DoubleConsumer scroll, String tip) {
        int rx = x, ry = y, rw = w, rh = h;
        if (clip != null) {
            int x2 = Math.min(x + w, clip[2]), y2 = Math.min(y + h, clip[3]);
            rx = Math.max(x, clip[0]);
            ry = Math.max(y, clip[1]);
            rw = x2 - rx;
            rh = y2 - ry;
            if (rw <= 0 || rh <= 0) return;
        }
        pending.add(new Region(rx, ry, rw, rh, c, scroll));
        if (tip != null && !tip.isEmpty() && mx >= rx && my >= ry && mx < rx + rw && my < ry + rh) {
            tooltip = tip;
            tipX = mx;
            tipY = my;
        }
    }

    public void tooltip(String t) { if (t != null && !t.isEmpty()) { tooltip = t; tipX = mx; tipY = my; } }

    public boolean click(int x, int y, int button) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.contains(x, y) && (r.click() != null || r.scroll() != null)) {
                if (r.click() == null) return true; // blocks clicks through panels
                sound();
                r.click().click(x, y, button);
                return true;
            }
        }
        return false;
    }

    public boolean scroll(int x, int y, double amount) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.scroll() != null && r.contains(x, y)) {
                r.scroll().accept(amount);
                return true;
            }
        }
        return false;
    }

    public void sound() {
        var s = lock.config.state("sounds");
        if (!s.bool("clicks")) return;
        float v = (float) s.num("volume");
        if (v <= 0) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, v * 0.5f));
    }

    // ------------------------------------------------------------------ text helpers

    public float text(String s, float x, float cy, float size, int color, LockFont.Weight w) { return gfx.textMid(s, x, cy, size, color, w); }

    public float width(String s, float size, LockFont.Weight w) { return gfx.width(s, size, w); }

    public String ellipsize(String s, float maxW, float size) {
        if (width(s, size, R) <= maxW) return s;
        while (s.length() > 1 && width(s + "…", size, R) > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    public String ellipsize(String s, int w) { return ellipsize(s, w, FS); }

    private final Map<String, List<String>> wrapCache = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<String>> e) { return size() > 256; }
    };

    /** Word-wraps text to width {@code w} (cached: descriptions are re-laid-out every frame otherwise). */
    public List<String> wrap(String text, float w, float size) {
        String key = text + '\u0000' + w + '\u0000' + size;
        List<String> cached = wrapCache.get(key);
        if (cached != null) return cached;
        List<String> out = wrapUncached(text, w, size);
        wrapCache.put(key, out);
        return out;
    }

    private List<String> wrapUncached(String text, float w, float size) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (width(next, size, R) > w && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else line = new StringBuilder(next);
            }
            out.add(line.toString());
        }
        return out;
    }

    public List<String> wrap(String text, int w) { return wrap(text, w, FS); }

    // ------------------------------------------------------------------ widgets

    public void button(int x, int y, int w, int h, String label, Runnable onClick) { button(x, y, w, h, label, onClick, false); }

    /** Rounded button; primary buttons are accent-filled. */
    public void button(int x, int y, int w, int h, String label, Runnable onClick, boolean primary) {
        float hv = ease("btn" + x + ":" + y, hover(x, y, w, h) ? 1 : 0);
        int bg = primary ? ThemeManager.blend(theme.accent(), 0xFFFFFFFF, 0.12f * hv)
                : ThemeManager.withAlpha(0xFFFFFF, (int) (0x12 + 0x14 * hv));
        gfx.rrect(x, y, w, h, Math.min(4, h / 2), bg);
        String l = ellipsize(label, w - 8, FS);
        float tw = width(l, FS, M);
        text(l, x + (w - tw) / 2f, y + h / 2f, FS, primary ? theme.onAccent() : theme.text(), M);
        region(x, y, w, h, (a, b, btn) -> onClick.run());
    }

    /** Square icon button. */
    public void iconButton(int x, int y, int size, String icon, int color, Runnable onClick, String tip) {
        float hv = ease("ib" + x + ":" + y, hover(x, y, size, size) ? 1 : 0);
        if (hv > 0.01f) gfx.rrect(x, y, size, size, 3, ThemeManager.withAlpha(0xFFFFFF, (int) (0x18 * hv)));
        float is = size * 0.62f;
        gfx.icon(icon, x + (size - is) / 2f, y + (size - is) / 2f, is, ThemeManager.blend(color, 0xFFFFFFFF, 0.3f * hv));
        region(x, y, size, size, (a, b, btn) -> onClick.run(), null, tip);
    }

    /** Pill switch with a round knob; 16x8. */
    public void toggle(int x, int y, boolean on, Runnable onClick) {
        int w = 16, h = 8;
        float t = ease("tg" + x + ":" + y + ":" + System.identityHashCode(onClick.getClass()), on ? 1 : 0);
        int track = ThemeManager.blend(0xFF3A3C42, theme.accent(), t);
        gfx.rrect(x, y, w, h, 4, track);
        float kx = x + 4 + (w - 8) * t;
        gfx.circle(kx, y + 4, 3f, 0xFFFFFFFF);
        region(x - 3, y - 3, w + 6, h + 6, (a, b, btn) -> onClick.run());
    }

    /** Rounded slider track with a circular knob. */
    public void slider(int x, int y, int w, double value, double min, double max, DoubleConsumer set) {
        double t = max > min ? (value - min) / (max - min) : 0;
        t = Math.max(0, Math.min(1, t));
        gfx.rrect(x, y + 2, w, 3, 1, 0xFF34363C);
        int fw = (int) Math.round(w * t);
        if (fw > 0) gfx.rrect(x, y + 2, Math.max(3, fw), 3, 1, theme.accent());
        boolean hov = hover(x - 3, y - 3, w + 6, 10);
        gfx.circle(x + (float) (w * t), y + 3.5f, hov ? 3.5f : 3f, 0xFFFFFFFF);
        Drag d = (px, py) -> set.accept(min + (max - min) * Math.max(0, Math.min(1, (px - x) / (double) w)));
        region(x - 4, y - 3, w + 8, 11, (px, py, btn) -> {
            d.drag(px, py);
            drag = d;
        }, amount -> set.accept(value + Math.signum(amount) * (max - min) / 50.0), null);
    }

    /** Rounded text field with optional leading icon. */
    public void field(int x, int y, int w, int h, Field f, String placeholder) { field(x, y, w, h, f, placeholder, null); }

    public void field(int x, int y, int w, int h, Field f, String placeholder, String icon) {
        boolean focus = focused == f;
        gfx.rrect(x, y, w, h, 3, focus ? 0xFF2A2C31 : 0xFF222327);
        if (focus) gfx.rrect(x + 2, y + h - 1, w - 4, 1, 0, theme.accent());
        int tx0 = x + 5;
        if (icon != null) {
            gfx.icon(icon, x + 4, y + (h - 7) / 2f, 7, focus ? theme.text() : 0xFF7C7E85);
            tx0 = x + 14;
        }
        String shown = f.text.isEmpty() && !focus ? placeholder : f.text;
        int color = f.text.isEmpty() && !focus ? 0xFF7C7E85 : theme.text();
        String vis = shown;
        while (width(vis, FS, R) > w - (tx0 - x) - 5 && vis.length() > 1) vis = vis.substring(1);
        float end = text(vis, tx0, y + h / 2f, FS, color, R);
        if (focus && (System.currentTimeMillis() / 500) % 2 == 0) gfx.fill((int) end + 1, y + 3, (int) end + 2, y + h - 3, theme.text());
        region(x, y, w, h, (a, b, btn) -> focused = f);
    }

    /** Round colour swatch with a checkerboard behind transparent colours. */
    public void swatch(int x, int y, int size, int argb) {
        float r = size / 2f;
        gfx.circle(x + r, y + r, r, 0xFF5A5C62);
        if ((argb >>> 24) < 0xF0) gfx.circle(x + r, y + r, r - 1, 0xFFB8B8B8);
        gfx.circle(x + r, y + r, r - 1, argb);
    }

    private void drawTooltip() {
        List<String> lines = wrap(tooltip, 170, FS_SMALL);
        float w = 0;
        for (String l : lines) w = Math.max(w, width(l, FS_SMALL, R));
        int x = tipX + 8, y = tipY + 10, h = lines.size() * 9 + 7, bw = (int) w + 10;
        gfx.shadow(x, y, bw, h, 4, 0x90000000);
        gfx.rrect(x, y, bw, h, 3, 0xFA222327);
        float ty = y + 4 + 4;
        for (String l : lines) { text(l, x + 5, ty, FS_SMALL, theme.text(), R); ty += 9; }
    }
}
