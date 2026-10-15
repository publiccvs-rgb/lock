package lock.gui;

import lock.module.General;
import lock.module.Module;
import lock.module.ModuleManager;
import lock.setting.*;

import java.util.*;

/**
 * Menu layout, animation, drawing and input.
 *
 * Proportions are taken from the reference by panel width (114 gui px): rows 21, header 19, name text 1.0,
 * setting labels 0.75, outer padding ~6, kebab menu ~10 from the right edge. Animation is exponential
 * smoothing (frame-rate independent); the hot path allocates nothing - hit regions live in pooled arrays
 * and per-module / per-setting state is created once.
 */
public final class GuiView {
    static final int PW = 114, ROW = 21, HEAD = 19, GAP = 4, MARGIN = 6, PR = 4;
    static final float LABEL = 0.75f;

    // ---- animation primitive -------------------------------------------------
    private static final class Anim {
        float v, t;
        Anim(float x) { v = t = x; }
        void step(float k) { v += (t - v) * k; if (Math.abs(t - v) < 0.002f) v = t; }
        void snap(float x) { v = t = x; }
    }

    private static final class ModState {
        final Module m;
        final Anim on, open = new Anim(0), hover = new Anim(0);
        ModState(Module m) { this.m = m; on = new Anim(m.enabled() ? 1 : 0); }
    }

    private static final class SetState {
        final Anim on = new Anim(0), val = new Anim(0);
        String fmt = "";
        double fmtVal = Double.NaN;
        boolean init;
    }

    private static final class Panel {
        final String id, title;
        final boolean draggable;
        float x, y, h;
        boolean collapsed;
        final Anim expand = new Anim(1), appear = new Anim(0), scroll = new Anim(0);
        float contentH, viewH, delay;
        Panel(String id, String title, boolean draggable) { this.id = id; this.title = title; this.draggable = draggable; }
    }

    // ---- hit pool -----------------------------------------------------------
    private static final int T_PANEL = 0, T_DRAG = 1, T_COLLAPSE = 2, T_TOGGLE = 3, T_OPEN = 4,
        T_BOOL = 5, T_MODE = 6, T_SLIDER = 7, T_COLOR = 8, T_NOOP = 9;
    private float[] hx = new float[128], hy = new float[128], hw = new float[128], hh = new float[128];
    private int[] ht = new int[128], hb = new int[128];
    private Object[] ha = new Object[128];
    private Panel[] hp = new Panel[128];
    private int hn;

    private final ModuleManager mods;
    private final Panel sidebar = new Panel("sidebar", "", false);
    private final Panel render = new Panel("render", "Render", true);
    private final Panel settings = new Panel("settings", "Settings", true);
    private final Panel profiles = new Panel("profiles", "Profiles", true);
    private final Panel[] order = {sidebar, render, settings, profiles};

    private final Module[] list;
    private final ModState[] ms;
    private final IdentityHashMap<Setting<?>, SetState> sstate = new IdentityHashMap<>();
    private final Anim master = new Anim(0);
    private final Anim tip = new Anim(0);
    private Module tipFor;
    private float hoverTime;
    private boolean closing, laidOut;

    private Panel dragPanel;
    private float dragDX, dragDY;
    private NumSetting dragNum;
    private float dragNumX, dragNumW;
    private Panel hoverPanel;
    private int sw = 480, sh = 270;

    public GuiView(ModuleManager mods) {
        this.mods = mods;
        List<Module> l = new ArrayList<>();
        for (Module m : mods.all) if (!(m instanceof General)) l.add(m);
        l.sort(Comparator.comparing(m -> m.name.toLowerCase(Locale.ROOT)));
        list = l.toArray(new Module[0]);
        ms = new ModState[list.length];
        for (int i = 0; i < list.length; i++) ms[i] = new ModState(list[i]);
        master.t = 1;
    }

    private int accent() { return 0xFF000000 | mods.general.accent.get(); }

    // ------------------------------------------------------------------ public API

    public void close() { closing = true; master.t = 0; }
    public boolean closed() { return closing && master.v < 0.01f; }

    /** Used by the preview tool: pre-open a module and skip entrance animations. */
    public void open(Module m) {
        for (ModState s : ms) if (s.m == m) { s.open.snap(1); s.m.settings.size(); expandedSet.add(m); }
    }
    public void settle() {
        master.snap(1);
        for (Panel p : order) { p.appear.snap(1); p.expand.snap(p.collapsed ? 0 : 1); }
    }
    private final Set<Module> expandedSet = new HashSet<>();

    // ------------------------------------------------------------------ frame

    /** dt in seconds. */
    public void render(Gfx g, int mx, int my, float dt, int screenW, int screenH) {
        sw = screenW; sh = screenH;
        if (!laidOut) layout();
        step(dt, mx, my);
        hn = 0;

        float ma = master.v;
        for (Panel p : order) {
            float pa = p.appear.v * ma;
            if (pa <= 0.004f) continue;
            g.alpha(pa);
            float ox = p.x, oy = p.y + (1f - p.appear.v) * 7f;
            if (p == sidebar) drawSidebar(g, p, ox, oy, mx, my);
            else drawPanel(g, p, ox, oy, mx, my);
        }
        g.alpha(ma);
        drawTooltip(g, mx, my);
        g.alpha(1f);
    }

    private void layout() {
        laidOut = true;
        float x = MARGIN;
        sidebar.x = x; sidebar.y = MARGIN;
        x += PW + GAP;
        for (Panel p : new Panel[] {render, settings, profiles}) {
            p.x = x; p.y = MARGIN;
            x += PW + GAP;
            float[] saved = mods.guiLayout.get(p.id);
            if (saved != null) { p.x = saved[0]; p.y = saved[1]; p.collapsed = saved[2] > 0.5f; }
            p.expand.snap(p.collapsed ? 0 : 1);
        }
        int i = 0;
        for (Panel p : order) { p.delay = i++ * 0.045f; p.appear.snap(0); }
        for (ModState s : ms) if (expandedSet.contains(s.m)) s.open.snap(1);
    }

    private float time;

    private void step(float dt, int mx, int my) {
        time += dt;
        float kFast = 1f - (float) Math.exp(-dt * 26f);
        float kMed = 1f - (float) Math.exp(-dt * 16f);
        float kSlow = 1f - (float) Math.exp(-dt * 10f);

        master.step(kMed);
        for (Panel p : order) {
            p.appear.t = time >= p.delay ? 1f : 0f;
            p.appear.step(kSlow);
            p.expand.t = p.collapsed ? 0f : 1f;
            p.expand.step(kMed);
            p.scroll.step(kFast);
        }
        hoverPanel = null;
        for (int i = order.length - 1; i >= 0; i--) {
            Panel p = order[i];
            if (mx >= p.x && mx < p.x + PW && my >= p.y && my < p.y + Math.max(p.h, HEAD)) { hoverPanel = p; break; }
        }

        for (ModState s : ms) {
            s.on.t = s.m.enabled() ? 1f : 0f;
            s.on.step(kMed);
            s.open.t = expandedSet.contains(s.m) ? 1f : 0f;
            s.open.step(kMed);
            s.hover.step(kFast);
        }
        for (SetState st : sstate.values()) { st.on.step(kMed); st.val.step(kFast); }

        // tooltip: appears after a short hover, fades quickly
        boolean showing = tipFor != null && hoverTime > 0.35f;
        tip.t = showing ? 1f : 0f;
        tip.step(kMed);
        if (tipFor != null) hoverTime += dt; else hoverTime = 0;
    }

    // ------------------------------------------------------------------ panels

    private void panelFrame(Gfx g, Panel p, float x, float y, float h, int bg) {
        g.round(x - 1, y, PW + 2, h + 2, PR + 1, 0x30000000);     // soft contact shadow
        g.round(x, y, PW, h, PR, bg);
    }

    private void drawSidebar(Gfx g, Panel p, float x, float y, int mx, int my) {
        int acc = accent();
        final int bg = 0xFF141414;
        float h = 20 + 2 * ROW + 15 + ROW + 19;
        p.h = h;
        panelFrame(g, p, x, y, h, bg);
        add(T_PANEL, x, y, PW, h, null, 0, p);

        // wordmark + search / settings
        g.text("lock", x + 8, y + 5.5f, 0xFFFFFFFF, 1.45f);
        g.text(".", x + 8 + g.width("lock", 1.45f), y + 5.5f, acc, 1.45f);
        Icons.search(g, x + PW - 30, y + 5.5f, Theme.DIM);
        Icons.gear(g, x + PW - 17, y + 5.5f, Theme.DIM);

        float iy = y + 20;
        String[] names = {"Render", "Settings"};
        for (int i = 0; i < 2; i++) {
            boolean hov = hoverPanel == p && inside(mx, my, x, iy, PW, ROW);
            if (hov) g.rect(x, iy, PW, ROW, Theme.HOVER);
            if (i == 0) Icons.eye(g, x + 9, iy + 6, acc);
            else Icons.gear(g, x + 9, iy + 6, acc);
            g.text(names[i], x + 24, iy + 6.5f, acc, 1f);
            Icons.chevron(g, x + PW - 10, iy + 8, 2, Theme.DIM);
            iy += ROW;
        }

        g.rect(x, iy, PW, 15, Theme.STRIP);
        g.text("MISC", x + 8, iy + 5.3f, 0xFF5E5E5E, 0.62f);
        iy += 15;

        boolean hov = hoverPanel == p && inside(mx, my, x, iy, PW, ROW);
        if (hov) g.rect(x, iy, PW, ROW, Theme.HOVER);
        g.text("Profiles", x + 8, iy + 6.5f, acc, 1f);
        String chip = "default";
        float cw = g.width(chip, 0.7f) + 9;
        g.round(x + PW - cw - 19, iy + 5, cw, 11, 3, Theme.CHIP);
        g.text(chip, x + PW - cw - 14.5f, iy + 7.8f, Theme.DIM, 0.7f);
        Icons.chevron(g, x + PW - 10, iy + 8, 2, Theme.DIM);
        iy += ROW;

        g.roundEx(x, iy, PW, 19, 0, 0, PR, PR, 0xFF111111);
        Icons.user(g, x + 8, iy + 5.5f, Theme.DIM);
        Icons.star(g, x + PW - 33, iy + 5.5f, Theme.DIM);
        Icons.bars(g, x + PW - 19, iy + 5.5f, Theme.DIM);
    }

    private void drawPanel(Gfx g, Panel p, float x, float y, int mx, int my) {
        int acc = accent();
        float bodyTop = y + HEAD;
        float contentH = p == render ? renderContentH() : p == settings ? settingsContentH() : profilesContentH();
        p.contentH = contentH;
        float maxView = Math.max(40, sh - bodyTop - MARGIN);
        p.viewH = Math.min(contentH, maxView);
        float maxScroll = Math.max(0, contentH - p.viewH);
        if (p.scroll.t > maxScroll) p.scroll.t = maxScroll;
        if (p.scroll.t < 0) p.scroll.t = 0;
        float visible = p.viewH * p.expand.v;
        float h = HEAD + visible;
        p.h = h;

        panelFrame(g, p, x, y, h, 0xFF0F0F0F);
        add(T_PANEL, x, y, PW, h, null, 0, p);

        // header
        switch (p.id) {
            case "render" -> Icons.eye(g, x + 9, y + 5, Theme.TEXT);
            case "settings" -> Icons.gear(g, x + 8, y + 5, Theme.TEXT);
            default -> Icons.list(g, x + 8.5f, y + 5, Theme.TEXT);
        }
        g.text(p.title, x + 22, y + 6, Theme.TEXT, 1f);
        float cx = x + PW - 12;
        if (p == profiles) Icons.gear(g, cx - 19, y + 5, Theme.DIM);
        // chevron rotates to "down" as the panel collapses
        Icons.chevron(g, cx, y + 8f, p.expand.v > 0.5f ? -1 : 1, hoverPanel == p && inside(mx, my, cx - 6, y, 18, HEAD) ? Theme.TEXT : Theme.DIM);
        add(T_DRAG, x, y, PW - 24, HEAD, p, 0, p);
        add(T_COLLAPSE, x + PW - 24, y, 24, HEAD, p, 0, p);

        if (visible < 0.5f) return;
        g.clip(x, bodyTop, PW, visible);
        float cy = bodyTop - p.scroll.v;
        float clipT = bodyTop, clipB = bodyTop + visible;
        switch (p.id) {
            case "render" -> drawRenderBody(g, p, x, cy, clipT, clipB, mx, my, acc);
            case "settings" -> {
                cy += 3;
                for (Setting<?> s : mods.general.settings) cy += drawSetting(g, p, s, x, cy, clipT, clipB, mx, my, acc, 0xFF0F0F0F);
            }
            default -> drawProfilesBody(g, p, x, cy, acc);
        }
        g.unclip();

        if (maxScroll > 0) {
            float trackH = visible - 4, th = Math.max(10, trackH * p.viewH / contentH);
            float ty = bodyTop + 2 + (trackH - th) * (p.scroll.v / maxScroll);
            g.round(x + PW - 2.5f, ty, 1.5f, th, 0.75f, 0x40FFFFFF);
        }
    }

    // ---- render panel (module list) ----
    private float renderContentH() {
        float h = 0;
        for (ModState s : ms) h += ROW + settingsHeight(s.m) * s.open.v;
        return h;
    }

    private void drawRenderBody(Gfx g, Panel p, float x, float y, float clipT, float clipB, int mx, int my, int acc) {
        Module newTip = null;
        for (int i = 0; i < ms.length; i++) {
            ModState s = ms[i];
            Module m = s.m;
            float sh2 = settingsHeight(m) * s.open.v;
            float rowH = ROW + sh2;
            if (y + rowH < clipT) { y += rowH; continue; }
            if (y > clipB) break;

            boolean last = i == ms.length - 1;
            float br = last && s.open.v < 0.02f ? PR : 0;
            boolean hov = hoverPanel == p && inside(mx, my, x, Math.max(y, clipT), PW, Math.min(y + ROW, clipB) - Math.max(y, clipT))
                && my >= y && my < y + ROW;
            s.hover.t = hov ? 1f : 0f;
            if (hov) newTip = m;

            float on = s.on.v, open = s.open.v, hv = s.hover.v;
            float white = open * (1f - on);
            int base = Theme.mix(0xFF0F0F0F, Theme.HOVER, hv);
            base = Theme.mix(base, Theme.WHITE_ROW, white);
            if (base != 0xFF0F0F0F) g.roundEx(x, y, PW, ROW, 0, 0, br, br, base);
            if (on > 0.004f) g.roundEx(x, y, PW, ROW, 0, 0, br, br, Theme.alpha(acc, on));

            float ink = Math.max(on, white);
            g.text(m.name, x + 7, y + 6.5f, Theme.mix(Theme.TEXT, Theme.DARK, ink), 1f);

            // right cluster: star + keybind reveal on hover, kebab always
            int dotC = Theme.mix(0xFFD2D2D2, Theme.DARK, ink);
            float reveal = Math.max(hv, open);
            if (reveal > 0.01f) {
                int c = Theme.alpha(Theme.mix(0xFFB4B4B4, 0xFF3A3A3A, ink), reveal);
                Icons.star(g, x + PW - 39, y + 6, c);
                Icons.keybind(g, x + PW - 27, y + 5, c);
            }
            Icons.dots(g, x + PW - 10, y + 6.8f, dotC);

            add(T_OPEN, x + PW - 17, y, 17, ROW, m, 0, p, clipT, clipB);
            add(T_TOGGLE, x, y, PW - 17, ROW, m, 0, p, clipT, clipB);
            y += ROW;

            if (sh2 > 0.5f) {
                g.roundEx(x, y, PW, sh2, 0, 0, last ? PR : 0, last ? PR : 0, Theme.INSET);
                g.clip(x, Math.max(y, clipT), PW, Math.min(y + sh2, clipB) - Math.max(y, clipT));
                float sy = y + 3;
                for (Setting<?> st : m.settings) sy += drawSetting(g, p, st, x, sy, Math.max(y, clipT), Math.min(y + sh2, clipB), mx, my, acc, Theme.INSET);
                g.unclip();
                y += sh2;
            }
        }
        if (newTip != tipFor) { tipFor = newTip; hoverTime = 0; }
        else if (newTip == null) tipFor = null;
    }

    // ---- settings rows ----
    private static float settingHeight(Setting<?> s) {
        if (s instanceof NumSetting) return 17;
        if (s instanceof ModeSetting) return 16;
        if (s instanceof ColorSetting) return 14;
        return 13;
    }

    private float settingsHeight(Module m) {
        float h = 6;
        for (Setting<?> s : m.settings) h += settingHeight(s);
        return h;
    }

    private float settingsContentH() {
        float h = 6;
        for (Setting<?> s : mods.general.settings) h += settingHeight(s);
        return h;
    }

    private SetState state(Setting<?> s) {
        SetState st = sstate.get(s);
        if (st == null) { st = new SetState(); sstate.put(s, st); }
        return st;
    }

    private float drawSetting(Gfx g, Panel p, Setting<?> s, float x, float y, float clipT, float clipB, int mx, int my, int acc, int bg) {
        float h = settingHeight(s);
        if (y + h < clipT || y > clipB) return h;
        float lx = x + 8, rx = x + PW - 8;
        boolean hov = hoverPanel == p && inside(mx, my, x, Math.max(y, clipT), PW, Math.min(y + h, clipB) - Math.max(y, clipT)) && my >= y && my < y + h;
        int label = hov ? Theme.TEXT : Theme.LABEL;
        SetState st = state(s);

        if (s instanceof BoolSetting b) {
            if (!st.init) { st.on.snap(b.get() ? 1 : 0); st.init = true; }
            st.on.t = b.get() ? 1 : 0;
            g.text(s.name, lx, y + 3.2f, label, LABEL);
            float tx = rx - 10, ty = y + 3.5f, a = st.on.v;
            g.round(tx, ty, 10, 6, 3, Theme.mix(Theme.TRACK, acc, a));
            g.round(tx + 1 + 4 * a, ty + 1, 4, 4, 2, Theme.mix(0xFF686868, 0xFFFFFFFF, a));
            add(T_BOOL, x, y, PW, h, b, 0, p, clipT, clipB);
        } else if (s instanceof ModeSetting m) {
            g.round(lx - 2, y + 1.5f, PW - 12, 11, 3, hov ? 0xFF202020 : Theme.FIELD);
            g.text(m.name + " - " + m.get(), lx + 3, y + 4.2f, label, LABEL);
            Icons.chevron(g, rx - 7, y + 5.2f, 1, Theme.DIM);
            add(T_MODE, x, y, PW, h, m, 0, p, clipT, clipB);
        } else if (s instanceof NumSetting n) {
            double v = n.get();
            if (!st.init) { st.val.snap(frac(n)); st.init = true; }
            st.val.t = frac(n);
            if (v != st.fmtVal) { st.fmtVal = v; st.fmt = n.step >= 1 ? Integer.toString(n.i()) : String.format(Locale.ROOT, "%.2f", v); }
            g.text(s.name, lx, y + 2.2f, label, LABEL);
            g.text(st.fmt, rx - g.width(st.fmt, LABEL), y + 2.2f, Theme.TEXT, LABEL);
            float tw = PW - 16, ty = y + 12, f = st.val.v;
            g.round(lx, ty, tw, 1.6f, 0.8f, Theme.TRACK);
            g.round(lx, ty, Math.max(1.6f, tw * f), 1.6f, 0.8f, 0xFFE6E6E6);
            float kr = (dragNum == n) ? 3.1f : 2.6f;
            g.round(lx + tw * f - kr, ty + 0.8f - kr, kr * 2, kr * 2, kr, 0xFFFFFFFF);
            add(T_SLIDER, lx - 3, y + 5, tw + 6, h - 4, n, 0, p, clipT, clipB);
        } else if (s instanceof ColorSetting c) {
            g.text(s.name, lx, y + 3.2f, label, LABEL);
            int cnt = ColorSetting.PRESETS.length;
            for (int i = 0; i < cnt; i++) {
                float sx = rx - (cnt - i) * 8f + 2;
                int col = 0xFF000000 | ColorSetting.PRESETS[i];
                if (c.get() == ColorSetting.PRESETS[i]) g.round(sx - 1.4f, y + 2.6f, 8.8f, 8.8f, 4.4f, 0x44FFFFFF);
                g.round(sx, y + 4f, 6, 6, 3, col);
                add(T_COLOR, sx - 1, y, 8, h, c, i, p, clipT, clipB);
            }
        }
        return h;
    }

    private static float frac(NumSetting n) { return (float) ((n.get() - n.min) / (n.max - n.min)); }

    // ---- profiles (visual only) ----
    private float profilesContentH() { return 5 + 15 + 6 + 17 + 6; }

    private void drawProfilesBody(Gfx g, Panel p, float x, float y, int acc) {
        float bw = (PW - 14) / 2f;
        float by = y + 5;
        g.round(x + 5, by, bw, 15, 3, Theme.CHIP);
        Icons.plusCircle(g, x + 9, by + 3, acc, 0xFF0A0A0A);
        g.text("CREATE NEW", x + 21, by + 5.2f, Theme.TEXT, 0.58f);
        g.round(x + 9 + bw, by, bw, 15, 3, Theme.CHIP);
        Icons.globe(g, x + 13 + bw, by + 3, Theme.DIM);
        g.text("PUBLIC", x + 25 + bw, by + 5.2f, Theme.TEXT, 0.58f);

        float ry = by + 15 + 6;
        g.round(x + 5, ry, PW - 10, 17, 3, acc);
        g.text("default", x + 11, ry + 5, Theme.DARK, 1f);
        Icons.dots(g, x + PW - 14, ry + 3.3f, Theme.DARK);
    }

    // ---- tooltip ----
    private void drawTooltip(Gfx g, int mx, int my) {
        if (tipFor == null || tip.v < 0.02f) return;
        g.alpha(tip.v * master.v);
        String t = tipFor.desc;
        float s = 0.85f;
        float w = g.width(t, s) + 14, h = 14;
        float tx = Math.min(mx + 10, sw - w - 4), ty = Math.min(my + 14, sh - h - 4);
        float lift = (1f - tip.v) * 3f;
        g.round(tx, ty + lift, w, h, 3, Theme.TIP_BG);
        g.text(t, tx + 7, ty + lift + 4.4f, 0xFFF0F0F0, s);
    }

    // ------------------------------------------------------------------ input

    private void add(int type, float x, float y, float w, float h, Object a, int b, Panel p) {
        add(type, x, y, w, h, a, b, p, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    private void add(int type, float x, float y, float w, float h, Object a, int b, Panel p, float clipT, float clipB) {
        float y0 = Math.max(y, clipT), y1 = Math.min(y + h, clipB);
        if (y1 <= y0) return;
        if (hn == hx.length) grow();
        hx[hn] = x; hy[hn] = y0; hw[hn] = w; hh[hn] = y1 - y0; ht[hn] = type; ha[hn] = a; hb[hn] = b; hp[hn] = p;
        hn++;
    }

    private void grow() {
        int n = hn * 2;
        hx = Arrays.copyOf(hx, n); hy = Arrays.copyOf(hy, n); hw = Arrays.copyOf(hw, n); hh = Arrays.copyOf(hh, n);
        ht = Arrays.copyOf(ht, n); hb = Arrays.copyOf(hb, n); ha = Arrays.copyOf(ha, n); hp = Arrays.copyOf(hp, n);
    }

    public boolean click(double mx, double my, int button) {
        for (int i = hn - 1; i >= 0; i--) {
            if (mx < hx[i] || mx >= hx[i] + hw[i] || my < hy[i] || my >= hy[i] + hh[i]) continue;
            Panel p = hp[i];
            bringToFront(p);
            switch (ht[i]) {
                case T_DRAG -> {
                    Panel pp = (Panel) ha[i];
                    if (pp.draggable) { dragPanel = pp; dragDX = (float) mx - pp.x; dragDY = (float) my - pp.y; }
                }
                case T_COLLAPSE -> {
                    Panel pp = (Panel) ha[i];
                    pp.collapsed = !pp.collapsed;
                    saveLayout(pp);
                }
                case T_TOGGLE -> {
                    Module m = (Module) ha[i];
                    if (button == 1) toggleOpen(m); else m.toggle();
                }
                case T_OPEN -> toggleOpen((Module) ha[i]);
                case T_BOOL -> ((BoolSetting) ha[i]).toggle();
                case T_MODE -> ((ModeSetting) ha[i]).cycle();
                case T_COLOR -> ((ColorSetting) ha[i]).set(ColorSetting.PRESETS[hb[i]]);
                case T_SLIDER -> {
                    dragNum = (NumSetting) ha[i];
                    dragNumX = hx[i] + 3;
                    dragNumW = hw[i] - 6;
                    slide(mx);
                }
                default -> {}
            }
            return true;
        }
        return false;
    }

    private void toggleOpen(Module m) {
        if (!expandedSet.remove(m)) expandedSet.add(m);
    }

    private void bringToFront(Panel p) {
        if (p == null || p == sidebar) return;
        int idx = -1;
        for (int i = 0; i < order.length; i++) if (order[i] == p) idx = i;
        if (idx < 0 || idx == order.length - 1) return;
        System.arraycopy(order, idx + 1, order, idx, order.length - idx - 1);
        order[order.length - 1] = p;
    }

    public void drag(double mx, double my) {
        if (dragNum != null) slide(mx);
        if (dragPanel != null) {
            dragPanel.x = Math.max(2, Math.min(sw - PW - 2, (float) mx - dragDX));
            dragPanel.y = Math.max(2, Math.min(sh - HEAD - 2, (float) my - dragDY));
        }
    }

    public void release() {
        if (dragPanel != null) saveLayout(dragPanel);
        dragPanel = null;
        dragNum = null;
    }

    public void scroll(double mx, double my, double amount) {
        for (int i = order.length - 1; i >= 0; i--) {
            Panel p = order[i];
            if (p == sidebar) continue;
            if (mx >= p.x && mx < p.x + PW && my >= p.y && my < p.y + p.h) {
                float max = Math.max(0, p.contentH - p.viewH);
                p.scroll.t = Math.max(0, Math.min(max, p.scroll.t - (float) amount * 14f));
                return;
            }
        }
    }

    private void saveLayout(Panel p) {
        mods.guiLayout.put(p.id, new float[] {p.x, p.y, p.collapsed ? 1 : 0});
    }

    private void slide(double mx) {
        double f = Math.max(0, Math.min(1, (mx - dragNumX) / dragNumW));
        dragNum.set(dragNum.min + f * (dragNum.max - dragNum.min));
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
