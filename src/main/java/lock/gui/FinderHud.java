package lock.gui;

import lock.basefinder.Verdict;
import lock.basefinder.Verdict.Family;

import java.util.List;
import java.util.Locale;

/**
 * Overlay for the chunk finder: the hover card shown next to a chunk marker and the ranked target list.
 * Draws through {@link Gfx}, so it renders identically in-game and in the preview tool.
 */
public final class FinderHud {
    private FinderHud() {}

    public static final int CARD_W = 206;

    /** Where a chunk is relative to the player: compass bearing and horizontal distance in blocks. */
    public static String bearing(double dx, double dz) {
        String[] pts = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.round(((ang % 360) + 360) % 360 / 45.0) % 8;
        return pts[i] + " " + Math.round(Math.hypot(dx, dz)) + "m";
    }

    static int typeColor(Verdict v, int accent) {
        return switch (v.type) {
            case BASE -> accent;
            case STORAGE -> 0xFFFFC857;
            case FARM -> 0xFFFF7AC8;
            case PORTAL -> 0xFFB48CFF;
            case TUNNELS, STRIP_MINE -> 0xFF6BE58A;
            case HIDDEN_VOID -> 0xFF5AA8FF;
            case OUTPOST, ACTIVITY -> 0xFFE8EEF7;
            default -> 0xFF9BA9BD;
        };
    }

    static int familyColor(Family f, int accent) {
        return switch (f) {
            case BUILT -> accent;
            case EXCAVATION -> 0xFF6BE58A;
            case TRACE -> 0xFF5AA8FF;
            case LIFE -> 0xFFFFC857;
            case SUPPORT -> 0xFFB48CFF;
            case NATURAL -> 0xFF8FA3BC;
        };
    }

    static int chanceColor(double p, int low, int high) { return Theme.mix(0xFF000000 | low, 0xFF000000 | high, (float) Math.min(1, p)); }

    // ------------------------------------------------------------------ hover card

    public static float cardHeight(Verdict v) {
        int rows = Math.min(7, v.reasons.size());
        return 44 + rows * 12.5f + 8 + 30;
    }

    /** Draws the card with its top-left at (x, y). alpha: 0..1 for the entrance fade. */
    public static void card(Gfx g, Verdict v, float x, float y, int accent, int low, int high, double px, double pz, float alpha) {
        g.alpha(alpha);
        float w = CARD_W, h = cardHeight(v), pad = 10;
        int tc = typeColor(v, accent);
        int pc = chanceColor(v.p, low, high);

        g.round(x - 2, y + 1, w + 4, h + 4, 6, 0x30000000);
        g.round(x, y, w, h, 4, Theme.PANEL);
        g.roundEx(x, y, w, 3, 4, 4, 0, 0, tc);

        // title + chance chip
        g.text(v.type.label, x + pad, y + 8, tc, 1.05f, true);
        String chip = v.pct() + "%";
        float cw = g.width(chip, 0.95f, true) + 12;
        g.round(x + w - pad - cw - 1, y + 6, cw + 2, 15, 5, Theme.alpha(pc, 0.55f));
        g.round(x + w - pad - cw, y + 7, cw, 13, 4, Theme.mix(0xFF0A1524, pc, 0.14f));
        g.text(chip, x + w - pad - cw + 6, y + 9.6f, pc, 0.95f, true);

        // bearing / y range / sections
        String where = bearing(v.cx * 16 + 8 - px, v.cz * 16 + 8 - pz);
        g.text(where, x + pad, y + 25, Theme.LABEL, 0.78f, true);
        String span = (v.hasEvidenceRange() ? "Y " + v.evidenceMinY + ".." + v.evidenceMaxY + "  " : "") + v.sectionsScanned + "/" + v.sectionsTotal + " sections";
        g.text(span, x + w - pad - g.width(span, 0.72f), y + 25.5f, Theme.DESC, 0.72f);
        g.rect(x + pad, y + 37, w - 2 * pad, 1, Theme.DIVIDER);

        // reasons: "+N  text", coloured by evidence family; discounts are muted with a minus marker
        float ry = y + 43;
        int shown = 0;
        for (Verdict.Reason r : v.reasons) {
            if (shown >= 7) break;
            boolean counter = r.counter();
            int col = counter ? 0xFF7F93AB : familyColor(r.family(), accent);
            g.round(x + pad, ry + 2.5f, 3, 3, 1.5f, col);
            String pts = counter ? "~" : "+" + (int) Math.max(1, Math.round(r.points()));
            g.text(pts, x + pad + 7, ry, col, 0.82f, true);
            float tx = x + pad + 7 + 15;
            String text = trim(g, r.text(), 0.74f, w - pad - (tx - x));
            g.text(text, tx, ry + 0.3f, counter ? Theme.DESC : Theme.TEXT, 0.74f);
            ry += 12.5f;
            shown++;
        }

        // trace integrity + footer
        float fy = y + h - 30;
        g.rect(x + pad, fy - 3, w - 2 * pad, 1, Theme.DIVIDER);
        Verdict.Integrity in = v.integrity;
        float tx = x + pad;
        g.text("traces", tx, fy + 1, Theme.DESC, 0.68f, true);
        tx += g.width("traces", 0.68f, true) + 6;
        tx = trace(g, tx, fy + 1, "light", !in.lightChecked ? 0 : !in.lightReliable ? 2 : in.lightMismatch >= 6 ? 3 : 1);
        tx = trace(g, tx, fy + 1, "height", !in.heightChecked ? 0 : in.heightMismatch >= 6 ? 3 : 1);
        trace(g, tx, fy + 1, "blocks", in.blocksSuspect() ? 3 : 1);

        float by = y + h - 16;
        g.text(v.points + " points", x + pad, by + 1, Theme.LABEL, 0.78f, true);
        String conf = v.confidence.name();
        int cc = v.confidence == Verdict.Confidence.HIGH ? 0xFF4DFF6B : v.confidence == Verdict.Confidence.MEDIUM ? 0xFFFFC857 : 0xFF9BA9BD;
        float pw = g.width(conf, 0.7f, true) + 12;
        g.round(x + w - pad - pw, by - 1, pw, 12, 6, Theme.alpha(cc, 0.5f));
        g.round(x + w - pad - pw + 1, by, pw - 2, 10, 5, Theme.mix(0xFF0A1524, cc, 0.14f));
        g.text(conf, x + w - pad - pw + 6, by + 1.9f, cc, 0.7f, true);
        String build = "build " + Math.round(v.buildPct * 100) + "%";
        g.text(build, x + pad + g.width(v.points + " points", 0.78f, true) + 10, by + 1.3f, Theme.DESC, 0.72f);
        g.alpha(1f);
    }

    /** state: 0 unknown, 1 ok, 2 unreliable, 3 mismatch. Returns the next x. */
    private static float trace(Gfx g, float x, float y, String name, int state) {
        int col = switch (state) { case 1 -> 0xFF4DFF6B; case 2 -> 0xFFFFC857; case 3 -> 0xFFFF6B6B; default -> 0xFF5A6E88; };
        String mark = switch (state) { case 1 -> "ok"; case 2 -> "?"; case 3 -> "!"; default -> "-"; };
        g.round(x, y + 2.2f, 3.5f, 3.5f, 1.75f, col);
        g.text(name + " " + mark, x + 6, y, state == 3 ? col : Theme.LABEL, 0.68f);
        return x + 6 + g.width(name + " " + mark, 0.68f) + 8;
    }

    private static String trim(Gfx g, String s, float scale, float max) {
        if (g.width(s, scale) <= max) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (g.width(s.substring(0, mid) + "...", scale) <= max) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, Math.max(0, lo)).stripTrailing() + "...";
    }

    // ------------------------------------------------------------------ target list

    public static final int LIST_W = 176;

    public static float listHeight(int rows) { return 22 + rows * 21 + 6; }

    public static void targets(Gfx g, List<Verdict> list, int count, float x, float y, int accent, int low, int high,
                               double px, double pz, int scanned, int queued, Verdict hover) {
        int rows = Math.min(count, list.size());
        float w = LIST_W, h = listHeight(Math.max(rows, 1));
        g.round(x - 2, y + 1, w + 4, h + 4, 5, 0x30000000);
        g.round(x, y, w, h, 4, Theme.PANEL);
        g.roundEx(x, y, w, 3, 4, 4, 0, 0, accent);

        g.text("Targets", x + 10, y + 7, Theme.TITLE, 0.98f, true);
        String meta = scanned + " scanned" + (queued > 0 ? "  " + queued + " queued" : "");
        g.text(meta, x + w - 10 - g.width(meta, 0.62f), y + 9, Theme.DESC, 0.62f);
        g.rect(x + 8, y + 20, w - 16, 1, Theme.DIVIDER);

        if (rows == 0) {
            g.text("Nothing flagged yet", x + 10, y + 29, Theme.DESC, 0.74f);
            return;
        }
        float ry = y + 23;
        for (int i = 0; i < rows; i++) {
            Verdict v = list.get(i);
            boolean hov = v == hover;
            if (hov) g.round(x + 4, ry, w - 8, 20, 5, Theme.alpha(accent, 0.14f));
            int pc = chanceColor(v.p, low, high);
            g.text(String.valueOf(i + 1), x + 10, ry + 5.5f, Theme.DESC, 0.74f, true);
            g.round(x + 21, ry + 7, 4, 4, 2, typeColor(v, accent));
            g.text(v.type.label, x + 30, ry + 3.5f, Theme.TEXT, 0.8f, true);
            g.text(bearing(v.cx * 16 + 8 - px, v.cz * 16 + 8 - pz) + (v.hasEvidenceRange() ? "  y" + (v.evidenceMinY + v.evidenceMaxY) / 2 : ""),
                x + 30, ry + 11.5f, Theme.DESC, 0.62f);
            String chip = v.pct() + "%";
            float cw = g.width(chip, 0.8f, true) + 10;
            g.round(x + w - 10 - cw, ry + 3, cw, 13, 4, Theme.mix(0xFF0A1524, pc, 0.18f));
            g.text(chip, x + w - 10 - cw + 5, ry + 5.7f, pc, 0.8f, true);
            ry += 21;
        }
    }
}
