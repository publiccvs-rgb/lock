package lock.basefinder;

import java.util.ArrayList;
import java.util.List;

/** Everything the UI shows about one chunk, including why and why not. */
public final class Verdict {
    public enum Confidence { LOW, MEDIUM, HIGH }
    public enum Type {
        BASE("Base"), STORAGE("Storage"), FARM("Redstone"), PORTAL("Portal"), TUNNELS("Tunnels"), STRIP_MINE("Strip mine"),
        HIDDEN_VOID("Hidden void"), OUTPOST("Outpost"), ACTIVITY("Activity"), UNKNOWN("Unknown");
        public final String label;
        Type(String l) { label = l; }
    }
    public enum Family { BUILT, EXCAVATION, TRACE, LIFE, SUPPORT, NATURAL }

    public record Reason(String text, double points, Family family, boolean counter) {}

    public static final class Integrity {
        public boolean lightChecked, lightReliable = true, heightChecked;
        public int lightMismatch, heightMismatch;
        public double oreFraction;
        public boolean oreAnomaly;
        /** True when the block data itself looks rewritten (fake ore field, hidden lit voids). */
        public boolean blocksSuspect() { return oreAnomaly || lightMismatch >= 6; }
    }

    public final int cx, cz;
    public double p, logit, baseLogit;
    public int points;
    public Confidence confidence = Confidence.LOW;
    public Type type = Type.UNKNOWN;
    public int evidenceMinY = Integer.MAX_VALUE, evidenceMaxY = Integer.MIN_VALUE;
    public int sectionsScanned, sectionsTotal;
    public boolean livePlayer;
    public double buildPct;
    public final List<Reason> reasons = new ArrayList<>();
    public final Integrity integrity = new Integrity();
    public long scannedAt;
    /** Set by site clustering. */
    public int siteId = -1;
    public int neighborFlagged;
    public int minY, planeBase;
    public List<int[]> amethyst = List.of();

    public Verdict(int cx, int cz) { this.cx = cx; this.cz = cz; }

    public void note(int y) { if (y < evidenceMinY) evidenceMinY = y; if (y > evidenceMaxY) evidenceMaxY = y; }
    /** Applies neighbour support on top of the chunk's own evidence. */
    public void applySupport(double support, int flaggedNeighbours) {
        logit = baseLogit + support;
        p = 1.0 / (1.0 + Math.exp(-logit));
        neighborFlagged = flaggedNeighbours;
        reasons.removeIf(r -> r.family() == Family.SUPPORT);
        if (support > 0.05) {
            reasons.add(new Reason(plural(flaggedNeighbours) + " next door " + (flaggedNeighbours == 1 ? "is" : "are") + " flagged too", support, Family.SUPPORT, false));
        }
        points = (int) Math.round(logit - Engine.PRIOR_LOGIT);
        if (p >= 0.75 && confidence == Confidence.MEDIUM) confidence = Confidence.HIGH;
        else if (p >= 0.40 && confidence == Confidence.LOW) confidence = Confidence.MEDIUM;
    }

    private static String plural(int n) { return n + " chunk" + (n == 1 ? "" : "s"); }

    /** Percentage for display; never claims certainty. */
    public int pct() { return (int) Math.min(99, Math.round(p * 100)); }

    public boolean hasEvidenceRange() { return evidenceMinY <= evidenceMaxY; }
}
