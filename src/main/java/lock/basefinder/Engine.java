package lock.basefinder;

import lock.basefinder.Verdict.Confidence;
import lock.basefinder.Verdict.Family;
import lock.basefinder.Verdict.Type;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores one chunk. The score is a posterior: prior odds of a base times the likelihood ratio of every piece of
 * evidence. Evidence is grouped into correlated clusters (built content, excavation, traces, life): inside a
 * cluster only the strongest item counts in full, across clusters evidence multiplies. Natural structures
 * (villages, mineshafts, strongholds, temples...) are modelled as a competing explanation that absorbs a fixed
 * allowance of the matching blocks, so a village is not a base but a base inside a village still is.
 */
public final class Engine {
    private Engine() {}

    public static final double PRIOR_LOGIT = Math.log(Model.PRIOR_BASE / (1 - Model.PRIOR_BASE));

    /** Options the UI can toggle per scan. */
    public static final class Options {
        public boolean built = true, excavation = true, traces = true, life = true, amethyst = true;
        public boolean horizontalTunnels = true, shafts = true, stairs = true;
        public double minTunnelRegularity = 0.5;
    }

    private static final class Evidence {
        final String key; final Family family; final String text; final double llr;
        Evidence(String key, Family family, String text, double llr) { this.key = key; this.family = family; this.text = text; this.llr = llr; }
    }

    public static Verdict analyze(ChunkSnapshot s, Model model, Options opt) {
        Verdict v = new Verdict(s.cx, s.cz);
        v.scannedAt = System.nanoTime();
        v.sectionsTotal = s.sections;
        v.minY = s.minY;
        v.amethyst = s.amethyst;
        for (int i = 0; i < s.sections; i++) if (s.grp[i] != null) v.sectionsScanned++;

        // ---- 1. census ----
        int[] cnt = new int[Blocks.COUNT];
        int[] gMinY = new int[Blocks.COUNT], gMaxY = new int[Blocks.COUNT];
        java.util.Arrays.fill(gMinY, Integer.MAX_VALUE);
        java.util.Arrays.fill(gMaxY, Integer.MIN_VALUE);
        int[] surf = new int[256], terrain = new int[256];
        for (int i = 0; i < 256; i++) { surf[i] = surfaceFromBlocks(s, i & 15, i >> 4); terrain[i] = terrainTop(s, i & 15, i >> 4); }
        int solidDeep = 0, oreDeep = 0, undergroundBuilt = 0;
        int[] ug = new int[Blocks.COUNT];
        int lightMismatch = 0, lightCells = 0, skyAirChecked = 0, skyAirLit = 0;
        int lightMinY = Integer.MAX_VALUE, lightMaxY = Integer.MIN_VALUE;
        boolean haveLight = false;

        for (int sy = 0; sy < s.sections; sy++) {
            byte[] sec = s.grp[sy];
            if (sec == null) continue;
            byte[] bl = s.blockLight[sy], sl = s.skyLight[sy];
            if (bl != null || sl != null) haveLight = true;
            int baseY = s.minY + sy * 16;
            for (int i = 0; i < 4096; i++) {
                int g = sec[i];
                if (g == Blocks.AIR || g == Blocks.CAVE_AIR) {
                    // sanity probe for the light data: open sky should be fully lit in daylight-engine terms
                    if (sl != null) {
                        int y = baseY + (i >> 8), x = i & 15, z = (i >> 4) & 15;
                        if (y >= surf[z << 4 | x] + 1) { skyAirChecked++; if (sl[i] > 0) skyAirLit++; }
                    }
                    continue;
                }
                cnt[g]++;
                int y = baseY + (i >> 8);
                if (y < gMinY[g]) gMinY[g] = y;
                if (y > gMaxY[g]) gMaxY[g] = y;
                int x = i & 15, z = (i >> 4) & 15;
                boolean under = y < terrain[z << 4 | x] - 3;
                if (under) ug[g]++;
                if (under && (g == Blocks.BUILDING || g == Blocks.MACHINE || g == Blocks.PLAYER_ONLY || g == Blocks.CONTAINER
                    || g == Blocks.FURNACE || g == Blocks.TORCH || g == Blocks.LIGHT_BLOCK || g == Blocks.BED)) undergroundBuilt++;
                if (g == Blocks.STONE) solidDeep++;
                if (g == Blocks.ORE) oreDeep++;
                // contradiction: a cell the blocks call solid stone but the server's light says is not
                if ((g == Blocks.STONE || g == Blocks.BEDROCK) && (bl != null || sl != null)) {
                    lightCells++;
                    if ((bl != null && bl[i] > 0) || (sl != null && sl[i] > 0)) {
                        lightMismatch++;
                        if (y < lightMinY) lightMinY = y;
                        if (y > lightMaxY) lightMaxY = y;
                    }
                }
            }
        }

        // ---- 2. trace integrity ----
        Verdict.Integrity in = v.integrity;
        in.lightChecked = haveLight;
        if (haveLight && skyAirChecked > 40) in.lightReliable = skyAirLit > skyAirChecked * 0.6;
        in.lightMismatch = in.lightReliable ? lightMismatch : 0;
        int solids = solidDeep + oreDeep;
        in.oreFraction = solids > 0 ? oreDeep / (double) solids : 0;
        in.oreAnomaly = solids >= 1500 && in.oreFraction > 0.045 && s.dimension == 0;
        if (s.serverSurface != null) {
            in.heightChecked = true;
            for (int i = 0; i < 256; i++) if (Math.abs(s.minY + s.serverSurface[i] - surf[i]) >= 1) in.heightMismatch++;
        }
        boolean suspect = in.blocksSuspect();
        double blockTrust = suspect ? 0.4 : 1.0;

        // ---- 3. natural structure explanation (competing hypothesis) ----
        int[] allow = new int[Blocks.COUNT];
        List<String> explained = new ArrayList<>();
        boolean village = cnt[Blocks.VILLAGE] >= 14 && s.minY + 0 < surf[0] + 64;
        boolean mineshaft = cnt[Blocks.MINESHAFT] >= 20 && cnt[Blocks.PLAYER_ONLY] + cnt[Blocks.MACHINE] == 0;
        boolean stronghold = cnt[Blocks.STRONGHOLD] >= 25;
        boolean structure = cnt[Blocks.NATURAL_STRUCT] >= 30;
        if (village) {
            allow[Blocks.BED] += 10; allow[Blocks.FURNACE] += 14; allow[Blocks.CONTAINER] += 6; allow[Blocks.TORCH] += 40;
            allow[Blocks.BUILDING] += 400; allow[Blocks.LIGHT_BLOCK] += 20;
            explained.add("village");
        }
        if (mineshaft) { allow[Blocks.CONTAINER] += 3; allow[Blocks.TORCH] += 12; allow[Blocks.BUILDING] += 20; explained.add("mineshaft"); }
        if (stronghold) { allow[Blocks.CONTAINER] += 4; allow[Blocks.TORCH] += 30; allow[Blocks.BUILDING] += 60; explained.add("stronghold"); }
        if (structure) {
            allow[Blocks.BUILDING] += 300; allow[Blocks.LIGHT_BLOCK] += 30; allow[Blocks.CONTAINER] += 8; allow[Blocks.TORCH] += 20;
            allow[Blocks.MACHINE] += 12; allow[Blocks.FURNACE] += 4; allow[Blocks.BED] += 2;
            explained.add("natural structure");
        }
        int[] eff = new int[Blocks.COUNT];
        for (int g = 0; g < Blocks.COUNT; g++) {
            int e = Math.max(0, cnt[g] - allow[g]);
            // embedded in solid rock, built content is far less explainable than on the surface
            if (g == Blocks.CONTAINER || g == Blocks.FURNACE || g == Blocks.BED || g == Blocks.TORCH || g == Blocks.LIGHT_BLOCK)
                e += Math.min(e, ug[g]);
            eff[g] = e;
        }

        // ---- 4. voids ----
        List<Voids.Component> voids = opt.excavation ? Voids.analyze(s) : List.of();
        int tunnelVoxels = 0, tunnelComps = 0, rooms = 0, inhabited = 0, inhabitedFurn = 0;
        int tunnelMinY = Integer.MAX_VALUE, tunnelMaxY = Integer.MIN_VALUE;
        List<int[]> lines = new ArrayList<>();       // {axis, perpendicular coordinate, y} for strip-mine detection
        int vertical = 0, horizontal = 0, stairs = 0;
        for (Voids.Component c : voids) {
            if (c.big || c.flooded || c.n < 12) continue;
            double wallNat = c.wallTotal == 0 ? 0 : c.wallCarvable / (double) c.wallTotal;
            double wallMine = c.wallTotal == 0 ? 0 : c.wallMineshaft / (double) c.wallTotal;
            double wallStr = c.wallTotal == 0 ? 0 : c.wallStronghold / (double) c.wallTotal;
            boolean naturalLined = wallMine > 0.12 || wallStr > 0.12 || mineshaft && wallNat < 0.9;
            int dir = c.dominantDir();
            boolean dirOk = dir == Voids.DIR_Y ? opt.shafts : dir == Voids.DIR_STAIR ? opt.stairs : opt.horizontalTunnels;
            if (c.regular >= 20 && wallNat >= 0.85 && !naturalLined && dirOk) {
                tunnelVoxels += c.regular;
                tunnelComps++;
                tunnelMinY = Math.min(tunnelMinY, s.minY + c.minY);
                tunnelMaxY = Math.max(tunnelMaxY, s.minY + c.maxY);
                if (dir == Voids.DIR_Y) vertical++; else if (dir == Voids.DIR_STAIR) stairs++; else horizontal++;
                if (dir == Voids.DIR_X) lines.add(new int[] {0, (c.minZ + c.maxZ) / 2, c.minY});
                else if (dir == Voids.DIR_Z) lines.add(new int[] {1, (c.minX + c.maxX) / 2, c.minY});
            } else if (c.n >= 40 && c.fill >= 0.72 && c.sizeX() >= 3 && c.sizeY() >= 2 && c.sizeZ() >= 3 && wallNat >= 0.8 && !structure) {
                rooms++;
            }
            if (c.torches + c.containers + c.furnishings >= 3 && wallNat >= 0.7 && !naturalLined && explained.isEmpty()) {
                inhabited++;
                inhabitedFurn += c.torches + c.containers + c.furnishings;
            }
        }
        boolean strip = false;
        if (lines.size() >= 3) {
            for (int axis = 0; axis < 2 && !strip; axis++) {
                List<Integer> coords = new ArrayList<>();
                for (int[] l : lines) if (l[0] == axis) coords.add(l[1]);
                java.util.Collections.sort(coords);
                int equalGaps = 0;
                for (int i = 2; i < coords.size(); i++)
                    if (coords.get(i) - coords.get(i - 1) == coords.get(i - 1) - coords.get(i - 2) && coords.get(i) - coords.get(i - 1) >= 2) equalGaps++;
                if (equalGaps >= 1) strip = true;
            }
        }

        // ---- 5. evidence ----
        List<Evidence> ev = new ArrayList<>();
        Map<String, Boolean> present = new HashMap<>();
        if (opt.built) {
            int machine = eff[Blocks.MACHINE] + eff[Blocks.PLAYER_ONLY], container = eff[Blocks.CONTAINER], furnace = eff[Blocks.FURNACE] + eff[Blocks.BED];
            int light = eff[Blocks.TORCH] + eff[Blocks.LIGHT_BLOCK], building = eff[Blocks.BUILDING], portal = cnt[Blocks.PORTAL];
            present.put("machine", machine > 0); present.put("container", container > 0); present.put("furnace", furnace > 0);
            present.put("build", building > 20); present.put("light", light > 4);
            if (machine > 0) ev.add(new Evidence("machine", Family.BUILT, plural(machine, "redstone/utility part") + " - nothing generates these",
                (2.2 + 1.4 * Math.log(1 + machine)) * blockTrust * model.scale("machine")));
            if (portal > 0) ev.add(new Evidence("machine", Family.BUILT, "lit nether portal", 3.4));
            if (container > 0) ev.add(new Evidence("container", Family.BUILT, plural(container, "storage block"),
                (1.2 + 0.9 * Math.log(1 + container)) * blockTrust * model.scale("container")));
            if (furnace > 0) ev.add(new Evidence("furnace", Family.BUILT, plural(furnace, "furnace/bed") + " outside a village",
                (0.8 + 0.7 * Math.log(1 + furnace)) * blockTrust * model.scale("furnace")));
            if (light > 4 || undergroundBuilt > 6) {
                double w = (0.5 + 0.35 * Math.log(1 + light + undergroundBuilt / 2.0)) * blockTrust * model.scale("light");
                ev.add(new Evidence("light", Family.BUILT, plural(light, "placed light") + (undergroundBuilt > 6 ? ", built blocks underground" : ""), w));
            }
            if (building > 20) ev.add(new Evidence("build", Family.BUILT, plural(building, "crafted building block"),
                Math.min(2.4, 0.45 * Math.log(1 + building / 4.0)) * blockTrust * model.scale("build")));
        }
        if (opt.excavation) {
            present.put("tunnel", tunnelVoxels > 0); present.put("strip", strip); present.put("room", rooms > 0); present.put("inhabited", inhabited > 0);
            if (tunnelVoxels > 0) {
                String kind = vertical > horizontal && vertical >= stairs ? "vertical shaft" : stairs > horizontal ? "staircase" : "tunnel";
                ev.add(new Evidence("tunnel", Family.EXCAVATION, plural(tunnelComps, "straight carved " + kind) + ", " + tunnelVoxels + " blocks",
                    Math.min(6.5, 2.2 * Math.log(1 + tunnelVoxels / 6.0)) * model.scale("tunnel")));
            }
            if (strip) ev.add(new Evidence("strip", Family.EXCAVATION, "parallel tunnels at regular spacing - strip mine", 3.6 * model.scale("strip")));
            if (rooms > 0) ev.add(new Evidence("room", Family.EXCAVATION, plural(rooms, "box-shaped room") + " cut into rock", Math.min(3.0, 1.8 + 0.5 * rooms) * model.scale("room")));
            if (inhabited > 0) ev.add(new Evidence("inhabited", Family.EXCAVATION, "lit/furnished void inside rock (" + inhabitedFurn + " fittings)",
                Math.min(4.0, 2.3 + 0.3 * inhabitedFurn) * model.scale("inhabited")));
        }
        if (opt.traces) {
            present.put("contradiction", in.lightMismatch >= 3); present.put("heightmap", in.heightMismatch >= 3);
            if (in.lightMismatch >= 3) ev.add(new Evidence("contradiction", Family.TRACE,
                plural(in.lightMismatch, "cell") + " the blocks call stone but the server's light says are open",
                Math.min(6.0, 1.6 + 0.8 * Math.log(1 + in.lightMismatch)) * model.scale("contradiction")));
            if (in.heightMismatch >= 3) ev.add(new Evidence("heightmap", Family.TRACE,
                plural(in.heightMismatch, "column") + " where the server's heightmap disagrees with the blocks",
                Math.min(4.5, 1.9 + 0.5 * Math.log(1 + in.heightMismatch)) * model.scale("heightmap")));
        }
        if (opt.life) {
            int itemsDeep = 0, frames = 0, players = 0, animals = 0, villagers = 0, carts = 0;
            for (ChunkSnapshot.Ent e : s.entities) {
                switch (e.kind()) {
                    case ChunkSnapshot.ENT_ITEM -> { if (e.y() < 0) itemsDeep++; }
                    case ChunkSnapshot.ENT_ITEM_FRAME, ChunkSnapshot.ENT_ARMOR_STAND, ChunkSnapshot.ENT_PAINTING -> frames++;
                    case ChunkSnapshot.ENT_PLAYER -> players++;
                    case ChunkSnapshot.ENT_ANIMAL -> animals++;
                    case ChunkSnapshot.ENT_VILLAGER -> villagers++;
                    case ChunkSnapshot.ENT_MINECART -> carts++;
                    default -> {}
                }
            }
            boolean mineCarts = mineshaft;
            present.put("entity", frames > 0 || itemsDeep > 2 || animals > 12 || villagers > 2 && !village);
            if (players > 0) { v.livePlayer = true; ev.add(new Evidence("player", Family.LIFE, plural(players, "player") + " in this chunk right now", 2.6 * model.scale("player"))); }
            if (frames > 0) ev.add(new Evidence("entity", Family.LIFE, plural(frames, "item frame/armor stand/painting"), 2.6 + 0.4 * Math.min(frames, 4)));
            if (itemsDeep > 2) ev.add(new Evidence("entity", Family.LIFE, plural(itemsDeep, "dropped item") + " below y 0", 1.0 + 0.8 * Math.log(1 + itemsDeep)));
            if (animals > 12) ev.add(new Evidence("entity", Family.LIFE, plural(animals, "penned animal") + " - herd is larger than spawns", 1.8));
            if (villagers > 2 && !village) ev.add(new Evidence("entity", Family.LIFE, plural(villagers, "villager") + " outside any village (trading hall)", 2.2));
            if (carts > 3 && !mineCarts) ev.add(new Evidence("entity", Family.LIFE, plural(carts, "minecart") + " outside a mineshaft", 1.6));
        }

        // ---- 6. combine ----
        EnumMap<Family, List<Evidence>> by = new EnumMap<>(Family.class);
        for (Evidence e : ev) by.computeIfAbsent(e.family, k -> new ArrayList<>()).add(e);
        double logit = PRIOR_LOGIT, builtLlr = 0, total = 0;
        int clusters = 0;
        for (var e : by.entrySet()) {
            List<Evidence> l = e.getValue();
            l.sort((a, b) -> Double.compare(b.llr, a.llr));
            double c = 0;
            for (int i = 0; i < l.size(); i++) c += l.get(i).llr * (i == 0 ? 1.0 : 0.5);
            logit += c;
            total += c;
            if (e.getKey() == Family.BUILT) builtLlr = c;
            if (c >= 1.5) clusters++;
            for (Evidence x : l) v.reasons.add(new Verdict.Reason(x.text, x.llr, x.family, false));
        }
        for (String x : explained) v.reasons.add(new Verdict.Reason("natural " + x + " signature absorbs matching blocks", 0, Family.NATURAL, true));
        if (suspect) v.reasons.add(new Verdict.Reason(in.oreAnomaly ? "block data looks rewritten (fake ore field, " + String.format("%.1f%%", in.oreFraction * 100) + " ore) - block evidence discounted"
            : "block data disagrees with light - block evidence discounted", 0, Family.TRACE, true));
        if (haveLight && !in.lightReliable) v.reasons.add(new Verdict.Reason("light data looks unreliable - light cross-check skipped", 0, Family.TRACE, true));

        v.logit = logit;
        v.baseLogit = logit;
        v.p = 1.0 / (1.0 + Math.exp(-logit));
        v.points = (int) Math.round(total);
        v.buildPct = total <= 0 ? 0 : Math.min(1.0, builtLlr / total);
        v.confidence = (v.p >= 0.75 && clusters >= 2 && !suspect) ? Confidence.HIGH : (v.p >= 0.40 && (clusters >= 2 || total >= 6)) ? Confidence.MEDIUM : Confidence.LOW;

        // evidence y-range
        for (int g : new int[] {Blocks.MACHINE, Blocks.PLAYER_ONLY, Blocks.CONTAINER, Blocks.FURNACE, Blocks.BED, Blocks.PORTAL, Blocks.TORCH, Blocks.LIGHT_BLOCK})
            if (cnt[g] > 0) { v.note(gMinY[g]); v.note(gMaxY[g]); }
        if (tunnelVoxels > 0) { v.note(tunnelMinY); v.note(tunnelMaxY); }
        if (in.lightMismatch >= 3 && lightMinY != Integer.MAX_VALUE) { v.note(lightMinY); v.note(lightMaxY); }

        // ---- 7. what is it? ----
        v.type = classify(v, cnt, tunnelVoxels, strip, inhabited, ev);
        model.observe(present, v.p);
        return v;
    }

    private static Type classify(Verdict v, int[] cnt, int tunnel, boolean strip, int inhabited, List<Evidence> ev) {
        if (v.p < 0.08) return Type.UNKNOWN;
        if (cnt[Blocks.PORTAL] > 0) return Type.PORTAL;
        int machines = cnt[Blocks.MACHINE] + cnt[Blocks.PLAYER_ONLY];
        int containers = cnt[Blocks.CONTAINER];
        if (strip) return Type.STRIP_MINE;
        if (machines >= 4 && containers < machines) return Type.FARM;
        if (containers >= 4 && cnt[Blocks.BED] + cnt[Blocks.FURNACE] == 0 && cnt[Blocks.BUILDING] < 40) return Type.STORAGE;
        if (cnt[Blocks.BED] + cnt[Blocks.FURNACE] > 0 || inhabited > 0 || containers + machines > 0 && cnt[Blocks.BUILDING] > 30) return Type.BASE;
        if (tunnel > 0) return Type.TUNNELS;
        if (v.integrity.lightMismatch >= 3) return Type.HIDDEN_VOID;
        if (v.livePlayer) return Type.ACTIVITY;
        return Type.UNKNOWN;
    }

    private static String plural(int n, String what) { return n + " " + what + (n == 1 ? "" : "s"); }

    /** Top of the natural terrain in a column, ignoring anything built on it (buildings must not look "underground"). */
    private static int terrainTop(ChunkSnapshot s, int x, int z) {
        for (int y = s.maxY() - 1; y >= s.minY; y--) {
            int g = s.get(x, y, z);
            if (g == Blocks.STONE || g == Blocks.SOIL || g == Blocks.ORE || g == Blocks.BEDROCK || g == Blocks.SNOW_ICE
                || g == Blocks.NETHER_END || g == Blocks.OBSIDIAN) return y + 1;
        }
        return s.minY;
    }

    private static int surfaceFromBlocks(ChunkSnapshot s, int x, int z) {
        for (int y = s.maxY() - 1; y >= s.minY; y--) {
            int g = s.get(x, y, z);
            if (g != Blocks.AIR && g != Blocks.CAVE_AIR) return y + 1;
        }
        return s.minY;
    }
}
