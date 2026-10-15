package dev.lock.client.gui;

import dev.lock.client.LockClient;
import dev.lock.client.core.GameInfo;
import dev.lock.client.core.KeybindManager;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Fmt;
import dev.lock.client.render.Gfx;
import dev.lock.core.config.ConfigStore;
import dev.lock.core.config.LockConfig;
import dev.lock.core.config.ModuleState;
import dev.lock.core.config.Profile;
import dev.lock.core.config.Waypoint;
import dev.lock.core.json.JsonUtil;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The Click GUI. A main sidebar (wordmark, search, categories, misc pages, quick actions) and one
 * floating column per opened category. Rows fill with the accent colour when a module is enabled;
 * the dots button expands its settings inline. Windows can be dragged and collapsed, and their
 * positions persist in the configuration.
 */
public final class LockScreen extends Screen {
    static final int MAIN_W = 128, COL_W = 128, HEADER = 20, ROW = 16, RADIUS = 5;
    static final int BG = 0xF8161719, HEADER_BG = 0xFF1C1D20, ROW_HOVER = 0x0EFFFFFF, DIM = 0xFF85878E, FAINT = 0xFF5D5F66, LINE = 0x14FFFFFF;
    static final float FS = Ui.FS, FS_S = Ui.FS_SMALL;

    private static final Map<Category, String> CAT_ICONS = Map.of(
            Category.VISUALS, "eye", Category.HUD, "hud", Category.MOVEMENT, "move", Category.PLAYER, "user",
            Category.WORLD, "globe", Category.UTILITY, "wrench", Category.INVENTORY, "box", Category.INTERFACE, "palette",
            Category.PERFORMANCE, "gauge", Category.RECORDING, "video");

    /** A floating panel. */
    static final class Win {
        final String id;
        int x, y, w;
        boolean open, collapsed;
        double scroll;
        int contentH = 200, drawnH;
        boolean scrollable;

        Win(String id, int x, int y, int w) { this.id = id; this.x = x; this.y = y; this.w = w; }

        boolean contains(int px, int py) { return px >= x && py >= y && px < x + w && py < y + drawnH; }
    }

    private final Screen parent;
    private final LockClient lock = LockClient.get();
    private final Ui ui = new Ui(lock);
    private final Map<String, Win> wins = new LinkedHashMap<>();
    private final List<String> z = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private final Ui.Field search = new Ui.Field("", 40, false);
    private final Map<String, Ui.Field> fields = new HashMap<>();
    private float scale = 1f;
    private int sw, sh;
    private final long openedAt = System.currentTimeMillis();
    private ListenTarget listening;
    private String expandedColor, status = "";
    private long statusAt, confirmResetAt;
    private String selectedWaypoint;
    private boolean allDimensions;

    private record ListenTarget(ClientModule module, Setting.Key setting) {}

    public LockScreen(Screen parent) {
        super(Component.literal("Lock"));
        this.parent = parent;
        loadWindows();
    }

    /** Opens the GUI with a module's category open and its settings expanded. */
    public static LockScreen forModule(String id) {
        LockScreen s = new LockScreen(null);
        ModuleDef d = Catalog.get(id);
        if (d != null) {
            Win w = s.wins.get("cat:" + d.category.name());
            w.open = true;
            w.collapsed = false;
            s.front(w.id);
            s.expanded.add(id);
        }
        return s;
    }

    // ------------------------------------------------------------------ window state

    private void loadWindows() {
        add(new Win("main", 8, 8, MAIN_W), true);
        int x = 8 + MAIN_W + 6;
        for (Category c : Category.values()) {
            add(new Win("cat:" + c.name(), x, 8, COL_W), c == Category.HUD || c == Category.VISUALS || c == Category.MOVEMENT);
            x += COL_W + 6;
            if (x > 900) x = 8 + MAIN_W + 6;
        }
        add(new Win("favorites", 8 + MAIN_W + 6, 140, COL_W), false);
        add(new Win("search", 8 + MAIN_W + 6, 8, COL_W + 20), false);
        add(new Win("profiles", 160, 40, 176), false);
        add(new Win("waypoints", 180, 50, 196), false);
        add(new Win("keybinds", 200, 60, 184), false);
        add(new Win("diagnostics", 220, 30, 220), false);
        Map<String, Object> saved = JsonUtil.obj(lock.config.config().gui.get("windows"));
        if (saved != null) {
            for (Map.Entry<String, Object> e : saved.entrySet()) {
                Win w = wins.get(e.getKey());
                Map<String, Object> m = JsonUtil.obj(e.getValue());
                if (w == null || m == null) continue;
                w.x = (int) JsonUtil.num(m, "x", w.x);
                w.y = (int) JsonUtil.num(m, "y", w.y);
                w.open = w.id.equals("main") || JsonUtil.bool(m, "open", w.open);
                w.collapsed = JsonUtil.bool(m, "collapsed", false);
            }
            Object order = lock.config.config().gui.get("order");
            if (order instanceof List<?> l) for (Object o : l) if (o instanceof String s && wins.containsKey(s)) front(s);
        }
        wins.get("search").open = false;
    }

    private void add(Win w, boolean open) {
        w.open = open;
        wins.put(w.id, w);
        z.add(w.id);
    }

    private void saveWindows() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Win w : wins.values()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("x", w.x);
            e.put("y", w.y);
            e.put("open", w.open);
            e.put("collapsed", w.collapsed);
            m.put(w.id, e);
        }
        lock.config.config().gui.put("windows", m);
        lock.config.config().gui.put("order", new ArrayList<>(z));
        lock.config.changed();
    }

    private void front(String id) {
        z.remove(id);
        z.add(id);
    }

    private void toggleWin(String id) {
        Win w = wins.get(id);
        w.open = !w.open;
        if (w.open) {
            w.collapsed = false;
            front(id);
        }
    }

    // ------------------------------------------------------------------ screen basics

    @Override public boolean isPauseScreen() { return lock.config.state("click_gui").bool("pause"); }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0x60000000); }

    @Override public void removed() {
        saveWindows();
        lock.config.saveAsync();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private void flash(String s) { status = s; statusAt = System.currentTimeMillis(); }

    private int local(double v) { return (int) Math.floor(v / scale); }

    private int accent() { return lock.theme.accent(); }

    private int text() { return lock.theme.text(); }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g, mouseX, mouseY, pt);
        // constant physical size: 1.6 screen pixels per GUI unit at scale 1.0, whatever the vanilla GUI scale
        scale = (float) (lock.config.state("click_gui").num("scale") * 1.6 / minecraft.getWindow().getGuiScale());
        sw = (int) (width / scale);
        sh = (int) (height / scale);
        Gfx gfx = new Gfx(g);
        gfx.push();
        gfx.scale(scale);
        if (lock.config.state("click_gui").bool("animations"))
            gfx.alpha = Math.min(1f, (System.currentTimeMillis() - openedAt) / 120f);
        ui.begin(gfx, local(mouseX), local(mouseY));
        wins.get("search").open = !search.text.isEmpty();
        if (!search.text.isEmpty() && !z.get(z.size() - 1).equals("search") && ui.focused == search) front("search");
        for (String id : new ArrayList<>(z)) {
            Win w = wins.get(id);
            if (w.open) drawWindow(gfx, w);
        }
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 3500) {
            float tw = ui.width(status, FS, Ui.M) + 16;
            int x = (int) ((sw - tw) / 2), y = sh - 24;
            gfx.shadow(x, y, (int) tw, 15, 5, 0x80000000);
            gfx.rrect(x, y, (int) tw, 15, 4, accent());
            ui.text(status, x + 8, y + 7.5f, FS, lock.theme.onAccent(), Ui.M);
        }
        ui.end();
        gfx.alpha = 1f;
        gfx.pop();
    }

    private void drawWindow(Gfx g, Win w) {
        w.x = Math.max(-w.w + 40, Math.min(sw - 40, w.x));
        w.y = Math.max(0, Math.min(sh - HEADER, w.y));
        int maxView = Math.max(40, sh - w.y - HEADER - 6);
        int view = w.collapsed ? 0 : Math.min(w.contentH, maxView);
        int h = HEADER + view + (view > 0 ? 4 : 0);
        w.drawnH = h;
        g.shadow(w.x, w.y, w.w, h, 6, 0x70000000);
        g.rrect(w.x, w.y, w.w, h, RADIUS, BG);
        // block clicks falling through to windows below
        ui.region(w.x, w.y, w.w, h, (a, b, c) -> front(w.id), amount -> {}, null);
        drawHeader(g, w);
        if (view <= 0) return;
        int top = w.y + HEADER, bottom = top + view;
        w.scroll = Math.max(0, Math.min(w.scroll, Math.max(0, w.contentH - view)));
        ui.clip(w.x, top, w.x + w.w, bottom);
        ui.region(w.x, top, w.w, view, null, a -> w.scroll -= a * 24, null);
        int y0 = top - (int) w.scroll;
        int end = switch (w.id) {
            case "main" -> drawMain(g, w, y0);
            case "favorites" -> drawModuleList(g, w, y0, favorites());
            case "search" -> drawModuleList(g, w, y0, searchResults());
            case "profiles" -> drawProfiles(g, w, y0);
            case "waypoints" -> drawWaypoints(g, w, y0);
            case "keybinds" -> drawKeybinds(g, w, y0);
            case "diagnostics" -> drawDiagnostics(g, w, y0);
            default -> drawModuleList(g, w, y0, categoryModules(Category.valueOf(w.id.substring(4))));
        };
        ui.unclip();
        w.contentH = end - y0 + 4;
        w.scrollable = w.contentH > view;
        if (w.contentH > view) {
            int barH = Math.max(14, view * view / w.contentH);
            int barY = top + (int) ((view - barH) * (w.scroll / Math.max(1, w.contentH - view)));
            g.rrect(w.x + w.w - 4, barY, 2, barH, 1, 0x50FFFFFF);
        }
    }

    private void drawHeader(Gfx g, Win w) {
        g.rrect(w.x, w.y, w.w, HEADER, RADIUS, HEADER_BG);
        if (!w.collapsed) g.fill(w.x, w.y + HEADER - RADIUS, w.x + w.w, w.y + HEADER, HEADER_BG);
        if (!w.collapsed) g.fill(w.x, w.y + HEADER - 1, w.x + w.w, w.y + HEADER, LINE);
        float cy = w.y + HEADER / 2f;
        if (w.id.equals("main")) {
            float x = ui.text("lock", w.x + 9, cy, 10.5f, text(), Ui.SB);
            ui.text(".", x, cy, 10.5f, accent(), Ui.SB);
            ui.iconButton(w.x + w.w - 18, w.y + 3, 14, "gear", DIM, () -> toggleWin("cat:" + Category.INTERFACE.name()), "Interface settings");
        } else {
            String[] t = title(w.id);
            g.icon(t[1], w.x + 8, cy - 4.5f, 9, accent());
            ui.text(t[0], w.x + 21, cy, FS, text(), Ui.SB);
            ui.iconButton(w.x + w.w - 30, w.y + 4, 12, w.collapsed ? "chevron_down" : "chevron_up", DIM, () -> w.collapsed = !w.collapsed, w.collapsed ? "Expand" : "Collapse");
            ui.iconButton(w.x + w.w - 16, w.y + 4, 12, "x", DIM, () -> {
                w.open = false;
                if (w.id.equals("search")) search.text = "";
            }, "Close");
        }
        // drag handle: the header minus its buttons
        int dragW = w.id.equals("main") ? w.w - 22 : w.w - 34;
        ui.region(w.x, w.y, dragW, HEADER, (px, py, btn) -> {
            front(w.id);
            if (btn == 1 && !w.id.equals("main")) { w.collapsed = !w.collapsed; return; }
            int ox = px - w.x, oy = py - w.y;
            ui.drag = (mx, my) -> { w.x = mx - ox; w.y = my - oy; };
        });
    }

    private String[] title(String id) {
        if (id.startsWith("cat:")) {
            Category c = Category.valueOf(id.substring(4));
            return new String[]{c.label, CAT_ICONS.get(c)};
        }
        return switch (id) {
            case "favorites" -> new String[]{"Favorites", "star"};
            case "search" -> new String[]{"Search", "search"};
            case "profiles" -> new String[]{"Profiles", "layers"};
            case "waypoints" -> new String[]{"Waypoints", "pin"};
            case "keybinds" -> new String[]{"Keybinds", "keyboard"};
            default -> new String[]{"Diagnostics", "pulse"};
        };
    }

    // ------------------------------------------------------------------ main panel

    private int drawMain(Gfx g, Win w, int y) {
        int x = w.x;
        ui.field(x + 6, y + 5, w.w - 12, 14, search, "Search", "search");
        y += 24;
        for (Category c : Category.values()) {
            Win cw = wins.get("cat:" + c.name());
            y = navRow(g, x, y, w.w, CAT_ICONS.get(c), c.label, cw.open, null, () -> toggleWin(cw.id));
        }
        y += 4;
        ui.text("MISC", x + 9, y + 4, 5.5f, FAINT, Ui.SB);
        y += 9;
        y = navRow(g, x, y, w.w, "star", "Favorites", wins.get("favorites").open, null, () -> toggleWin("favorites"));
        y = navRow(g, x, y, w.w, "layers", "Profiles", wins.get("profiles").open, lock.config.profile().name(), () -> toggleWin("profiles"));
        y = navRow(g, x, y, w.w, "pin", "Waypoints", wins.get("waypoints").open, null, () -> toggleWin("waypoints"));
        y = navRow(g, x, y, w.w, "keyboard", "Keybinds", wins.get("keybinds").open, null, () -> toggleWin("keybinds"));
        int errs = Log.errorCount() + lock.modules.faultedIds().size();
        y = navRow(g, x, y, w.w, "pulse", "Diagnostics", wins.get("diagnostics").open, errs > 0 ? Integer.toString(errs) : null, () -> toggleWin("diagnostics"));
        y += 4;
        g.fill(x + 6, y, x + w.w - 6, y + 1, LINE);
        y += 4;
        // quick actions
        int bx = x + 6, size = 16;
        ui.iconButton(bx, y, size, "layout", DIM, () -> minecraft.setScreen(new HudEditorScreen(this)), "HUD editor");
        ui.iconButton(bx + size + 2, y, size, lock.visibility.clean() ? "x" : "eye", DIM, lock.visibility::cycle, "Visibility mode: " + lock.visibility.mode() + " (click to cycle)");
        ui.iconButton(bx + (size + 2) * 2, y, size, "folder", DIM, () -> net.minecraft.util.Util.getPlatform().openPath(LockPaths.home()), "Open Lock folder");
        String mode = lock.visibility.mode();
        float mw = ui.width(mode, FS_S, Ui.M) + 10;
        g.rrect((int) (x + w.w - 6 - mw), y + 3, (int) mw, 10, 5, 0x18FFFFFF);
        ui.text(mode, x + w.w - 6 - mw + 5, y + 8, FS_S, DIM, Ui.M);
        return y + size + 2;
    }

    private int navRow(Gfx g, int x, int y, int w, String icon, String label, boolean active, String pill, Runnable click) {
        boolean hov = ui.hover(x, y, w, ROW + 2);
        float t = ui.ease("nav" + label, active ? 1 : 0);
        if (hov || t > 0) g.rrect(x + 4, y + 1, w - 8, ROW, 4, ThemeManager.withAlpha(0xFFFFFF, (int) (hov ? 0x10 : 0) + (int) (0x0A * t)));
        int ic = ThemeManager.blend(DIM, accent(), t);
        g.icon(icon, x + 10, y + 1 + (ROW - 9) / 2f, 9, ic);
        ui.text(label, x + 24, y + 1 + ROW / 2f, FS, ThemeManager.blend(text(), accent(), t), Ui.M);
        if (pill != null) {
            String p = ui.ellipsize(pill, 46, FS_S);
            float pw = ui.width(p, FS_S, Ui.M) + 8;
            g.rrect((int) (x + w - 20 - pw), y + 4, (int) pw, 10, 5, 0x1CFFFFFF);
            ui.text(p, x + w - 16 - pw, y + 9, FS_S, DIM, Ui.M);
        }
        g.icon("chevron_right", x + w - 15, y + 1 + (ROW - 7) / 2f, 7, active ? accent() : FAINT);
        ui.region(x, y, w, ROW + 2, (a, b, c) -> click.run());
        return y + ROW + 2;
    }

    // ------------------------------------------------------------------ module columns

    private List<ClientModule> categoryModules(Category c) {
        List<ClientModule> out = new ArrayList<>();
        for (ClientModule m : lock.modules.all()) if (m.def.category == c) out.add(m);
        return out;
    }

    private List<ClientModule> favorites() {
        List<ClientModule> out = new ArrayList<>();
        for (ClientModule m : lock.modules.all()) if (lock.config.config().favorites.contains(m.def.id)) out.add(m);
        return out;
    }

    private List<ClientModule> searchResults() {
        List<ClientModule> out = new ArrayList<>();
        for (ClientModule m : lock.modules.all()) if (m.def.matches(search.text)) out.add(m);
        return out;
    }

    private int drawModuleList(Gfx g, Win w, int y, List<ClientModule> mods) {
        y += 3;
        if (mods.isEmpty()) {
            String msg = w.id.equals("favorites") ? "Middle-click a module to favourite it." : "No modules match.";
            for (String l : ui.wrap(msg, w.w - 16, FS_S)) { ui.text(l, w.x + 8, y + 6, FS_S, DIM, Ui.R); y += 9; }
            return y + 4;
        }
        for (ClientModule m : mods) {
            y = moduleRow(g, w, m, y);
            if (expanded.contains(m.def.id)) y = settings(g, m, w.x + 3, y, w.w - 6 - (w.scrollable ? 4 : 0));
        }
        return y + 1;
    }

    private int moduleRow(Gfx g, Win w, ClientModule m, int y) {
        int x = w.x + 3, rw = w.w - 6 - (w.scrollable ? 4 : 0);
        boolean on = m.def.toggleable() && m.enabled();
        boolean hov = ui.hover(x, y, rw, ROW);
        float t = ui.ease("row" + m.def.id, on ? 1 : 0);
        if (t > 0.01f) g.rrect(x, y + 1, rw, ROW - 1, 3, ThemeManager.withAlpha(accent(), (int) (0xFF * t)));
        if (hov) g.rrect(x, y + 1, rw, ROW - 1, 3, ROW_HOVER);
        int tc = ThemeManager.blend(m.def.toggleable() ? text() : DIM, lock.theme.onAccent(), t);
        float nx = ui.text(ui.ellipsize(m.def.name, rw - 40, FS), x + 6, y + ROW / 2f + 0.5f, FS, tc, Ui.M);
        if (lock.modules.faulted(m.def.id)) g.circle(nx + 4, y + ROW / 2f, 2, 0xFFF2575F);
        else if (lock.config.config().favorites.contains(m.def.id)) g.icon("star_fill", nx + 3, y + 5, 6, ThemeManager.blend(accent(), tc, t));
        int key = m.def.toggleable() ? m.state().key(ModuleDef.KEYBIND) : Keys.NONE;
        if (key != Keys.NONE) {
            String k = Keys.display(key);
            float kw = ui.width(k, FS_S, Ui.R);
            ui.text(k, x + rw - 16 - kw, y + ROW / 2f + 0.5f, FS_S, ThemeManager.withAlpha(tc, 0x90), Ui.R);
        }
        boolean open = expanded.contains(m.def.id);
        ui.region(x, y, rw - 14, ROW, (a, b, btn) -> rowClick(m, btn), null, m.def.description);
        ui.iconButton(x + rw - 14, y + 2, 12, open ? "chevron_up" : "dots", ThemeManager.blend(DIM, lock.theme.onAccent(), t), () -> toggleExpand(m.def.id), "Settings");
        return y + ROW;
    }

    private void toggleExpand(String id) { if (!expanded.remove(id)) expanded.add(id); }

    private void rowClick(ClientModule m, int button) {
        if (button == 2) {
            var fav = lock.config.config().favorites;
            if (!fav.remove(m.def.id)) fav.add(m.def.id);
            lock.config.changed();
        } else if (button == 1 || !m.def.toggleable()) toggleExpand(m.def.id);
        else lock.modules.toggle(m.def.id);
    }

    // ------------------------------------------------------------------ inline settings

    private int settings(Gfx g, ClientModule m, int x, int y, int w) {
        int top = y;
        int pad = 6, cx = x + pad, cw = w - pad * 2;
        // the panel background uses the height measured on the previous frame
        int h = heights.getOrDefault(m.def.id, 120);
        g.rrect(x, y + 1, w, h, 3, 0xFF111214);
        y += 5;
        for (String l : ui.wrap(m.def.description, cw, FS_S)) { ui.text(l, cx, y + 3, FS_S, DIM, Ui.R); y += 8; }
        if (m.def.support != Support.SUPPORTED) {
            int col = m.def.support == Support.PARTIAL ? 0xFFE9A23B : 0xFFF2575F;
            y += 2;
            for (String l : ui.wrap(m.def.support.label + ": " + m.def.supportNote, cw, FS_S)) { ui.text(l, cx, y + 3, FS_S, col, Ui.R); y += 8; }
        }
        if (lock.modules.faulted(m.def.id)) {
            ui.text("Stopped after errors", cx, y + 6, FS_S, 0xFFF2575F, Ui.M);
            ui.button(cx + cw - 34, y + 1, 34, 10, "Retry", () -> { lock.modules.resetFault(m.def.id); lock.modules.sync(m); });
            y += 12;
        }
        y += 3;
        String group = null;
        for (Setting<?> s : m.def.settings().values()) {
            if (!s.group.equals(group)) {
                group = s.group;
                if (!group.isEmpty() && !group.equals("Module")) {
                    y += 3;
                    ui.text(group.toUpperCase(), cx, y + 3, 5.5f, FAINT, Ui.SB);
                    y += 8;
                }
            }
            y = settingRow(g, m, s, cx, y, cw);
        }
        y += 3;
        int bw = m instanceof HudModule ? (cw - 4) / 2 : cw;
        ui.button(cx, y, bw, 12, "Reset", () -> {
            m.state().reset();
            lock.settingChanged(m, "*");
            lock.modules.sync(m);
            fields.keySet().removeIf(k -> k.startsWith(m.def.id + "."));
            flash(m.def.name + " reset");
        });
        if (m instanceof HudModule) ui.button(cx + bw + 4, y, bw, 12, "Position", () -> minecraft.setScreen(new HudEditorScreen(this, m.def.id)));
        y += 16;
        if (y - top != h + 1) heights.put(m.def.id, y - top - 1);
        return y + 2;
    }

    private final Map<String, Integer> heights = new HashMap<>();

    private int settingRow(Gfx g, ClientModule m, Setting<?> s, int x, int y, int w) {
        ModuleState st = m.state();
        if (!s.description.isEmpty() && ui.hover(x, y, w - 30, 11)) ui.tooltip(s.description);
        float cy = y + 6;
        switch (s) {
            case Setting.Bool b -> {
                ui.text(s.name, x, cy, FS_S, text(), Ui.R);
                boolean v = st.get(b);
                ui.toggle(x + w - 16, y + 2, v, () -> set(m, s.id, !v));
                ui.region(x, y, w - 20, 12, (a, b2, btn) -> set(m, s.id, !v));
                return y + 13;
            }
            case Setting.Num n -> {
                double v = st.get(n);
                ui.text(s.name, x, cy, FS_S, text(), Ui.R);
                String vs = n.format(v);
                if (n.max - n.min > 500) {
                    // large ranges (HUD offsets): stepper instead of a slider
                    float vw = ui.width(vs, FS_S, Ui.M);
                    ui.iconButton(x + w - 30 - (int) vw, y, 11, "chevron_left", DIM, () -> set(m, s.id, v - n.step), null);
                    ui.text(vs, x + w - 18 - vw, cy, FS_S, accent(), Ui.M);
                    ui.iconButton(x + w - 11, y, 11, "chevron_right", DIM, () -> set(m, s.id, v + n.step), null);
                    return y + 13;
                }
                ui.text(vs, x + w - ui.width(vs, FS_S, Ui.M), cy, FS_S, accent(), Ui.M);
                ui.slider(x, y + 10, w, v, n.min, n.max, nv -> set(m, s.id, nv));
                return y + 20;
            }
            case Setting.Choice c -> {
                ui.text(s.name, x, cy, FS_S, text(), Ui.R);
                String v = st.get(c);
                String shown = ui.ellipsize(pretty(v), w / 2f - 14, FS_S);
                float vw = ui.width(shown, FS_S, Ui.M);
                int bx = (int) (x + w - vw - 20);
                g.rrect(bx, y + 1, (int) vw + 20, 10, 5, 0x1CFFFFFF);
                g.icon("chevron_left", bx + 2, y + 2.5f, 7, DIM);
                ui.text(shown, bx + 10, cy, FS_S, text(), Ui.M);
                g.icon("chevron_right", bx + vw + 11, y + 2.5f, 7, DIM);
                ui.region(bx, y, (int) vw + 20, 12, (px, py, btn) -> set(m, s.id, c.next(v, (btn == 1 || px < bx + 10) ? -1 : 1)), null, null);
                return y + 13;
            }
            case Setting.Color c -> {
                int v = st.get(c);
                ui.text(s.name, x, cy, FS_S, text(), Ui.R);
                String key = m.def.id + "." + s.id;
                ui.swatch(x + w - 10, y + 1, 10, v);
                String hex = Setting.Color.format(v);
                ui.text(hex, x + w - 14 - ui.width(hex, FS_S, Ui.R), cy, FS_S, FAINT, Ui.R);
                ui.region(x + w - 60, y, 60, 12, (a, b2, btn) -> expandedColor = key.equals(expandedColor) ? null : key);
                y += 13;
                if (key.equals(expandedColor)) y = colorEditor(g, m, s.id, v, x, y, w);
                return y;
            }
            case Setting.Text t -> {
                ui.text(s.name, x, cy, FS_S, text(), Ui.R);
                Ui.Field f = fields.computeIfAbsent(m.def.id + "." + s.id, k -> new Ui.Field(st.get(t), t.maxLength, false));
                if (ui.focused != f) f.text = st.get(t);
                f.onChange = val -> set(m, s.id, val);
                ui.field(x, y + 11, w, 13, f, "");
                return y + 27;
            }
            case Setting.Key k -> {
                ui.text(s.name, x, cy, FS_S, text(), Ui.R);
                boolean listen = listening != null && listening.setting() == k && listening.module() == m;
                String label = listen ? "Press a key" : Keys.display(st.get(k));
                float lw = ui.width(label, FS_S, Ui.M) + 10;
                int bx = (int) (x + w - lw);
                g.rrect(bx, y + 1, (int) lw, 10, 5, listen ? accent() : 0x1CFFFFFF);
                ui.text(label, bx + 5, cy, FS_S, listen ? lock.theme.onAccent() : text(), Ui.M);
                ui.region(bx, y, (int) lw, 12, (a, b2, btn) -> listening = new ListenTarget(m, k));
                return y + 13;
            }
        }
    }

    private static String pretty(String v) {
        if (!v.contains("_") || !v.equals(v.toUpperCase())) return v;
        String s = v.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int colorEditor(Gfx g, ClientModule m, String id, int argb, int x, int y, int w) {
        float[] hsv = rgbToHsv(argb);
        int a = (argb >>> 24) & 0xFF;
        String[] labels = {"Hue", "Sat", "Val", "Alpha"};
        double[] vals = {hsv[0], hsv[1], hsv[2], a / 255.0};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            ui.text(labels[i], x, y + 3.5f, 5.5f, DIM, Ui.R);
            int sx = x + 22, swd = w - 24;
            if (i == 0) for (int px = 0; px < swd; px++) g.fill(sx + px, y + 2, sx + px + 1, y + 5, hsvToRgb(px / (float) swd, 1, 1, 255));
            ui.slider(sx, y, swd, vals[i], 0, 1, nv -> {
                double[] v = vals.clone();
                v[idx] = nv;
                set(m, id, hsvToRgb((float) v[0], (float) v[1], (float) v[2], (int) Math.round(v[3] * 255)));
            });
            y += 9;
        }
        return y + 3;
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
        // (no java.awt.Color: keep AWT usage to off-screen rasterising only)
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

    // ------------------------------------------------------------------ profiles

    private Ui.Field field(String key, String init, int max) { return fields.computeIfAbsent(key, k -> new Ui.Field(init, max, false)); }

    private int sectionLabel(String s, int x, int y) {
        ui.text(s.toUpperCase(), x, y + 5, 5.5f, FAINT, Ui.SB);
        return y + 10;
    }

    private int drawProfiles(Gfx g, Win w, int y) {
        LockConfig cfg = lock.config.config();
        int x = w.x + 3, rw = w.w - 6, cx = w.x + 8, cw = w.w - 16;
        y += 3;
        for (Profile p : new ArrayList<>(cfg.profiles().values())) {
            boolean active = p.name().equals(cfg.activeName());
            String key = "profile:" + p.name();
            float t = ui.ease(key, active ? 1 : 0);
            boolean hov = ui.hover(x, y, rw, ROW);
            if (t > 0.01f) g.rrect(x, y + 1, rw, ROW - 1, 3, ThemeManager.withAlpha(accent(), (int) (0xFF * t)));
            if (hov) g.rrect(x, y + 1, rw, ROW - 1, 3, ROW_HOVER);
            int tc = ThemeManager.blend(text(), lock.theme.onAccent(), t);
            float nx = ui.text(p.name(), x + 6, y + ROW / 2f + 0.5f, FS, tc, Ui.M);
            if (p.builtIn()) ui.text("built-in", nx + 5, y + ROW / 2f + 0.5f, FS_S, ThemeManager.withAlpha(tc, 0x80), Ui.R);
            String name = p.name();
            ui.region(x, y, rw - 14, ROW, (a, b, btn) -> {
                if (btn == 1) toggleExpand(key);
                else lock.switchProfile(name);
            }, null, active ? "Active profile" : "Click to switch");
            ui.iconButton(x + rw - 14, y + 2, 12, expanded.contains(key) ? "chevron_up" : "dots", ThemeManager.blend(DIM, lock.theme.onAccent(), t), () -> toggleExpand(key), "Actions");
            y += ROW;
            if (expanded.contains(key)) {
                int bw = (cw - 4) / 2;
                g.rrect(x, y + 1, rw, 44, 3, 0xFF111214);
                y += 4;
                Ui.Field nf = field("pname:" + name, "", 32);
                ui.field(cx, y, cw, 12, nf, "New name");
                y += 15;
                ui.button(cx, y, bw, 11, "Duplicate", () -> profileOp(cfg.duplicate(name, nf.text)));
                ui.button(cx + bw + 4, y, bw, 11, "Rename", () -> profileOp(cfg.rename(name, nf.text)));
                y += 13;
                ui.button(cx, y, (cw - 8) / 3, 11, "Reset", () -> { p.resetAll(); lock.config.changed(); lock.modules.syncAll(); flash("Profile reset"); });
                ui.button(cx + (cw - 8) / 3 + 4, y, (cw - 8) / 3, 11, "Export", () -> exportProfile(p));
                ui.button(cx + ((cw - 8) / 3 + 4) * 2, y, (cw - 8) / 3, 11, "Delete", () -> profileOp(cfg.delete(name)));
                y += 16;
            }
        }
        y += 4;
        y = sectionLabel("New profile", cx, y);
        Ui.Field nn = field("profile.new", "", 32);
        ui.field(cx, y, cw - 30, 12, nn, "Name");
        ui.button(cx + cw - 26, y, 26, 12, "Add", () -> { profileOp(cfg.create(nn.text)); nn.text = ""; }, true);
        y += 17;
        y = sectionLabel("HUD layouts", cx, y);
        Ui.Field ln = field("layout.name", "", 24);
        ui.field(cx, y, cw - 30, 12, ln, "Layout name");
        ui.button(cx + cw - 26, y, 26, 12, "Save", () -> {
            String err = LockConfig.validateName(ln.text);
            if (err != null) { flash(err); return; }
            cfg.layouts.put(ln.text.trim(), captureLayout());
            lock.config.changed();
            flash("Layout saved");
            ln.text = "";
        }, true);
        y += 15;
        for (String lname : new ArrayList<>(cfg.layouts.keySet())) {
            boolean hov = ui.hover(x, y, rw, 13);
            if (hov) g.rrect(x, y, rw, 13, 3, ROW_HOVER);
            g.icon("hud", cx, y + 3, 7, DIM);
            ui.text(lname, cx + 11, y + 6.5f, FS_S, text(), Ui.R);
            ui.region(x, y, rw - 14, 13, (a, b, c) -> { applyLayout(cfg.layouts.get(lname)); flash("Layout applied"); }, null, "Apply layout");
            ui.iconButton(x + rw - 13, y + 1, 11, "trash", DIM, () -> { cfg.layouts.remove(lname); lock.config.changed(); }, "Delete");
            y += 13;
        }
        y += 4;
        List<Path> files = exportFiles();
        if (!files.isEmpty()) {
            y = sectionLabel("Import from Lock/exports", cx, y);
            for (Path f : files) {
                boolean hov = ui.hover(x, y, rw, 13);
                if (hov) g.rrect(x, y, rw, 13, 3, ROW_HOVER);
                g.icon("download", cx, y + 3, 7, DIM);
                ui.text(ui.ellipsize(f.getFileName().toString(), cw - 12, FS_S), cx + 11, y + 6.5f, FS_S, text(), Ui.R);
                ui.region(x, y, rw, 13, (a, b, c) -> {
                    try { flash(ConfigStore.importFile(cfg, f)); lock.config.changed(); } catch (Exception e) { flash("Import failed: " + e.getMessage()); }
                }, null, "Import");
                y += 13;
            }
        }
        return y + 2;
    }

    private void profileOp(String error) {
        if (error != null) { flash(error); return; }
        lock.config.changed();
        lock.modules.syncAll();
        fields.keySet().removeIf(k -> k.startsWith("pname:"));
        flash("Done");
    }

    private void exportProfile(Profile p) {
        try {
            Path dir = LockPaths.home().resolve("exports");
            Files.createDirectories(dir);
            Path f = dir.resolve(p.name().replaceAll("[^A-Za-z0-9 _-]", "_") + ".json");
            ConfigStore.exportProfile(p, f);
            flash("Exported " + f.getFileName());
        } catch (Exception e) {
            flash("Export failed: " + e.getMessage());
        }
    }

    private List<Path> exportFiles() {
        Path dir = LockPaths.home().resolve("exports");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().limit(12).toList();
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

    private void applyLayout(Map<String, Object> layout) {
        for (HudModule h : lock.modules.huds()) {
            Map<String, Object> e = JsonUtil.obj(layout.get(h.def.id));
            if (e == null) continue;
            ModuleState st = h.state();
            if (e.get("enabled") instanceof Boolean b) st.setEnabled(b);
            for (String k : new String[]{ModuleDef.H_ANCHOR, ModuleDef.H_X, ModuleDef.H_Y, ModuleDef.H_SCALE})
                if (e.containsKey(k)) st.set(k, e.get(k));
        }
        lock.config.changed();
        lock.modules.syncAll();
    }

    // ------------------------------------------------------------------ waypoints

    private int drawWaypoints(Gfx g, Win w, int y) {
        int x = w.x + 3, rw = w.w - 6, cx = w.x + 8, cw = w.w - 16;
        boolean inWorld = minecraft.player != null;
        y += 4;
        ui.button(cx, y, cw - 64, 13, "Add at my position", () -> {
            Waypoint wp = lock.waypoints.addHere(null);
            if (wp != null) selectedWaypoint = wp.id;
            else flash("Join a world first");
        }, true);
        ui.button(cx + cw - 60, y, 60, 13, allDimensions ? "All dims" : "This dim", () -> allDimensions = !allDimensions);
        y += 17;
        List<Waypoint> list = !inWorld ? List.of() : allDimensions ? lock.waypoints.inScope() : lock.waypoints.here();
        if (list.isEmpty()) {
            ui.text(inWorld ? "No waypoints here yet." : "Join a world to manage waypoints.", cx, y + 6, FS_S, DIM, Ui.R);
            return y + 14;
        }
        Waypoint target = lock.waypoints.selected();
        for (Waypoint wp : list) {
            boolean isTarget = wp == target;
            float t = ui.ease("wp" + wp.id, isTarget ? 1 : 0);
            boolean hov = ui.hover(x, y, rw, ROW);
            if (t > 0.01f) g.rrect(x, y + 1, rw, ROW - 1, 3, ThemeManager.withAlpha(accent(), (int) (0xFF * t)));
            if (hov) g.rrect(x, y + 1, rw, ROW - 1, 3, ROW_HOVER);
            int tc = ThemeManager.blend(wp.visible ? text() : DIM, lock.theme.onAccent(), t);
            g.circle(x + 9, y + ROW / 2f + 0.5f, 3, wp.color | 0xFF000000);
            ui.text(ui.ellipsize(wp.name, rw - 70, FS), x + 17, y + ROW / 2f + 0.5f, FS, tc, Ui.M);
            double d = lock.waypoints.distance(wp);
            String info = d >= 0 ? Fmt.distance(d) + "m" : Fmt.prettyId(wp.dimension);
            ui.text(info, x + rw - 16 - ui.width(info, FS_S, Ui.R), y + ROW / 2f + 0.5f, FS_S, ThemeManager.withAlpha(tc, 0xA0), Ui.R);
            String id = wp.id;
            ui.region(x, y, rw - 14, ROW, (a, b, btn) -> {
                if (btn == 1) selectedWaypoint = id.equals(selectedWaypoint) ? null : id;
                else lock.waypoints.select(isTarget ? null : wp);
            }, null, "Click: set as target · Right-click: edit");
            ui.iconButton(x + rw - 14, y + 2, 12, id.equals(selectedWaypoint) ? "chevron_up" : "dots", ThemeManager.blend(DIM, lock.theme.onAccent(), t),
                    () -> selectedWaypoint = id.equals(selectedWaypoint) ? null : id, "Edit");
            y += ROW;
            if (id.equals(selectedWaypoint)) y = waypointEditor(g, wp, x, y, rw);
        }
        return y + 2;
    }

    private int waypointEditor(Gfx g, Waypoint sel, int x, int y, int w) {
        int cx = x + 5, cw = w - 10;
        g.rrect(x, y + 1, w, 76, 3, 0xFF111214);
        y += 4;
        Ui.Field nf = fields.computeIfAbsent("wp.name." + sel.id, k -> new Ui.Field(sel.name, 48, false));
        nf.onChange = v -> { if (!v.isBlank()) { sel.name = v; lock.config.changed(); } };
        ui.field(cx, y, cw, 12, nf, "Name");
        y += 15;
        String[] axes = {"X", "Y", "Z"};
        int fw = (cw - 6) / 3;
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
            ui.field(cx + i * (fw + 3), y, fw, 12, f, axes[i]);
        }
        y += 15;
        int[] palette = {0xFF26A884, 0xFF4C8DF6, 0xFF5CD6A9, 0xFFEC5F8A, 0xFFE9A23B, 0xFFF2575F, 0xFF8B6CF0, 0xFFFFFFFF};
        int px = cx;
        for (int c : palette) {
            boolean cur = (sel.color | 0xFF000000) == c;
            if (cur) g.circle(px + 5, y + 5, 6, 0xFFFFFFFF);
            g.circle(px + 5, y + 5, 4.5f, c);
            ui.region(px, y, 10, 10, (a, b, btn) -> { sel.color = c; lock.config.changed(); });
            px += 13;
        }
        ui.toggle(cx + cw - 16, y + 1, sel.visible, () -> { sel.visible = !sel.visible; lock.config.changed(); });
        y += 14;
        int bw = (cw - 4) / 2;
        ui.button(cx, y, bw, 12, "Move here", () -> {
            if (minecraft.player == null) return;
            sel.x = minecraft.player.getBlockX();
            sel.y = minecraft.player.getBlockY();
            sel.z = minecraft.player.getBlockZ();
            sel.dimension = GameInfo.dimension();
            fields.keySet().removeIf(k -> k.endsWith(sel.id));
            lock.config.changed();
        });
        ui.button(cx + bw + 4, y, bw, 12, "Delete", () -> { lock.waypoints.delete(sel); selectedWaypoint = null; });
        return y + 18;
    }

    // ------------------------------------------------------------------ keybinds

    private int drawKeybinds(Gfx g, Win w, int y) {
        int x = w.x + 3, rw = w.w - 6;
        y += 3;
        Map<Integer, List<String>> conflicts = lock.keys.conflicts();
        String q = search.text.toLowerCase();
        for (KeybindManager.Bind b : lock.keys.all()) {
            if (!q.isEmpty() && !b.label().toLowerCase().contains(q)) continue;
            int code = b.module().state().get(b.setting());
            boolean hov = ui.hover(x, y, rw, 14);
            if (hov) g.rrect(x, y, rw, 14, 3, ROW_HOVER);
            boolean listen = listening != null && listening.setting() == b.setting() && listening.module() == b.module();
            String label = listen ? "Press a key" : Keys.display(code);
            float lw = ui.width(label, FS_S, Ui.M) + 10;
            ui.text(ui.ellipsize(b.label(), rw - lw - 22, FS_S), x + 5, y + 7, FS_S, code == Keys.NONE ? DIM : text(), Ui.R);
            List<String> c = conflicts.get(code);
            if (c != null) {
                g.icon("info", x + rw - lw - 14, y + 3, 8, 0xFFE9A23B);
                if (ui.hover((int) (x + rw - lw - 15), y, 10, 14)) ui.tooltip("Also bound: " + String.join(", ", c));
            }
            int bx = (int) (x + rw - lw - 3);
            g.rrect(bx, y + 2, (int) lw, 10, 5, listen ? accent() : 0x1CFFFFFF);
            ui.text(label, bx + 5, y + 7, FS_S, listen ? lock.theme.onAccent() : text(), Ui.M);
            ui.region(bx, y, (int) lw, 14, (a, b2, btn) -> listening = new ListenTarget(b.module(), b.setting()));
            y += 14;
        }
        return y + 3;
    }

    // ------------------------------------------------------------------ diagnostics

    private int drawDiagnostics(Gfx g, Win w, int y) {
        int cx = w.x + 8, cw = w.w - 16;
        y += 2;
        y = sectionLabel("Runtime", cx, y);
        y = kv(cx, y, cw, "Lock", lock.agent.lockVersion());
        y = kv(cx, y, cw, "Minecraft", lock.agent.minecraftVersion());
        y = kv(cx, y, cw, "Java", System.getProperty("java.version") + " · " + System.getProperty("os.arch"));
        y = kv(cx, y, cw, "Hooks", lock.diagnostics.hooksApplied() + " active, " + lock.diagnostics.pendingHooks() + " waiting for class load");
        for (var h : lock.diagnostics.failedHooks()) y = kv(cx, y, cw, "Hook failed", h.name() + ": " + h.detail(), 0xFFF2575F);
        y = kv(cx, y, cw, "Startup", "agent " + lock.agent.startupInfo().get("agentInitMs") + " ms · client " + lock.agent.startupInfo().get("clientInitMs") + " ms");
        y += 3;
        y = sectionLabel("Modules", cx, y);
        int active = 0;
        for (ClientModule m : lock.modules.all()) if (m.active()) active++;
        y = kv(cx, y, cw, "Catalog", lock.modules.all().size() + " modules · " + active + " active");
        y = kv(cx, y, cw, "Faulted", lock.modules.faultedIds().isEmpty() ? "none" : String.join(", ", lock.modules.faultedIds()));
        y += 3;
        y = sectionLabel("Rendering", cx, y);
        y = kv(cx, y, cw, "HUD cost", String.format("%.0f µs / frame", lock.hud.averageCostMicros()));
        y = kv(cx, y, cw, "FPS", String.format("%d · avg %.0f · 1%% low %.0f", minecraft.getFps(), lock.stats.avgFps(), lock.stats.lowFps()));
        y += 3;
        y = sectionLabel("Configuration", cx, y);
        y = kv(cx, y, cw, "Validation", lock.config.loadProblems().isEmpty() ? "no problems" : lock.config.loadProblems().size() + " value(s) corrected");
        if (lock.config.recoveredFrom() != null) y = kv(cx, y, cw, "Recovered", lock.config.recoveredFrom(), 0xFFE9A23B);
        y += 3;
        y = sectionLabel("Capture", cx, y);
        for (String l : ui.wrap(Catalog.get("capture_status").supportNote, cw, FS_S)) { ui.text(l, cx, y + 4, FS_S, DIM, Ui.R); y += 8; }
        y += 4;
        y = sectionLabel("Recent warnings", cx, y);
        List<Log.Entry> log = Log.recent();
        int shown = 0;
        for (int i = log.size() - 1; i >= 0 && shown < 8; i--) {
            Log.Entry e = log.get(i);
            if (e.level() == Log.Level.INFO) continue;
            for (String l : ui.wrap(e.message(), cw, FS_S)) {
                ui.text(l, cx, y + 4, FS_S, e.level() == Log.Level.ERROR ? 0xFFF2575F : 0xFFE9A23B, Ui.R);
                y += 8;
            }
            shown++;
        }
        if (shown == 0) { ui.text("None", cx, y + 4, FS_S, DIM, Ui.R); y += 9; }
        y += 5;
        int bw = (cw - 4) / 2;
        ui.button(cx, y, bw, 13, "Write report", () -> {
            try { flash("Saved " + lock.diagnostics.writeReport().getFileName()); } catch (Exception e) { flash("Failed: " + e.getMessage()); }
        });
        boolean confirm = System.currentTimeMillis() - confirmResetAt < 3000;
        ui.button(cx + bw + 4, y, bw, 13, confirm ? "Confirm reset" : "Reset profile", () -> {
            if (System.currentTimeMillis() - confirmResetAt < 3000) {
                lock.config.profile().resetAll();
                lock.config.changed();
                lock.modules.syncAll();
                confirmResetAt = 0;
                flash("Active profile reset");
            } else confirmResetAt = System.currentTimeMillis();
        }, confirm);
        return y + 17;
    }

    private int kv(int x, int y, int w, String k, String v) { return kv(x, y, w, k, v, text()); }

    private int kv(int x, int y, int w, String k, String v, int color) {
        ui.text(k, x, y + 4, FS_S, DIM, Ui.R);
        for (String l : ui.wrap(v == null ? "" : v, w - 54, FS_S)) { ui.text(l, x + 54, y + 4, FS_S, color, Ui.R); y += 8; }
        return y + 1;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        int mx = local(e.x()), my = local(e.y());
        if (listening != null) {
            if (e.button() >= 2) {
                set(listening.module(), listening.setting().id, Keys.MOUSE_BASE + e.button());
                listening = null;
                return true;
            }
            listening = null;
        }
        for (int i = z.size() - 1; i >= 0; i--) {
            Win w = wins.get(z.get(i));
            if (w.open && w.contains(mx, my)) { front(w.id); break; }
        }
        Ui.Field before = ui.focused;
        ui.focused = null;
        ui.click(mx, my, e.button());
        if (ui.focused == null && before != null && before.onCommit != null) before.onCommit.accept(before.text);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        ui.drag = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (ui.drag != null) ui.drag.drag(local(e.x()), local(e.y()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        ui.scroll(local(x), local(y), sy);
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
                    if (key == GLFW.GLFW_KEY_ESCAPE && ui.focused == search) search.text = "";
                    ui.focused = null;
                }
                case GLFW.GLFW_KEY_V -> {
                    if ((e.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) ui.focused.type(minecraft.keyboardHandler.getClipboard());
                }
                default -> { }
            }
            return true;
        }
        if (key == lock.config.state("click_gui").key("openKey") || key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (ui.focused == null) {
            if (!Character.isLetterOrDigit(e.codepoint())) return false;
            ui.focused = search; // typing anywhere searches
        }
        ui.focused.type(e.codepointAsString());
        return true;
    }
}
