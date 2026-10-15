package lock.gui;

import lock.module.General;
import lock.module.Module;
import lock.module.ModuleManager;
import lock.setting.*;

import java.util.*;

/**
 * Menu layout, animation, drawing and input.
 *
 * Structure follows a classic column click-GUI (sidebar + module columns), the look flat and dark like the
 * reference: solid near-black panels with no gradients or glass, thin 1px dividers for structure, flat solid
 * accent fills on active rows, and a horizontal named-scheme strip for colour settings. Animation is exponential
 * smoothing (frame-rate independent); the hot path allocates nothing - hit regions live in pooled arrays and
 * per-module / per-setting state is created once.
 */
public final class GuiView {
    static final int PW = 196, ROW = 23, HEAD = 25, GAP = 6, MARGIN = 8, PR = 4, IN = 5, SROW = 26, SIDEW = 132;
    static final int SCHEME_ROW = 40;
    static final float TITLE_SCALE = 0.92f, DESC_SCALE = 0.66f;

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
        final Anim on = new Anim(0), val = new Anim(0), hover = new Anim(0);
        String fmt = "", fit = "", title = "";
        float titleW = -1;
        double fmtVal = Double.NaN;
        float fitW = -1;
        boolean init;
    }

    private static final class Panel {
        final String id, title;
        final boolean draggable;
        float x, y, h, w = PW;
        boolean collapsed;
        final Anim expand = new Anim(1), appear = new Anim(0), scroll = new Anim(0);
        float contentH, viewH, delay;
        Panel(String id, String title, boolean draggable) { this.id = id; this.title = title; this.draggable = draggable; }
    }

    // ---- hit pool -----------------------------------------------------------
    private static final int T_PANEL = 0, T_DRAG = 1, T_COLLAPSE = 2, T_TOGGLE = 3, T_OPEN = 4, T_BOOL = 5, T_MODE = 6,
        T_SLIDER = 7, T_COLOR = 8, T_BIND_KEY = 9, T_BIND_MODE = 10, T_NOOP = 11;
    private float[] hx = new float[256], hy = new float[256], hw = new float[256], hh = new float[256];
    private int[] ht = new int[256], hb = new int[256];
    private Object[] ha = new Object[256];
    private Panel[] hp = new Panel[256];
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
    private final Set<Module> expandedSet = new HashSet<>();
    private final Anim master = new Anim(0);
    private final Anim tip = new Anim(0);
    private String tipText = "", tipCandidate = "";
    private float hoverTime;
    private boolean closing, laidOut;

    private Panel dragPanel;
    private float dragDX, dragDY;
    private NumSetting dragNum;
    private float dragNumX, dragNumW;
    private ColorSetting dragScheme;
    private float dragSchemeX, dragSchemeW;
    private Panel hoverPanel;
    private BindSetting listening;
    private int sw = 480, sh = 270;
    private float time;

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
    public boolean isListening() { return listening != null; }

    /** Used by the preview tool: pre-open a module and skip entrance animations. */
    public void open(Module m) {
        expandedSet.add(m);
        for (ModState s : ms) if (s.m == m) s.open.snap(1);
    }

    public void settle() {
        master.snap(1);
        for (Panel p : order) { p.appear.snap(1); p.expand.snap(p.collapsed ? 0 : 1); }
    }

    /** Key press while a bind row is waiting for input. Returns true if consumed. */
    public boolean key(int code) {
        if (listening == null) return false;
        if (code == 256) { listening = null; return true; }                       // ESC cancels
        listening.set(code == 259 || code == 261 ? -1 : code);                      // backspace/delete clears
        listening = null;
        return true;
    }

    // ------------------------------------------------------------------ frame

    /** dt in seconds. */
    public void render(Gfx g, int mx, int my, float dt, int screenW, int screenH) {
        sw = screenW; sh = screenH;
        if (!laidOut) layout();
        step(dt, mx, my);
        hn = 0;
        tipCandidate = "";

        float ma = master.v;
        for (Panel p : order) {
            float pa = p.appear.v * ma;
            if (pa <= 0.004f) continue;
            g.alpha(pa);
            float ox = p.x, oy = p.y + (1f - p.appear.v) * 8f;
            if (p == sidebar) drawSidebar(g, p, ox, oy, mx, my);
            else drawPanel(g, p, ox, oy, mx, my);
        }
        g.alpha(ma);
        if (!tipCandidate.equals(tipText)) { tipText = tipCandidate; hoverTime = 0; }
        else if (tipText.isEmpty()) hoverTime = 0;
        drawTooltip(g, mx, my);
        g.alpha(1f);
    }

    private void layout() {
        laidOut = true;
        sidebar.w = SIDEW;
        float x = MARGIN;
        sidebar.x = x; sidebar.y = MARGIN;
        x += SIDEW + GAP;
        for (Panel p : new Panel[] {render, settings, profiles}) {
            p.x = x; p.y = MARGIN;
            x += PW + GAP;
            float[] saved = mods.guiLayout.get(p.id);
            if (saved != null) { p.x = saved[0]; p.y = saved[1]; p.collapsed = saved[2] > 0.5f; }
            p.expand.snap(p.collapsed ? 0 : 1);
        }
        int i = 0;
        for (Panel p : order) { p.delay = i++ * 0.05f; p.appear.snap(0); }
        for (ModState s : ms) if (expandedSet.contains(s.m)) s.open.snap(1);
    }

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
            if (mx >= p.x && mx < p.x + p.w && my >= p.y && my < p.y + Math.max(p.h, HEAD)) { hoverPanel = p; break; }
        }
        for (ModState s : ms) {
            s.on.t = s.m.enabled() ? 1f : 0f;
            s.on.step(kMed);
            s.open.t = expandedSet.contains(s.m) ? 1f : 0f;
            s.open.step(kMed);
            s.hover.step(kFast);
        }
        for (SetState st : sstate.values()) { st.on.step(kMed); st.val.step(kFast); st.hover.step(kFast); }
        tip.t = (!tipText.isEmpty() && hoverTime > 0.45f) ? 1f : 0f;
        tip.step(kMed);
        if (!tipText.isEmpty()) hoverTime += dt;
    }

    // ------------------------------------------------------------------ chrome

    /** Flat panel: a soft contact shadow, a 1px edge, and a solid fill - no gradients. */
    private void frame(Gfx g, float x, float y, float w, float h, int fill) {
        g.round(x - 2, y + 1, w + 4, h + 4, PR + 2, 0x1A000000);
        g.round(x, y, w, h, PR, fill);
    }

    private void drawSidebar(Gfx g, Panel p, float x, float y, int mx, int my) {
        int acc = accent();
        float w = p.w;
        float h = 26 + 2 * ROW + 16 + ROW + 22;
        p.h = h;
        frame(g, x, y, w, h, Theme.SIDEBAR);
        add(T_PANEL, x, y, w, h, null, 0, p);

        g.text("lock", x + 10, y + 6.5f, 0xFFFFFFFF, 1.5f, true);
        g.text(".", x + 10 + g.width("lock", 1.5f, true), y + 6.5f, acc, 1.5f, true);
        Icons.search(g, x + w - 34, y + 8, Theme.DIM);
        Icons.gear(g, x + w - 19, y + 8, Theme.DIM);
        g.rect(x + 8, y + 25, w - 16, 1, Theme.DIVIDER);

        float iy = y + 28;
        String[] names = {"Render", "Settings"};
        for (int i = 0; i < 2; i++) {
            boolean hov = hoverPanel == p && inside(mx, my, x, iy, w, ROW);
            if (hov) g.rect(x, iy, w, ROW, Theme.ROW_HOVER);
            if (i == 0) Icons.eye(g, x + 12, iy + 7, acc); else Icons.gear(g, x + 12, iy + 7, acc);
            g.text(names[i], x + 30, iy + 7, acc, 0.95f, true);
            Icons.chevron(g, x + w - 14, iy + 9, 2, Theme.DIM);
            iy += ROW;
        }

        g.rect(x, iy, w, 14, 0xFF0D0D0D);
        g.text("MISC", x + 12, iy + 5.5f, Theme.DIM, 0.6f, true);
        iy += 16;

        boolean hov = hoverPanel == p && inside(mx, my, x, iy, w, ROW);
        if (hov) g.rect(x, iy, w, ROW, Theme.ROW_HOVER);
        g.text("Profiles", x + 12, iy + 7, acc, 0.95f, true);
        String chip = "default";
        float cw = g.width(chip, 0.7f) + 10;
        g.round(x + w - cw - 24, iy + 5.5f, cw, 12, 3, Theme.CHIP);
        g.text(chip, x + w - cw - 18, iy + 8.7f, Theme.LABEL, 0.7f);
        Icons.chevron(g, x + w - 14, iy + 9, 2, Theme.DIM);
        iy += ROW;

        g.rect(x + 8, iy, w - 16, 1, Theme.DIVIDER);
        Icons.user(g, x + 12, iy + 7, Theme.DIM);
        Icons.star(g, x + w - 38, iy + 7, Theme.DIM);
        Icons.bars(g, x + w - 22, iy + 7, Theme.DIM);
    }

    private void header(Gfx g, Panel p, float x, float y, int acc, int mx, int my) {
        g.roundEx(x, y, p.w, HEAD, PR, PR, 0, 0, Theme.HEADER);
        g.rect(x, y + HEAD - 1, p.w, 1, Theme.DIVIDER);
        switch (p.id) {
            case "render" -> Icons.eye(g, x + 11, y + 8, Theme.TITLE);
            case "settings" -> Icons.gear(g, x + 10, y + 8, Theme.TITLE);
            default -> Icons.list(g, x + 10, y + 8, Theme.TITLE);
        }
        g.text(p.title, x + 27, y + 8, Theme.TITLE, 0.98f, true);
        float cx = x + p.w - 16;
        if (p == profiles) Icons.gear(g, cx - 22, y + 8, Theme.DIM);
        boolean over = hoverPanel == p && inside(mx, my, cx - 8, y, 24, HEAD);
        Icons.chevron(g, cx, y + 10f, p.expand.v > 0.5f ? -1 : 1, over ? Theme.TITLE : Theme.DIM);
    }

    private void drawPanel(Gfx g, Panel p, float x, float y, int mx, int my) {
        int acc = accent();
        float bodyTop = y + HEAD;
        float contentH = p == render ? renderContentH() : p == settings ? settingsContentH() : profilesContentH();
        p.contentH = contentH;
        float maxView = Math.max(60, sh - bodyTop - MARGIN);
        p.viewH = Math.min(contentH, maxView);
        float maxScroll = Math.max(0, contentH - p.viewH);
        if (p.scroll.t > maxScroll) p.scroll.t = maxScroll;
        if (p.scroll.t < 0) p.scroll.t = 0;
        float visible = p.viewH * p.expand.v;
        float h = HEAD + visible;
        p.h = h;

        frame(g, x, y, p.w, h, Theme.PANEL);
        add(T_PANEL, x, y, p.w, h, null, 0, p);
        header(g, p, x, y, acc, mx, my);
        add(T_DRAG, x, y, p.w - 28, HEAD, p, 0, p);
        add(T_COLLAPSE, x + p.w - 28, y, 28, HEAD, p, 0, p);

        if (visible < 0.5f) return;
        g.clip(x + 1, bodyTop, p.w - 2, visible + 3);
        float cy = bodyTop - p.scroll.v + 2;
        float clipT = bodyTop, clipB = bodyTop + visible;
        switch (p.id) {
            case "render" -> drawRenderBody(g, p, x, cy, clipT, clipB, mx, my, acc);
            case "settings" -> {
                for (Setting<?> s : mods.general.settings) {
                    if (s instanceof BindSetting) continue;
                    cy += drawSetting(g, p, s, x, cy, clipT, clipB, mx, my, acc, false);
                }
            }
            default -> drawProfilesBody(g, p, x, cy, acc);
        }
        g.unclip();

        if (maxScroll > 0) {
            float trackH = visible - 6, th = Math.max(12, trackH * p.viewH / contentH);
            float ty = bodyTop + 3 + (trackH - th) * (p.scroll.v / maxScroll);
            g.round(x + p.w - 3.5f, ty, 2, th, 1, Theme.alpha(acc, 0.45f));
        }
    }

    // ---- render panel (module list) ----
    private float renderContentH() {
        float h = 4;
        for (ModState s : ms) h += ROW + settingsHeight(s.m) * s.open.v;
        return h;
    }

    private void drawRenderBody(Gfx g, Panel p, float x, float y, float clipT, float clipB, int mx, int my, int acc) {
        String newTip = "";
        for (int i = 0; i < ms.length; i++) {
            ModState s = ms[i];
            Module m = s.m;
            float sh2 = settingsHeight(m) * s.open.v;
            float rowH = ROW + sh2;
            if (y + rowH < clipT) { y += rowH; continue; }
            if (y > clipB) break;

            boolean hov = hoverPanel == p && inside(mx, my, x, Math.max(y, clipT), p.w, Math.min(y + ROW, clipB) - Math.max(y, clipT))
                && my >= y && my < y + ROW;
            s.hover.t = hov ? 1f : 0f;
            if (hov) newTip = m.desc;

            float on = s.on.v, open = s.open.v, hv = s.hover.v;
            if (hv > 0.01f) g.rect(x, y, p.w, ROW, Theme.alpha(Theme.ROW_HOVER, hv));
            if (on > 0.004f) g.rect(x, y, p.w, ROW, Theme.alpha(acc, on));
            if (on > 0.004f) g.rect(x, y, 2.5f, ROW, Theme.alpha(Theme.brighten(acc, 0.3f), on));

            int ink = Theme.mix(Theme.TEXT, Theme.inkOn(acc), on);
            drawModuleIcon(g, m.name, x + IN + 2, y + 6, Theme.mix(0xFF707070, ink, on));
            g.text(m.name, x + IN + 15, y + 7.5f, ink, 0.96f, on > 0.5f);
            int dotC = Theme.mix(0xFF7A7A7A, ink, on);
            float reveal = Math.max(hv, open * 0.8f);
            if (reveal > 0.01f) {
                int c = Theme.mix(0xFF7A7A7A, ink, on);
                Icons.star(g, x + p.w - 46, y + 7, Theme.alpha(c, reveal));
                Icons.keybind(g, x + p.w - 33, y + 6.5f, Theme.alpha(c, reveal));
            }
            Icons.dots(g, x + p.w - 15, y + 7.5f, dotC);

            add(T_OPEN, x + p.w - 22, y, 22, ROW, m, 0, p, clipT, clipB);
            add(T_TOGGLE, x, y, p.w - 22, ROW, m, 0, p, clipT, clipB);
            y += ROW;

            if (sh2 > 0.5f) {
                float top = y, bot = y + sh2;
                g.rect(x, top, p.w, sh2, Theme.INSET);
                g.clip(x, Math.max(top, clipT), p.w, Math.min(bot, clipB) - Math.max(top, clipT));
                float sy = y + 2;
                for (Setting<?> st : m.settings) sy += drawSetting(g, p, st, x, sy, Math.max(top, clipT), Math.min(bot, clipB), mx, my, acc, true);
                g.unclip();
                y += sh2;
            }
        }
        tipCandidate = newTip.isEmpty() ? tipCandidate : newTip;
    }

    /** Per-module icon, matching the reference module list's icon-per-row treatment. */
    private static void drawModuleIcon(Gfx g, String name, float x, float y, int c) {
        switch (name) {
            case "NameTags" -> Icons.nametag(g, x, y, c);
            case "Tracers" -> Icons.tracerLine(g, x, y, c);
            case "Fullbright" -> Icons.sun(g, x, y, c);
            case "StorageESP", "ItemESP" -> Icons.box(g, x, y, c);
            case "BaseFinder" -> Icons.grid(g, x + 1, y + 1, c);
            case "Ambience" -> Icons.globe(g, x, y, c);
            case "Settings" -> Icons.gear(g, x, y, c);
            default -> Icons.person(g, x, y, c);
        }
    }

    // ---- settings rows ----
    private static float settingHeight(Setting<?> s) { return s instanceof ColorSetting ? SCHEME_ROW : SROW; }

    private float settingsHeight(Module m) {
        float h = 6;
        for (Setting<?> s : m.settings) h += settingHeight(s);
        return h;
    }

    private float settingsContentH() {
        float h = 8;
        for (Setting<?> s : mods.general.settings) if (!(s instanceof BindSetting)) h += settingHeight(s);
        return h;
    }

    private SetState state(Setting<?> s) {
        SetState st = sstate.get(s);
        if (st == null) { st = new SetState(); sstate.put(s, st); }
        return st;
    }

    /** One setting row. {@code x}/width: the row spans the panel (or the inset block inside a module). */
    private float drawSetting(Gfx g, Panel p, Setting<?> s, float x, float y, float clipT, float clipB, int mx, int my, int acc, boolean inset) {
        float h = settingHeight(s);
        if (y + h < clipT || y > clipB) return h;
        float w = p.w;
        float lx = x + 9, rx = x + w - 9;
        boolean hov = hoverPanel == p && inside(mx, my, x, Math.max(y, clipT), w, Math.min(y + h, clipB) - Math.max(y, clipT)) && my >= y && my < y + h;
        SetState st = state(s);
        st.hover.t = hov ? 1f : 0f;

        // hairline between rows
        g.rect(x + 8, y + h - 1, w - 16, 1, Theme.DIVIDER);

        if (s instanceof ColorSetting c) {
            drawScheme(g, c, x, y, w, h, hov, clipT, clipB, p);
            return h;
        }

        float cw = controlWidth(s);
        float textMax = w - 18 - cw - 8;
        boolean hasDesc = !s.desc.isEmpty();
        float ty = hasDesc ? y + 5 : y + 9.5f;
        if (st.titleW != textMax) { st.title = fitBold(g, s.name, TITLE_SCALE, textMax); st.titleW = textMax; }
        g.text(st.title, lx, ty, Theme.mix(Theme.TEXT, Theme.TITLE, st.hover.v), TITLE_SCALE, true);
        if (hasDesc) {
            if (st.fitW != textMax) { st.fit = fit(g, s.desc, DESC_SCALE, textMax); st.fitW = textMax; }
            g.text(st.fit, lx, y + 16, Theme.DESC, DESC_SCALE);
            if (hov && !st.fit.equals(s.desc)) tipCandidate = s.desc;
        }

        float cy = y + (h - 1) / 2f;     // control centre line
        if (s instanceof BoolSetting b) {
            if (!st.init) { st.on.snap(b.get() ? 1 : 0); st.init = true; }
            st.on.t = b.get() ? 1 : 0;
            float a = st.on.v, tw = 21, th = 11, tx = rx - tw, tyy = cy - th / 2;
            g.round(tx, tyy, tw, th, 5.5f, Theme.mix(Theme.TOGGLE_OFF, acc, a));
            g.round(tx + 1.5f + 10 * a, tyy + 1.5f, 8, 8, 4, 0xFFFFFFFF);
            add(T_BOOL, x, y, w, h - 1, b, 0, p, clipT, clipB);
        } else if (s instanceof ModeSetting m) {
            float bw = cw, bx = rx - bw;
            field(g, bx, cy - 7, bw, 14, hov);
            g.text(m.get(), bx + 6, cy - 3.6f, Theme.TEXT, 0.82f);
            Icons.chevron(g, bx + bw - 10, cy - 1.2f, 1, Theme.DIM);
            add(T_MODE, bx, y, bw, h - 1, m, 0, p, clipT, clipB);
        } else if (s instanceof NumSetting n) {
            double v = n.get();
            if (!st.init) { st.val.snap(frac(n)); st.init = true; }
            st.val.t = frac(n);
            if (v != st.fmtVal) { st.fmtVal = v; st.fmt = n.step >= 1 ? Integer.toString(n.i()) : String.format(Locale.ROOT, "%.2f", v); }
            float bw = 30, bx = rx - bw;
            field(g, bx, cy - 7, bw, 14, false);
            g.text(st.fmt, bx + (bw - g.width(st.fmt, 0.85f)) / 2f, cy - 3.8f, Theme.TEXT, 0.85f);
            float tw = 40, tx = bx - 8 - tw, f = st.val.v;
            g.round(tx, cy - 1.5f, tw, 3, 1.5f, Theme.TRACK);
            g.round(tx, cy - 1.5f, Math.max(3, tw * f), 3, 1.5f, acc);
            float kr = dragNum == n ? 5.2f : 4.2f;
            g.round(tx + tw * f - kr, cy - kr, kr * 2, kr * 2, kr, 0xFFFFFFFF);
            add(T_SLIDER, tx - 4, y + 2, tw + 8, h - 4, n, 0, p, clipT, clipB);
        } else if (s instanceof BindSetting b) {
            float kw = 36, kx = rx - kw;
            boolean wait = listening == b;
            field(g, kx, cy - 7, kw, 14, wait);
            String label = wait ? "..." : b.keyName();
            g.text(label, kx + (kw - g.width(label, 0.82f)) / 2f, cy - 3.6f, wait ? acc : Theme.TEXT, 0.82f);
            add(T_BIND_KEY, kx, y, kw, h - 1, b, 0, p, clipT, clipB);
            // hold / toggle segmented control
            float sw2 = 44, sx = kx - 7 - sw2;
            g.round(sx, cy - 6.5f, sw2, 13, 6.5f, Theme.FIELD);
            float segX = b.hold ? sx + 1 : sx + 1 + (sw2 - 2) / 2f;
            g.round(segX, cy - 5.5f, (sw2 - 2) / 2f, 11, 5.5f, Theme.CHIP);
            g.text("Hold", sx + 5, cy - 3.2f, b.hold ? Theme.TITLE : Theme.DIM, 0.62f);
            g.text("Toggle", sx + 24.5f, cy - 3.2f, b.hold ? Theme.DIM : Theme.TITLE, 0.62f);
            add(T_BIND_MODE, sx, y, sw2, h - 1, b, 0, p, clipT, clipB);
        }
        return h;
    }

    /** Full-width named-scheme strip: eight flat swatches forming one bar, a white thumb over the selected one. */
    private void drawScheme(Gfx g, ColorSetting c, float x, float y, float w, float h, boolean hov, float clipT, float clipB, Panel p) {
        float lx = x + 9, rx = x + w - 9;
        int idx = Theme.schemeIndex(c.get());
        if (idx < 0) idx = 0;
        g.text(c.name, lx, y + 6, Theme.mix(Theme.TEXT, Theme.TITLE, hov ? 1 : 0), TITLE_SCALE, true);
        String sn = Theme.SCHEME_NAMES[idx];
        g.text(sn, rx - g.width(sn, 0.78f), y + 6.6f, Theme.LABEL, 0.78f);

        float barY = y + 22, barH = 8, barX = lx, barW = rx - lx;
        int n = Theme.SCHEME_COLORS.length;
        float seg = barW / n;
        g.round(barX, barY, barW, barH, barH / 2, 0xFF000000);
        for (int i = 0; i < n; i++) {
            float sx = barX + i * seg;
            float tl = i == 0 ? barH / 2 : 0, bl = i == 0 ? barH / 2 : 0, tr = i == n - 1 ? barH / 2 : 0, br = i == n - 1 ? barH / 2 : 0;
            g.roundEx(sx + (i == 0 ? 0 : -0.5f), barY + 1, seg + (i == 0 || i == n - 1 ? 0.5f : 1f), barH - 2, tl, tr, br, bl, 0xFF000000 | Theme.SCHEME_COLORS[i]);
        }
        float thumbCx = barX + (idx + 0.5f) * seg;
        float tr2 = barH / 2f + 2.5f;
        g.round(thumbCx - tr2, barY + barH / 2f - tr2, tr2 * 2, tr2 * 2, tr2, 0xFFFFFFFF);
        g.round(thumbCx - tr2 + 2, barY + barH / 2f - tr2 + 2, tr2 * 2 - 4, tr2 * 2 - 4, tr2 - 2, 0xFF000000 | Theme.SCHEME_COLORS[idx]);

        add(T_COLOR, barX - 2, barY - 4, barW + 4, barH + 8, c, 0, p, clipT, clipB);
    }

    private void field(Gfx g, float x, float y, float w, float h, boolean hot) {
        g.round(x, y, w, h, 3, hot ? Theme.FIELD_HOVER : Theme.FIELD);
    }

    private float controlWidth(Setting<?> s) {
        if (s instanceof BoolSetting) return 21;
        if (s instanceof ModeSetting) return 64;
        if (s instanceof NumSetting) return 78;
        if (s instanceof BindSetting) return 87;
        return 40;
    }

    private static float frac(NumSetting n) { return (float) ((n.get() - n.min) / (n.max - n.min)); }

    private static String fitBold(Gfx g, String s, float scale, float max) {
        if (g.width(s, scale, true) <= max) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (g.width(s.substring(0, mid) + "...", scale, true) <= max) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, Math.max(0, lo)).stripTrailing() + "...";
    }

    private static String fit(Gfx g, String s, float scale, float max) {
        if (g.width(s, scale) <= max) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (g.width(s.substring(0, mid) + "...", scale) <= max) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, Math.max(0, lo)).stripTrailing() + "...";
    }

    // ---- profiles (visual only) ----
    private float profilesContentH() { return 7 + 17 + 7 + 19 + 8; }

    private void drawProfilesBody(Gfx g, Panel p, float x, float y, int acc) {
        float bw = (p.w - 20) / 2f;
        float by = y + 7;
        g.round(x + 8, by, bw, 17, 3, Theme.FIELD);
        Icons.plusCircle(g, x + 14, by + 4, acc, Theme.inkOn(acc));
        g.text("CREATE NEW", x + 27, by + 5.6f, Theme.TEXT, 0.6f, true);
        g.round(x + 12 + bw, by, bw, 17, 3, Theme.FIELD);
        Icons.globe(g, x + 18 + bw, by + 4, Theme.DIM);
        g.text("PUBLIC", x + 32 + bw, by + 5.6f, Theme.TEXT, 0.6f, true);

        float ry = by + 17 + 7;
        g.round(x + 8, ry, p.w - 16, 21, 3, acc);
        g.text("default", x + 16, ry + 5.8f, Theme.inkOn(acc), 0.98f, true);
        Icons.dots(g, x + p.w - 21, ry + 5.5f, Theme.inkOn(acc));
    }

    // ---- tooltip ----
    private void drawTooltip(Gfx g, int mx, int my) {
        if (tipText.isEmpty() || tip.v < 0.02f) return;
        g.alpha(tip.v * master.v);
        float s = 0.82f;
        float w = g.width(tipText, s) + 16, h = 17;
        float tx = Math.min(mx + 12, sw - w - 4), ty = Math.min(my + 16, sh - h - 4);
        float lift = (1f - tip.v) * 3f;
        g.round(tx - 1, ty + lift - 1, w + 2, h + 2, 4, 0x30000000);
        g.round(tx, ty + lift, w, h, 3, Theme.TIP_BG);
        g.text(tipText, tx + 8, ty + lift + 5.6f, Theme.TITLE, s);
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
        if (listening != null) { listening = null; return true; }
        for (int i = hn - 1; i >= 0; i--) {
            if (mx < hx[i] || mx >= hx[i] + hw[i] || my < hy[i] || my >= hy[i] + hh[i]) continue;
            Panel p = hp[i];
            bringToFront(p);
            switch (ht[i]) {
                case T_DRAG -> {
                    Panel pp = (Panel) ha[i];
                    if (pp.draggable) { dragPanel = pp; dragDX = (float) mx - pp.x; dragDY = (float) my - pp.y; }
                }
                case T_COLLAPSE -> { Panel pp = (Panel) ha[i]; pp.collapsed = !pp.collapsed; saveLayout(pp); }
                case T_TOGGLE -> { Module m = (Module) ha[i]; if (button == 1) toggleOpen(m); else m.toggle(); }
                case T_OPEN -> toggleOpen((Module) ha[i]);
                case T_BOOL -> ((BoolSetting) ha[i]).toggle();
                case T_MODE -> ((ModeSetting) ha[i]).cycle(button == 1 ? -1 : 1);
                case T_COLOR -> {
                    dragScheme = (ColorSetting) ha[i];
                    dragSchemeX = hx[i] + 2;
                    dragSchemeW = hw[i] - 4;
                    pickScheme(mx);
                }
                case T_BIND_KEY -> { if (button == 1) ((BindSetting) ha[i]).set(-1); else listening = (BindSetting) ha[i]; }
                case T_BIND_MODE -> ((BindSetting) ha[i]).hold = !((BindSetting) ha[i]).hold;
                case T_SLIDER -> {
                    dragNum = (NumSetting) ha[i];
                    dragNumX = hx[i] + 4;
                    dragNumW = hw[i] - 8;
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
        if (dragScheme != null) pickScheme(mx);
        if (dragPanel != null) {
            dragPanel.x = Math.max(2, Math.min(sw - dragPanel.w - 2, (float) mx - dragDX));
            dragPanel.y = Math.max(2, Math.min(sh - HEAD - 2, (float) my - dragDY));
        }
    }

    public void release() {
        if (dragPanel != null) saveLayout(dragPanel);
        dragPanel = null;
        dragNum = null;
        dragScheme = null;
    }

    private void pickScheme(double mx) {
        int n = Theme.SCHEME_COLORS.length;
        double f = Math.max(0, Math.min(0.9999, (mx - dragSchemeX) / dragSchemeW));
        int idx = (int) (f * n);
        dragScheme.set(Theme.SCHEME_COLORS[Math.max(0, Math.min(n - 1, idx))]);
    }

    public void scroll(double mx, double my, double amount) {
        for (int i = order.length - 1; i >= 0; i--) {
            Panel p = order[i];
            if (p == sidebar) continue;
            if (mx >= p.x && mx < p.x + p.w && my >= p.y && my < p.y + p.h) {
                float max = Math.max(0, p.contentH - p.viewH);
                p.scroll.t = Math.max(0, Math.min(max, p.scroll.t - (float) amount * 16f));
                return;
            }
        }
    }

    private void saveLayout(Panel p) { mods.guiLayout.put(p.id, new float[] {p.x, p.y, p.collapsed ? 1 : 0}); }

    private void slide(double mx) {
        double f = Math.max(0, Math.min(1, (mx - dragNumX) / dragNumW));
        dragNum.set(dragNum.min + f * (dragNum.max - dragNum.min));
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
