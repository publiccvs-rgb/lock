package lock.basefinder;

import java.util.HashMap;
import java.util.Map;

/**
 * Self-calibrating baseline. Every evidence family starts from a prior for how often it shows up in untouched
 * terrain; as chunks are scanned and judged natural, the observed rate pulls that prior toward what this server
 * actually looks like. A family that keeps appearing in chunks we believe are natural loses weight, so
 * server-specific false positives (a popular spawn build, a quirky generator) fade without any manual tuning.
 */
public final class Model {
    private static final class Rate {
        final double prior;          // natural presence rate we start from
        double seen, present;
        Rate(double prior) { this.prior = prior; }
        double rate() { return (present + prior * 60) / (seen + 60); }
    }

    private final Map<String, Rate> rates = new HashMap<>();
    public static final double PRIOR_BASE = 0.004;

    public Model() {
        reg("machine", 0.004); reg("container", 0.05); reg("furnace", 0.01); reg("build", 0.06);
        reg("light", 0.03); reg("tunnel", 0.02); reg("strip", 0.002); reg("room", 0.01); reg("inhabited", 0.002);
        reg("contradiction", 0.003); reg("heightmap", 0.002); reg("entity", 0.01); reg("player", 0.01);
    }

    private void reg(String k, double prior) { rates.put(k, new Rate(prior)); }

    /** Multiplier for a family's weight: >1 if it is rarer here than assumed, <1 if it is common. */
    public synchronized double scale(String family) {
        Rate r = rates.get(family);
        if (r == null) return 1;
        double s = Math.log(0.5 / r.rate()) / Math.log(0.5 / r.prior);
        return Math.max(0.35, Math.min(1.25, s));
    }

    /** Feed back a judged chunk. Only chunks we are fairly sure are natural teach the baseline. */
    public synchronized void observe(Map<String, Boolean> present, double p) {
        if (p > 0.25) return;
        for (var e : present.entrySet()) {
            Rate r = rates.get(e.getKey());
            if (r == null) continue;
            r.seen++;
            if (e.getValue()) r.present++;
        }
    }

    public synchronized Map<String, double[]> dump() {
        Map<String, double[]> m = new HashMap<>();
        for (var e : rates.entrySet()) m.put(e.getKey(), new double[] {e.getValue().rate(), e.getValue().seen});
        return m;
    }
}
