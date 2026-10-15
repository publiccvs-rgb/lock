package dev.lock.core.ui;

import dev.lock.core.config.ModuleState;

/** Colours shared by the launcher and the in-game client. All values are ARGB. */
public final class Palette {
    public static final int BG = 0xFF111214, PANEL = 0xFF17181B, PANEL_2 = 0xFF1C1D20, SUNKEN = 0xFF0F1012,
            FIELD = 0xFF222327, LINE = 0x14FFFFFF, TEXT = 0xFFDCDDE1, DIM = 0xFF85878E, FAINT = 0xFF5D5F66,
            OK = 0xFF5CD6A9, WARN = 0xFFE9A23B, ERROR = 0xFFF2575F;

    private Palette() {}

    /** Accent for a theme preset ("Custom" uses the given custom colour). */
    public static int accent(String preset, int custom) {
        return switch (preset) {
            case "Blue" -> 0xFF4C8DF6;
            case "Mint" -> 0xFF5CD6A9;
            case "Rose" -> 0xFFEC5F8A;
            case "Amber" -> 0xFFE9A23B;
            case "Violet" -> 0xFF8B6CF0;
            case "Mono" -> 0xFFD0D2D8;
            case "Custom" -> custom;
            default -> 0xFF26A884;
        };
    }

    public static int accent(ModuleState theme) { return accent(theme.str("preset"), theme.color("accent")); }

    /** Black or white, whichever reads better on {@code argb}. */
    public static int onColor(int argb) {
        float lum = (((argb >> 16) & 0xFF) * 0.299f + ((argb >> 8) & 0xFF) * 0.587f + (argb & 0xFF) * 0.114f) / 255f;
        return lum > 0.72f ? 0xFF111214 : 0xFFFFFFFF;
    }
}
