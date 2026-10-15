package lock.gui;

/**
 * Flat dark theme matching Vape's actual rendering: solid near-black panels (no gradients/glass), thin
 * 1px dividers for structure instead of borders, and a handful of named accent schemes picked from a
 * horizontal strip like the reference Options page.
 */
final class Theme {
    private Theme() {}

    // panel surfaces - flat, no gradient
    static final int PANEL = 0xFF131313;
    static final int HEADER = 0xFF161616;
    static final int SIDEBAR = 0xFF121212;
    static final int ROW_HOVER = 0xFF1C1C1C;
    static final int INSET = 0xFF0D0D0D;
    static final int FIELD = 0xFF1A1A1A;
    static final int FIELD_HOVER = 0xFF202020;
    static final int CHIP = 0xFF1E1E1E;
    static final int TRACK = 0xFF2B2B2B;
    static final int DIVIDER = 0xFF232323;
    static final int TOGGLE_OFF = 0xFF2E2E2E;

    // text
    static final int TITLE = 0xFFF2F2F2;
    static final int TEXT = 0xFFE4E4E4;
    static final int DESC = 0xFF6E6E6E;
    static final int LABEL = 0xFF9A9A9A;
    static final int DIM = 0xFF5C5C5C;
    static final int DARK = 0xFF0A0A0A;
    static final int WARN = 0xFFFF5C5C;

    static final int TIP_BG = 0xF20E0E0E;

    /** Named accent schemes, sampled to match the reference strip's hues. */
    static final String[] SCHEME_NAMES = {"Cobalt", "Mint", "Gold", "Fire", "Cherry", "Amethyst", "Watermelon", "Black"};
    static final int[] SCHEME_COLORS = {
        0x3B82F6, // Cobalt
        0x1CE8A6, // Mint
        0xE8B84B, // Gold
        0xFF7A3D, // Fire
        0xFF4D6D, // Cherry
        0xA368FF, // Amethyst
        0xFF5DA0, // Watermelon
        0xD8D8D8, // Black (near-white accent on black panels, like the reference's "Black" swatch)
    };

    static int schemeIndex(int rgb) {
        for (int i = 0; i < SCHEME_COLORS.length; i++) if (SCHEME_COLORS[i] == rgb) return i;
        return -1;
    }

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

    static int brighten(int argb, float t) { return mix(argb, 0xFFFFFFFF, t); }
    static int darken(int argb, float t) { return mix(argb, 0xFF000000, t); }

    /** Readable ink colour for text/icons drawn on top of a filled accent row (dark accents need light ink). */
    static int inkOn(int accent) {
        int r = (accent >> 16) & 255, g = (accent >> 8) & 255, b = accent & 255;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return lum > 0.55 ? DARK : 0xFFF5F5F5;
    }
}
