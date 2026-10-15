package lock.basefinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chunks are not independent: bases, tunnel networks and strip mines span chunks, and natural false positives
 * almost never do. A chunk with its own evidence borrows confidence from flagged neighbours (never the other way
 * round, so a strong chunk cannot promote an empty neighbour), then connected chunks are grouped into sites.
 */
public final class Sites {
    private Sites() {}

    public static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    public record Site(int id, int chunks, double maxP, double centerX, double centerZ, Verdict best) {}

    public static List<Site> link(Map<Long, Verdict> all) {
        // 1. neighbour support from base (own-evidence) logits only - no feedback loops
        for (Verdict v : all.values()) {
            if (v.baseLogit < -4.6) { v.applySupport(0, 0); continue; }      // below ~1%: nothing to support
            double support = 0;
            int flagged = 0;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    Verdict n = all.get(key(v.cx + dx, v.cz + dz));
                    if (n == null) continue;
                    double w = n.baseLogit + 3.5;
                    if (w > 0) { support += 0.6 * w; flagged++; }
                }
            }
            v.applySupport(Math.min(3.6, support), flagged);
        }

        // 2. connected components of "interesting" chunks
        Map<Long, Integer> parent = new HashMap<>();
        for (Verdict v : all.values()) if (v.p >= 0.10) parent.put(key(v.cx, v.cz), 0);
        List<Long> keys = new ArrayList<>(parent.keySet());
        Map<Long, Long> uf = new HashMap<>();
        for (long k : keys) uf.put(k, k);
        for (Verdict v : all.values()) {
            if (v.p < 0.10) continue;
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    long nk = key(v.cx + dx, v.cz + dz);
                    if (uf.containsKey(nk)) union(uf, key(v.cx, v.cz), nk);
                }
        }
        Map<Long, List<Verdict>> groups = new HashMap<>();
        for (Verdict v : all.values()) {
            if (v.p < 0.10) { v.siteId = -1; continue; }
            groups.computeIfAbsent(find(uf, key(v.cx, v.cz)), k -> new ArrayList<>()).add(v);
        }
        List<Site> sites = new ArrayList<>();
        int id = 0;
        for (List<Verdict> g : groups.values()) {
            double sx = 0, sz = 0, w = 0, mp = 0;
            Verdict best = g.get(0);
            for (Verdict v : g) {
                sx += (v.cx + 0.5) * v.p; sz += (v.cz + 0.5) * v.p; w += v.p;
                if (v.p > mp) { mp = v.p; best = v; }
                v.siteId = id;
            }
            sites.add(new Site(id++, g.size(), mp, sx / w * 16, sz / w * 16, best));
        }
        sites.sort((a, b) -> Double.compare(b.maxP(), a.maxP()));
        return sites;
    }

    private static long find(Map<Long, Long> uf, long x) {
        while (uf.get(x) != x) { uf.put(x, uf.get(uf.get(x))); x = uf.get(x); }
        return x;
    }

    private static void union(Map<Long, Long> uf, long a, long b) {
        long ra = find(uf, a), rb = find(uf, b);
        if (ra != rb) uf.put(ra, rb);
    }
}
