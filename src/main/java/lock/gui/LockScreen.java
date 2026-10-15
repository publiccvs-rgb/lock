package lock.gui;

import lock.LockClient;
import lock.module.Module;
import lock.render.Colors;
import lock.setting.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Single slim column of modules. Left click toggles, right click folds the settings open.
 * Dark glass panel, one accent colour, no borders, no icons.
 */
public class LockScreen extends Screen {
    private static final int W = 128, ROW = 15, SROW = 12;

    private final Map<Module, Float> open = new HashMap<>();
    private final Map<Module, Boolean> expanded = new HashMap<>();
    private final Map<Module, Float> glow = new HashMap<>();
    private NumSetting dragging;
    private int dragX, dragW;

    public LockScreen() { super(Component.literal("lock.")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        LockClient.modules.save();
        super.onClose();
    }

    private int accent() { return LockClient.modules.general.accent.get(); }

    private int panelX() { return 14; }
    private int panelY() { return 14; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0x55000000);
        int x = panelX(), y = panelY();
        int acc = accent();

        g.drawString(font, "lock", x + 2, y, 0xFFFFFFFF, false);
        g.drawString(font, ".", x + 2 + font.width("lock"), y, Colors.argb(acc, 1f), false);
        y += 14;

        int total = 0;
        for (Module m : LockClient.modules.all) {
            float o = open.getOrDefault(m, 0f);
            o += ((expanded.getOrDefault(m, false) ? 1f : 0f) - o) * Math.min(1f, dt * 0.45f);
            open.put(m, o);
            total += ROW + Math.round(m.settings.size() * SROW * o);
        }
        g.fill(x, y, x + W, y + total, 0xB00B0B10);

        for (Module m : LockClient.modules.all) {
            boolean hover = mx >= x && mx < x + W && my >= y && my < y + ROW;
            float gl = glow.getOrDefault(m, 0f);
            gl += ((m.enabled() ? 1f : 0f) - gl) * Math.min(1f, dt * 0.4f);
            glow.put(m, gl);

            if (hover) g.fill(x, y, x + W, y + ROW, 0x14FFFFFF);
            if (gl > 0.01f) {
                g.fill(x, y + 2, x + 1, y + ROW - 2, Colors.argb(acc, gl));
                g.fill(x + 1, y, x + 36, y + ROW, Colors.argb(acc, 0.07f * gl));
            }
            int tc = Colors.lerp(0x7C7C88, 0xFFFFFF, gl);
            g.drawString(font, m.name, x + 7, y + 4, 0xFF000000 | tc, false);
            if (!m.settings.isEmpty()) {
                g.drawString(font, expanded.getOrDefault(m, false) ? "-" : "+", x + W - 10, y + 4, 0xFF55555F, false);
            }
            y += ROW;

            float o = open.get(m);
            int h = Math.round(m.settings.size() * SROW * o);
            if (h > 0) {
                g.enableScissor(x, y, x + W, y + h);
                int sy = y;
                for (Setting<?> s : m.settings) {
                    drawSetting(g, s, x, sy, mx, my);
                    sy += SROW;
                }
                g.disableScissor();
                y += h;
            }
        }
    }

    private void drawSetting(GuiGraphics g, Setting<?> s, int x, int y, int mx, int my) {
        int acc = accent();
        int tx = x + 9;
        int ty = y + 2;
        g.drawString(font, s.name, tx, ty, 0xFF8A8A96, false);
        int rx = x + W - 8;

        if (s instanceof BoolSetting b) {
            // slim switch
            int sx = rx - 14;
            g.fill(sx, y + 4, sx + 14, y + 8, b.get() ? Colors.argb(acc, 0.55f) : 0xFF2A2A33);
            int kx = b.get() ? sx + 8 : sx;
            g.fill(kx, y + 3, kx + 6, y + 9, b.get() ? 0xFF000000 | acc : 0xFF5A5A66);
        } else if (s instanceof ModeSetting ms) {
            String v = ms.get();
            g.drawString(font, v, rx - font.width(v), ty, 0xFF000000 | acc, false);
        } else if (s instanceof NumSetting n) {
            String v = n.step >= 1 ? String.valueOf(n.i()) : String.format("%.2f", n.get());
            g.drawString(font, v, rx - font.width(v), ty, 0xFFB8B8C4, false);
            int bx = x + 9, bw = W - 18, by = y + SROW - 2;
            float f = (float) ((n.get() - n.min) / (n.max - n.min));
            g.fill(bx, by, bx + bw, by + 1, 0xFF2A2A33);
            g.fill(bx, by, bx + Math.round(bw * f), by + 1, 0xFF000000 | acc);
        } else if (s instanceof ColorSetting c) {
            int n = ColorSetting.PRESETS.length;
            for (int i = 0; i < n; i++) {
                int sx = rx - (n - i) * 9;
                int col = ColorSetting.PRESETS[i];
                g.fill(sx, y + 3, sx + 6, y + 9, 0xFF000000 | col);
                if (c.get() == col) g.fill(sx, y + 10, sx + 6, y + 11, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = panelX(), y = panelY() + 14;
        if (mx < x || mx >= x + W) return super.mouseClicked(mx, my, btn);

        for (Module m : LockClient.modules.all) {
            if (my >= y && my < y + ROW) {
                if (btn == 0) m.toggle();
                else if (btn == 1 && !m.settings.isEmpty()) expanded.merge(m, true, (a, b) -> !a);
                return true;
            }
            y += ROW;
            int h = Math.round(m.settings.size() * SROW * open.getOrDefault(m, 0f));
            if (my >= y && my < y + h) {
                int idx = (int) ((my - y) / SROW);
                if (idx < m.settings.size()) click(m.settings.get(idx), mx, btn, x);
                return true;
            }
            y += h;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void click(Setting<?> s, double mx, int btn, int x) {
        if (s instanceof BoolSetting b) b.toggle();
        else if (s instanceof ModeSetting m) m.cycle();
        else if (s instanceof NumSetting n) {
            dragging = n;
            dragX = x + 9;
            dragW = W - 18;
            slide(mx);
        } else if (s instanceof ColorSetting c) {
            int count = ColorSetting.PRESETS.length;
            int rx = x + W - 8;
            for (int i = 0; i < count; i++) {
                int sx = rx - (count - i) * 9;
                if (mx >= sx && mx < sx + 8) { c.set(ColorSetting.PRESETS[i]); return; }
            }
        }
    }

    private void slide(double mx) {
        if (dragging == null) return;
        double f = Mth.clamp((mx - dragX) / dragW, 0, 1);
        dragging.set(dragging.min + f * (dragging.max - dragging.min));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging != null) { slide(mx); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = null;
        return super.mouseReleased(mx, my, btn);
    }
}
