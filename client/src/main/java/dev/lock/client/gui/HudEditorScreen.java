package dev.lock.client.gui;

import dev.lock.client.LockClient;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Gfx;
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
        ui.begin(gfx, mx, my);
        if (!preview) {
            g.fill(0, 0, width, height, 0x50000000);
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
                int c = sel ? lock.theme.accent() : hov ? 0xC0FFFFFF : 0x40FFFFFF;
                gfx.outline(m.lastX - 1, m.lastY - 1, m.lastW + 2, m.lastH + 2, c);
                if (sel || hov) {
                    String label = m.def.name + "  " + Math.round(lock.hud.scale(m) * 100) + "%";
                    int ly = m.lastY > 12 ? m.lastY - 11 : m.lastY + m.lastH + 2;
                    gfx.round(m.lastX - 1, ly - 1, m.lastX + gfx.width(label) + 3, ly + 9, 0xD0101114);
                    gfx.text(label, m.lastX + 1, ly, sel ? lock.theme.accent() : 0xFFFFFFFF, false);
                }
            }
            if (lock.config.state("hud_editor").bool("guides"))
                for (int[] gd : guides) {
                    if (gd[0] == 1) g.fill(gd[1], 0, gd[1] + 1, height, ThemeManager.withAlpha(lock.theme.accent(), 0xA0));
                    else g.fill(0, gd[1], width, gd[1] + 1, ThemeManager.withAlpha(lock.theme.accent(), 0xA0));
                }
            toolbar(gfx);
            if (panel) elementPanel(gfx);
        } else {
            gfx.text("Preview — press P or Esc", 4, height - 12, 0x80FFFFFF, true);
            ui.region(0, 0, width, height, (a, b, c) -> preview = false);
        }
        ui.end();
    }

    private void toolbar(Gfx g) {
        String[] labels = {"Grid: " + (snap() ? "on" : "off"), "Guides: " + (lock.config.state("hud_editor").bool("guides") ? "on" : "off"),
                "Reset selected", "Reset all", "Elements", "Preview", "Done"};
        int total = 0;
        for (String l : labels) total += g.width(l) + 14;
        int x = (width - total) / 2, y = 4;
        g.round(x - 4, y - 2, x + total, y + 16, 0xE0121317);
        for (int i = 0; i < labels.length; i++) {
            int w = g.width(labels[i]) + 10;
            final int idx = i;
            ui.button(x, y, w, 14, labels[i], () -> toolbarAction(idx), idx == labels.length - 1);
            x += w + 4;
        }
        String hint = "Drag to move · Scroll to scale · Arrows nudge · Right-click for settings · Del hides";
        g.text(hint, (width - g.width(hint)) / 2, y + 18, 0x90FFFFFF, true);
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

    /** Per-element visibility list. */
    private void elementPanel(Gfx g) {
        int x = 4, y = 40, w = 130, h = Math.min(height - 48, lock.modules.huds().size() * 13 + 16);
        g.round(x, y, x + w, y + h, 0xE0121317);
        g.text("HUD elements", x + 5, y + 4, lock.theme.dim(), false);
        int top = y + 15, bottom = y + h - 2;
        int content = lock.modules.huds().size() * 13;
        panelScroll = Math.max(0, Math.min(panelScroll, Math.max(0, content - (bottom - top))));
        ui.clip(x, top, x + w, bottom);
        ui.region(x, top, w, bottom - top, null, a -> panelScroll -= a * 20, null);
        int ry = top - (int) panelScroll;
        for (HudModule m : lock.modules.huds()) {
            if (ry + 13 >= top && ry <= bottom) {
                g.text(ui.ellipsize(m.def.name, w - 32), x + 5, ry + 2, m.enabled() ? lock.theme.text() : lock.theme.dim(), false);
                ui.toggle(x + w - 22, ry + 1, m.enabled(), () -> lock.modules.setEnabled(m.def.id, !m.enabled()));
                ui.region(x, ry, w - 26, 12, (a, b, c) -> { if (m.enabled()) selected = m; });
            }
            ry += 13;
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
        if (ui.click(mx, my, e.button())) return true;
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
        if (ui.drag != null) { ui.drag.drag(mx, my); return true; }
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
        if (ui.scroll((int) x, (int) y, sy)) return true;
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
