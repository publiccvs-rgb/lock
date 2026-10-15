package lock.basefinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds enclosed open space inside a chunk and decides whether it was carved by a player.
 *
 * The test is geometric: a player tunnel is an extrusion - the 3x3x3 neighbourhood of a voxel is identical one
 * step further along some direction, both ways (axis, or a diagonal for stairs). Natural caves are carved from
 * noise and almost never satisfy that over a run. Walls are checked too: a straight corridor through plain
 * stone is a miner; a corridor lined with planks and rails is a mineshaft.
 */
public final class Voids {
    public static final int DIR_X = 0, DIR_Z = 1, DIR_Y = 2, DIR_STAIR = 3;

    public static final class Component {
        public int n, minX = 99, minY = Integer.MAX_VALUE, minZ = 99, maxX = -1, maxY = Integer.MIN_VALUE, maxZ = -1;
        public int regular;                 // voxels that are part of a perfect extrusion
        public final int[] dirVotes = new int[4];
        public int wallCarvable, wallMineshaft, wallStronghold, wallBuilt, wallTotal;
        public int furnishings, torches, containers, machines;
        public boolean touchesSide, flooded, big, openToSky;
        public double fill;                 // voxels / bounding box volume
        public double regularity() { return n == 0 ? 0 : regular / (double) n; }
        public int dominantDir() {
            int b = 0;
            for (int i = 1; i < 4; i++) if (dirVotes[i] > dirVotes[b]) b = i;
            return b;
        }
        public int sizeX() { return maxX - minX + 1; }
        public int sizeY() { return maxY - minY + 1; }
        public int sizeZ() { return maxZ - minZ + 1; }
    }

    private static final int MAX_VOXELS = 24000;
    // extrusion directions: three axes plus the four stair diagonals
    private static final int[][] DIRS = {{1, 0, 0}, {0, 0, 1}, {0, 1, 0}, {1, 1, 0}, {1, -1, 0}, {0, 1, 1}, {0, 1, -1}};

    private Voids() {}

    private static boolean open(int g) {
        return switch (g) {
            case Blocks.AIR, Blocks.CAVE_AIR, Blocks.TORCH, Blocks.LIGHT_BLOCK, Blocks.CONTAINER, Blocks.BED,
                 Blocks.FURNACE, Blocks.VILLAGE, Blocks.PLANT, Blocks.NATURAL_MISC -> true;
            default -> false;
        };
    }

    public static List<Component> analyze(ChunkSnapshot s) {
        int h = s.sections * 16;
        int[] surf = new int[256];
        for (int i = 0; i < 256; i++) surf[i] = terrainTop(s, i & 15, i >> 4);

        // open grid, y-major to match the snapshot layout
        boolean[] op = new boolean[h * 256];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    op[y << 8 | z << 4 | x] = s.minY + y < surf[z << 4 | x] && open(s.get(x, s.minY + y, z));
                }
            }
        }

        int[] comp = new int[h * 256];
        Arrays.fill(comp, -1);
        int[] queue = new int[h * 256];
        List<Component> out = new ArrayList<>();
        int[] voxels = new int[MAX_VOXELS + 1];

        for (int start = 0; start < op.length; start++) {
            if (!op[start] || comp[start] >= 0) continue;
            int id = out.size();
            Component c = new Component();
            int qh = 0, qt = 0, stored = 0;
            queue[qt++] = start;
            comp[start] = id;
            while (qh < qt) {
                int v = queue[qh++];
                int x = v & 15, z = (v >> 4) & 15, y = v >> 8;
                if (stored < MAX_VOXELS) voxels[stored++] = v;
                c.n++;
                if (x < c.minX) c.minX = x; if (x > c.maxX) c.maxX = x;
                if (y < c.minY) c.minY = y; if (y > c.maxY) c.maxY = y;
                if (z < c.minZ) c.minZ = z; if (z > c.maxZ) c.maxZ = z;
                if (x == 0 || x == 15 || z == 0 || z == 15) c.touchesSide = true;
                // a void whose top voxel sits right under the surface opens to the sky (a shaft or a cave mouth)
                if (s.minY + y == surf[z << 4 | x] - 1) c.openToSky = true;
                for (int d = 0; d < 6; d++) {
                    int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0), nz = z + (d == 4 ? 1 : d == 5 ? -1 : 0);
                    if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < 0 || ny >= h) continue;
                    int nv = ny << 8 | nz << 4 | nx;
                    if (op[nv]) {
                        if (comp[nv] < 0) { comp[nv] = id; queue[qt++] = nv; }
                    } else {
                        int g = s.get(nx, s.minY + ny, nz);
                        if (g == Blocks.WATER || g == Blocks.LAVA) c.flooded = true;
                        c.wallTotal++;
                        if (Blocks.carvable(g)) c.wallCarvable++;
                        else if (g == Blocks.MINESHAFT) c.wallMineshaft++;
                        else if (g == Blocks.STRONGHOLD || g == Blocks.NATURAL_STRUCT) c.wallStronghold++;
                        else if (g == Blocks.BUILDING || g == Blocks.MACHINE || g == Blocks.PLAYER_ONLY) c.wallBuilt++;
                    }
                }
                int g = s.get(x, s.minY + y, z);
                if (g == Blocks.TORCH || g == Blocks.LIGHT_BLOCK) c.torches++;
                else if (g == Blocks.CONTAINER) c.containers++;
                else if (g == Blocks.FURNACE || g == Blocks.BED) c.furnishings++;
            }
            out.add(c);
            if (c.n < 6) { c.big = true; continue; }
            c.fill = c.n / (double) (c.sizeX() * c.sizeY() * c.sizeZ());
            if (c.n > MAX_VOXELS) { c.big = true; continue; }
            measureRegularity(c, comp, id, voxels, stored, h);
        }
        out.removeIf(c -> c.n < 6);
        return out;
    }

    /** Voids are only interesting below the natural terrain; anything built above it is not a carved void. */
    private static int terrainTop(ChunkSnapshot s, int x, int z) {
        for (int y = s.maxY() - 1; y >= s.minY; y--) {
            int g = s.get(x, y, z);
            if (Blocks.carvable(g) || g == Blocks.BEDROCK || g == Blocks.OBSIDIAN) return y + 1;
        }
        return s.minY;
    }

    private static boolean in(int[] comp, int id, int x, int y, int z, int h) {
        return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < h && comp[y << 8 | z << 4 | x] == id;
    }

    private static int patch(int[] comp, int id, int x, int y, int z, int h) {
        int m = 0, bit = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++, bit++)
                    if (in(comp, id, x + dx, y + dy, z + dz, h)) m |= 1 << bit;
        return m;
    }

    /**
     * A voxel is "tunnel-regular" when its neighbourhood repeats along a direction (extrusion) AND the cross-section
     * there is a small, completely filled rectangle. Rounded, uneven natural tubes fail the second test even when
     * they happen to be straight for a stretch.
     */
    private static void measureRegularity(Component c, int[] comp, int id, int[] voxels, int stored, int h) {
        for (int i = 0; i < stored; i++) {
            int v = voxels[i];
            int x = v & 15, z = (v >> 4) & 15, y = v >> 8;
            int here = patch(comp, id, x, y, z, h);
            for (int d = 0; d < DIRS.length; d++) {
                int[] dd = DIRS[d];
                if (!in(comp, id, x + dd[0], y + dd[1], z + dd[2], h) || !in(comp, id, x - dd[0], y - dd[1], z - dd[2], h)) continue;
                if (patch(comp, id, x + dd[0], y + dd[1], z + dd[2], h) != here || patch(comp, id, x - dd[0], y - dd[1], z - dd[2], h) != here) continue;
                // the slice perpendicular to the (horizontal part of the) direction
                int axis = d == 0 || d == 3 || d == 4 ? 0 : d == 1 || d == 5 || d == 6 ? 2 : 1;
                if (!smallRectangle(comp, id, x, y, z, axis, h)) continue;
                c.regular++;
                c.dirVotes[d < 3 ? d : DIR_STAIR]++;
                break;
            }
        }
    }

    /** Flood the plane perpendicular to {@code axis} around (x,y,z); true if it is a full rectangle no bigger than 4x4. */
    private static boolean smallRectangle(int[] comp, int id, int x, int y, int z, int axis, int h) {
        int ua = axis == 0 ? 2 : 0, va = axis == 1 ? 2 : 1;      // plane axes: 0=x 1=y 2=z
        int[] p = {x, y, z};
        int minU = 99, maxU = -99, minV = 99, maxV = -99, area = 0;
        // 9x9 window, 4-connected flood
        boolean[][] seen = new boolean[9][9];
        int[] qu = new int[81], qv = new int[81];
        int qh = 0, qt = 0;
        qu[qt] = 4; qv[qt++] = 4; seen[4][4] = true;
        while (qh < qt) {
            int u = qu[qh], vv = qv[qh++];
            int[] q = p.clone();
            q[ua] += u - 4; q[va] += vv - 4;
            area++;
            minU = Math.min(minU, u); maxU = Math.max(maxU, u); minV = Math.min(minV, vv); maxV = Math.max(maxV, vv);
            if (maxU - minU >= 4 || maxV - minV >= 4) return false;
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] n : nb) {
                int nu = u + n[0], nv = vv + n[1];
                if (nu < 0 || nu > 8 || nv < 0 || nv > 8 || seen[nu][nv]) continue;
                int[] r = p.clone();
                r[ua] += nu - 4; r[va] += nv - 4;
                if (in(comp, id, r[0], r[1], r[2], h)) { seen[nu][nv] = true; qu[qt] = nu; qv[qt++] = nv; }
            }
        }
        return area == (maxU - minU + 1) * (maxV - minV + 1);
    }
}
