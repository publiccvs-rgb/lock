package dev.lock.client.gui;

import dev.lock.client.LockClient;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Gfx;
import dev.lock.core.config.ConfigStore;
import dev.lock.core.config.LockConfig;
import dev.lock.core.config.ModuleState;
import dev.lock.core.config.Profile;
import dev.lock.core.config.Waypoint;
import dev.lock.core.module.Catalog;
import dev.lock.core.module.Category;
import dev.lock.core.module.Keys;
import dev.lock.core.module.ModuleDef;
import dev.lock.core.module.Setting;
import dev.lock.core.module.Support;
import dev.lock.core.util.Log;
import dev.lock.core.util.LockPaths;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The Click GUI: searchable module manager with categories, expandable settings, favourites,
 * profiles, waypoints, keybinds and diagnostics. Everything is drawn in panel-local coordinates.
 */
public final class LockScreen extends Screen {
    static final int W = 500, H = 300, SIDEBAR = 104, HEADER = 26, LIST_W = 150;

    private enum Tab { MODULES, FAVORITES, PROFILES, WAYPOINTS, KEYBINDS, DIAGNOSTICS }

    private static Tab lastTab = Tab.MODULES;
    private static Category lastCategory = Category.HUD;
    private static String lastSelected;

    private final Screen parent;
    private final LockClient lock = LockClient.get();
    private final Ui ui = new Ui(lock);
    private Tab tab = lastTab;
    private Category category = lastCategory;
    private String selected = lastSelected;
    private final Ui.Field search = new Ui.Field("", 40, false);
    private double listScroll, settingsScroll, pageScroll;
    private float scale = 1f;
    private int ox, oy;
    private long openedAt;
    private ListenTarget listening;
    private String expandedColor;
    private final Map<String, Ui.Field> fields = new HashMap<>();
    private String status = "";
    private long statusAt;
    private String selectedProfile;
    private String selectedWaypoint;
    private boolean allDimensions;
    private long confirmResetAt;

    private record ListenTarget(ClientModule module, Setting.Key setting) {}

    public LockScreen(Screen parent) {
        super(Component.literal("Lock"));
        this.parent = parent;
        this.openedAt = System.currentTimeMillis();
    }

    /** Opens the GUI with a module preselected. */
    public static LockScreen forModule(String id) {
        LockScreen s = new LockScreen(null);
        ModuleDef d = Catalog.get(id);
        if (d != null) {
            s.tab = Tab.MODULES;
            s.category = d.category;
            s.selected = id;
        }
        return s;
    }

    @Override public boolean isPauseScreen() { return lock.config.state("click_gui").bool("pause"); }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x66000000);
    }

    @Override public void removed() {
        lastTab = tab;
        lastCategory = category;
        lastSelected = selected;
        lock.config.saveAsync();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private void flash(String s) { status = s; statusAt = System.currentTimeMillis(); }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g, mouseX, mouseY, pt);
        double want = lock.config.state("click_gui").num("scale");
        scale = (float) Math.min(want, Math.min((width - 16) / (double) W, (height - 16) / (double) H));
        ox = Math.round((width - W * scale) / 2f);
        oy = Math.round((height - H * scale) / 2f);
        float anim = 1f;
        if (lock.config.state("click_gui").bool("animations")) anim = Math.min(1f, (System.currentTimeMillis() - openedAt) / 140f);
        Gfx gfx = new Gfx(g);
        gfx.push();
        gfx.translate(ox, oy + (1 - anim) * 6);
        gfx.scale(scale);
        gfx.alpha = 0.3f + 0.7f * anim;
        ui.begin(gfx, local(mouseX, ox), local(mouseY, oy));
        drawFrame(gfx);
        switch (tab) {
            case MODULES, FAVORITES -> drawModules(gfx);
            case PROFILES -> drawProfiles(gfx);
            case WAYPOINTS -> drawWaypoints(gfx);
            case KEYBINDS -> drawKeybinds(gfx);
            case DIAGNOSTICS -> drawDiagnostics(gfx);
        }
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
            int sw = gfx.width(status) + 12;
            gfx.round(W - sw - 8, H - 18, W - 8, H - 5, ThemeManager.withAlpha(lock.theme.accent(), 0xE0));
            gfx.text(status, W - sw - 2, H - 15, 0xFF0E0F12, false);
        }
        ui.end();
        gfx.alpha = 1f;
        gfx.pop();
    }

    private int local(double v, int o) { return (int) Math.floor((v - o) / scale); }

    private void drawFrame(Gfx g) {
        int accent = lock.theme.accent(), text = lock.theme.text();
        g.round(-1, -1, W + 1, H + 1, ThemeManager.withAlpha(accent, 0x40));
        g.round(0, 0, W, H, lock.theme.panel());
        g.fill(0, HEADER, W, HEADER + 1, lock.theme.faint());
        g.fill(SIDEBAR, HEADER + 1, SIDEBAR + 1, H, lock.theme.faint());
        // header
        g.push();
        g.translate(10, 7);
        g.scale(1.4f);
        int x = g.text("lock", 0, 0, text, false);
        g.text(".", x, 0, accent, false);
        g.pop();
        ui.field(SIDEBAR + 8, 6, 170, 14, search, "Search modules…");
        String prof = "Profile: " + lock.config.profile().name();
        int pw = g.width(prof) + 10;
        String mode = lock.visibility.mode();
        int mw = g.width(mode) + 10;
        ui.button(W - pw - mw - 14, 6, pw, 14, prof, () -> tab = Tab.PROFILES);
        ui.button(W - mw - 8, 6, mw, 14, mode, lock.visibility::cycle);
        // sidebar
        int y = HEADER + 6;
        for (Category c : Category.values()) {
            boolean sel = tab == Tab.MODULES && category == c && search.text.isEmpty();
            sideItem(g, y, c.label, sel, () -> { tab = Tab.MODULES; category = c; search.text = ""; listScroll = 0; });
            y += 14;
        }
        g.fill(8, y + 2, SIDEBAR - 8, y + 3, lock.theme.faint());
        y += 7;
        sideItem(g, y, "★ Favorites", tab == Tab.FAVORITES, () -> { tab = Tab.FAVORITES; listScroll = 0; }); y += 14;
        sideItem(g, y, "Profiles", tab == Tab.PROFILES, () -> { tab = Tab.PROFILES; pageScroll = 0; }); y += 14;
        sideItem(g, y, "Waypoints", tab == Tab.WAYPOINTS, () -> { tab = Tab.WAYPOINTS; pageScroll = 0; }); y += 14;
        sideItem(g, y, "Keybinds", tab == Tab.KEYBINDS, () -> { tab = Tab.KEYBINDS; pageScroll = 0; }); y += 14;
        sideItem(g, y, "Diagnostics", tab == Tab.DIAGNOSTICS, () -> { tab = Tab.DIAGNOSTICS; pageScroll = 0; });
        ui.button(8, H - 22, SIDEBAR - 16, 15, "HUD Editor", () -> minecraft.setScreen(new HudEditorScreen(this)), true);
    }

    private void sideItem(Gfx g, int y, String label, boolean sel, Runnable r) {
        boolean hov = ui.hover(4, y, SIDEBAR - 8, 13);
        if (sel) g.round(4, y, SIDEBAR - 4, y + 13, ThemeManager.withAlpha(lock.theme.accent(), 0x30));
        else if (hov) g.round(4, y, SIDEBAR - 4, y + 13, lock.theme.faint());
        if (sel) g.fill(4, y + 3, 6, y + 10, lock.theme.accent());
        g.text(label, 10, y + 3, sel ? lock.theme.text() : lock.theme.dim(), false);
        ui.region(4, y, SIDEBAR - 8, 13, (a, b, c) -> r.run());
    }

    // ------------------------------------------------------------------ modules tab

    private List<ClientModule> visibleModules() {
        List<ClientModule> out = new ArrayList<>();
        String q = search.text;
        for (ClientModule m : lock.modules.all()) {
            if (!q.isEmpty()) { if (m.def.matches(q)) out.add(m); }
            else if (tab == Tab.FAVORITES) { if (lock.config.config().favorites.contains(m.def.id)) out.add(m); }
            else if (m.def.category == category) out.add(m);
        }
        return out;
    }

    private void drawModules(Gfx g) {
        List<ClientModule> mods = visibleModules();
        int x0 = SIDEBAR + 6, y0 = HEADER + 6, rowH = 18;
        String title = !search.text.isEmpty() ? "Results (" + mods.size() + ")" : tab == Tab.FAVORITES ? "Favorites" : category.label;
        g.text(title, x0 + 2, y0, lock.theme.dim(), false);
        int listTop = y0 + 12, listBottom = H - 6;
        int total = mods.size() * rowH;
        listScroll = clampScroll(listScroll, total, listBottom - listTop);
        ui.clip(x0, listTop, x0 + LIST_W, listBottom);
        ui.region(x0, listTop, LIST_W, listBottom - listTop, null, a -> listScroll -= a * 20, null);
        int y = listTop - (int) listScroll;
        if (mods.isEmpty()) g.text(tab == Tab.FAVORITES ? "Middle-click a module to favourite it." : "No modules match.", x0 + 4, y + 4, lock.theme.dim(), false);
        if (selected == null && !mods.isEmpty()) selected = mods.get(0).def.id;
        for (ClientModule m : mods) {
            if (y + rowH >= listTop && y <= listBottom) moduleRow(g, m, x0, y, LIST_W, rowH - 2);
            y += rowH;
        }
        ui.unclip();
        ClientModule sel = selected == null ? null : lock.modules.get(selected);
        if (sel != null) drawSettings(g, sel, x0 + LIST_W + 6, HEADER + 6, W - (x0 + LIST_W + 6) - 6, H - HEADER - 12);
    }

    private void moduleRow(Gfx g, ClientModule m, int x, int y, int w, int h) {
        boolean sel = m.def.id.equals(selected), hov = ui.hover(x, y, w, h);
        boolean on = m.enabled();
        boolean fav = lock.config.config().favorites.contains(m.def.id);
        g.round(x, y, x + w, y + h, sel ? ThemeManager.withAlpha(lock.theme.accent(), 0x26) : hov ? lock.theme.faint() : ThemeManager.withAlpha(lock.theme.text(), 0x0A));
        int tc = on ? lock.theme.text() : lock.theme.dim();
        String name = ui.ellipsize(m.def.name, w - 40);
        int nx = g.text(name, x + 6, y + 4, tc, false);
        if (fav) g.text("★", nx + 3, y + 4, lock.theme.accent(), false);
        if (lock.modules.faulted(m.def.id)) g.text("!", nx + (fav ? 12 : 3), y + 4, 0xFFF2575F, false);
        if (m.def.toggleable()) {
            ui.region(x, y, w - 26, h, (a, b, btn) -> rowClick(m, btn), null, m.def.description);
            ui.toggle(x + w - 23, y + 3, on, () -> lock.modules.toggle(m.def.id));
        } else {
            g.text("⚙", x + w - 14, y + 4, lock.theme.dim(), false);
            ui.region(x, y, w, h, (a, b, btn) -> rowClick(m, btn), null, m.def.description);
        }
    }

    private void rowClick(ClientModule m, int button) {
        if (button == 2) {
            var fav = lock.config.config().favorites;
            if (!fav.remove(m.def.id)) fav.add(m.def.id);
            lock.config.changed();
        } else if (button == 1 && m.def.toggleable()) lock.modules.toggle(m.def.id);
        else { if (!m.def.id.equals(selected)) settingsScroll = 0; selected = m.def.id; }
    }

    private void drawSettings(Gfx g, ClientModule m, int x, int y, int w, int h) {
        g.round(x, y, x + w, y + h, lock.theme.panelAlt());
        int pad = 8, cx = x + pad, cw = w - pad * 2;
        ui.clip(x, y, x + w, y + h);
        ui.region(x, y, w, h, null, a -> settingsScroll -= a * 22, null);
        int cy = y + pad - (int) settingsScroll;
        int start = cy;
        // header
        g.text(m.def.name, cx, cy, m.enabled() ? lock.theme.accent() : lock.theme.text(), false);
        String tag = m.def.category.label + (m.def.toggleable() ? "" : " · always on") + (m.def.changesBehavior ? " · changes game options" : " · display only");
        g.textScaled(tag, cx, cy + 10, 0.75f, lock.theme.dim(), false);
        if (m.def.toggleable()) ui.toggle(x + w - pad - 18, cy, m.enabled(), () -> lock.modules.toggle(m.def.id));
        boolean fav = lock.config.config().favorites.contains(m.def.id);
        ui.button(x + w - pad - 34, cy - 1, 13, 12, fav ? "★" : "☆", () -> {
            if (!lock.config.config().favorites.remove(m.def.id)) lock.config.config().favorites.add(m.def.id);
            lock.config.changed();
        });
        cy += 20;
        for (String line : ui.wrap(m.def.description, cw)) { g.text(line, cx, cy, lock.theme.dim(), false); cy += 10; }
        if (m.def.support != Support.SUPPORTED) {
            cy += 2;
            int col = m.def.support == Support.PARTIAL ? 0xFFF5B748 : 0xFFF2575F;
            g.text(m.def.support.label + ":", cx, cy, col, false);
            cy += 10;
            for (String line : ui.wrap(m.def.supportNote, cw)) { g.text(line, cx, cy, ThemeManager.withAlpha(col, 0xC0), false); cy += 10; }
        }
        if (lock.modules.faulted(m.def.id)) {
            g.text("Stopped after repeated errors.", cx, cy + 2, 0xFFF2575F, false);
            ui.button(cx + cw - 50, cy, 50, 12, "Retry", () -> { lock.modules.resetFault(m.def.id); lock.modules.sync(m); });
            cy += 14;
        }
        cy += 6;
        String group = null;
        for (Setting<?> s : m.def.settings().values()) {
            if (!s.group.equals(group)) {
                group = s.group;
                if (!group.isEmpty()) {
                    cy += 4;
                    g.textScaled(group.toUpperCase(), cx, cy, 0.7f, ThemeManager.withAlpha(lock.theme.accent(), 0xC0), false);
                    g.fill(cx + Math.round(g.width(group.toUpperCase()) * 0.7f) + 4, cy + 3, cx + cw, cy + 4, lock.theme.faint());
                    cy += 10;
                }
            }
            cy = settingRow(g, m, s, cx, cy, cw);
        }
        cy += 8;
        int bw = (cw - 4) / 2;
        ui.button(cx, cy, bw, 14, "Reset module", () -> {
            m.state().reset();
            lock.settingChanged(m, "*");
            lock.modules.sync(m);
            flash(m.def.name + " reset to defaults");
        });
        if (m instanceof HudModule) ui.button(cx + bw + 4, cy, bw, 14, "Edit in HUD editor", () -> minecraft.setScreen(new HudEditorScreen(this, m.def.id)));
        cy += 20;
        ui.unclip();
        int content = cy - start + (int) settingsScroll;
        settingsScroll = clampScroll(settingsScroll, cy - start, h);
        if (content > h) {
            int barH = Math.max(12, h * h / (cy - start));
            int barY = y + (int) ((h - barH) * (settingsScroll / Math.max(1, (cy - start) - h)));
            g.fill(x + w - 2, barY, x + w - 1, barY + barH, ThemeManager.withAlpha(lock.theme.text(), 0x40));
        }
    }

    private int settingRow(Gfx g, ClientModule m, Setting<?> s, int x, int y, int w) {
        ModuleState st = m.state();
        int text = lock.theme.text(), dim = lock.theme.dim();
        boolean labelHover = ui.hover(x, y, w - 60, 10);
        if (labelHover && !s.description.isEmpty()) ui.tooltip(s.description);
        switch (s) {
            case Setting.Bool b -> {
                g.text(s.name, x, y + 1, text, false);
                boolean v = st.get(b);
                ui.toggle(x + w - 18, y, v, () -> set(m, s.id, !v));
                return y + 14;
            }
            case Setting.Num n -> {
                double v = st.get(n);
                g.text(s.name, x, y + 1, text, false);
                String vs = n.format(v);
                g.text(vs, x + w - g.width(vs), y + 1, dim, false);
                ui.slider(x, y + 11, w, v, n.min, n.max, nv -> set(m, s.id, nv));
                return y + 22;
            }
            case Setting.Choice c -> {
                g.text(s.name, x, y + 1, text, false);
                String v = st.get(c);
                int bw = Math.min(w / 2, Math.max(50, g.width(v) + 16));
                ui.button(x + w - bw, y - 1, bw, 12, v, () -> {});
                ui.region(x + w - bw, y - 1, bw, 12, (a, b2, btn) -> set(m, s.id, c.next(v, btn == 1 ? -1 : 1)), null, "Click: next · Right-click: previous");
                return y + 15;
            }
            case Setting.Color c -> {
                int v = st.get(c);
                g.text(s.name, x, y + 1, text, false);
                String hex = Setting.Color.format(v);
                g.text(hex, x + w - 24 - g.width(hex), y + 1, dim, false);
                String key = m.def.id + "." + s.id;
                ui.swatch(x + w - 20, y, 20, 10, v);
                ui.region(x + w - 20, y, 20, 10, (a, b2, btn) -> expandedColor = key.equals(expandedColor) ? null : key);
                y += 14;
                if (key.equals(expandedColor)) y = colorEditor(g, m, s.id, v, x, y, w);
                return y;
            }
            case Setting.Text t -> {
                g.text(s.name, x, y + 1, text, false);
                Ui.Field f = fields.computeIfAbsent(m.def.id + "." + s.id, k -> new Ui.Field(st.get(t), t.maxLength, false));
                if (ui.focused != f) f.text = st.get(t);
                f.onChange = val -> set(m, s.id, val);
                ui.field(x, y + 11, w, 13, f, "");
                return y + 28;
            }
            case Setting.Key k -> {
                g.text(s.name, x, y + 1, text, false);
                boolean listen = listening != null && listening.setting() == k && listening.module() == m;
                String label = listen ? "Press a key…" : Keys.display(st.get(k));
                int bw = Math.max(50, g.width(label) + 12);
                ui.button(x + w - bw, y - 1, bw, 12, label, () -> listening = new ListenTarget(m, k), listen);
                return y + 15;
            }
        }
    }

    private int colorEditor(Gfx g, ClientModule m, String id, int argb, int x, int y, int w) {
        float[] hsv = rgbToHsv(argb);
        int a = (argb >>> 24) & 0xFF;
        String[] labels = {"H", "S", "V", "A"};
        double[] vals = {hsv[0], hsv[1], hsv[2], a / 255.0};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            g.text(labels[i], x, y, lock.theme.dim(), false);
            if (i == 0) for (int px = 0; px < w - 12; px++) g.fill(x + 10 + px, y + 2, x + 11 + px, y + 4, hsvToRgb(px / (float) (w - 12), 1, 1, 255));
            ui.slider(x + 10, y, w - 12, vals[i], 0, 1, nv -> {
                double[] v = vals.clone();
                v[idx] = nv;
                set(m, id, hsvToRgb((float) v[0], (float) v[1], (float) v[2], (int) Math.round(v[3] * 255)));
            });
            y += 11;
        }
        return y + 4;
    }

    static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, gg = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(gg, b)), min = Math.min(r, Math.min(gg, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((gg - b) / d) % 6;
            else if (max == gg) h = (b - r) / d + 2;
            else h = (r - gg) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    static int hsvToRgb(float h, float s, float v, int a) {
        // (no java.awt here: AWT can conflict with GLFW's window system on macOS)
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.max(0, Math.min(255, a)) << 24) | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private void set(ClientModule m, String key, Object value) {
        m.state().set(key, value);
        lock.settingChanged(m, key);
    }

    private static double clampScroll(double s, int content, int view) { return Math.max(0, Math.min(s, Math.max(0, content - view))); }

    // ------------------------------------------------------------------ profiles tab

    private Ui.Field field(String key, String init, int max) { return fields.computeIfAbsent(key, k -> new Ui.Field(init, max, false)); }

    private void drawProfiles(Gfx g) {
        LockConfig cfg = lock.config.config();
        int x = SIDEBAR + 10, y = HEADER + 8, w = W - SIDEBAR - 20;
        if (selectedProfile == null || !cfg.profiles().containsKey(selectedProfile)) selectedProfile = cfg.activeName();
        g.text("Profiles", x, y, lock.theme.text(), false);
        g.textScaled("Each profile stores module states, settings, keybinds, HUD layout and visibility.", x, y + 11, 0.75f, lock.theme.dim(), false);
        y += 24;
        int listW = 150;
        int ly = y;
        for (Profile p : cfg.profiles().values()) {
            boolean sel = p.name().equals(selectedProfile), active = p.name().equals(cfg.activeName());
            g.round(x, ly, x + listW, ly + 15, sel ? ThemeManager.withAlpha(lock.theme.accent(), 0x26) : ThemeManager.withAlpha(lock.theme.text(), 0x0A));
            g.text((active ? "● " : "   ") + p.name(), x + 5, ly + 4, active ? lock.theme.accent() : lock.theme.text(), false);
            if (p.builtIn()) g.textScaled("built-in", x + listW - 30, ly + 5, 0.7f, lock.theme.dim(), false);
            String name = p.name();
            ui.region(x, ly, listW, 15, (a, b, c) -> selectedProfile = name);
            ly += 17;
        }
        int bx = x + listW + 10, bw = 110;
        int by = y;
        Profile sp = cfg.profiles().get(selectedProfile);
        g.text(selectedProfile, bx, by, lock.theme.text(), false);
        by += 12;
        ui.button(bx, by, bw, 14, "Switch to this profile", () -> lock.switchProfile(selectedProfile), true); by += 17;
        Ui.Field name = field("profile.name", "", 32);
        ui.field(bx, by, bw * 2 + 6, 14, name, "Name for new / duplicate / rename"); by += 17;
        ui.button(bx, by, bw, 14, "New from defaults", () -> profileOp(cfg.create(name.text), name.text)); 
        ui.button(bx + bw + 6, by, bw, 14, "Duplicate", () -> profileOp(cfg.duplicate(selectedProfile, name.text), name.text)); by += 17;
        ui.button(bx, by, bw, 14, "Rename", () -> profileOp(cfg.rename(selectedProfile, name.text), name.text));
        ui.button(bx + bw + 6, by, bw, 14, "Delete", () -> profileOp(cfg.delete(selectedProfile), null)); by += 17;
        ui.button(bx, by, bw, 14, "Reset to defaults", () -> { sp.resetAll(); lock.config.changed(); lock.modules.syncAll(); flash("Profile reset"); });
        ui.button(bx + bw + 6, by, bw, 14, "Export", () -> {
            try {
                Path dir = LockPaths.home().resolve("exports");
                Files.createDirectories(dir);
                Path f = dir.resolve(selectedProfile.replaceAll("[^A-Za-z0-9 _-]", "_") + ".json");
                ConfigStore.exportProfile(sp, f);
                flash("Exported to exports/" + f.getFileName());
            } catch (Exception e) { flash("Export failed: " + e.getMessage()); }
        });
        by += 24;
        g.text("Import (files in Lock/exports)", bx, by, lock.theme.dim(), false);
        by += 11;
        for (Path f : exportFiles()) {
            g.text(ui.ellipsize(f.getFileName().toString(), bw * 2 - 50), bx, by + 3, lock.theme.text(), false);
            ui.button(bx + bw * 2 + 6 - 44, by, 44, 13, "Import", () -> {
                try { flash(ConfigStore.importFile(cfg, f)); lock.config.changed(); } catch (Exception e) { flash("Import failed: " + e.getMessage()); }
            });
            by += 15;
            if (by > H - 70) break;
        }
        // layouts
        int ly2 = Math.max(ly + 10, H - 60);
        g.text("HUD layouts", x, ly2, lock.theme.dim(), false);
        Ui.Field lname = field("layout.name", "", 24);
        ui.field(x, ly2 + 11, listW - 50, 14, lname, "Layout name");
        ui.button(x + listW - 46, ly2 + 11, 46, 14, "Save", () -> {
            if (LockConfig.validateName(lname.text) != null) { flash(LockConfig.validateName(lname.text)); return; }
            cfg.layouts.put(lname.text.trim(), captureLayout());
            lock.config.changed();
            flash("Layout '" + lname.text.trim() + "' saved");
        });
        int lx = x + listW + 10;
        for (String ln : new ArrayList<>(cfg.layouts.keySet())) {
            int tw = g.width(ln) + 10;
            if (lx + tw + 14 > W - 10) break;
            ui.button(lx, ly2 + 11, tw, 14, ln, () -> { applyLayout(cfg.layouts.get(ln)); flash("Layout '" + ln + "' applied"); });
            ui.button(lx + tw + 1, ly2 + 11, 11, 14, "×", () -> { cfg.layouts.remove(ln); lock.config.changed(); });
            lx += tw + 16;
        }
    }

    private void profileOp(String error, String newName) {
        if (error != null) { flash(error); return; }
        if (newName != null) selectedProfile = newName.trim();
        lock.config.changed();
        lock.modules.syncAll();
        fields.get("profile.name").text = "";
        flash("Done");
    }

    private List<Path> exportFiles() {
        Path dir = LockPaths.home().resolve("exports");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> captureLayout() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (HudModule h : lock.modules.huds()) {
            Map<String, Object> e = new LinkedHashMap<>();
            ModuleState st = h.state();
            e.put("enabled", st.enabled());
            for (String k : new String[]{ModuleDef.H_ANCHOR, ModuleDef.H_X, ModuleDef.H_Y, ModuleDef.H_SCALE}) e.put(k, st.raw(k));
            m.put(h.def.id, e);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private void applyLayout(Map<String, Object> layout) {
        for (HudModule h : lock.modules.huds()) {
            Object o = layout.get(h.def.id);
            if (!(o instanceof Map<?, ?> e)) continue;
            ModuleState st = h.state();
            if (e.get("enabled") instanceof Boolean b) st.setEnabled(b);
            for (String k : new String[]{ModuleDef.H_ANCHOR, ModuleDef.H_X, ModuleDef.H_Y, ModuleDef.H_SCALE})
                if (e.containsKey(k)) st.set(k, ((Map<String, Object>) e).get(k));
        }
        lock.config.changed();
        lock.modules.syncAll();
    }

    // ------------------------------------------------------------------ waypoints tab

    private void drawWaypoints(Gfx g) {
        int x = SIDEBAR + 10, y = HEADER + 8, w = W - SIDEBAR - 20;
        g.text("Waypoints", x, y, lock.theme.text(), false);
        boolean inWorld = minecraft.player != null;
        String scope = inWorld ? lock.waypoints.scope() : "-";
        g.textScaled("World/server: " + scope + (allDimensions ? "  · all dimensions" : "  · " + dev.lock.client.core.GameInfo.dimensionName()), x, y + 11, 0.75f, lock.theme.dim(), false);
        ui.button(x + w - 110, y - 2, 110, 14, "Add at my position", () -> {
            Waypoint wp = lock.waypoints.addHere(null);
            if (wp != null) selectedWaypoint = wp.id;
        }, true);
        ui.button(x + w - 200, y - 2, 86, 14, allDimensions ? "All dimensions" : "This dimension", () -> allDimensions = !allDimensions);
        y += 24;
        List<Waypoint> list = allDimensions ? lock.waypoints.inScope() : lock.waypoints.here();
        int listW = 220, rowH = 16;
        int top = y, bottom = H - 8;
        pageScroll = clampScroll(pageScroll, list.size() * rowH, bottom - top);
        ui.clip(x, top, x + listW, bottom);
        ui.region(x, top, listW, bottom - top, null, a -> pageScroll -= a * 20, null);
        int ry = top - (int) pageScroll;
        Waypoint target = lock.waypoints.selected();
        if (list.isEmpty()) g.text("No waypoints yet.", x + 4, ry + 4, lock.theme.dim(), false);
        for (Waypoint wp : list) {
            boolean sel = wp.id.equals(selectedWaypoint);
            g.round(x, ry, x + listW, ry + rowH - 2, sel ? ThemeManager.withAlpha(lock.theme.accent(), 0x26) : ThemeManager.withAlpha(lock.theme.text(), 0x0A));
            g.fill(x + 4, ry + 4, x + 10, ry + 10, wp.color | 0xFF000000);
            g.text(ui.ellipsize(wp.name, 80), x + 14, ry + 3, wp.visible ? lock.theme.text() : lock.theme.dim(), false);
            double d = lock.waypoints.distance(wp);
            String info = wp.x + " " + wp.y + " " + wp.z + (d >= 0 ? "  " + Math.round(d) + "m" : "");
            g.textScaled(info, x + listW - g.width(info) * 0.75f - (wp == target ? 12 : 4), ry + 4, 0.75f, lock.theme.dim(), false);
            if (wp == target) g.text("◎", x + listW - 10, ry + 3, lock.theme.accent(), false);
            String id = wp.id;
            ui.region(x, ry, listW, rowH - 2, (a, b, c) -> selectedWaypoint = id);
            ry += rowH;
        }
        ui.unclip();
        Waypoint sel = selectedWaypoint == null ? null : lock.config.config().waypoint(selectedWaypoint);
        int ex = x + listW + 10, ew = w - listW - 10;
        if (sel == null) { g.text("Select a waypoint to edit it.", ex, top + 4, lock.theme.dim(), false); return; }
        int ey = top;
        Ui.Field nf = fields.computeIfAbsent("wp.name." + sel.id, k -> new Ui.Field(sel.name, 48, false));
        nf.onChange = v -> { if (!v.isBlank()) { sel.name = v; lock.config.changed(); } };
        g.text("Name", ex, ey + 3, lock.theme.dim(), false);
        ui.field(ex + 34, ey, ew - 34, 14, nf, "");
        ey += 18;
        String[] axes = {"X", "Y", "Z"};
        int fw = (ew - 34) / 3 - 3;
        g.text("Pos", ex, ey + 3, lock.theme.dim(), false);
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            int val = i == 0 ? sel.x : i == 1 ? sel.y : sel.z;
            Ui.Field f = fields.computeIfAbsent("wp." + axes[i] + "." + sel.id, k -> new Ui.Field(Integer.toString(val), 9, true));
            f.onChange = v -> {
                try {
                    int n = Integer.parseInt(v);
                    if (axis == 0) sel.x = n; else if (axis == 1) sel.y = n; else sel.z = n;
                    lock.config.changed();
                } catch (NumberFormatException ignored) { }
            };
            ui.field(ex + 34 + i * (fw + 3), ey, fw, 14, f, axes[i]);
        }
        ey += 18;
        g.text("Dimension: " + dev.lock.client.render.Fmt.prettyId(sel.dimension), ex, ey + 2, lock.theme.dim(), false);
        ey += 14;
        g.text("Colour", ex, ey + 2, lock.theme.dim(), false);
        ui.swatch(ex + 34, ey + 1, 20, 10, sel.color | 0xFF000000);
        int[] palette = {0xFF6EA8FE, 0xFF5CD6A9, 0xFFF2789F, 0xFFF5B748, 0xFFF2575F, 0xFFB48CFF, 0xFFFFFFFF};
        int px = ex + 60;
        for (int c : palette) {
            ui.swatch(px, ey + 1, 10, 10, c);
            ui.region(px, ey + 1, 10, 10, (a, b, btn) -> { sel.color = c; lock.config.changed(); });
            px += 13;
        }
        ey += 16;
        g.text("Visible", ex, ey + 1, lock.theme.dim(), false);
        ui.toggle(ex + 34, ey, sel.visible, () -> { sel.visible = !sel.visible; lock.config.changed(); });
        ey += 18;
        int bw = (ew - 4) / 2;
        ui.button(ex, ey, bw, 14, target == sel ? "Targeted" : "Set as target", () -> lock.waypoints.select(sel), target != sel);
        ui.button(ex + bw + 4, ey, bw, 14, "Move to my position", () -> {
            if (minecraft.player == null) return;
            sel.x = minecraft.player.getBlockX(); sel.y = minecraft.player.getBlockY(); sel.z = minecraft.player.getBlockZ();
            sel.dimension = dev.lock.client.core.GameInfo.dimension();
            fields.keySet().removeIf(k -> k.endsWith(sel.id));
            lock.config.changed();
        });
        ey += 17;
        ui.button(ex, ey, bw, 14, "Delete", () -> { lock.waypoints.delete(sel); selectedWaypoint = null; });
        if (!inWorld) g.text("Join a world to add waypoints.", ex, ey + 20, lock.theme.dim(), false);
    }

    // ------------------------------------------------------------------ keybinds tab

    private void drawKeybinds(Gfx g) {
        int x = SIDEBAR + 10, y = HEADER + 8, w = W - SIDEBAR - 20;
        g.text("Keybinds", x, y, lock.theme.text(), false);
        g.textScaled("Click a key to rebind. Escape clears it. Mouse buttons 3-8 can be bound by clicking them.", x, y + 11, 0.75f, lock.theme.dim(), false);
        y += 24;
        Map<Integer, List<String>> conflicts = lock.keys.conflicts();
        int top = y, bottom = H - 8, rowH = 15;
        List<dev.lock.client.core.KeybindManager.Bind> binds = new ArrayList<>();
        for (var b : lock.keys.all()) if (search.text.isEmpty() || b.label().toLowerCase().contains(search.text.toLowerCase())) binds.add(b);
        pageScroll = clampScroll(pageScroll, binds.size() * rowH, bottom - top);
        ui.clip(x, top, x + w, bottom);
        ui.region(x, top, w, bottom - top, null, a -> pageScroll -= a * 20, null);
        int ry = top - (int) pageScroll;
        for (var b : binds) {
            if (ry + rowH >= top && ry <= bottom) {
                int code = b.module().state().get(b.setting());
                List<String> c = conflicts.get(code);
                g.text(ui.ellipsize(b.label(), w - 140), x + 4, ry + 3, code == Keys.NONE ? lock.theme.dim() : lock.theme.text(), false);
                if (c != null) {
                    g.text("⚠", x + w - 112, ry + 3, 0xFFF5B748, false);
                    if (ui.hover(x + w - 114, ry, 10, 12)) ui.tooltip("Also bound: " + String.join(", ", c));
                }
                boolean listen = listening != null && listening.setting() == b.setting() && listening.module() == b.module();
                ui.button(x + w - 100, ry, 96, 13, listen ? "Press a key…" : Keys.display(code), () -> listening = new ListenTarget(b.module(), b.setting()), listen);
            }
            ry += rowH;
        }
        ui.unclip();
    }

    // ------------------------------------------------------------------ diagnostics tab

    private void drawDiagnostics(Gfx g) {
        int x = SIDEBAR + 10, y = HEADER + 8, w = W - SIDEBAR - 20;
        int top = y, bottom = H - 26;
        ui.clip(x, top, x + w, bottom);
        ui.region(x, top, w, bottom - top, null, a -> pageScroll -= a * 20, null);
        int cy = top - (int) pageScroll;
        int start = cy;
        cy = section(g, "Runtime", x, cy);
        cy = kv(g, x, cy, w, "Lock", lock.agent.lockVersion());
        cy = kv(g, x, cy, w, "Minecraft", lock.agent.minecraftVersion());
        cy = kv(g, x, cy, w, "Java", System.getProperty("java.version") + " · " + System.getProperty("os.arch"));
        cy = kv(g, x, cy, w, "Integration", "Java agent loaded at launch · hooks " + lock.diagnostics.hooksApplied() + "/" + lock.agent.hooks().size());
        for (var h : lock.diagnostics.failedHooks()) cy = kv(g, x, cy, w, "  ⚠ " + h.name(), h.detail());
        Object init = lock.agent.startupInfo().get("clientInitMs");
        cy = kv(g, x, cy, w, "Startup", "agent " + lock.agent.startupInfo().get("agentInitMs") + " ms, client " + init + " ms, mappings " + lock.agent.startupInfo().get("mappingsSource"));
        cy = section(g, "Modules", x, cy + 4);
        int active = 0;
        for (ClientModule m : lock.modules.all()) if (m.active()) active++;
        cy = kv(g, x, cy, w, "Catalog", lock.modules.all().size() + " modules, " + active + " active, unbound: " + (lock.modules.unbound().isEmpty() ? "none" : lock.modules.unbound()));
        cy = kv(g, x, cy, w, "Faulted", lock.modules.faultedIds().isEmpty() ? "none" : String.join(", ", lock.modules.faultedIds()));
        cy = section(g, "Rendering", x, cy + 4);
        cy = kv(g, x, cy, w, "HUD cost", String.format("%.0f µs per frame (average)", lock.hud.averageCostMicros()));
        cy = kv(g, x, cy, w, "FPS", String.format("%d now · %.0f avg · %.0f 1%% low", minecraft.getFps(), lock.stats.avgFps(), lock.stats.lowFps()));
        cy = section(g, "Configuration", x, cy + 4);
        cy = kv(g, x, cy, w, "File", LockPaths.config().toString());
        cy = kv(g, x, cy, w, "Validation", lock.config.loadProblems().isEmpty() ? "no problems found" : lock.config.loadProblems().size() + " value(s) corrected on load");
        for (String p : lock.config.loadProblems()) cy = kv(g, x, cy, w, "", p);
        if (lock.config.recoveredFrom() != null) cy = kv(g, x, cy, w, "Recovered", "corrupt file kept at " + lock.config.recoveredFrom());
        cy = section(g, "Capture compatibility", x, cy + 4);
        for (String line : ui.wrap(Catalog.get("capture_status").supportNote, w - 4)) { g.text(line, x + 2, cy, lock.theme.dim(), false); cy += 10; }
        cy = section(g, "Recent warnings & errors", x, cy + 4);
        List<Log.Entry> log = Log.recent();
        int shown = 0;
        for (int i = log.size() - 1; i >= 0 && shown < 12; i--) {
            Log.Entry e = log.get(i);
            if (e.level() == Log.Level.INFO) continue;
            for (String line : ui.wrap(e.level() + "  " + e.message(), w - 4)) {
                g.text(line, x + 2, cy, e.level() == Log.Level.ERROR ? 0xFFF2575F : 0xFFF5B748, false);
                cy += 10;
            }
            shown++;
        }
        if (shown == 0) { g.text("None.", x + 2, cy, lock.theme.dim(), false); cy += 10; }
        ui.unclip();
        pageScroll = clampScroll(pageScroll, cy - start, bottom - top);
        int by = H - 22, bw = 120;
        ui.button(x, by, bw, 15, "Write diagnostic report", () -> {
            try { flash("Saved " + lock.diagnostics.writeReport().getFileName()); } catch (Exception e) { flash("Failed: " + e.getMessage()); }
        });
        ui.button(x + bw + 6, by, bw, 15, "Open Lock folder", () -> net.minecraft.util.Util.getPlatform().openPath(LockPaths.home()));
        boolean confirm = System.currentTimeMillis() - confirmResetAt < 3000;
        ui.button(x + (bw + 6) * 2, by, bw, 15, confirm ? "Click again to confirm" : "Reset all settings", () -> {
            if (System.currentTimeMillis() - confirmResetAt < 3000) {
                lock.config.profile().resetAll();
                lock.config.changed();
                lock.modules.syncAll();
                confirmResetAt = 0;
                flash("Active profile reset to defaults");
            } else confirmResetAt = System.currentTimeMillis();
        }, confirm);
    }

    private int section(Gfx g, String title, int x, int y) {
        g.textScaled(title.toUpperCase(), x, y, 0.75f, lock.theme.accent(), false);
        return y + 10;
    }

    private int kv(Gfx g, int x, int y, int w, String k, String v) {
        g.text(k, x + 2, y, lock.theme.dim(), false);
        List<String> lines = ui.wrap(v == null ? "" : v, w - 90);
        for (String line : lines) { g.text(line, x + 88, y, lock.theme.text(), false); y += 10; }
        return y + 1;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        int mx = local(e.x(), ox), my = local(e.y(), oy);
        if (listening != null) {
            if (e.button() >= 2) {
                set(listening.module(), listening.setting().id, Keys.MOUSE_BASE + e.button());
                listening = null;
                return true;
            }
            listening = null;
        }
        Ui.Field before = ui.focused;
        ui.focused = null;
        boolean handled = ui.click(mx, my, e.button());
        if (ui.focused == null && before != null && before.onCommit != null) before.onCommit.accept(before.text);
        return handled || true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        ui.drag = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (ui.drag != null) ui.drag.drag(local(e.x(), ox), local(e.y(), oy));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        ui.scroll(local(x, ox), local(y, oy), sy);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (listening != null) {
            set(listening.module(), listening.setting().id, key == GLFW.GLFW_KEY_ESCAPE ? Keys.NONE : key);
            listening = null;
            return true;
        }
        if (ui.focused != null) {
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> ui.focused.backspace();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB -> {
                    if (ui.focused.onCommit != null) ui.focused.onCommit.accept(ui.focused.text);
                    ui.focused = null;
                }
                case GLFW.GLFW_KEY_V -> {
                    if ((e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 || (e.modifiers() & GLFW.GLFW_MOD_SUPER) != 0)
                        ui.focused.type(minecraft.keyboardHandler.getClipboard());
                }
                default -> { }
            }
            if (ui.focused == search) { listScroll = 0; }
            return true;
        }
        if (key == lock.config.state("click_gui").key("openKey") || key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if ((e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_F) { ui.focused = search; return true; }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (ui.focused == null) {
            // typing anywhere starts a search
            if (!Character.isLetterOrDigit(e.codepoint())) return false;
            ui.focused = search;
            tab = Tab.MODULES;
        }
        ui.focused.type(e.codepointAsString());
        if (ui.focused == search) listScroll = 0;
        return true;
    }
}
