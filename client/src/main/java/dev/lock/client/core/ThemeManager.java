package dev.lock.client.core;

import dev.lock.core.config.ModuleState;

/** Resolves theme colours from the "theme" module. */
public final class ThemeManager {
    private final ConfigManager config;

    public ThemeManager(ConfigManager config) { this.config = config; }

    private ModuleState s() { return config.state("theme"); }

    public int accent() { return dev.lock.core.ui.Palette.accent(s()); }

    public int panel() { return "Custom".equals(s().str("preset")) ? s().color("panel") : 0xF717181B; }

    public int panelAlt() { return blend(panel(), 0xFFFFFFFF, 0.035f); }

    /** Deeper background used behind expanded settings. */
    public int sunken() { return blend(panel(), 0xFF000000, 0.35f); }

    public int text() { return "Custom".equals(s().str("preset")) ? s().color("text") : 0xFFDCDDE1; }

    /** Readable text colour on top of the accent. */
    public int onAccent() { return dev.lock.core.ui.Palette.onColor(accent()); }

    public int dim() { return withAlpha(text(), 0x99); }

    public int faint() { return withAlpha(text(), 0x22); }

    public static int withAlpha(int argb, int alpha) { return (argb & 0x00FFFFFF) | (alpha << 24); }

    public static int mulAlpha(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, Math.round(a * f))) << 24);
    }

    public static int blend(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
