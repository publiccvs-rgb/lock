import lock.basefinder.*;

import java.util.*;

/** Synthetic-world checks for the finder. Not real terrain: it exercises each decision path and the false-positive rate. */
public final class BaseFinderTest {
    static final int MIN_Y = -64, SECTIONS = 24;

    static ChunkSnapshot natural(long seed) { return natural(seed, (int) seed); }

    static boolean caves = true;

    static ChunkSnapshot natural(long seed, int chunkX) {
        Random r = new Random(seed);
        ChunkSnapshot s = new ChunkSnapshot(chunkX, 0, MIN_Y, SECTIONS);
        int[] h = new int[256];
        double a = r.nextDouble() * 6, b = r.nextDouble() * 6;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) h[z << 4 | x] = 64 + (int) (Math.sin(x / 5.0 + a) * 3 + Math.cos(z / 4.0 + b) * 3);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int top = h[z << 4 | x];
            for (int y = MIN_Y; y < top; y++) {
                int g = y < MIN_Y + 1 ? Blocks.BEDROCK : y >= top - 4 ? Blocks.SOIL : Blocks.STONE;
                if (g == Blocks.STONE && r.nextDouble() < 0.012) g = Blocks.ORE;
                s.set(x, y, z, g);
            }
        }
        // natural caves: wandering worms and a few blobs
        int worms = caves ? r.nextInt(4) : 0;
        for (int i = 0; i < worms; i++) {
            double x = r.nextInt(16), y = -40 + r.nextInt(80), z = r.nextInt(16);
            double yaw = r.nextDouble() * 6.28, pitch = (r.nextDouble() - .5) * .6;
            for (int k = 0; k < 60; k++) {
                yaw += (r.nextDouble() - .5) * .5; pitch += (r.nextDouble() - .5) * .25; pitch *= .9;
                x += Math.cos(yaw) * Math.cos(pitch); z += Math.sin(yaw) * Math.cos(pitch); y += Math.sin(pitch);
                double rad = 1.2 + r.nextDouble() * 1.6;
                carve(s, x, y, z, rad, h);
            }
        }
        for (int i = 0; i < (caves ? r.nextInt(3) : 0); i++) carve(s, r.nextInt(16), -50 + r.nextInt(100), r.nextInt(16), 3 + r.nextDouble() * 3, h);
        s.serverSurface = new short[256];
        for (int i = 0; i < 256; i++) s.serverSurface[i] = (short) (h[i] - MIN_Y);
        light(s);
        return s;
    }

    static void carve(ChunkSnapshot s, double cx, double cy, double cz, double rad, int[] h) {
        for (int dx = -(int) rad - 1; dx <= rad + 1; dx++) for (int dy = -(int) rad - 1; dy <= rad + 1; dy++) for (int dz = -(int) rad - 1; dz <= rad + 1; dz++) {
            int x = (int) Math.round(cx) + dx, y = (int) Math.round(cy) + dy, z = (int) Math.round(cz) + dz;
            if (x < 0 || x > 15 || z < 0 || z > 15 || y <= MIN_Y + 1 || y >= h[z << 4 | x] - 5) continue;
            if (dx * dx + dy * dy + dz * dz <= rad * rad) s.set(x, y, z, Blocks.AIR);
        }
    }

    /** Honest light: sky light above the surface, block light flooded from placed torches/lanterns through open cells. */
    static void light(ChunkSnapshot s) {
        int h = SECTIONS * 16;
        byte[] bl = new byte[h * 256], sl = new byte[h * 256];
        int[] surf = new int[256];
        for (int i = 0; i < 256; i++) {
            int y = s.maxY() - 1;
            while (y > MIN_Y && (s.get(i & 15, y, i >> 4) == Blocks.AIR)) y--;
            surf[i] = y + 1;
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int y = 0; y < h; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int g = s.get(x, MIN_Y + y, z);
            if (MIN_Y + y >= surf[z << 4 | x] && g == Blocks.AIR) sl[y << 8 | z << 4 | x] = 15;
            if (g == Blocks.TORCH) { bl[y << 8 | z << 4 | x] = 14; q.add(new int[] {x, y, z}); }
            if (g == Blocks.LIGHT_BLOCK) { bl[y << 8 | z << 4 | x] = 15; q.add(new int[] {x, y, z}); }
        }
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int lv = bl[c[1] << 8 | c[2] << 4 | c[0]];
            if (lv <= 1) continue;
            for (int[] dd : d) {
                int x = c[0] + dd[0], y = c[1] + dd[1], z = c[2] + dd[2];
                if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y >= h) continue;
                int g = s.get(x, MIN_Y + y, z);
                if (Blocks.solid(g) && g != Blocks.BUILDING) continue;
                if (g == Blocks.BUILDING) continue;
                int idx = y << 8 | z << 4 | x;
                if (bl[idx] < lv - 1) { bl[idx] = (byte) (lv - 1); q.add(new int[] {x, y, z}); }
            }
        }
        for (int sy = 0; sy < SECTIONS; sy++) {
            s.blockLight[sy] = Arrays.copyOfRange(bl, sy * 4096, sy * 4096 + 4096);
            s.skyLight[sy] = Arrays.copyOfRange(sl, sy * 4096, sy * 4096 + 4096);
        }
    }

    /** The server's own view after all edits: heightmap and light computed from the true blocks. */
    static void finish(ChunkSnapshot s) {
        s.serverSurface = new short[256];
        for (int i = 0; i < 256; i++) {
            int y = s.maxY() - 1;
            while (y > MIN_Y && s.get(i & 15, y, i >> 4) == Blocks.AIR) y--;
            s.serverSurface[i] = (short) (y + 1 - MIN_Y);
        }
        light(s);
    }

    static void box(ChunkSnapshot s, int x0, int y0, int z0, int x1, int y1, int z1, int g) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) if (x >= 0 && x < 16 && z >= 0 && z < 16) s.set(x, y, z, g);
    }

    // ---------- planted structures ----------
    static void tunnel(ChunkSnapshot s, int y, int z, int len) { for (int x = 0; x < len; x++) { s.set(x, y, z, Blocks.AIR); s.set(x, y + 1, z, Blocks.AIR); } }

    static void plantTunnel(ChunkSnapshot s) {
        tunnel(s, -20, 6, 16);
        for (int z = 6; z < 14; z++) { s.set(15, -20, z, Blocks.AIR); s.set(15, -19, z, Blocks.AIR); }
        for (int x = 4; x < 15; x++) { s.set(x, -20, 13, Blocks.AIR); s.set(x, -19, 13, Blocks.AIR); }
    }

    static void plantStripMine(ChunkSnapshot s) { for (int z = 2; z < 16; z += 3) tunnel(s, -58, z, 16); }

    static void plantUndergroundBase(ChunkSnapshot s, boolean lit) {
        box(s, 3, -30, 3, 11, -27, 11, Blocks.AIR);
        s.set(4, -30, 4, Blocks.CONTAINER); s.set(5, -30, 4, Blocks.CONTAINER); s.set(6, -30, 4, Blocks.CONTAINER);
        s.set(9, -30, 4, Blocks.FURNACE); s.set(10, -30, 4, Blocks.FURNACE);
        s.set(4, -28, 10, Blocks.TORCH); s.set(10, -28, 10, Blocks.TORCH); s.set(4, -28, 4, Blocks.TORCH);
        s.set(8, -30, 10, Blocks.BED);
        if (lit) s.set(7, -28, 7, Blocks.LIGHT_BLOCK);
        tunnel(s, -30, 12, 3);
    }

    static void plantSurfaceBase(ChunkSnapshot s) {
        int top = 64;
        box(s, 2, top, 2, 10, top, 10, Blocks.BUILDING);
        box(s, 2, top + 1, 2, 10, top + 4, 2, Blocks.BUILDING); box(s, 2, top + 1, 10, 10, top + 4, 10, Blocks.BUILDING);
        box(s, 2, top + 1, 2, 2, top + 4, 10, Blocks.BUILDING); box(s, 10, top + 1, 2, 10, top + 4, 10, Blocks.BUILDING);
        box(s, 2, top + 5, 2, 10, top + 5, 10, Blocks.BUILDING);
        s.set(4, top + 1, 4, Blocks.CONTAINER); s.set(5, top + 1, 4, Blocks.CONTAINER); s.set(8, top + 1, 4, Blocks.FURNACE);
        s.set(6, top + 1, 8, Blocks.BED); s.set(3, top + 3, 3, Blocks.TORCH); s.set(9, top + 3, 9, Blocks.TORCH);
        s.set(7, top + 1, 6, Blocks.MACHINE); s.set(8, top + 1, 6, Blocks.MACHINE);
    }

    static void plantFarm(ChunkSnapshot s) {
        box(s, 2, -10, 2, 12, -7, 12, Blocks.AIR);
        for (int i = 0; i < 6; i++) s.set(3 + i, -9, 5, Blocks.MACHINE);
        for (int i = 0; i < 3; i++) s.set(3 + i, -9, 7, Blocks.MACHINE);
        s.set(4, -9, 9, Blocks.CONTAINER); s.set(5, -9, 9, Blocks.CONTAINER);
        s.set(11, -8, 11, Blocks.TORCH);
    }

    // ---------- natural structures that must NOT be flagged ----------
    static void naturalMineshaft(ChunkSnapshot s) {
        int y = -20;
        for (int x = 0; x < 16; x++) {
            for (int dy = 0; dy < 3; dy++) for (int dz = 0; dz < 3; dz++) s.set(x, y + dy, 6 + dz, Blocks.AIR);
            // wooden lining on the walls, supports every 4
            for (int dy = 0; dy < 3; dy++) { s.set(x, y + dy, 5, Blocks.MINESHAFT); s.set(x, y + dy, 9, Blocks.MINESHAFT); }
            s.set(x, y + 3, 6, Blocks.MINESHAFT); s.set(x, y + 3, 7, Blocks.MINESHAFT); s.set(x, y + 3, 8, Blocks.MINESHAFT);
            s.set(x, y - 1, 7, Blocks.MINESHAFT);
        }
        for (int x = 1; x < 16; x += 4) s.set(x, y + 1, 6, Blocks.TORCH);
        s.set(8, y, 7, Blocks.CONTAINER);
        for (int i = 0; i < 10; i++) s.set(2 + i, y + 2, 7, Blocks.MINESHAFT);
    }

    static void naturalVillage(ChunkSnapshot s, Random r) {
        int top = 64;
        for (int house = 0; house < 3; house++) {
            int ox = 1 + house * 5, oz = 2 + (house % 2) * 5;
            box(s, ox, top, oz, ox + 3, top, oz + 3, Blocks.BUILDING);
            box(s, ox, top + 1, oz, ox + 3, top + 3, oz, Blocks.BUILDING); box(s, ox, top + 1, oz + 3, ox + 3, top + 3, oz + 3, Blocks.BUILDING);
            s.set(ox + 1, top + 1, oz, Blocks.VILLAGE); s.set(ox + 1, top + 2, oz, Blocks.VILLAGE);
            s.set(ox + 1, top + 1, oz + 2, Blocks.BED); s.set(ox + 2, top + 1, oz + 2, Blocks.FURNACE);
            s.set(ox + 2, top + 1, oz + 1, Blocks.CONTAINER); s.set(ox, top + 3, oz + 1, Blocks.TORCH);
            for (int i = 0; i < 4; i++) s.set(ox + i, top + 1, oz + 3, Blocks.VILLAGE);
        }
        s.entities.add(new ChunkSnapshot.Ent(ChunkSnapshot.ENT_VILLAGER, 5, 65, 5));
        s.entities.add(new ChunkSnapshot.Ent(ChunkSnapshot.ENT_VILLAGER, 6, 65, 6));
        s.entities.add(new ChunkSnapshot.Ent(ChunkSnapshot.ENT_VILLAGER, 7, 65, 6));
    }

    static void naturalTemple(ChunkSnapshot s) {
        box(s, 2, -5, 2, 12, -1, 12, Blocks.NATURAL_STRUCT);
        box(s, 3, -4, 3, 11, -2, 11, Blocks.AIR);
        for (int i = 0; i < 9; i++) s.set(4 + i % 3, -5, 4 + i / 3, Blocks.MACHINE);
        for (int i = 0; i < 4; i++) s.set(3 + i, -4, 3, Blocks.CONTAINER);
    }

    static void naturalDungeon(ChunkSnapshot s) {
        box(s, 4, -30, 4, 10, -26, 10, Blocks.NATURAL_STRUCT);
        box(s, 5, -29, 5, 9, -27, 9, Blocks.AIR);
        s.set(5, -29, 5, Blocks.CONTAINER); s.set(9, -29, 9, Blocks.CONTAINER); s.set(7, -29, 7, Blocks.NATURAL_STRUCT);
    }

    static void naturalStronghold(ChunkSnapshot s) {
        box(s, 0, -40, 4, 15, -35, 10, Blocks.STRONGHOLD);
        box(s, 0, -39, 5, 15, -37, 9, Blocks.AIR);
        for (int x = 2; x < 16; x += 5) s.set(x, -38, 5, Blocks.TORCH);
        s.set(8, -39, 8, Blocks.CONTAINER);
    }

    // ---------- anti-xray ----------
    /** Mode-2 style: unexposed stone becomes random fake ore; isolated enclosed air cells are filled. Light is untouched. */
    static void antiXray(ChunkSnapshot s, long seed, boolean hideAllAir) {
        Random r = new Random(seed);
        for (int y = MIN_Y; y < 64; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int g = s.get(x, y, z);
            if (g == Blocks.STONE && r.nextDouble() < 0.22) s.set(x, y, z, Blocks.ORE);
            if (hideAllAir && g == Blocks.AIR && y < 60 && y < top(s, x, z) - 2) s.set(x, y, z, r.nextDouble() < 0.5 ? Blocks.STONE : Blocks.ORE);
        }
    }

    static int top(ChunkSnapshot s, int x, int z) {
        for (int y = s.maxY() - 1; y >= MIN_Y; y--) { int g = s.get(x, y, z); if (g == Blocks.STONE || g == Blocks.SOIL || g == Blocks.ORE) return y + 1; }
        return MIN_Y;
    }

    // ---------- harness ----------
    static int fails;
    static void check(boolean ok, String msg) { if (!ok) fails++; System.out.println((ok ? "  ok   " : "  FAIL ") + msg); }

    static Verdict run(ChunkSnapshot s, Model m) { return Engine.analyze(s, m, new Engine.Options()); }

    public static void main(String[] a) {
        Model model = new Model();
        Engine.Options opt = new Engine.Options();

        System.out.println("== natural terrain (false positives) ==");
        int fp = 0, n = 400; double maxP = 0, sumP = 0;
        for (int i = 0; i < n; i++) {
            Verdict v = run(natural(1000 + i), model);
            maxP = Math.max(maxP, v.p); sumP += v.p;
            if (v.p >= 0.30) { fp++; System.out.printf("    FP seed %d p=%.2f %s%n", 1000 + i, v.p, v.reasons.stream().map(Verdict.Reason::text).toList()); }
        }
        System.out.printf("  %d/%d natural chunks >= 30%%, max p %.3f, mean %.4f%n", fp, n, maxP, sumP / n);
        check(fp == 0, "no natural chunk reaches 30%");

        System.out.println("== natural structures ==");
        String[] names = {"mineshaft", "village", "temple", "dungeon", "stronghold"};
        for (int k = 0; k < names.length; k++) {
            int bad = 0; double worst = 0;
            for (int i = 0; i < 40; i++) {
                ChunkSnapshot s = natural(5000 + k * 100 + i);
                switch (k) {
                    case 0 -> naturalMineshaft(s); case 1 -> naturalVillage(s, new Random(i)); case 2 -> naturalTemple(s);
                    case 3 -> naturalDungeon(s); default -> naturalStronghold(s);
                }
                finish(s);
                Verdict v = run(s, model);
                worst = Math.max(worst, v.p);
                if (v.p >= 0.30) bad++;
            }
            check(bad == 0, String.format("%s is not flagged (worst %.1f%%)", names[k], worst * 100));
            if (bad > 0) { ChunkSnapshot t = natural(5000 + k * 100); switch (k) { case 0 -> naturalMineshaft(t); case 1 -> naturalVillage(t, new Random(0)); case 2 -> naturalTemple(t); case 3 -> naturalDungeon(t); default -> naturalStronghold(t); } finish(t); Verdict vv = run(t, new Model()); vv.reasons.forEach(r -> System.out.printf("      %+.1f %s%n", r.points(), r.text())); }
        }

        System.out.println("== planted player activity ==");
        record Case(String name, java.util.function.Consumer<ChunkSnapshot> f, double min, boolean xray) {}
        List<Case> cases = List.of(
            new Case("underground tunnel", BaseFinderTest::plantTunnel, 0.12, false),
            new Case("strip mine", BaseFinderTest::plantStripMine, 0.25, false),
            new Case("underground base", s -> plantUndergroundBase(s, false), 0.40, false),
            new Case("surface base", BaseFinderTest::plantSurfaceBase, 0.40, false),
            new Case("redstone farm", BaseFinderTest::plantFarm, 0.40, false),
            new Case("underground base behind anti-xray", s -> plantUndergroundBase(s, true), 0.30, true));
        for (Case c : cases) {
            int hit = 0; double lo = 1, avg = 0; int trials = 40;
            for (int i = 0; i < trials; i++) {
                ChunkSnapshot s = natural(9000 + i);
                c.f().accept(s);
                finish(s);
                if (c.xray()) antiXray(s, i, true);
                Verdict v = run(s, model);
                lo = Math.min(lo, v.p); avg += v.p;
                if (v.p >= c.min()) hit++;
            }
            check(hit >= trials * 0.9, String.format("%s detected %d/%d (min %.0f%%, mean %.0f%%)", c.name(), hit, trials, lo * 100, avg / trials * 100));
        }

        System.out.println("== anti-xray on natural chunks must not create base evidence ==");
        int fpx = 0;
        for (int i = 0; i < 200; i++) {
            ChunkSnapshot s = natural(20000 + i);
            antiXray(s, i, true);
            Verdict v = run(s, model);
            if (v.p >= 0.30) { if (fpx++ == 0) { System.out.printf("    first FP p=%.2f%n", v.p); v.reasons.forEach(r -> System.out.printf("      %+.1f %s%n", r.points(), r.text())); } }
        }
        check(fpx == 0, "fake ore field alone is not a base (" + fpx + "/200)");

        System.out.println("== neighbour support ==");
        {
            Map<Long, Verdict> map = new HashMap<>();
            Model m2 = new Model();
            caves = false;
            for (int i = 0; i < 4; i++) {            // a tunnel running through four adjacent chunks
                ChunkSnapshot s2 = natural(31000 + i, i);
                for (int x = 0; x < 16; x++) { s2.set(x, -20, 6, Blocks.AIR); s2.set(x, -19, 6, Blocks.AIR); }
                finish(s2);
                map.put(Sites.key(i, 0), Engine.analyze(s2, m2, new Engine.Options()));
            }
            ChunkSnapshot lone = natural(31100, 40);
            for (int x = 0; x < 16; x++) { lone.set(x, -20, 6, Blocks.AIR); lone.set(x, -19, 6, Blocks.AIR); }
            finish(lone);
            Verdict vl = Engine.analyze(lone, m2, new Engine.Options());
            double aloneP = vl.p;
            map.put(Sites.key(40, 0), vl);
            double before1 = map.get(Sites.key(1, 0)).p;
            ChunkSnapshot empty = natural(31200, 4);      // an empty natural chunk next to the tunnel must not be promoted
            finish(empty);
            map.put(Sites.key(4, 0), Engine.analyze(empty, m2, new Engine.Options()));
            List<Sites.Site> sites = Sites.link(map);
            double mid = map.get(Sites.key(1, 0)).p;
            check(mid > before1 + 0.05, String.format("chained tunnel chunk rises %.0f%% -> %.0f%% with neighbours", before1 * 100, mid * 100));
            check(Math.abs(map.get(Sites.key(40, 0)).p - aloneP) < 1e-9, "an isolated chunk gets no support");
            check(map.get(Sites.key(4, 0)).p < 0.05, String.format("empty neighbour not promoted (%.1f%%)", map.get(Sites.key(4, 0)).p * 100));
            caves = true;
            check(!sites.isEmpty() && sites.get(0).chunks() >= 2, "tunnel chunks grouped into one site (" + (sites.isEmpty() ? 0 : sites.get(0).chunks()) + " chunks)");
        }

        System.out.println("== sample verdict ==");
        ChunkSnapshot s = natural(777); plantUndergroundBase(s, true); plantTunnel(s); finish(s); antiXray(s, 1, true);
        Verdict v = run(s, new Model());
        System.out.printf("  p=%.2f points=%d conf=%s type=%s y=%d..%d integrity: lightMismatch=%d ore=%.1f%% suspect=%s%n", v.p, v.points, v.confidence, v.type.label,
            v.evidenceMinY, v.evidenceMaxY, v.integrity.lightMismatch, v.integrity.oreFraction * 100, v.integrity.blocksSuspect());
        for (Verdict.Reason r : v.reasons) System.out.printf("   %s%.1f  %s%n", r.counter() ? "-" : "+", r.points(), r.text());
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
