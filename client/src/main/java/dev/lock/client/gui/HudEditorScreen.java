package dev.lock.client.gui;

import dev.lock.client.LockClient;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Gfx;
import dev.lock.client.render.LockFont;
import dev.lock.core.module.ModuleDef;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag-and-drop HUD layout editor: selection, alignment guides, optional grid snapping, scroll to
 * scale, per-element visibility, preview mode and resets. Positions are stored as anchor + offset,
 * so layouts adapt to window size, GUI scale and fullscreen/windowed changes.
 */
public final class HudEditorScreen extends Screen {
    private static final int SNAP = 4;
    private final Screen parent;
    private final LockClient lock = LockClient.get();
    private final Ui ui = new Ui(lock);
    private HudModule selected, dragging;
    private int dragDX, dragDY;
    private final List<int[]> guides = new ArrayList<>(); // {vertical?1:0, position}
    private boolean preview, panel = true;
    private double panelScroll;

    public HudEditorScreen(Screen parent) { this(parent, null); }

    public HudEditorScreen(Screen parent, String select) {
        super(Component.literal("Lock HUD Editor"));
        this.parent = parent;
        if (select != null && lock.modules.get(select) instanceof HudModule h) selected = h;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }

    @Override public void onClose() {
        lock.config.changed();
        lock.config.saveAsync();
        minecraft.setScreen(parent);
    }

    private boolean snap() { return lock.config.state("hud_editor").bool("snap"); }
    private int grid() { return lock.config.state("hud_editor").integer("grid"); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Gfx gfx = new Gfx(g);
        if (!preview) {
            g.fill(0, 0, width, height, 0x40000000);
            if (snap()) {
                int step = grid() * 4;
                for (int x = 0; x < width; x += step) g.fill(x, 0, x + 1, height, 0x10FFFFFF);
                for (int y = 0; y < height; y += step) g.fill(0, y, width, y + 1, 0x10FFFFFF);
            }
            g.fill(width / 2, 0, width / 2 + 1, height, 0x20FFFFFF);
            g.fill(0, height / 2, width, height / 2 + 1, 0x20FFFFFF);
        }
        lock.hud.render(gfx, true);
        if (!preview) {
            for (HudModule m : lock.modules.huds()) {
                if (!m.active()) continue;
                boolean hov = m == hovered(mx, my), sel = m == selected;
                int c = sel ? lock.theme.accent() : hov ? 0xB0FFFFFF : 0x30FFFFFF;
                gfx.outline(m.lastX - 1, m.lastY - 1, m.lastW + 2, m.lastH + 2, c);
                if (sel || hov) {
                    String label = m.def.name + "  " + Math.round(lock.hud.scale(m) * 100) + "%";
                    float lw = gfx.width(label, 6.5f, LockFont.Weight.MEDIUM) + 10;
                    int ly = m.lastY > 14 ? m.lastY - 13 : m.lastY + m.lastH + 3;
                    gfx.rrect(m.lastX - 1, ly, (int) lw, 11, 5, sel ? lock.theme.accent() : 0xE0222327);
                    gfx.textMid(label, m.lastX + 4, ly + 5.5f, 6.5f, sel ? lock.theme.onAccent() : 0xFFFFFFFF, LockFont.Weight.MEDIUM);
                }
            }
            if (lock.config.state("hud_editor").bool("guides"))
                for (int[] gd : guides) {
                    if (gd[0] == 1) g.fill(gd[1], 0, gd[1] + 1, height, ThemeManager.withAlpha(lock.theme.accent(), 0xC0));
                    else g.fill(0, gd[1], width, gd[1] + 1, ThemeManager.withAlpha(lock.theme.accent(), 0xC0));
                }
            k = chromeScale();
            gfx.push();
            gfx.scale(k);
            ui.begin(gfx, (int) (mx / k), (int) (my / k));
            cw = (int) (width / k);
            ch = (int) (height / k);
            toolbar(gfx);
            if (panel) elementPanel(gfx);
            ui.end();
            gfx.pop();
        } else {
            ui.begin(gfx, mx, my);
            String p = "Preview \u2014 P or Esc to return";
            gfx.textMid(p, 6, height - 8, 6.5f, 0x90FFFFFF, LockFont.Weight.REGULAR);
            ui.region(0, 0, width, height, (a, b, c) -> preview = false);
            ui.end();
            k = 1f;
        }
    }

    private float k = 1f;
    private int cw, ch;

    private float chromeScale() { return (float) (lock.config.state("click_gui").num("scale") * 1.6 / minecraft.getWindow().getGuiScale()); }

    private void toolbar(Gfx g) {
        var st = lock.config.state("hud_editor");
        String[][] items = {
                {"grid", "Grid snapping: " + (snap() ? "on" : "off") + " (G)"},
                {"crosshair", "Alignment guides: " + (st.bool("guides") ? "on" : "off")},
                {"refresh", "Reset selected element (R)"},
                {"trash", "Reset all positions"},
                {"list", "Element list"},
                {"eye", "Preview (P)"}};
        int size = 16, gap = 2, doneW = 40;
        int total = items.length * (size + gap) + doneW + 10;
        int x = (cw - total) / 2, y = 6;
        g.shadow(x, y, total, size + 6, 5, 0x80000000);
        g.rrect(x, y, total, size + 6, 6, 0xF8161719);
        int bx = x + 4;
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            boolean on = (i == 0 && snap()) || (i == 1 && st.bool("guides")) || (i == 4 && panel);
            ui.iconButton(bx, y + 3, size, items[i][0], on ? lock.theme.accent() : 0xFF9A9CA3, () -> toolbarAction(idx), items[i][1]);
            bx += size + gap;
        }
        ui.button(bx + 2, y + 4, doneW, size - 2, "Done", () -> toolbarAction(6), true);
        String hint = "Drag to move \u00B7 Scroll to scale \u00B7 Shift disables snapping \u00B7 Right-click for settings";
        float hw = g.width(hint, 6f, LockFont.Weight.REGULAR);
        g.textMid(hint, (cw - hw) / 2f, y + size + 14, 6f, 0x90FFFFFF, LockFont.Weight.REGULAR);
    }

    private void toolbarAction(int i) {
        var st = lock.config.state("hud_editor");
        switch (i) {
            case 0 -> st.set("snap", !st.bool("snap"));
            case 1 -> st.set("guides", !st.bool("guides"));
            case 2 -> { if (selected != null) lock.hud.resetPosition(selected); }
            case 3 -> { for (HudModule m : lock.modules.huds()) lock.hud.resetPosition(m); }
            case 4 -> panel = !panel;
            case 5 -> preview = true;
            case 6 -> onClose();
            default -> { }
        }
        lock.config.changed();
    }

    /** Per-element visibility list, styled like a Click GUI column. */
    private void elementPanel(Gfx g) {
        int rowH = 14, header = 18;
        int x = 6, y = 34, w = 118;
        int h = Math.min(ch - y - 6, lock.modules.huds().size() * rowH + header + 6);
        g.shadow(x, y, w, h, 6, 0x70000000);
        g.rrect(x, y, w, h, 5, 0xF8161719);
        g.rrect(x, y, w, header, 5, 0xFF1C1D20);
        g.fill(x, y + header - 5, x + w, y + header, 0xFF1C1D20);
        g.icon("hud", x + 7, y + 5, 8, lock.theme.accent());
        g.textMid("HUD elements", x + 19, y + header / 2f, 7f, lock.theme.text(), LockFont.Weight.SEMIBOLD);
        int top = y + header + 2, bottom = y + h - 3;
        int content = lock.modules.huds().size() * rowH;
        panelScroll = Math.max(0, Math.min(panelScroll, Math.max(0, content - (bottom - top))));
        ui.clip(x, top, x + w, bottom);
        ui.region(x, top, w, bottom - top, null, a -> panelScroll -= a * 20, null);
        int ry = top - (int) panelScroll;
        for (HudModule m : lock.modules.huds()) {
            if (ry + rowH >= top && ry <= bottom) {
                boolean on = m.enabled();
                float t = ui.ease("ed" + m.def.id, on ? 1 : 0);
                if (t > 0.01f) g.rrect(x + 3, ry, w - 6, rowH - 1, 3, ThemeManager.withAlpha(lock.theme.accent(), (int) (0xFF * t)));
                if (ui.hover(x + 3, ry, w - 6, rowH - 1)) g.rrect(x + 3, ry, w - 6, rowH - 1, 3, 0x0EFFFFFF);
                int tc = ThemeManager.blend(lock.theme.text(), lock.theme.onAccent(), t);
                g.textMid(ui.ellipsize(m.def.name, w - 16, 6.5f), x + 8, ry + rowH / 2f, 6.5f, tc, LockFont.Weight.MEDIUM);
                ui.region(x + 3, ry, w - 6, rowH - 1, (a, b, c) -> {
                    if (c == 1) { if (m.enabled()) selected = m; }
                    else lock.modules.setEnabled(m.def.id, !m.enabled());
                }, null, "Click: show/hide \u00B7 Right-click: select");
            }
            ry += rowH;
        }
        ui.unclip();
    }

    private HudModule hovered(int mx, int my) {
        List<HudModule> list = lock.modules.huds();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule m = list.get(i);
            if (m.active() && mx >= m.lastX && my >= m.lastY && mx < m.lastX + m.lastW && my < m.lastY + m.lastH) return m;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int mx = (int) e.x(), my = (int) e.y();
        if (ui.click((int) (e.x() / k), (int) (e.y() / k), e.button())) return true;
        HudModule h = hovered(mx, my);
        selected = h;
        if (h != null && e.button() == 0) {
            dragging = h;
            dragDX = mx - h.lastX;
            dragDY = my - h.lastY;
        } else if (h != null && e.button() == 1) {
            minecraft.setScreen(LockScreen.forModule(h.def.id));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        int mx = (int) e.x(), my = (int) e.y();
        if (ui.drag != null) { ui.drag.drag((int) (e.x() / k), (int) (e.y() / k)); return true; }
        if (dragging == null) return true;
        int x = mx - dragDX, y = my - dragDY;
        int w = dragging.lastW, h = dragging.lastH;
        guides.clear();
        if (!hasShift()) {
            int[] sx = snapAxis(x, w, width, true);
            int[] sy = snapAxis(y, h, height, false);
            x = sx[0];
            y = sy[0];
            if (snap() && sx[1] == 0) x = Math.round(x / (float) grid()) * grid();
            if (snap() && sy[1] == 0) y = Math.round(y / (float) grid()) * grid();
        }
        x = Math.max(0, Math.min(width - w, x));
        y = Math.max(0, Math.min(height - h, y));
        lock.hud.moveTo(dragging, x, y, width, height);
        return true;
    }

    private boolean hasShift() {
        long win = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    /** Snaps an edge/centre of the dragged box to screen edges, centre and other elements. Returns {pos, snapped?1:0}. */
    private int[] snapAxis(int pos, int size, int screen, boolean vertical) {
        List<Integer> targets = new ArrayList<>(List.of(0, screen / 2, screen));
        for (HudModule o : lock.modules.huds()) {
            if (o == dragging || !o.active()) continue;
            int a = vertical ? o.lastX : o.lastY, s = vertical ? o.lastW : o.lastH;
            targets.add(a);
            targets.add(a + s);
            targets.add(a + s / 2);
        }
        int[] edges = {pos, pos + size / 2, pos + size};
        for (int t : targets) {
            for (int k = 0; k < 3; k++) {
                if (Math.abs(edges[k] - t) <= SNAP) {
                    int np = t - (k == 0 ? 0 : k == 1 ? size / 2 : size);
                    guides.add(new int[]{vertical ? 1 : 0, t});
                    return new int[]{np, 1};
                }
            }
        }
        return new int[]{pos, 0};
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) lock.config.changed();
        dragging = null;
        ui.drag = null;
        guides.clear();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (ui.scroll((int) (x / k), (int) (y / k), sy)) return true;
        HudModule h = hovered((int) x, (int) y);
        if (h == null) h = selected;
        if (h == null) return true;
        double s = h.state().num(ModuleDef.H_SCALE) + Math.signum(sy) * 0.05;
        h.state().set(ModuleDef.H_SCALE, s);
        selected = h;
        lock.config.changed();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (preview) { preview = false; return true; }
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_P) { preview = !preview; return true; }
        if (key == GLFW.GLFW_KEY_G) { toolbarAction(0); return true; }
        if (selected == null) return super.keyPressed(e);
        int step = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
        int x = selected.lastX, y = selected.lastY;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> x -= step;
            case GLFW.GLFW_KEY_RIGHT -> x += step;
            case GLFW.GLFW_KEY_UP -> y -= step;
            case GLFW.GLFW_KEY_DOWN -> y += step;
            case GLFW.GLFW_KEY_R -> { lock.hud.resetPosition(selected); lock.config.changed(); return true; }
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> { lock.modules.setEnabled(selected.def.id, false); selected = null; return true; }
            default -> { return super.keyPressed(e); }
        }
        lock.hud.moveTo(selected, Math.max(0, Math.min(width - selected.lastW, x)), Math.max(0, Math.min(height - selected.lastH, y)), width, height);
        lock.config.changed();
        return true;
    }
}
