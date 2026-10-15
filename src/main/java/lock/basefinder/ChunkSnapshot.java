package lock.basefinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-data copy of one loaded chunk, taken on the game thread and analysed anywhere. Blocks are group ids
 * (see {@link Blocks}); light is the server's own nibble data and heightmaps are the server's own - they are kept
 * separate from the blocks on purpose, because they are the traces a block-rewriting plugin forgets about.
 */
public final class ChunkSnapshot {
    public static final int ENT_ITEM = 0, ENT_MINECART = 1, ENT_BOAT = 2, ENT_ARMOR_STAND = 3, ENT_ITEM_FRAME = 4,
        ENT_PAINTING = 5, ENT_PLAYER = 6, ENT_ANIMAL = 7, ENT_VILLAGER = 8, ENT_OTHER = 9;

    public record Ent(int kind, int x, int y, int z) {}

    public final int cx, cz, minY, sections;
    /** [section][y<<8 | z<<4 | x] -> group id. A null section is treated as all air. */
    public final byte[][] grp;
    /** Same indexing, 0..15; null when the server sent no light for that section. */
    public final byte[][] blockLight, skyLight;
    /** Server WORLD_SURFACE heightmap: first free y above the highest non-air block, per column (z<<4|x); null if unknown. */
    public short[] serverSurface;
    public final List<Ent> entities = new ArrayList<>();
    public int dimension;               // 0 overworld, 1 nether, 2 end
    public long gameTime;

    public ChunkSnapshot(int cx, int cz, int minY, int sections) {
        this.cx = cx; this.cz = cz; this.minY = minY; this.sections = sections;
        grp = new byte[sections][];
        blockLight = new byte[sections][];
        skyLight = new byte[sections][];
    }

    public int maxY() { return minY + sections * 16; }

    public int get(int x, int y, int z) {
        int sy = (y - minY) >> 4;
        if (sy < 0 || sy >= sections) return Blocks.AIR;
        byte[] s = grp[sy];
        return s == null ? Blocks.AIR : s[((y - minY) & 15) << 8 | z << 4 | x];
    }

    public void set(int x, int y, int z, int g) {
        int sy = (y - minY) >> 4;
        if (grp[sy] == null) grp[sy] = new byte[4096];
        grp[sy][((y - minY) & 15) << 8 | z << 4 | x] = (byte) g;
    }

    public int blockLight(int x, int y, int z) {
        int sy = (y - minY) >> 4;
        byte[] l = sy < 0 || sy >= sections ? null : blockLight[sy];
        return l == null ? -1 : l[((y - minY) & 15) << 8 | z << 4 | x];
    }

    public int skyLight(int x, int y, int z) {
        int sy = (y - minY) >> 4;
        byte[] l = sy < 0 || sy >= sections ? null : skyLight[sy];
        return l == null ? -1 : l[((y - minY) & 15) << 8 | z << 4 | x];
    }
}
