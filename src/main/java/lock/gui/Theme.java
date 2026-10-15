package lock.gui;

/**
 * Navy glass with one cyan accent. Values are read off the reference: near-black blue panels lit slightly from the
 * top, hairline cyan-tinted borders, muted blue-grey secondary text, white titles.
 */
final class Theme {
    private Theme() {}

    // surfaces (top -> bottom gradients give the glass depth)
    static final int PANEL_TOP = 0xFF0E2033, PANEL_BOT = 0xFF08131F;
    static final int HEAD_TOP = 0xFF12283D, HEAD_BOT = 0xFF0D2033;
    static final int SIDE_TOP = 0xFF0F2236, SIDE_BOT = 0xFF09141F;
    static final int INSET = 0xFF070F19;
    static final int BORDER = 0xFF1A3A50;
    static final int BORDER_SOFT = 0xFF112B3E;
    static final int RULE = 0xFF112538;
    static final int GLOW = 0x2222D3EE;
    static final int SHADOW = 0x38000000;

    // controls
    static final int FIELD = 0xFF0A1524;
    static final int FIELD_HI = 0xFF0E1C2F;
    static final int FIELD_BORDER = 0xFF1B3148;
    static final int TRACK = 0xFF15283D;
    static final int HOVER = 0x14FFFFFF;

    // text
    static final int TITLE = 0xFFF2F7FC;
    static final int TEXT = 0xFFE4EDF7;
    static final int DESC = 0xFF627791;
    static final int LABEL = 0xFF8FA3BC;
    static final int DIM = 0xFF5A6E88;
    static final int DARK = 0xFF04202B;
    static final int WHITE_ROW = 0xFFEAF3FB;
    static final int WARN = 0xFFFF6B6B;
    static final int GOOD = 0xFF4DFF6B;

    static final int TIP_BG = 0xF2070F19;

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

    /** Lighter / darker variants of an accent for gradients. */
    static int brighten(int argb, float t) { return mix(argb, 0xFFFFFFFF, t); }
    static int darken(int argb, float t) { return mix(argb, 0xFF000000, t); }
}
