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
    public double p, logit;
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

    public Verdict(int cx, int cz) { this.cx = cx; this.cz = cz; }

    public void note(int y) { if (y < evidenceMinY) evidenceMinY = y; if (y > evidenceMaxY) evidenceMaxY = y; }
    public boolean hasEvidenceRange() { return evidenceMinY <= evidenceMaxY; }
}
