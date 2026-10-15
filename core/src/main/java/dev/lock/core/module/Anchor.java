package dev.lock.core.module;

/** Screen anchor for HUD elements. Offsets are applied relative to the anchor so layouts survive resolution changes. */
public enum Anchor {
    TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
    MIDDLE_LEFT(0, 1), CENTER(1, 1), MIDDLE_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

    /** 0 = left/top, 1 = center, 2 = right/bottom. */
    public final int hx, vy;

    Anchor(int hx, int vy) { this.hx = hx; this.vy = vy; }

    /** Resolves the top-left pixel of a box of size w*h placed at (offX, offY) from this anchor. */
    public int resolveX(int screenW, int w, int offX) {
        return switch (hx) {
            case 0 -> offX;
            case 1 -> (screenW - w) / 2 + offX;
            default -> screenW - w - offX;
        };
    }

    public int resolveY(int screenH, int h, int offY) {
        return switch (vy) {
            case 0 -> offY;
            case 1 -> (screenH - h) / 2 + offY;
            default -> screenH - h - offY;
        };
    }

    /** Inverse of resolve: offset that places a box with top-left (x, y) at this anchor. */
    public int offsetX(int screenW, int w, int x) {
        return switch (hx) {
            case 0 -> x;
            case 1 -> x - (screenW - w) / 2;
            default -> screenW - w - x;
        };
    }

    public int offsetY(int screenH, int h, int y) {
        return switch (vy) {
            case 0 -> y;
            case 1 -> y - (screenH - h) / 2;
            default -> screenH - h - y;
        };
    }

    /** Picks the nearest anchor for a box centred at (cx, cy). */
    public static Anchor nearest(int screenW, int screenH, int cx, int cy) {
        int hx = cx < screenW / 3 ? 0 : cx > screenW * 2 / 3 ? 2 : 1;
        int vy = cy < screenH / 3 ? 0 : cy > screenH * 2 / 3 ? 2 : 1;
        for (Anchor a : values()) if (a.hx == hx && a.vy == vy) return a;
        return TOP_LEFT;
    }
}
