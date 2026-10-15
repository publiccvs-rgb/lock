package lock.gui;

/** Palette, sampled by eye from the reference: near-black columns, one saturated mint accent. */
final class Theme {
    private Theme() {}

    static final int PANEL = 0xFF111111;
    static final int STRIP = 0xFF181818;
    static final int HOVER = 0xFF1B1B1B;
    static final int INSET = 0xFF0C0C0C;
    static final int FIELD = 0xFF1A1A1A;
    static final int CHIP = 0xFF1E1E1E;

    static final int TEXT = 0xFFEDEDED;
    static final int LABEL = 0xFF9C9C9C;
    static final int DIM = 0xFF7E7E7E;
    static final int FAINT = 0xFF3C3C3C;
    static final int DARK = 0xFF0A0A0A;
    static final int TRACK = 0xFF2A2A2A;
    static final int WHITE_ROW = 0xFFF1F1F1;
    static final int STAR = 0xFFF5A524;
    static final int WARN = 0xFFE5484D;
    static final int TIP_BG = 0xF20E0E0E;

    static int mix(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
            | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int alpha(int argb, float m) {
        int a = (int) ((argb >>> 24) * m);
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }
}
