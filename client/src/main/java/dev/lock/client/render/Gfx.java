package dev.lock.client.render;

import dev.lock.client.core.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Drawing facade over GuiGraphics: smooth TTF text (LockFont), anti-aliased rounded shapes,
 * shadows and icons. The current element opacity is applied to every colour. Text positions refer
 * to the top of capital letters, so layouts read the same as with the vanilla font.
 */
public final class Gfx {
    public static final float TEXT = 8f;
    public final GuiGraphics g;
    public final Font font;
    public float alpha = 1f;

    public Gfx(GuiGraphics g) {
        this.g = g;
        this.font = Minecraft.getInstance().font;
    }

    public int a(int c) { return alpha >= 0.999f ? c : ThemeManager.mulAlpha(c, alpha); }

    /** Physical pixels per current GUI unit (pose scale x window GUI scale). */
    public float realScale() {
        return Math.abs(g.pose().m00()) * Minecraft.getInstance().getWindow().getGuiScale();
    }

    // ------------------------------------------------------------------ shapes

    public void fill(int x1, int y1, int x2, int y2, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        g.fill(x1, y1, x2, y2, a(color));
    }

    public void outline(int x, int y, int w, int h, int color) {
        fill(x, y, x + w, y + 1, color);
        fill(x, y + h - 1, x + w, y + h, color);
        fill(x, y + 1, x + 1, y + h - 1, color);
        fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Rounded rectangle with the default small radius. */
    public void round(int x1, int y1, int x2, int y2, int color) { rrect(x1, y1, x2 - x1, y2 - y1, 3, color); }

    public void rrect(int x, int y, int w, int h, int r, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        Shapes.rrect(this, x, y, w, h, r, a(color));
    }

    /** Rounded outline drawn as an outer shape minus an inner one in the background colour. */
    public void rrectOutline(int x, int y, int w, int h, int r, int thickness, int color, int inner) {
        rrect(x, y, w, h, r, color);
        rrect(x + thickness, y + thickness, w - thickness * 2, h - thickness * 2, Math.max(0, r - thickness), inner);
    }

    public void circle(float cx, float cy, float radius, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        Shapes.circle(this, cx, cy, radius, a(color));
    }

    public void shadow(int x, int y, int w, int h, int spread, int color) { Shapes.shadow(this, x, y, w, h, spread, a(color)); }

    public void icon(String name, float x, float y, float size, int color) { Icons.draw(this, name, x, y, size, a(color)); }

    /** Render thread: makes sure the shared shape textures exist (part of warm-up). */
    public static void warmShapes() { Shapes.init(); }

    // ------------------------------------------------------------------ text

    /** Draws text with caps starting at y. Returns the x after the text (rounded up). */
    public int text(String s, int x, int y, int color, boolean shadow) {
        return Math.round(text(s, (float) x, (float) y, TEXT, color, LockFont.Weight.REGULAR));
    }

    public float text(String s, float x, float y, float size, int color, LockFont.Weight w) {
        if (s == null || s.isEmpty()) return x;
        return LockFont.draw(this, s, x, y - LockFont.capTop(size), size, a(color), w);
    }

    /** Draws text vertically centred on {@code cy}. */
    public float textMid(String s, float x, float cy, float size, int color, LockFont.Weight w) {
        return text(s, x, cy - LockFont.capHeight(size) / 2f, size, color, w);
    }

    public void textScaled(String s, float x, float y, float scale, int color, boolean shadow) {
        text(s, x, y, TEXT * scale, color, LockFont.Weight.REGULAR);
    }

    public void centered(String s, int cx, int y, int color, boolean shadow) {
        float w = LockFont.width(s, TEXT, LockFont.Weight.REGULAR);
        text(s, cx - w / 2f, y, TEXT, color, LockFont.Weight.REGULAR);
    }

    public int width(String s) { return s == null ? 0 : (int) Math.ceil(LockFont.width(s, TEXT, LockFont.Weight.REGULAR)); }

    public float width(String s, float size, LockFont.Weight w) { return LockFont.width(s, size, w); }

    // ------------------------------------------------------------------ misc

    public void item(ItemStack stack, int x, int y, boolean decorations) {
        if (stack == null || stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        if (decorations) g.renderItemDecorations(font, stack, x, y);
    }

    public void push() { g.pose().pushMatrix(); }
    public void pop() { g.pose().popMatrix(); }
    public void translate(float x, float y) { g.pose().translate(x, y); }
    public void scale(float s) { g.pose().scale(s, s); }
    public void scissor(int x1, int y1, int x2, int y2) { g.enableScissor(x1, y1, x2, y2); }
    public void unscissor() { g.disableScissor(); }

    /** Line between two points made of small squares (the GUI has no line primitive). */
    public void line(float x1, float y1, float x2, float y2, int color, int thickness) {
        float dx = x2 - x1, dy = y2 - y1;
        int steps = (int) Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) { fill((int) x1, (int) y1, (int) x1 + thickness, (int) y1 + thickness, color); return; }
        int half = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            int px = Math.round(x1 + dx * i / steps) - half, py = Math.round(y1 + dy * i / steps) - half;
            fill(px, py, px + thickness, py + thickness, color);
        }
    }
}
