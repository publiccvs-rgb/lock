package lock.render;

public final class Colors {
    private Colors() {}

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float mix(float t, float a, float b) { return a + (b - a) * t; }

    public static int argb(int rgb, float alpha) {
        int a = clamp((int) (alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, float mul) {
        int a = (int) (((argb >>> 24) & 0xFF) * mul);
        return (clamp(a, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        int r = (int) mix(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) mix(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) mix(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /** Soft red -> amber -> mint, deliberately desaturated so it stays easy on the eyes. */
    public static int health(float frac) {
        frac = clamp(frac, 0f, 1f);
        int red = 0xFF6B6B, amber = 0xFFD479, mint = 0x7DF0B4;
        return frac < 0.5f ? lerp(red, amber, frac * 2f) : lerp(amber, mint, (frac - 0.5f) * 2f);
    }

    /** Slow pulse between accent and a cooler tint, ~6s period. */
    public static int fade(int accent) {
        double t = (System.currentTimeMillis() % 6000L) / 6000.0;
        float k = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
        return lerp(accent, 0x7FD6FF, k);
    }

    public static float r(int c) { return ((c >> 16) & 0xFF) / 255f; }
    public static float g(int c) { return ((c >> 8) & 0xFF) / 255f; }
    public static float b(int c) { return (c & 0xFF) / 255f; }
    public static float a(int c) { return ((c >>> 24) & 0xFF) / 255f; }
}
