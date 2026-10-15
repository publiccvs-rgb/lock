package lock.basefinder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Game-thread adapter: copies a loaded chunk into a {@link ChunkSnapshot}. The only place the finder touches Minecraft. */
public final class McScan {
    private McScan() {}

    private static final Map<Block, Byte> GROUPS = new HashMap<>();
    private static final Map<Block, Boolean> GROWN = new HashMap<>();

    private static byte group(BlockState bs) {
        Block b = bs.getBlock();
        Byte g = GROUPS.get(b);
        if (g == null) {
            String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            g = (byte) Blocks.classify(path);
            GROUPS.put(b, g);
            GROWN.put(b, path.equals("amethyst_cluster") || path.equals("large_amethyst_bud"));
        }
        return g;
    }

    /** One pass over the entity list, bucketed by chunk, so each scan is O(1) for entities. */
    public static Map<Long, List<ChunkSnapshot.Ent>> bucketEntities(ClientLevel level) {
        Map<Long, List<ChunkSnapshot.Ent>> out = new HashMap<>();
        LocalPlayer me = net.minecraft.client.Minecraft.getInstance().player;
        for (Entity e : level.entitiesForRendering()) {
            if (e == me) continue;
            int kind = e instanceof ItemEntity ? ChunkSnapshot.ENT_ITEM
                : e instanceof AbstractMinecart ? ChunkSnapshot.ENT_MINECART
                : e instanceof AbstractBoat ? ChunkSnapshot.ENT_BOAT
                : e instanceof ArmorStand ? ChunkSnapshot.ENT_ARMOR_STAND
                : e instanceof ItemFrame ? ChunkSnapshot.ENT_ITEM_FRAME
                : e instanceof Painting ? ChunkSnapshot.ENT_PAINTING
                : e instanceof Player ? ChunkSnapshot.ENT_PLAYER
                : e instanceof AbstractVillager ? ChunkSnapshot.ENT_VILLAGER
                : e instanceof Animal ? ChunkSnapshot.ENT_ANIMAL : -1;
            if (kind < 0) continue;
            ChunkPos cp = e.chunkPosition();
            out.computeIfAbsent(Sites.key(cp.x, cp.z), k -> new ArrayList<>())
                .add(new ChunkSnapshot.Ent(kind, e.getBlockX(), e.getBlockY(), e.getBlockZ()));
        }
        return out;
    }

    public static ChunkSnapshot snapshot(ClientLevel level, LevelChunk chunk, List<ChunkSnapshot.Ent> entities) {
        int minY = level.getMinY();
        int n = chunk.getSectionsCount();
        ChunkPos cp = chunk.getPos();
        ChunkSnapshot s = new ChunkSnapshot(cp.x, cp.z, minY, n);
        s.dimension = level.dimension() == Level.OVERWORLD ? 0 : level.dimension() == Level.NETHER ? 1 : 2;
        s.gameTime = level.getGameTime();
        LevelLightEngine light = level.getLightEngine();
        int minSection = level.getMinSectionY();

        for (int i = 0; i < n; i++) {
            LevelChunkSection sec = chunk.getSection(i);
            int baseY = minY + i * 16;
            if (!sec.hasOnlyAir()) {
                byte[] g = new byte[4096];
                PalettedContainer<BlockState> st = sec.getStates();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            byte grp = group(st.get(x, y, z));
                            g[y << 8 | z << 4 | x] = grp;
                            if (grp == Blocks.AMETHYST) {
                                BlockState bs = st.get(x, y, z);
                                s.amethyst.add(new int[] {cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z, GROWN.get(bs.getBlock()) ? 1 : 0});
                            }
                        }
                    }
                }
                s.grp[i] = g;
            }
            SectionPos sp = SectionPos.of(cp, minSection + i);
            s.blockLight[i] = nibbles(light.getLayerListener(LightLayer.BLOCK).getDataLayerData(sp));
            s.skyLight[i] = nibbles(light.getLayerListener(LightLayer.SKY).getDataLayerData(sp));
        }

        s.serverSurface = new short[256];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++)
                s.serverSurface[z << 4 | x] = (short) (chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1 - minY);

        if (entities != null) s.entities.addAll(entities);
        return s;
    }

    private static byte[] nibbles(DataLayer layer) {
        if (layer == null || layer.isEmpty()) return null;
        byte[] out = new byte[4096];
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    out[y << 8 | z << 4 | x] = (byte) layer.get(x, y, z);
        return out;
    }
}
