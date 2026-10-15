package dev.lock.client.render;

import java.util.Locale;

/** Formatting helpers shared by HUD elements. */
public final class Fmt {
    private Fmt() {}

    public static String coord(double v, int decimals) {
        if (decimals <= 0) return Long.toString((long) Math.floor(v));
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    public static String ticks(int ticks) {
        if (ticks < 0) return "∞";
        return duration(ticks * 50L);
    }

    public static String distance(double blocks) {
        if (blocks >= 10_000) return String.format(Locale.ROOT, "%.1fk", blocks / 1000);
        return String.format(Locale.ROOT, "%.0f", blocks);
    }

    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
    }

    /** Cardinal direction for a Minecraft yaw (0 = south). */
    public static String direction(float yaw, boolean longNames) {
        float y = ((yaw % 360) + 360) % 360;
        int i = Math.round(y / 45f) % 8;
        String[] s = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        String[] l = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
        return longNames ? l[i] : s[i];
    }

    /** Axis the player faces, e.g. "+Z". */
    public static String axis(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        int i = Math.round(y / 90f) % 4;
        return new String[]{"+Z", "-X", "-Z", "+X"}[i];
    }

    public static String prettyId(String id) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("[_/]")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
